#!/usr/bin/env python3
"""Compile isolated semantic mutants; only assertion failures count as detections."""
import argparse, pathlib, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
MAIN=ROOT/'app/src/main/kotlin/com/lmg/vk/engine/automix/render'
TEST=ROOT/'app/src/test/kotlin/com/lmg/vk/engine/automix/render/RenderBoundaryScenarios.kt'
def run(args, **kw):return subprocess.run(args,text=True,capture_output=True,timeout=45,**kw)
source=(MAIN/'RenderBoundary.kt').read_text()
mutations=[
 ('source-uri-ignored','uri == other.uri &&','true &&','wrongUriDoesNotBind'),
 ('offered-not-accepted','(buffer.position()-retryInitialPosition).toLong()/f.bytesPerFrame','(beforeLimit-retryInitialPosition).toLong()/f.bytesPerFrame','partialCountsAcceptedOnly'),
 ('late-callback-overwrites','if(old.epoch !== epoch || epoch.closed)return','if(epoch.closed)return','lateAfterCannotOverwrite'),
 ('past-cue-allowed','it>p.outgoingCueFloorUs','false','positionPastCue'),
 ('report-executable','val canExecute: Boolean get() = false','val canExecute: Boolean get() = true','bothNotExecutable'),
 ('receipt-ignores-consumption','if(a.version!=receipt.aVersion || b.version!=receipt.bVersion)return false','if(false)return false','zeroAcceptanceDoesNotOwn'),
]
# The last mutant must be exercised with a consumed partial buffer, not a zero-consumption case.
mutations[-1]=(*mutations[-1][:3], 'partialCountsAcceptedOnly')
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--case', choices=[m[0] for m in mutations])
selected=parser.parse_args().case
mutations=[m for m in mutations if selected is None or m[0]==selected]
with tempfile.TemporaryDirectory(prefix='lmg-boundary-mutations-') as folder:
 out=pathlib.Path(folder)
 for name,old,new,test in mutations:
  if old not in source:raise SystemExit('Missing mutation anchor: '+name)
  mutant=source.replace(old,new) if name in ('report-executable','past-cue-allowed') else source.replace(old,new,1)
  target=out/'RenderBoundary.kt';target.write_text(mutant)
  build=run(['kotlinc',str(target),str(MAIN/'BoundaryForwarding.kt'),str(MAIN/'PcmCueGate.kt'),str(TEST),'-include-runtime','-d',str(out/'test.jar')])
  if build.returncode:raise SystemExit(name+': compilation failure is NOT detection\n'+build.stderr)
  result=run(['java','-jar',str(out/'test.jar'),test])
  if result.returncode<=0 or 'IllegalStateException' not in result.stderr or ('Check failed' not in result.stderr and 'Expected ' not in result.stderr):
   raise SystemExit(name+': not detected by an assertion\n'+result.stdout+result.stderr)
  print('CAUGHT '+name,flush=True)
print(f'Render boundary mutations: {len(mutations)}/{len(mutations)} caught by assertions')
