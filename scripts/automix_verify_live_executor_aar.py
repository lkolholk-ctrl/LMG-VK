#!/usr/bin/env python3
"""Require accepted boundary/gain wiring AND the actual internal live loop/clock hooks.
Bytecode verification is not Android execution and does not certify device playback.
"""
import argparse, hashlib, io, json, re, subprocess, tempfile
from pathlib import Path
from zipfile import ZipFile
from automix_verify_output_port_aar import verify as previous_verify
def method(text: str, name: str) -> str:
    blocks=re.split(r'(?m)(?=^  (?:public|private|protected|static|\w+) )',text)
    matches=[b for b in blocks if re.search(r'\b'+re.escape(name)+r'\(',b.split('\n',1)[0])]
    if len(matches)!=1:raise ValueError('Missing/ambiguous method: '+name)
    return matches[0]

CLASSES={
 'internal':'androidx.media3.exoplayer.ExoPlayerImplInternal',
 'clock':'androidx.media3.exoplayer.DefaultMediaClock',
 'lease':'androidx.media3.exoplayer.LmgLivePlaybackLease',
 'gain':'androidx.media3.exoplayer.PlayerAudioFadeControl',
 'client':'androidx.media3.exoplayer.LmgLivePlaybackClient',
 'renderer':'androidx.media3.exoplayer.audio.MediaCodecAudioRenderer',
 'sink':'androidx.media3.exoplayer.audio.LmgLivePlaybackSink',
}
def need(ok,message):
 if not ok:raise ValueError(message)
def check(d):
 work=method(d['internal'],'doSomeWork')
 need('lmgMaybeAcquireLive:' in work and work.count('lmgWorkLive:')>=2,'Missing actual playback-loop wiring')
 need(work.index('lmgMaybeAcquireLive:')<work.index('updatePeriods:'),'Late acquisition hook')
 acquire=method(d['internal'],'lmgMaybeAcquireLive')
 for text in ['getPlayingPeriod:','getNext:','pendingRequest:','seekToUs:','LmgLivePlaybackLease','Renderer.enable:','prepareLmgLiveUnityGains:','onLease:']:
  need(text in acquire,'Missing actual lease/acquisition binding: '+text)
 period=method(d['internal'],'lmgLivePeriodsCurrent')
 for text in ['getStream:','getPlayingPeriod:','matches:','playWhenReady' if 'playWhenReady' in period else 'shouldPlayWhenReady:']:
  need(text in period,'Missing actual period guard: '+text)
 loop=method(d['internal'],'lmgWorkLive')
 for text in ['isCurrent:','onWork:','setCurrentStreamFinal:','advancePlayingPeriodWithoutReleasing:','beforeMetadataSwitch:','outputFullyEnded:','onCompleted:','disableRenderer:']:
  need(text in loop,'Incomplete live transition/EOF loop: '+text)
 need('updatePlayingPeriodRenderers:' not in loop,'Live path switches to silent companion clock')
 control=method(d['internal'],'handleMessage');need('lmgBeforeControlMessage:' in control,'Missing user-control cancellation')
 for name in ['maybeUpdateReadingPeriod','maybeUpdateReadingRenderers','maybeUpdatePlayingPeriod','maybeUpdateFadeInPeriod','maybeUpdateFadeOutPeriod','maybeReleaseFadeOutPeriod']:
  need('lmgLiveLease:' in method(d['internal'],name),'Unguarded period mutation: '+name)
 for name in ['getPositionUs','syncAndGetPositionUs']:
  body=method(d['clock'],name);need('lmgOwnedClock:' in body and 'MediaClock.getPositionUs:' in body,'No output-clock pin: '+name)
 need('setLmgOwnedClock:' in method(d['internal'],'lmgPinExistingClock'),'Missing physical-clock installation')
 lease=d['lease'];need('final class androidx.media3.exoplayer.LmgLivePlaybackLease' in lease,'Lease must be final')
 need('public androidx.media3.exoplayer.LmgLivePlaybackLease(' not in lease,'Public caller-created lease forbidden')
 for text in ['Thread.currentThread:','BooleanSupplier.getAsBoolean:','AtomicBoolean.get:']:
  need(text in method(lease,'isCurrent'),'Missing lease authority check')
 gain=method(d['gain'],'prepareLmgLiveUnityGains')
 for text in ['isCrossFadeInProgress:', 'lastVolume:', 'setVolume:']:
  need(text in gain,'Missing actual master-gain establishment: '+text)
 renderer=method(d['renderer'],'getLmgLivePlaybackClient')
 need('audioSink:' in renderer and 'LmgLivePlaybackSink.getLmgLivePlaybackClient:' in renderer,'Client not linked to existing sink')
 need(re.search(r'iconst_1\s*\n\s*\d+: ireturn',method(d['sink'],'protocolVersion')) is not None,'Unsupported live protocol')
def verify(aar):
 report=previous_verify(aar);raw=aar.read_bytes();need(hashlib.sha256(raw).hexdigest()==report['sha256'],'AAR changed')
 with ZipFile(io.BytesIO(raw)) as z:
  need(z.namelist().count('classes.jar')==1 and z.getinfo('classes.jar').file_size<=96*1024*1024,'Invalid classes.jar');classes=z.read('classes.jar')
 with ZipFile(io.BytesIO(classes)) as z:
  need(len(z.namelist())==len(set(z.namelist())),'Duplicate classes')
  for cls in CLASSES.values():need(cls.replace('.','/')+'.class' in z.namelist(),'Missing live class '+cls)
 with tempfile.TemporaryDirectory(prefix='lmg-live-javap-') as d:
  jar=Path(d)/'classes.jar';jar.write_bytes(classes);dumps={}
  for name,cls in CLASSES.items():
   p=subprocess.run(['javap','-classpath',str(jar),'-p','-c',cls],capture_output=True,text=True,timeout=30)
   need(p.returncode==0,'javap failed '+cls);dumps[name]=p.stdout
 check(dumps)
 return dict(status='LIVE_PERIOD_CLOCK_DSP_HOOKS_BYTECODE_VERIFIED',sha256=report['sha256'],base='1.5.1-lmg30',automaticActivation=False,physicalAudioVerified=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--aar',type=Path,required=True);a=p.parse_args()
 try:print(json.dumps(verify(a.aar),indent=2))
 except (OSError,ValueError,subprocess.SubprocessError) as e:raise SystemExit(str(e))
