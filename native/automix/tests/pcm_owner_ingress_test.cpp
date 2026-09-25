#include "lmg/automix/pcm_owner_ingress.h"
#include <atomic>
#include <cmath>
#include <cstring>
#include <functional>
#include <iostream>
#include <limits>
#include <random>
#include <stdexcept>
#include <thread>
using namespace lmg::automix;
namespace {
unsigned groups=0;
#define CHECK(x) do { if(!(x))throw std::runtime_error(std::string("ASSERT: ") + #x); } while(false)
void test(const char* name,const std::function<void()>& run){run();++groups;std::cout<<"PASS "<<name<<'\n';}
template<class F>void rejected(F run){bool bad=false;try{run();}catch(const std::exception&){bad=true;}CHECK(bad);}
std::vector<std::uint8_t> bytes(std::uint32_t frames,unsigned channels,OwnerEncoding e,unsigned start=0){
 std::vector<std::uint8_t> b(static_cast<std::size_t>(frames)*channels*static_cast<unsigned>(e));
 for(unsigned i=0;i<frames*channels;++i){
  const int raw=int(((start*channels+i)*97)%60001)-30000;
  const auto at=static_cast<std::size_t>(i)*static_cast<unsigned>(e);
  std::uint32_t bits;
  if(e==OwnerEncoding::pcm16le)bits=static_cast<std::uint16_t>(raw);
  else {const float f=float(raw)/32768.f;std::memcpy(&bits,&f,4);}
  for(unsigned n=0;n<static_cast<unsigned>(e);++n)b[at+n]=std::uint8_t(bits>>(n*8));
 }
 return b;
}
void initial(PcmOwnerIngress& q,const std::vector<std::uint8_t>& x,std::int64_t cue=4,std::uint64_t ticket=1){
 q.stage(ticket,1,0,{x.data(),x.size(),0,cue},{x.data(),x.size(),0,cue});
}
void conserved(const OwnerQueueStats& s){CHECK(s.accepted==s.read+s.discarded+s.queued);CHECK(s.nextReadFrame<=s.nextWriteFrame);}
void randomStream(OwnerEncoding encoding,unsigned channels,unsigned seed){
 constexpr unsigned total=2048,capacity=17,initialFrames=8;
 PcmOwnerIngress q(channels,encoding,capacity);auto data=bytes(total,channels,encoding);
 const unsigned bpf=channels*static_cast<unsigned>(encoding);
 q.stage(1,8,9,{data.data(),initialFrames*bpf,0,4},{data.data(),initialFrames*bpf,0,4});CHECK(q.commit(1));
 std::mt19937 rng(seed);std::array<unsigned,2> sent{initialFrames,initialFrames},read{};
 std::vector<float> out(capacity*channels);
 unsigned calls=0;
 while(read[0]<total||read[1]<total){
  CHECK(++calls<20000);
  for(unsigned side=0;side<2;++side){
   if(sent[side]<total){
    const unsigned offer=std::min<unsigned>(1+rng()%capacity,total-sent[side]);
    const auto n=q.push(side,data.data()+sent[side]*bpf,offer*bpf,sent[side]);CHECK(n<=offer);sent[side]+=n;
   }
   const auto r=q.read(side,out.data(),1+rng()%capacity);CHECK(r.firstFrame==read[side]);
   if(r.frames){CHECK(r.prefix==(read[side]<4));CHECK(!r.prefix||read[side]+r.frames<=4);}
   for(unsigned i=0;i<r.frames*channels;++i){
    const int raw=int(((read[side]*channels+i)*97)%60001)-30000;
    CHECK(out[i]==float(raw)/32768.f);
   }
   read[side]+=r.frames;conserved(q.stats(side));
  }
 }
 for(unsigned s=0;s<2;++s){auto v=q.stats(s);CHECK(v.accepted==total&&v.read==total&&v.queued==0&&v.discarded==0);}
}
}
int main(int argc,char** argv){try{
 const auto e=OwnerEncoding::float32le;auto x=bytes(8,1,e);std::vector<float> out(32);
 if(argc==2&&std::string(argv[1])=="--random"){
  for(auto enc:{OwnerEncoding::pcm16le,e})for(unsigned ch:{1U,2U})for(unsigned seed=0;seed<128;++seed)randomStream(enc,ch,seed);
  std::cout<<"PASS 512 randomized two-source conservation streams\n";return 0;
 }
 test("configurationDomain",[&]{rejected([&]{PcmOwnerIngress q(0,e,16);});rejected([&]{PcmOwnerIngress q(3,e,16);});rejected([&]{PcmOwnerIngress q(1,e,0);});rejected([&]{PcmOwnerIngress q(1,e,262145);});rejected([&]{PcmOwnerIngress q(1,static_cast<OwnerEncoding>(3),16);});});
 test("invisibleBeforeCommit",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);CHECK(q.phase()==OwnerIngressPhase::staged&&q.stats(0).queued==0&&q.stats(1).queued==0);rejected([&]{q.read(0,out.data(),4);});});
 test("bothSidesCommit",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);CHECK(q.commit(1));CHECK(q.stats(0).queued==8&&q.stats(1).queued==8);});
 test("wrongTicketNoMutation",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);CHECK(!q.commit(2)&&!q.abort(2));CHECK(q.phase()==OwnerIngressPhase::staged);CHECK(q.commit(1));});
 test("noDoubleCommit",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);CHECK(q.commit(1));CHECK(!q.commit(1));CHECK(q.stats(0).accepted==8);});
 test("precommitRollbackRestage",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);CHECK(q.abort(1));CHECK(q.phase()==OwnerIngressPhase::empty);initial(q,x,4,2);CHECK(q.commit(2));});
 test("postcommitAbortTerminal",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);q.commit(1);CHECK(q.abort(1));CHECK(q.phase()==OwnerIngressPhase::aborted);CHECK(q.stats(0).discarded==8);rejected([&]{initial(q,x,4,2);});rejected([&]{q.read(0,out.data(),1);});});
 test("prefixNeverCrossesCue",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);q.commit(1);auto a=q.read(0,out.data(),16);CHECK(a.frames==4&&a.prefix&&a.firstFrame==0);auto b=q.read(0,out.data(),16);CHECK(b.frames==4&&!b.prefix&&b.firstFrame==4);});
 test("zeroPrefix",[&]{PcmOwnerIngress q(1,e,16);initial(q,x,0);q.commit(1);auto a=q.read(0,out.data(),16);CHECK(!a.prefix&&a.frames==8);});
 test("lastFrameCue",[&]{PcmOwnerIngress q(1,e,16);initial(q,x,7);q.commit(1);CHECK(q.read(0,out.data(),16).frames==7);CHECK(q.read(0,out.data(),16).frames==1);});
 test("invalidSecondSideAtomic",[&]{PcmOwnerIngress q(1,e,16);auto y=x;y[0]=0;y[1]=0;y[2]=0xc0;y[3]=0x7f;rejected([&]{q.stage(1,1,0,{x.data(),x.size(),0,4},{y.data(),y.size(),0,4});});CHECK(q.phase()==OwnerIngressPhase::empty&&q.stats(0).queued==0);initial(q,x);CHECK(q.commit(1));});
 test("invalidCueAtomic",[&]{PcmOwnerIngress q(1,e,16);rejected([&]{initial(q,x,8);});rejected([&]{initial(q,x,-1);});CHECK(q.phase()==OwnerIngressPhase::empty);});
 test("invalidIdentity",[&]{PcmOwnerIngress q(1,e,16);for(auto t:{0U})rejected([&]{initial(q,x,4,t);});rejected([&]{q.stage(1,0,0,{x.data(),x.size(),0,4},{x.data(),x.size(),0,4});});});
 test("noOverwriteStage",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);rejected([&]{initial(q,x,4,2);});CHECK(q.commit(1));});
 test("capacityAndAlignment",[&]{PcmOwnerIngress q(1,e,4);rejected([&]{initial(q,x);});PcmOwnerIngress y(1,e,16);rejected([&]{y.stage(1,1,0,{x.data(),31,0,4},{x.data(),32,0,4});});});
 test("sourceOverflow",[&]{PcmOwnerIngress q(1,e,16);rejected([&]{q.stage(1,1,0,{x.data(),32,PcmOwnerIngress::maximumFrame-4,PcmOwnerIngress::maximumFrame-3},{x.data(),32,0,4});});});
 test("backpressureNoOverwrite",[&]{PcmOwnerIngress q(1,e,8);initial(q,x);q.commit(1);auto y=bytes(8,1,e,8);CHECK(q.push(0,y.data(),y.size(),8)==0);CHECK(q.stats(0).nextWriteFrame==8);});
 test("partialPushAndWrap",[&]{PcmOwnerIngress q(1,e,8);initial(q,x);q.commit(1);q.read(0,out.data(),3);auto y=bytes(8,1,e,8);CHECK(q.push(0,y.data(),y.size(),8)==3);CHECK(q.stats(0).nextWriteFrame==11);q.read(0,out.data(),8);auto b=q.read(0,out.data(),8);CHECK(b.frames==7&&b.firstFrame==4);conserved(q.stats(0));});
 test("noncontiguousRejected",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);q.commit(1);rejected([&]{q.push(0,x.data(),x.size(),9);});rejected([&]{q.push(0,x.data(),x.size(),0);});CHECK(q.stats(0).accepted==8);});
 test("invalidSide",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);q.commit(1);rejected([&]{q.read(2,out.data(),1);});rejected([&]{q.stats(3);});});
 test("zeroReadNoAdvance",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);q.commit(1);CHECK(q.read(0,nullptr,0).frames==0);CHECK(q.stats(0).nextReadFrame==0);});
 test("readBoundBeforeMutation",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);q.commit(1);rejected([&]{q.read(0,out.data(),17);});rejected([&]{q.read(0,nullptr,1);});CHECK(q.stats(0).queued==8);});
 test("pcm16Endpoints",[&]{PcmOwnerIngress q(1,OwnerEncoding::pcm16le,4);std::vector<std::uint8_t> b{0,128,255,127,0,0,0,64};initial(q,b,0);q.commit(1);CHECK(q.read(0,out.data(),4).frames==4);CHECK(out[0]==-1&&out[1]==32767.f/32768&&out[2]==0&&out[3]==.5f);});
 test("floatSignedZeroAndUnclipped",[&]{PcmOwnerIngress q(1,e,4);std::vector<std::uint8_t> b{0,0,0,128,0,0,0,64};initial(q,b,0);q.commit(1);q.read(0,out.data(),4);CHECK(std::signbit(out[0])&&out[1]==2.f);});
 test("wrongThreadRejected",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);std::atomic<bool> caught{false};std::thread t([&]{try{q.commit(1);}catch(const std::logic_error&){caught=true;}});t.join();CHECK(caught&&q.phase()==OwnerIngressPhase::staged);});
 test("conservationAfterPartialDiscard",[&]{PcmOwnerIngress q(1,e,16);initial(q,x);q.commit(1);q.read(0,out.data(),3);q.abort(1);auto a=q.stats(0),b=q.stats(1);CHECK(a.read==3&&a.discarded==5&&b.discarded==8);conserved(a);conserved(b);});
 std::cout<<"Owner ingress: "<<groups<<"/"<<groups<<" groups PASSED\n";
}catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<'\n';return 1;}}
