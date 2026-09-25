#!/usr/bin/env python3
"""CI: install one hash-pinned fork AAR as a NEW local version; never overwrite a release."""
import argparse,hashlib,json,os,pathlib,re,tempfile,urllib.parse,urllib.request
from publish_boundary_aar import publish

def base_version(path):
    text=path.read_text()
    found=re.findall(r'com\.liquidmusicglass\.media3:media3-common:([^"\n]+)',text)
    if len(found)!=1 or found[0] not in ('1.5.1-lmg30','1.11.0-lmg31'):raise ValueError('Unknown app Media3 base')
    return found[0]
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--gradle',type=pathlib.Path,default=pathlib.Path('app/build.gradle.kts'))
    p.add_argument('--repository',type=pathlib.Path,default=pathlib.Path('media3-m2'))
    p.add_argument('--print-base',action='store_true');a=p.parse_args()
    try:
        base=base_version(a.gradle)
        if a.print_base:print(base);raise SystemExit(0)
        url=os.environ.get('AUTOMIX_BOUNDARY_AAR_URL','')
        digest=os.environ.get('AUTOMIX_BOUNDARY_AAR_SHA256','')
        version=os.environ.get('AUTOMIX_BOUNDARY_EXOPLAYER_VERSION','')
        parsed=urllib.parse.urlsplit(url)
        if parsed.scheme!='https' or parsed.netloc!='github.com' or parsed.query or parsed.fragment or \
           not parsed.path.startswith('/lkolholk-ctrl/media3-lmg/releases/download/'):
            raise ValueError('Set a release AAR URL in AUTOMIX_BOUNDARY_AAR_URL (owned media3-lmg repo)')
        if not re.fullmatch('[0-9a-f]{64}',digest):raise ValueError('Set AUTOMIX_BOUNDARY_AAR_SHA256')
        if not re.fullmatch(re.escape(base)+r'-boundary[0-9][A-Za-z0-9.-]*',version):
            raise ValueError('Set a matching unique BASE-boundaryN artifact version')
        limit=128*1024*1024
        with tempfile.TemporaryDirectory(prefix='boundary-ci-') as folder:
            target=pathlib.Path(folder)/'exoplayer.aar'
            with urllib.request.urlopen(url,timeout=60) as response, target.open('xb') as output:
                total=0
                while True:
                    chunk=response.read(1024*1024)
                    if not chunk:break
                    total+=len(chunk)
                    if total>limit:raise ValueError('AAR download exceeds limit')
                    output.write(chunk)
            if hashlib.sha256(target.read_bytes()).hexdigest()!=digest:raise ValueError('AAR SHA-256 mismatch')
            print(json.dumps(publish(a.repository,target,base,version),indent=2))
        envfile=os.environ.get('GITHUB_ENV')
        if envfile:
            with open(envfile,'a',encoding='utf-8') as output:output.write('AUTOMIX_EXOPLAYER_VERSION='+version+'\n')
    except (OSError,ValueError) as error:raise SystemExit(str(error))
