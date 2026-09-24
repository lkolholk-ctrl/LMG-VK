#!/usr/bin/env python3
"""Regression guards for required NEW real JNI cases and a genuinely defined ELF export."""
from pathlib import Path
import copy
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from automix_verify_observation_apk import SCHEDULE_JNI_CLASS, SCHEDULE_JNI_CASES, verify_schedule_report
from automix_verify_jni_export import SYMBOL,SOURCE_SYMBOL,SCHEDULE_SYMBOL,verify_schedule_jni_exports


def main():
    rejected=0
    with tempfile.TemporaryDirectory(prefix='schedule-report-') as name:
        root=Path(name);path=root/f'TEST-{SCHEDULE_JNI_CLASS}.xml'
        positive=ET.Element('testsuite',name=SCHEDULE_JNI_CLASS,tests=str(len(SCHEDULE_JNI_CASES)),failures='0',errors='0',skipped='0')
        for case in sorted(SCHEDULE_JNI_CASES):ET.SubElement(positive,'testcase',name=case,classname=SCHEDULE_JNI_CLASS)
        def write(s):ET.ElementTree(s).write(path)
        write(positive);assert verify_schedule_report(root)==10
        variants=[]
        for tag in ('failure','error','skipped'):
            s=copy.deepcopy(positive);ET.SubElement(s[0],tag);variants.append(s)
        for field in ('failures','errors','skipped','disabled'):
            s=copy.deepcopy(positive);s.set(field,'1');variants.append(s)
        for field,value in (('name','wrong'),('tests','0')):
            s=copy.deepcopy(positive);s.set(field,value);variants.append(s)
        for case in list(positive):
            s=copy.deepcopy(positive)
            for x in s:
                if x.get('name')==case.get('name'):x.set('name','unrelated')
            variants.append(s)
        for attr in ('classname','name'):
            s=copy.deepcopy(positive);s[0].set(attr,'');variants.append(s)
        s=copy.deepcopy(positive);s[1].set('name',s[0].get('name'));variants.append(s)
        for s in variants:
            write(s)
            try:verify_schedule_report(root)
            except (ValueError,ET.ParseError):rejected+=1
            else:raise AssertionError('Malformed schedule report accepted')
        path.unlink()
        try:verify_schedule_report(root)
        except OSError:rejected+=1
        else:raise AssertionError('Missing report accepted')
        source=root/'fixture.c'
        def elf(symbols,bits):
            source.write_text('\n'.join(f'void {s.decode()}(void) {{}}' for s in symbols))
            target=root/f'fixture{bits}.so'
            subprocess.run(['cc',f'-m{bits}','-shared','-fPIC','-nostdlib',str(source),'-o',str(target)],check=True,capture_output=True)
            return target.read_bytes()
        elf_rejected=0
        for width,machine,cls in ((32,3,1),(64,62,2)):
            correct=elf((SYMBOL,SOURCE_SYMBOL,SCHEDULE_SYMBOL),width)
            verify_schedule_jni_exports(correct,cls,machine)
            for symbols in ((SYMBOL,SOURCE_SYMBOL),(SYMBOL,SCHEDULE_SYMBOL),(SOURCE_SYMBOL,SCHEDULE_SYMBOL)):
                try:verify_schedule_jni_exports(elf(symbols,width),cls,machine)
                except ValueError:elf_rejected+=1
                else:raise AssertionError('Incomplete JNI library accepted')
            for bad in (b'',correct[:48],correct.replace(SCHEDULE_SYMBOL,SCHEDULE_SYMBOL[:-1]+b'0')):
                try:verify_schedule_jni_exports(bad,cls,machine)
                except ValueError:elf_rejected+=1
                else:raise AssertionError('Malformed JNI export accepted')
    print(f'Schedule report guard: 1 positive, {rejected} rejected variants; 2 compiled triple-export ELF fixtures, {elf_rejected} rejected libraries')
if __name__=='__main__':main()
