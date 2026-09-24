#!/usr/bin/env python3
"""Isolated semantic mutations. Only assertion failures count; compile/signal/timeout do not."""
import os
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]
CORE='planner_scoring planner_loudness planner_vocals planner_flex planner_beats planner_analysis planner_regions planner_observation planner_region_algebra planner_candidate_selector planner_candidate_observation planner_input_producers planner_produced_observation planner_selection_binding planner_source_bindings planner_source_context planner_transition_schedule planner_schedule_observation automation continuous_schedule playback_time parameter_catalog style_schedule style_timing style_curves'.split()
CHANGES=[
 ('scaled-beat-order','planner_transition_schedule','static_cast<double>(inBeats))/\n                     static_cast<double>(outBeats)','static_cast<double>(outBeats))/\n                     static_cast<double>(inBeats)'),
 ('effective-duration','planner_transition_schedule','(in.end-in.begin)/ratio','(in.end-in.begin)*ratio'),
 ('virtual-ramp-start','planner_transition_schedule','ContinuousAutomationValues(virtualRamp).valueAt(in.begin)','ContinuousAutomationValues(virtualRamp).valueAt(virtualStart)'),
 ('incoming-delay','planner_transition_schedule','std::max(0.0,ta-tb)','0.0'),
 ('outgoing-anchor','planner_transition_schedule','{0,ta},handoff,std::move(out)','{0,std::max(ta,tb)},handoff,std::move(out)'),
 ('reference-time-offset','planner_transition_schedule','incomingStart+(ta-incomingStart)*0.5','ta*0.5'),
 ('deadline-inclusive','planner_transition_schedule','*position>cue?','*position>=cue?'),
 ('revision-guard','planner_transition_schedule','currentRevision!=revision','currentRevision<revision'),
 ('execution-flag','planner_schedule_observation','w[36]=0;w[37]=1;','w[36]=1;w[37]=1;'),
]
def main():
    cxx=os.environ.get('CXX','c++');flags=['-std=c++17','-O1','-Wall','-Wextra','-Wpedantic','-Werror','-ffp-contract=off','-I',str(ROOT/'native/automix/include')]
    def build(command):
        r=subprocess.run(command,capture_output=True,text=True,timeout=60)
        if r.returncode:raise RuntimeError('Compilation is not a detection:\n'+r.stderr)
    with tempfile.TemporaryDirectory(prefix='lmg-schedule-mutations-') as name:
        tmp=Path(name);objects={}
        for m in CORE:
            out=tmp/f'{m}.o';build([cxx,*flags,'-c',str(ROOT/f'native/automix/src/{m}.cpp'),'-o',str(out)]);objects[m]=str(out)
        def test(module,src,mutant):
            testname='planner_schedule_observation_test.cpp' if module=='planner_schedule_observation' else 'planner_transition_schedule_test.cpp'
            exe=tmp/'test';other=[v for k,v in objects.items() if k!=module]
            build([cxx,*flags,str(src),str(ROOT/'native/automix/tests'/testname),*other,'-o',str(exe)])
            result=subprocess.run([str(exe)],capture_output=True,text=True,timeout=30)
            if not mutant:
                if result.returncode:raise RuntimeError('Baseline failed:'+result.stderr)
            elif result.returncode!=1 or 'assertion' not in result.stderr:
                raise RuntimeError('Mutation escaped or failed without assertion:'+result.stdout+result.stderr)
        for module in ('planner_transition_schedule','planner_schedule_observation'):
            test(module,ROOT/f'native/automix/src/{module}.cpp',False)
        for label,module,before,after in CHANGES:
            text=(ROOT/f'native/automix/src/{module}.cpp').read_text()
            if text.count(before)!=1:raise RuntimeError('Mutation anchor changed:'+label)
            src=tmp/f'{module}.cpp';src.write_text(text.replace(before,after));test(module,src,True);print('DETECTED',label)
    print(f'Schedule mutations: {len(CHANGES)}/{len(CHANGES)} detected by assertions')
if __name__=='__main__':main()
