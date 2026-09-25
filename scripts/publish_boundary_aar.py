#!/usr/bin/env python3
"""Create-only local Maven overlay for the patched ExoPlayer AAR. Never replace a release."""
from __future__ import annotations
import argparse, hashlib, json, os, pathlib, re, tempfile, xml.etree.ElementTree as ET
from automix_verify_output_port_aar import verify

def publish(repository:pathlib.Path,aar:pathlib.Path,base:str,version:str):
    if base not in ('1.5.1-lmg30','1.11.0-lmg31') or not re.fullmatch(re.escape(base)+r'-boundary[0-9][A-Za-z0-9.-]*',version):
        raise ValueError('Use a new BASE-boundaryN version; do not change the Media3 base')
    repository=repository.resolve(strict=True)
    group='com/liquidmusicglass/media3/media3-exoplayer'
    root=repository
    for component in group.split('/'):
        root=root/component
        if root.is_symlink():raise ValueError('Symlink in local Maven destination')
    if (root/base).is_symlink():raise ValueError('Symlink in base Maven version')
    pom=root/base/f'media3-exoplayer-{base}.pom'
    if not pom.is_file() or pom.is_symlink():raise ValueError('Existing base ExoPlayer POM is required')
    tree=ET.fromstring(pom.read_bytes())
    ns={'m':'http://maven.apache.org/POM/4.0.0'}
    if tree.tag!='{'+ns['m']+'}project':raise ValueError('Unexpected Maven POM namespace')
    for tag,expected in [('groupId','com.liquidmusicglass.media3'),('artifactId','media3-exoplayer'),('version',base)]:
        node=tree.find('m:'+tag,ns)
        if node is None or node.text!=expected:raise ValueError('Unexpected POM '+tag)
    # Keep the base dependency coordinates. Only this artifact's version changes.
    for dep in tree.findall('.//m:dependency',ns):
        group_node=dep.find('m:groupId',ns);version_node=dep.find('m:version',ns)
        if group_node is not None and group_node.text=='com.liquidmusicglass.media3' and \
          (version_node is None or version_node.text!=base):raise ValueError('Cross-base dependency in POM')
    report=verify(aar)
    tree.find('m:version',ns).text=version
    ET.register_namespace('',ns['m'])
    new_pom=ET.tostring(tree,encoding='utf-8',xml_declaration=True)
    target=root/version
    if target.exists() or target.is_symlink():raise FileExistsError('Version already exists; choose a new suffix')
    # Freeze both inputs before any publication; no remote credentials/network are used.
    raw=aar.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=report['sha256']:raise ValueError('AAR changed during verification')
    target.mkdir() # Atomic create-only directory ownership. Do not run concurrent publishers.
    try:
        with (target/f'media3-exoplayer-{version}.aar').open('xb') as f:f.write(raw)
        with (target/f'media3-exoplayer-{version}.pom').open('xb') as f:f.write(new_pom)
        report.update(baseVersion=base,artifactVersion=version)
        with (target/'boundary-provenance.json').open('x') as f:json.dump(report,f,indent=2)
    except BaseException:
        # Leave an incomplete newly owned version for inspection; never remove/replace user files.
        raise
    return {**report,'bytecodeStatus':report['status'],'status':'LOCAL_ARTIFACT_CREATED','path':str(target)}
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repository',required=True,type=pathlib.Path);p.add_argument('--aar',required=True,type=pathlib.Path)
    p.add_argument('--base-version',required=True);p.add_argument('--version',required=True)
    a=p.parse_args()
    try:print(json.dumps(publish(a.repository,a.aar,a.base_version,a.version),indent=2))
    except (OSError,ValueError) as e:raise SystemExit(str(e))
