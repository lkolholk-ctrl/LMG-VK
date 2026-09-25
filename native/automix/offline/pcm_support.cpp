#include "pcm_support.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <limits>
#include <locale>
#include <sstream>
#include <stdexcept>
#include <cerrno>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

namespace lmg::automix::offline {
namespace {
constexpr std::size_t maxSamples = 32U * 1024U * 1024U;
void need(bool v,const char* m){if(!v)throw std::invalid_argument(m);}
void put16(std::vector<unsigned char>& out,std::uint16_t v){out.push_back(v&255);out.push_back(v>>8);}
void put32(std::vector<unsigned char>& out,std::uint32_t v){for(unsigned i=0;i<4;++i)out.push_back((v>>(8*i))&255);}
std::uint16_t get16(const unsigned char* p){return std::uint16_t(p[0])|std::uint16_t(p[1])<<8;}
std::uint32_t get32(const unsigned char* p){return std::uint32_t(p[0])|std::uint32_t(p[1])<<8|std::uint32_t(p[2])<<16|std::uint32_t(p[3])<<24;}
void four(std::vector<unsigned char>& out,const char* s){out.insert(out.end(),s,s+4);}
void publish(const std::string& path,const unsigned char* data,std::size_t size){
  // Host-only POSIX target. O_EXCL + O_NOFOLLOW prevents overwriting user data.
  const int fd=::open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW,0600);
  if(fd<0)throw std::runtime_error("Cannot create output (exists, symlink or inaccessible): "+path);
  std::size_t done=0;bool closed=false;
  try {
    while(done<size){const auto n=::write(fd,data+done,size-done);if(n<0&&errno==EINTR)continue;
      if(n<=0)throw std::runtime_error("Output write failed");
      done+=static_cast<std::size_t>(n);}
    if(::fsync(fd)!=0)throw std::runtime_error("Output flush failed");
    const int rc=::close(fd);closed=true;if(rc!=0)throw std::runtime_error("Output close failed");
  }catch(...){if(!closed)::close(fd);::unlink(path.c_str());throw;}
}
}
void Format::validate() const {need(sampleRate>=8000&&sampleRate<=192000,"Unsupported PCM sample rate");need(channels==1||channels==2,"PCM must be mono or stereo");}
std::size_t checkedSamples(std::size_t n,unsigned c){need(c==1||c==2,"Invalid channel count");need(n<=maxSamples/c,"PCM allocation exceeds bench limit");return n*c;}
std::size_t roundedFrames(double seconds,std::uint32_t rate){
  Format{rate,1}.validate();need(std::isfinite(seconds)&&seconds>=0,"Invalid duration");
  const long double n=std::round(static_cast<long double>(seconds)*rate);
  need(n<=maxSamples,"PCM duration exceeds bench limit");return static_cast<std::size_t>(n);
}
Metrics measure(const std::vector<float>& pcm,Format f,std::size_t begin,std::size_t end){
  f.validate();need(pcm.size()%f.channels==0,"Incomplete PCM frame");const auto n=pcm.size()/f.channels;
  checkedSamples(n,f.channels);if(end==static_cast<std::size_t>(-1))end=n;need(begin<=end&&end<=n,"Invalid metrics interval");
  Metrics r;r.frames=end-begin;r.peakFrame=begin;long double squares=0,sum=0;
  for(auto i=begin*f.channels;i<end*f.channels;++i){const double v=pcm[i];need(std::isfinite(v),"Nonfinite PCM");
    const double a=std::abs(v);if(a>r.peak){r.peak=a;r.peakFrame=i/f.channels;}r.samplesAboveUnity+=a>1;
    sum+=v;squares+=static_cast<long double>(v)*v;}
  if(r.frames){const auto count=r.frames*f.channels;r.mean=static_cast<double>(sum/count);r.rms=std::sqrt(static_cast<double>(squares/count));}return r;
}
Difference compare(const std::vector<float>& a,const std::vector<float>& b){
  need(a.size()==b.size(),"Different PCM lengths");Difference d;long double sum=0;
  for(std::size_t i=0;i<a.size();++i){need(std::isfinite(a[i])&&std::isfinite(b[i]),"Nonfinite comparison");
    const double v=double(a[i])-double(b[i]);if(std::abs(v)>d.maxAbsolute){d.maxAbsolute=std::abs(v);d.atSample=i;}sum+=static_cast<long double>(v)*v;}
  if(!a.empty())d.rms=std::sqrt(static_cast<double>(sum/a.size()));
  return d;
}
std::vector<float> signal(Format f,std::size_t n,std::uint32_t seed){
  f.validate();std::vector<float> out(checkedSamples(n,f.channels));std::uint32_t rng=seed?seed:1;
  for(std::size_t i=0;i<n;++i){rng^=rng<<13;rng^=rng>>17;rng^=rng<<5;
    const double noise=(double(rng)/4294967296.0-0.5)*0.002;
    for(unsigned c=0;c<f.channels;++c){const double t=double(i)/f.sampleRate;
      out[i*f.channels+c]=static_cast<float>(0.04*std::sin(6.283185307179586*(311+137*c)*t)+0.01*std::sin(6.283185307179586*997*t)+noise);}}
  return out;
}
std::vector<float> impulse(Format f,std::size_t n,std::size_t at,float amplitude){
  f.validate();need(at<n&&std::isfinite(amplitude),"Invalid impulse");std::vector<float> out(checkedSamples(n,f.channels));out[at*f.channels]=amplitude;return out;
}
std::string jsonNumber(double v){need(std::isfinite(v),"Nonfinite JSON value");std::ostringstream s;s.imbue(std::locale::classic());s<<std::setprecision(17)<<v;return s.str();}
void writeTextExclusive(const std::string& path,const std::string& text){publish(path,reinterpret_cast<const unsigned char*>(text.data()),text.size());}
void writeFloatWav(const std::string& path,const std::vector<float>& pcm,Format f){
  f.validate();const auto m=measure(pcm,f);need(pcm.size()<=((std::uint64_t{1}<<32)-60)/4,"WAV too large");
  const auto bytes=static_cast<std::uint32_t>(pcm.size()*4);std::vector<unsigned char> out;out.reserve(56+bytes);
  four(out,"RIFF");put32(out,48+bytes);four(out,"WAVE");four(out,"fmt ");put32(out,16);put16(out,3);put16(out,static_cast<std::uint16_t>(f.channels));
  put32(out,f.sampleRate);put32(out,f.sampleRate*f.channels*4);put16(out,static_cast<std::uint16_t>(f.channels*4));put16(out,32);
  four(out,"fact");put32(out,4);put32(out,static_cast<std::uint32_t>(m.frames));four(out,"data");put32(out,bytes);
  static_assert(sizeof(float)==4&&std::numeric_limits<float>::is_iec559,"IEEE float32 required");
  for(float v:pcm){std::uint32_t bits;std::memcpy(&bits,&v,4);put32(out,bits);}publish(path,out.data(),out.size());
}
std::vector<float> readFloatWav(const std::string& path,Format expected){
  expected.validate();std::ifstream f(path,std::ios::binary|std::ios::ate);if(!f)throw std::runtime_error("Cannot open WAV");
  const auto length=f.tellg();need(length>=44&&length<=static_cast<std::streamoff>(maxSamples*4+1024*1024),"Invalid WAV size");
  std::vector<unsigned char> bytes(static_cast<std::size_t>(length));f.seekg(0);need(bool(f.read(reinterpret_cast<char*>(bytes.data()),length)),"Short WAV read");
  need(std::memcmp(bytes.data(),"RIFF",4)==0&&std::memcmp(bytes.data()+8,"WAVE",4)==0,"Not a RIFF WAVE");
  need(std::uint64_t(get32(bytes.data()+4))+8==bytes.size(),"RIFF extent mismatch");
  bool fmt=false,data=false;std::size_t offset=12;std::vector<float> result;
  while(offset<bytes.size()){
    need(bytes.size()-offset>=8,"Truncated WAV chunk");const auto* p=bytes.data()+offset;const auto size=get32(p+4);offset+=8;
    need(size<=bytes.size()-offset,"WAV chunk overflow");const auto* body=bytes.data()+offset;
    if(std::memcmp(p,"fmt ",4)==0){need(!fmt&&size>=16,"Duplicate/short WAV format");fmt=true;
      need(get16(body)==3&&get16(body+2)==expected.channels&&get32(body+4)==expected.sampleRate&&get16(body+14)==32,"WAV format mismatch (float32 only)");
      need(get16(body+12)==expected.channels*4&&get32(body+8)==expected.sampleRate*expected.channels*4,"Invalid WAV stride");
    } else if(std::memcmp(p,"data",4)==0){need(fmt&&!data&&size%4==0,"WAV data order/size");data=true;
      need((size/4)%expected.channels==0,"Incomplete WAV frame");checkedSamples(size/4/expected.channels,expected.channels);result.resize(size/4);
      for(std::size_t i=0;i<result.size();++i){const auto bits=get32(body+4*i);std::memcpy(&result[i],&bits,4);}
    }
    const auto padded=std::uint64_t(size)+(size&1U);need(padded<=bytes.size()-offset,"Missing WAV padding");offset+=static_cast<std::size_t>(padded);
  }
  need(fmt&&data,"WAV format/data missing");(void)measure(result,expected);return result;
}
} // namespace lmg::automix::offline
