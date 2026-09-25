#!/usr/bin/env python3
"""Inspect a rebuilt ExoPlayer AAR's actual bytecode; does not execute Android classes."""
from __future__ import annotations
import argparse, hashlib, io, pathlib, re, subprocess, tempfile, zipfile

def verify(path: pathlib.Path) -> dict:
    if not path.is_file() or path.stat().st_size>128*1024*1024:raise ValueError('Invalid/bounded AAR required')
    raw=path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        matches=[i for i in z.infolist() if i.filename=='classes.jar']
        if len(matches)!=1 or matches[0].file_size>96*1024*1024:raise ValueError('Missing or ambiguous classes.jar')
        classes=z.read(matches[0])
    names={
      'listener':'androidx.media3.exoplayer.audio.LmgPcmBoundaryListener',
      'codec':'androidx.media3.exoplayer.mediacodec.MediaCodecRenderer',
      'audio':'androidx.media3.exoplayer.audio.MediaCodecAudioRenderer',
      'entry':'androidx.media3.exoplayer.mediacodec.MediaCodecRenderer$OutputStreamInfo'}
    with zipfile.ZipFile(io.BytesIO(classes)) as z:
        for n in names.values():
            if len([i for i in z.infolist() if i.filename==n.replace('.','/')+'.class'])!=1:
                raise ValueError('Patched class missing: '+n)
    with tempfile.TemporaryDirectory(prefix='lmg-boundary-aar-') as folder:
        jar=pathlib.Path(folder)/'classes.jar';jar.write_bytes(classes)
        dumps={}
        for key,name in names.items():
            r=subprocess.run(['javap','-classpath',str(jar),'-p','-c',name],capture_output=True,text=True,timeout=30)
            if r.returncode:raise ValueError('javap failed for '+name)
            dumps[key]=r.stdout
    return check_dumps(dumps,hashlib.sha256(raw).hexdigest())

def method(text: str,name: str) -> str:
    blocks=re.split(r'(?m)(?=^  (?:public|private|protected|static) )',text)
    matches=[b for b in blocks if re.search(r'\b'+re.escape(name)+r'\(',b.split('\n',1)[0])]
    if len(matches)!=1:raise ValueError('Missing/ambiguous method: '+name)
    return matches[0]

def check_dumps(dumps:dict,sha:str) -> dict:
    version=method(dumps['listener'],'protocolVersion')
    if not re.search(r'iconst_1\s*\n\s*\d+: ireturn',version):raise ValueError('Wrong hook protocol')
    notify=method(dumps['audio'],'notifyLmgPcmBoundary')
    required=('LmgPcmBoundaryListener.onLmgPcmOutputBoundary','getLmgOutputStreamToken',
              'getLmgOutputTimeline','getLmgOutputMediaPeriodId','getLmgOutputStreamOffsetUs')
    if not all(x in notify for x in required):raise ValueError('Incomplete actual output notification')
    handle=method(dumps['audio'],'processOutputBuffer')
    if 'notifyLmgPcmBoundary' not in handle or 'AudioSink.handleBuffer' not in handle or \
       handle.index('notifyLmgPcmBoundary')>handle.index('AudioSink.handleBuffer'):
        raise ValueError('Hook is absent or after sink consumption')
    changes=method(dumps['codec'],'onStreamChanged')
    if len(re.findall(r'OutputStreamInfo\."<init>"[^\n]*Timeline;[^\n]*MediaPeriodId;',changes))!=3:
        raise ValueError('All three output-queue constructions must capture identity')
    identity=method(dumps['codec'],'getLmgOutputMediaPeriodId')
    if not all(x in identity for x in ('lmgOutputIdentityValid','lmgOutputMediaPeriodId','aconst_null')):
        raise ValueError('Output identity does not fail closed')
    for name,field in [('getLmgOutputStreamToken','outputStreamInfo'),('getLmgOutputTimeline','lmgOutputTimeline'),
                       ('getLmgOutputStreamOffsetUs','lmgOutputOffsetUs')]:
        if field not in method(dumps['codec'],name):raise ValueError('Wrong output getter '+name)
    if 'lmgOutputTimeline' not in dumps['entry'] or 'lmgOutputMediaPeriodId' not in dumps['entry']:
        raise ValueError('No stored queue identity')
    return {'status':'PATCHED_OUTPUT_BOUNDARY_BYTECODE_VERIFIED','sha256':sha,'protocol':1,
            'androidClassesExecuted':False,'playbackVerified':False}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--aar',type=pathlib.Path,required=True)
    a=p.parse_args()
    import json
    try: print(json.dumps(verify(a.aar),indent=2))
    except (OSError,ValueError,zipfile.BadZipFile,subprocess.SubprocessError) as e:raise SystemExit(str(e))
if __name__=='__main__':main()
