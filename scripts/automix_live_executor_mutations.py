#!/usr/bin/env python3
"""Isolated native mutations. Baseline must pass. Compilation failures/signals/timeouts are NOT detections."""
import argparse, os, pathlib, shlex, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
MUTATIONS=[
 ('double-output-gain','*gains[side];','*gains[side]*gains[side];'),
 ('starvation-becomes-zero-padding','if(!s.eos)break;','if(false)break;'),
 ('missing-time-map','TimePitchTimeMapView{&mapping,Mapping::source}','TimePitchTimeMapView{}'),
 ('wrong-pcm16-saturation','if(v>32767){v=32767;','if(v>32767){v=32766;'),
 ('executable-plan-accepted','p[36]==0&&p[37]==1','p[36]>=0&&p[37]==1'),
]
def run(cmd,timeout=120):return subprocess.run(cmd,capture_output=True,text=True,timeout=timeout)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build-dir',type=pathlib.Path,required=True);a=p.parse_args()
 libraries=list(a.build_dir.rglob('liblmg_automix.a'))
 if len(libraries)!=1:raise SystemExit('Need exactly one complete built liblmg_automix.a')
 src=ROOT/'native/automix/src/live_pcm_executor.cpp';test=ROOT/'native/automix/tests/live_pcm_executor_test.cpp'
 original=src.read_text();cxx=shlex.split(os.environ.get('CXX','c++'))
 with tempfile.TemporaryDirectory(prefix='automix-live-mutants-') as d:
  root=pathlib.Path(d)
  for name,old,new in [('baseline',None,None),*MUTATIONS]:
   text=original
   if old is not None:
    if text.count(old)!=1:raise SystemExit('Changed mutation anchor: '+name)
    text=text.replace(old,new)
   source=root/'live.cpp';source.write_text(text);exe=root/'case'
   result=run(cxx+['-std=c++17','-O1','-g','-ffp-contract=off','-I',str(ROOT/'native/automix/include'),str(source),str(test),str(libraries[0]),'-pthread','-o',str(exe)])
   if result.returncode:raise SystemExit('Compilation failure, NOT detection: '+name+'\n'+result.stderr)
   result=run([str(exe)],60);text=result.stdout+result.stderr
   if name=='baseline':
    if result.returncode or 'LIVE_PCM_DSP_TESTS_PASSED' not in text:raise SystemExit('Baseline failed\n'+text)
   elif result.returncode!=1 or 'ASSERT: ' not in text:raise SystemExit('NOT assertion-detected: '+name+'\n'+text)
   print('PASS '+name,flush=True)
 print('LIVE_EXECUTOR_MUTATIONS_VERIFIED 5/5; originals unchanged')
if __name__=='__main__':main()
