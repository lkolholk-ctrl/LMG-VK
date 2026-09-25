#include "pcm_support.h"
#include <cmath>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <unistd.h>
using namespace lmg::automix::offline;
namespace {
int checks=0;
void check(bool x){++checks;if(!x)throw std::runtime_error("PCM support assertion "+std::to_string(checks));}
template<class F>void rejects(F f){bool yes=false;try{f();}catch(const std::exception&){yes=true;}check(yes);}
}
int main(){std::filesystem::path temp;try{
  char name[]="/tmp/lmg-pcm-utils.XXXXXX";auto* made=::mkdtemp(name);check(made);temp=made;
  Format f{48000,2};f.validate();rejects([]{Format{0,1}.validate();});rejects([]{Format{48000,3}.validate();});
  check(checkedSamples(7,2)==14);rejects([]{checkedSamples(static_cast<std::size_t>(-1),2);});
  check(roundedFrames(0.5,48000)==24000);rejects([]{roundedFrames(-1,48000);});rejects([]{roundedFrames(std::numeric_limits<double>::infinity(),48000);});
  const std::vector<float> pcm{0.125f,-0.25f,0.5f,-0.0f};const auto m=measure(pcm,f);
  check(m.frames==2&&m.peak==.5&&m.peakFrame==1&&m.samplesAboveUnity==0);check(std::abs(m.mean-.09375)<1e-12);
  check(measure({},f).rms==0);check(measure(pcm,f,1,2).peak==.5);
  rejects([&]{measure(pcm,f,2,1);});rejects([&]{measure({1},f);});rejects([&]{measure({std::numeric_limits<float>::quiet_NaN(),0},f);});
  check(measure({1.25f,-2.0f},f).samplesAboveUnity==2);check(compare(pcm,pcm).maxAbsolute==0);
  rejects([&]{compare(pcm,{});});rejects([&]{compare({std::numeric_limits<float>::infinity()},{0});});
  check(compare({1},{.5f}).maxAbsolute==.5);check(signal(f,300,12)==signal(f,300,12));check(signal(f,300,12)!=signal(f,300,13));
  const auto imp=impulse(f,100,17);check(imp[34]==.125f&&imp[35]==0&&measure(imp,f).peakFrame==17);
  rejects([&]{impulse(f,10,10);});
  const auto wav=(temp/"test.wav").string();writeFloatWav(wav,pcm,f);const auto roundtrip=readFloatWav(wav,f);
  check(roundtrip==pcm&&std::signbit(roundtrip.back()));rejects([&]{writeFloatWav(wav,pcm,f);});
  rejects([&]{readFloatWav(wav,{44100,2});});rejects([&]{readFloatWav(wav,{48000,1});});
  auto bad=pcm;bad[0]=std::numeric_limits<float>::quiet_NaN();rejects([&]{writeFloatWav((temp/"bad.wav").string(),bad,f);});check(!std::filesystem::exists(temp/"bad.wav"));
  std::filesystem::create_symlink(temp/"test.wav",temp/"link.wav");rejects([&]{writeFloatWav((temp/"link.wav").string(),pcm,f);});check(readFloatWav(wav,f)==pcm);
  auto corrupt=[&](std::size_t offset,char value){std::ifstream in(wav,std::ios::binary);std::string b((std::istreambuf_iterator<char>(in)),{});b.at(offset)=value;
    auto name=temp/("bad-"+std::to_string(offset)+".wav");std::ofstream out(name,std::ios::binary);out.write(b.data(),b.size());out.close();rejects([&]{readFloatWav(name.string(),f);});};
  corrupt(0,'X');corrupt(4,0);corrupt(20,1);corrupt(22,3);corrupt(32,0);corrupt(34,16);corrupt(52,3);
  rejects([&]{writeTextExclusive(wav,"overwrite");});check(jsonNumber(.125)=="0.125");rejects([]{jsonNumber(std::numeric_limits<double>::quiet_NaN());});
  std::filesystem::remove_all(temp);std::cout<<"Offline PCM support: "<<checks<<" assertions PASSED\n";return 0;
}catch(const std::exception&e){if(!temp.empty())std::filesystem::remove_all(temp);std::cerr<<e.what()<<'\n';return 1;}}
