#!/usr/bin/env python3
"""Verify split transition/player gain bytecode AND accepted output-boundary wiring.
This validates a rebuilt AAR, not Android execution, sink hardware, or MediaClock ownership.
"""
from __future__ import annotations
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tempfile
from zipfile import ZipFile
from verify_boundary_aar import verify as verify_boundary, method

MESSAGE=0x4c4d4701
CLASSES={
 'gain':'androidx.media3.exoplayer.audio.LmgTransitionGainSink',
 'update':'androidx.media3.exoplayer.audio.LmgTransitionGainSink$Update',
 'legacy':'androidx.media3.exoplayer.audio.LmgTransitionGainSink$LegacyVolume',
 'fade':'androidx.media3.exoplayer.PlayerAudioFadeControl',
 'renderer':'androidx.media3.exoplayer.audio.MediaCodecAudioRenderer',
}

def check_gain_dumps(dumps:dict[str,str])->None:
    version=method(dumps['gain'],'protocolVersion')
    if not re.search(r'iconst_1\s*\n\s*\d+: ireturn',version):raise ValueError('Wrong gain protocol')
    dispatch=method(dumps['gain'],'dispatch')
    if not all(x in dispatch for x in ('instanceof','onLmgTransitionGain','combined:()F','LegacyVolume.set:(F)V')):
        raise ValueError('Incomplete split/fallback dispatch')
    player=method(dumps['gain'],'dispatchPlayer')
    if not all(x in player for x in ('instanceof','onLmgPlayerGain','LegacyVolume.set:(F)V')):
        raise ValueError('Incomplete normal-player dispatch')
    combined=method(dumps['update'],'combined')
    if len(re.findall(r'\bfmul\b',combined))!=1 or not all(x in combined for x in ('transitionGain:F','playerGain:F')):
        raise ValueError('Legacy product changed')
    for name in ('setVolume','restoreFullGain'):
        body=method(dumps['fade'],name)
        if not all(x in body for x in (str(MESSAGE),'LmgTransitionGainSink$Update."<init>":(FF)V','playerVolume:F','Renderer.handleMessage')):
            raise ValueError('Fade control does not transmit separate components: '+name)
        if re.search(r'\bfmul\b',body):raise ValueError('Fade control still premultiplies components')
    handler=method(dumps['renderer'],'handleMessage')
    target=re.search(r'(?m)^\s*'+str(MESSAGE)+r':\s*(\d+)\s*$',handler)
    if target is None:raise ValueError('Gain message case absent')
    tail=re.search(r'(?ms)^\s*'+target.group(1)+r':.*',handler)
    if tail is None:raise ValueError('Message case has no code')
    block=re.split(r'(?m)^\s*\d+:\s*(?:goto|return)\b',tail.group(0),maxsplit=1)[0]
    if not re.search(r'LmgTransitionGainSink\.dispatch:',block):raise ValueError('Gain message does not dispatch')
    if not re.search(r'LmgTransitionGainSink\.dispatchPlayer:',handler):raise ValueError('Normal player volume not typed')

def gain_dumps(classes:bytes)->dict[str,str]:
    with ZipFile(io.BytesIO(classes)) as z:
        names=z.namelist()
        if len(names)!=len(set(names)):raise ValueError('Duplicate class entries')
        for cls in CLASSES.values():
            name=cls.replace('.','/')+'.class'
            if name not in names or z.getinfo(name).file_size>8*1024*1024:raise ValueError('Missing or large patched class: '+cls)
    with tempfile.TemporaryDirectory(prefix='lmg-gain-bytecode-') as d:
        jar=Path(d)/'classes.jar';jar.write_bytes(classes);result={}
        for key,cls in CLASSES.items():
            p=subprocess.run(['javap','-classpath',str(jar),'-p','-c',cls],capture_output=True,text=True,timeout=30)
            if p.returncode:raise ValueError('javap failed: '+cls)
            result[key]=p.stdout
        return result

def verify(aar:Path)->dict:
    if aar.is_symlink() or not aar.is_file() or aar.stat().st_size>128*1024*1024:raise ValueError('Invalid AAR')
    raw=aar.read_bytes();sha=hashlib.sha256(raw).hexdigest()
    boundary=verify_boundary(aar)
    if boundary['sha256']!=sha:raise ValueError('AAR changed during verification')
    with ZipFile(io.BytesIO(raw)) as z:
        if z.namelist().count('classes.jar')!=1 or z.getinfo('classes.jar').file_size>96*1024*1024:raise ValueError('Invalid classes.jar')
        dumps=gain_dumps(z.read('classes.jar'))
    check_gain_dumps(dumps)
    return dict(status='SAME_SINK_GAIN_BYTECODE_VERIFIED',sha256=sha,boundaryProtocol=1,gainProtocol=1,
      physicalAudioOutputVerified=False,completePlaybackLeaseInstalled=False,canExecute=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--aar',required=True,type=Path)
    a=p.parse_args()
    try:print(json.dumps(verify(a.aar),indent=2))
    except (OSError,ValueError,subprocess.SubprocessError) as error:raise SystemExit(str(error))
