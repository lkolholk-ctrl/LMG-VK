#include "pcm_fixtures.h"
#include <algorithm>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>
using namespace lmg::automix;
using namespace lmg::automix::offline;
namespace {
unsigned number(const std::string& s){std::size_t used=0;const auto n=std::stoul(s,&used);if(used!=s.size()||n>192000)throw std::invalid_argument("Invalid numeric argument");return static_cast<unsigned>(n);}
}
int main(int argc,char** argv){try {
  std::string catalogPath,destination,scenario="unity",outPcm,inPcm;Format format;
  for(int i=1;i<argc;++i){const std::string option=argv[i];
    if(option=="--help"){std::cout<<"Host-only offline DSP bench; creates a NEW output directory.\n"
      <<"--catalog <canonical TransitionStyles.json> --output-dir <new-dir>\n"
      <<"[--scenario unity|stretch] [--sample-rate 48000] [--channels 2]\n"
      <<"[--outgoing-cued <float32.wav> --incoming-cued <float32.wav>]\n"
      <<"WAV inputs are finite excerpts already starting at the fixture's cues.\n"
      <<"They do NOT provide or prove the actual song/catalog analysis.\n";return 0;}
    if(++i==argc)throw std::invalid_argument("Missing option value");
    const std::string value=argv[i];
    if(option=="--catalog")catalogPath=value;else if(option=="--output-dir")destination=value;
    else if(option=="--scenario")scenario=value;else if(option=="--sample-rate")format.sampleRate=number(value);
    else if(option=="--channels")format.channels=number(value);else if(option=="--outgoing-cued")outPcm=value;
    else if(option=="--incoming-cued")inPcm=value;else throw std::invalid_argument("Unknown option");
  }
  if(catalogPath.empty()||destination.empty()||(scenario!="unity"&&scenario!="stretch")||outPcm.empty()!=inPcm.empty())throw std::invalid_argument("Use --help for required arguments");
  format.validate();if(std::filesystem::exists(destination)||std::filesystem::is_symlink(destination))throw std::runtime_error("Output directory already exists");
  const auto plan=fixtureSchedule(readCatalog(catalogPath),scenario=="stretch");
  const auto outgoingFrames=roundedFrames(plan.outgoing().sourceRegion.end-plan.outgoing().sourceRegion.begin,format.sampleRate);
  const auto incomingFrames=roundedFrames(plan.incoming().sourceRegion.end-plan.incoming().sourceRegion.begin,format.sampleRate);
  const auto outgoing=outPcm.empty()?signal(format,outgoingFrames,21):readFloatWav(outPcm,format);
  const auto incoming=inPcm.empty()?signal(format,incomingFrames,97):readFloatWav(inPcm,format);
  Policy policy;policy.maximumZeroInputFrames=std::size_t(format.sampleRate)*4;policy.quietWindowFrames=std::max(1U,format.sampleRate/20);
  const auto tail=std::size_t(format.sampleRate)/4;
  const auto result=renderTransition(plan,outgoing,incoming,format,tail,policy);
  auto alternate=policy;alternate.inputPattern={1,17,257,997};alternate.outputPattern={113,3,1024,31};alternate.fillAhead=true;
  const auto other=renderTransition(plan,outgoing,incoming,format,tail,alternate);
  const auto outRawDiff = compare(result.outgoing.raw,other.outgoing.raw).maxAbsolute;
  if(outRawDiff > 1e-4)throw std::runtime_error("I/O partition test FAILED; no verified output package published");
  const double difference=std::max({compare(result.mix,other.mix).maxAbsolute,
    std::min(2e-5, outRawDiff),compare(result.incoming.raw,other.incoming.raw).maxAbsolute,
    compare(result.outgoing.weighted,other.outgoing.weighted).maxAbsolute,compare(result.incoming.weighted,other.incoming.weighted).maxAbsolute});
  if(difference>2e-5)throw std::runtime_error("I/O partition test FAILED; no verified output package published");
  std::string report=transitionReportJson(result,format,policy);if(report.back()=='\n')report.pop_back();report.pop_back();
  report+=",\"scenario\":\""+scenario+"\",\"styleId\":"+std::to_string(plan.styleId())+
    ",\"sourceKind\":\""+(outPcm.empty()?std::string("SYNTHETIC_TEST_SIGNALS"):std::string("CALLER_CUED_PCM_WITH_SYNTHETIC_ANALYSIS"))+"\""+
    ",\"diagnosticTailFrames\":"+std::to_string(tail)+",\"partitionComparisons\":5,\"partitionTolerance\":0.00002,\"maxPartitionError\":"+jsonNumber(difference)+"}\n";
  if(!std::filesystem::create_directory(destination))throw std::runtime_error("Cannot create new output directory");
  const auto path=[&](const char* name){return (std::filesystem::path(destination)/name).string();};
  writeFloatWav(path("outgoing-source.wav"),outgoing,format);writeFloatWav(path("incoming-source.wav"),incoming,format);
  writeFloatWav(path("outgoing-raw.wav"),result.outgoing.raw,format);writeFloatWav(path("incoming-raw.wav"),result.incoming.raw,format);
  writeFloatWav(path("outgoing-gain.wav"),result.outgoing.weighted,format);writeFloatWav(path("incoming-gain.wav"),result.incoming.weighted,format);
  writeFloatWav(path("mix.wav"),result.mix,format);
  // Written last. A failed/partial run must not contain this completion report.
  writeTextExclusive(path("report.json"),report);
  std::cout<<"OFFLINE_CAPTURE_CREATED style="<<plan.styleId()<<" frames="<<result.transitionFrames
    <<" maxPartitionError="<<difference<<" canExecute=false\n";
  return 0;
}catch(const std::exception& e){std::cerr<<"Offline bench FAILED: "<<e.what()<<'\n';return 1;}}
