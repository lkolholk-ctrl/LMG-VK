#include "lmg/automix/planner_input_producers.h"
#include <algorithm>
#include <cmath>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
namespace {
void check(bool ok){if(!ok)throw std::runtime_error("producer reference mismatch");}
std::vector<std::string> split(const std::string&s,char sep){std::vector<std::string>r;std::istringstream in(s);std::string p;
  while(std::getline(in,p,sep)){r.push_back(p);}return r;}
double number(const std::string&s){std::size_t pos=0;double v=std::stod(s,&pos);check(pos==s.size()&&std::isfinite(v));return v;}
std::optional<double> maybe(const std::string&s){return s=="_"?std::nullopt:std::optional<double>(number(s));}
void near(double a,double z){check(std::isfinite(a)&&std::isfinite(z)&&std::abs(a-z)<=1e-11*std::max(1.,std::abs(z)));}
void opt(std::optional<double>a,std::optional<double>z){check(bool(a)==bool(z));if(a)near(*a,*z);}
int integer(const std::string&s){double v=number(s);check(v>=0&&v<=4096&&std::trunc(v)==v);return static_cast<int>(v);}
PlannerSongPreparation prep(int bars,double bpm,int meter){std::vector<FlexEvent>e;
  for(int i=0;i<=bars*meter;++i)e.push_back({i*(60./bpm),i%(4*meter)==0?FlexTimeScale::extraLong:
    i%(2*meter)==0?FlexTimeScale::longTime:i%meter==0?FlexTimeScale::medium:FlexTimeScale::shortTime,0});
  PlannerSongPreparation p;p.status=PlannerPreparationStatus::prepared;p.durationMs=1000000;p.maps.structure=songStructureFromFlexEvents(e);return p;
}
}
int main(int argc,char**argv){try{
  check(argc==2);std::ifstream in(argv[1]);check(bool(in));std::string line;std::size_t n=0;
  check(bool(std::getline(in,line))&&line=="# LMG producer specification oracle v1; binary64 finite domain; NOT ARM execution");
  while(std::getline(in,line)){
    check(line.size()<32768&&!line.empty());auto row=split(line,'\t');check(row.size()>=3);
    try{
      if(row[0]=="D"){
        check(row.size()==3);auto e=split(row[2],',');check(e.size()==3);const auto r=plannerMusicKitDurationEnvelope(number(row[1]));
        near(r.preferredOffset,number(e[0]));near(r.maximumOutgoingDuration,number(e[1]));near(r.maximumIncomingDuration,number(e[2]));
      }else if(row[0]=="S"){
        check(row.size()==3);auto v=split(row[1],',');check(v.size()==3);
        auto r=normalizePlannerMusicKitScalarComposite(CloudComposite<double>{maybe(v[0]),maybe(v[1]),maybe(v[2])});
        if(row[2]=="_")check(!r);else{auto e=split(row[2],',');check(e.size()==3&&r);opt(r->main,maybe(e[0]));opt(r->beginning,maybe(e[1]));opt(r->ending,maybe(e[2]));}
      }else if(row[0]=="L"){
        check(row.size()==5);PlannerLoudnessMap points;
        if(row[1]!="_")for(const auto&p:split(row[1],';')){auto v=split(p,',');check(v.size()==2);points.push_back({number(v[1]),number(v[0])});}
        auto w=split(row[2],',');check(w.size()==2);opt(plannerLoudnessLinearSlope(points),maybe(row[3]));
        check(row[4]=="0"||row[4]=="1");check(plannerOutgoingSeedNonSilent(&points,{number(w[0]),number(w[1])})==(row[4]=="1"));
      }else if(row[0]=="G"){
        check(row.size()==3);auto v=split(row[1],',');check(v.size()==8);
        const auto a=prep(integer(v[0]),number(v[2]),integer(v[4])),z=prep(integer(v[1]),number(v[3]),integer(v[4]));
        std::optional<std::int64_t> count;if(v[5]!="_")count=integer(v[5]);
        const auto r=producePlannerCandidateSeeds(a,z,{{8,count}},{number(v[6]),number(v[7])});
        check(r.completeForResolvedInputs&&r.status!=PlannerSeedProductionStatus::resourceLimit);
        if(row[2]=="_")check(r.seeds.empty());else{auto seeds=split(row[2],';');check(seeds.size()==r.seeds.size());
          for(std::size_t i=0;i<seeds.size();++i){auto e=split(seeds[i],',');check(e.size()==5);const auto&s=r.seeds[i];
            check(s.outgoing.startEvent==static_cast<std::size_t>(integer(e[0]))&&s.outgoing.endEvent==static_cast<std::size_t>(integer(e[1]))&&
                  s.incoming.startEvent==static_cast<std::size_t>(integer(e[2]))&&s.incoming.endEvent==static_cast<std::size_t>(integer(e[3]))&&
                  static_cast<int>(s.incomingScale)==integer(e[4])&&s.incomingUnit==region_algebra::PlannerRegionUnit::beats);}}
      }else throw std::runtime_error("Unknown producer fixture kind");
    }catch(...){std::cerr<<"FAILED producer fixture row "<<n+1<<" kind="<<row[0]<<'\n';throw;}
    ++n;
  }
  check(in.eof()&&n==1685);
  std::cout<<"Producer independent specification oracle: "<<n<<"/"<<n<<" cases PASSED (not ARM execution)\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}
