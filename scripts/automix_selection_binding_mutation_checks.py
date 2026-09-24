#!/usr/bin/env python3
"""Isolated semantic mutations. Build errors, signals and timeouts are NOT detections."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]
MODULES=('planner_scoring planner_loudness planner_vocals planner_flex planner_beats planner_analysis '
 'planner_regions planner_observation planner_region_algebra planner_candidate_selector '
 'planner_candidate_observation planner_input_producers planner_produced_observation planner_source_bindings').split()
def run(args,**kwargs):return subprocess.run(args,check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=60,**kwargs)
def main():
 cxx=os.environ.get('CXX','c++');flags=['-std=c++17','-O1','-ffp-contract=off','-I',str(ROOT/'native/automix/include')]
 source=(ROOT/'native/automix/src/planner_selection_binding.cpp').read_text()
 mutations=[('execution-flag','  w[7]=q.explicitResolvedScope?0:static_cast<std::int64_t>(kPlannerRemainingDefaultBindings);','  w[9]=1; w[7]=q.explicitResolvedScope?0:static_cast<std::int64_t>(kPlannerRemainingDefaultBindings);'),
  ('revision-echo','w[4]=q.revision;','w[4]=0;'),
  ('default-blocker','if (!q.explicitResolvedScope) return empty(q);','if (!q.explicitResolvedScope) { auto w=empty(q); w[7]=0; return w; }'),
  ('budget-binding','q.workBudget=static_cast<std::uint64_t>(w[9]);','q.workBudget=kPlannerProducerWorkBudget;'),
  ('descriptor-presence','require(present==1?bars>=0:bars==0);','require(bars>=0);')]
 with tempfile.TemporaryDirectory(prefix='lmg-binding-mutations-') as d:
  out=Path(d);objects=[]
  for module in MODULES:
   obj=out/(module+'.o');run([cxx,*flags,'-c',str(ROOT/f'native/automix/src/{module}.cpp'),'-o',str(obj)]);objects.append(str(obj))
  for label,old,new in [('baseline',None,None),*mutations]:
   if old is not None and source.count(old)!=1:raise RuntimeError('Mutation target changed: '+label)
   file=out/'binding.cpp';file.write_text(source if old is None else source.replace(old,new))
   exe=out/'test';run([cxx,*flags,str(file),str(ROOT/'native/automix/tests/planner_selection_binding_test.cpp'),*objects,'-o',str(exe)])
   r=subprocess.run([str(exe)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=15)
   if old is None:
    if r.returncode!=0:raise RuntimeError('Baseline failed: '+r.stdout)
   elif r.returncode!=1 or 'selection binding assertion' not in r.stdout:raise RuntimeError('Not an assertion detection: '+label+'\n'+r.stdout)
   else:print('DETECTED',label)
  # A same-epoch late error must not cancel the newer bound calculation.
  kt=ROOT/'app/src/main/kotlin/com/lmg/vk/engine/automix/observation'
  tests=ROOT/'app/src/test/kotlin/com/lmg/vk/engine/automix/observation'
  compiler=Path(shutil.which('kotlinc')).resolve();jar=os.environ.get('COROUTINES_JAR',str(compiler.parents[1]/'lib/kotlinx-coroutines-core-jvm.jar'))
  original=(kt/'ObservationPipeline.kt').read_text()
  old='if (bindingRevision == revision) reject(epoch, reason, detail)'
  if original.count(old)!=1:raise RuntimeError('Revision guard moved')
  changed=out/'ObservationPipeline.kt';changed.write_text(original.replace(old,'reject(epoch, reason, detail)'))
  exe=out/'mutation.jar'
  run([str(compiler),str(changed),str(kt/'ResolvedPlannerScope.kt'),str(kt/'PlannerSelectionReport.kt'),
       str(tests/'ObservationBindingScenarios.kt'),'-cp',jar,'-include-runtime','-d',str(exe)])
  r=subprocess.run(['java','-cp',str(exe)+os.pathsep+jar,'com.lmg.vk.engine.automix.observation.ObservationBindingScenarios'],
   stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=20)
  if r.returncode!=1 or 'Check failed' not in r.stdout or 'TimeoutCancellationException' in r.stdout:
   raise RuntimeError('Late-error mutation not detected by assertion: '+r.stdout)
  print('DETECTED same-epoch-late-error')
 print('Selection binding mutations: 6/6 detected by assertions')
if __name__=='__main__':main()
