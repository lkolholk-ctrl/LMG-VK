#!/usr/bin/env python3
"""Mutate isolated copies. Compilation failure, signal and timeout are NOT detections."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CORE = 'planner_scoring planner_loudness planner_vocals planner_flex planner_beats planner_analysis planner_regions planner_observation planner_region_algebra planner_candidate_selector planner_candidate_observation planner_input_producers planner_produced_observation planner_selection_binding planner_source_bindings'.split()

def main():
    source = (ROOT/'native/automix/src/planner_source_context.cpp').read_text()
    changes = [
        ('catalog-count-units', 'std::optional<std::int64_t>(*s.duration)', 'std::optional<std::int64_t>(std::int64_t(*s.duration)*2)'),
        ('missing-count-vs-zero', ':std::nullopt});', ':std::optional<std::int64_t>(0)});'),
        ('provider-field-role', 'reference-envelope.preferredOffset,', 'reference-envelope.maximumOutgoingDuration,'),
        ('duration-mapping', 'if(outRef!=static_cast<double>(*r.outgoingDurationMs) || inRef!=static_cast<double>(*r.incomingDurationMs))', 'if(false && (outRef!=static_cast<double>(*r.outgoingDurationMs) || inRef!=static_cast<double>(*r.incomingDurationMs)))'),
        ('spatial-not-silently-stereo', 'if(c.outgoingSpatial==PlannerSourceKnowledge::present || c.incomingSpatial==PlannerSourceKnowledge::present)', 'if(false && (c.outgoingSpatial==PlannerSourceKnowledge::present || c.incomingSpatial==PlannerSourceKnowledge::present))'),
        ('unknown-source-facts', 'if(v==PlannerSourceKnowledge::unknown) return Status::sourceFactsUnknown;', 'if(false && v==PlannerSourceKnowledge::unknown) return Status::sourceFactsUnknown;'),
        ('previous-state-not-dropped', 'if(c.outgoingPreviousState==PlannerSourceKnowledge::present || c.incomingPreviousState==PlannerSourceKnowledge::present)', 'if(false && (c.outgoingPreviousState==PlannerSourceKnowledge::present || c.incomingPreviousState==PlannerSourceKnowledge::present))'),
        ('generation-echo', '1,12,request.generation,request.revision,', '1,12,request.generation == 0 ? 1 : 0,request.revision,'),
        ('catalog-default-completeness', 'if(selected.status!=PlannerStyleResolutionStatus::resolved)', 'if(false && selected.status!=PlannerStyleResolutionStatus::resolved)'),
        ('execution-flag', 'need(result.size()<=kPlannerSourceMaxResponse);', 'result[12+scope.resolvedRequest.size()+9]=1; need(result.size()<=kPlannerSourceMaxResponse);'),
    ]
    with tempfile.TemporaryDirectory(prefix='lmg-source-context-mut-') as tmp:
        out = Path(tmp); cxx = os.environ.get('CXX','c++')
        flags = ['-std=c++17','-O1','-Wall','-Wextra','-Wpedantic','-Werror','-ffp-contract=off','-I',str(ROOT/'native/automix/include')]
        objects = []
        for name in CORE:
            obj = out/(name+'.o')
            subprocess.run([cxx,*flags,'-c',str(ROOT/f'native/automix/src/{name}.cpp'),'-o',str(obj)],check=True,timeout=45)
            objects.append(str(obj))
        for name, before, after in [('baseline','',''), *changes]:
            text = source
            if name != 'baseline':
                if source.count(before) != 1: raise RuntimeError('Mutation anchor changed: '+name)
                text = source.replace(before,after)
            src = out/'context.cpp'; exe = out/'test'; src.write_text(text)
            build = subprocess.run([cxx,*flags,str(src),str(ROOT/'native/automix/tests/planner_source_context_test.cpp'),*objects,'-o',str(exe)],capture_output=True,text=True,timeout=45)
            if build.returncode: raise RuntimeError('Compilation is not a detection: '+name+'\n'+build.stderr)
            result = subprocess.run([str(exe),'all'],capture_output=True,text=True,timeout=20)
            if name == 'baseline':
                if result.returncode: raise RuntimeError('Baseline failed: '+result.stderr)
            elif result.returncode != 1 or 'source context assertion' not in result.stderr:
                raise RuntimeError('Mutation escaped or no assertion: '+name+'\n'+result.stdout+result.stderr)
            else: print('DETECTED',name)
    print('Source-context mutations: 10/10 detected by assertions')
if __name__ == '__main__': main()
