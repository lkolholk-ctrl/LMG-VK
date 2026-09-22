#include "lmg/automix/styles.h"
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f){T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.good());return v;}
template<class F> void rejects(F f){bool yes=false;try{f();}catch(const std::invalid_argument&){yes=true;}CHECK(yes);}
int main(){try{
  std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/style_timing.bin",std::ios::binary);
  const auto count=read<std::uint32_t>(f);CHECK(count==126);
  for(unsigned i=0;i<count;++i){
    const SecondsWindow parent{read<double>(f),read<double>(f)};
    const StyleTime ps{read<double>(f),read<double>(f)},pe{read<double>(f),read<double>(f)};
    const StyleTime rs{read<double>(f),read<double>(f)},re{read<double>(f),read<double>(f)};
    const double expected[2]={read<double>(f),read<double>(f)};
    StyleInstruction instruction{"GAIN",ps,pe,{{"out_gain",rs,re,{1,{}},{0,{}},"linear"}}};
    const auto windows=resolveStyleWindows({instruction},parent);CHECK(windows.size()==1);
    const double actual[2]={windows[0].begin,windows[0].end};
    if(std::memcmp(actual,expected,sizeof actual)){
      std::cerr<<"ARM window mismatch at "<<i<<'\n';throw std::runtime_error("Timing mismatch");
    }
  }
  CHECK(f.peek()==std::char_traits<char>::eof());
  const auto absent=resolveStyleWindow({5,13},{.25,{}},{.75,{}});
  CHECK(absent.begin==7 && absent.end==11);
  rejects([]{resolveStyleWindows({}, {2,1});});
  rejects([]{resolveStyleWindow({0,1},{-.1,{}},{1,{}});});
  rejects([]{resolveStyleWindow({0,1},{0,{}},{1,std::numeric_limits<double>::infinity()});});
  std::cout<<"126 ARM style timing cases passed\n";
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
