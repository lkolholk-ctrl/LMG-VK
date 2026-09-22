#!/usr/bin/env python3
"""Run original ARM MusicKit SongStructure builders with Swift storage shims.
Requires unicorn, capstone and the existing locally extracted Packages binary.
No network, Android build or emulator. Fixtures retain original payload padding;
only semantic fields are compared by C++ tests. See PLANNER_BEATS_IMPLEMENTATION.
"""
import argparse
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--binary',type=Path,default=Path('/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages'))
parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'tests/fixtures')
args=parser.parse_args()
fixtures=args.output
fixtures.mkdir(parents=True,exist_ok=True)
import struct,json,random
from pathlib import Path
from unicorn import Uc,UC_ARCH_ARM64,UC_MODE_ARM,UC_HOOK_CODE
from unicorn.arm64_const import *
from capstone import Cs,CS_ARCH_ARM64,CS_MODE_ARM
b=args.binary.read_bytes();u=Uc(UC_ARCH_ARM64,UC_MODE_ARM);o=32
for _ in range(struct.unpack_from('<I',b,16)[0]):
 c,n=struct.unpack_from('<II',b,o)
 if c==25 and b[o+8:o+24].rstrip(b'\0')==b'__TEXT':
  va,vs,fo,fs=struct.unpack_from('<QQQQ',b,o+24);u.mem_map(va,(vs+4095)&~4095);u.mem_write(va,b[fo:fo+fs])
 o+=n
u.mem_map(0x100000,0x300000)
for a in [0x2743dd000,0x27808a000,0x2780e3000,0x2824d0000]:u.mem_map(a,0x1000)
def wq(a,x):u.mem_write(a,struct.pack('<Q',x))
def rq(a):return struct.unpack('<Q',u.mem_read(a,8))[0]
def x(n):return u.reg_read(UC_ARM64_REG_X0+n)
def wx(n,v):u.reg_write(UC_ARM64_REG_X0+n,v)
# x29/x30 are not contiguous with X0 in Unicorn; helpers only use x0..28.
meta=0x110100;vwt=0x110300;emeta=0x110200;evwt=0x110400
wq(meta-8,vwt);wq(emeta-8,evwt);wq(vwt+0x40,1);wq(vwt+0x58,0x120020);wq(vwt+8,0x120030)
wq(evwt+0x48,24);u.mem_write(evwt+0x50,b'\x07')
wq(0x2824d0f28,0x120000)
for i,off in enumerate([0xc98,0xca0,0xc90,0xca8]):wq(0x27808a000+off,0x110500+4*i);u.mem_write(0x110500+4*i,struct.pack('<I',i))
empty=0x130000;wq(0x2780e3c50,empty);wq(empty+0x18,4096)
md=Cs(CS_ARCH_ARM64,CS_MODE_ARM)
for ins in md.disasm(bytes(u.mem_read(0x272225ef4,0x272226588-0x272225ef4)),0x272225ef4):
 if ins.mnemonic in ['pacibsp','autda','xpacd']:u.mem_write(ins.address,struct.pack('<I',0xd503201f))
 if ins.mnemonic=='retab':u.mem_write(ins.address,struct.pack('<I',0xd65f03c0))
 if ins.mnemonic=='blraa':
  reg=int(ins.op_str.split(',')[0][1:]);u.mem_write(ins.address,struct.pack('<I',0xd63f0000|(reg<<5)))
heap=0x200000
hooks={0x2743dd110,0x2743dd130,0x2743dd100,0x2743dd120,0x2743dde70,0x272253794,0x120000,0x120020,0x120030}
def hook(uc,a,s,d):
 global heap
 if a not in hooks:return
 if a==0x2743dd110:wx(0,meta)
 elif a==0x2743dd130:wx(0,emeta)
 elif a==0x2743dd100:u.reg_write(UC_ARM64_REG_D0,rq(x(20)))
 elif a==0x2743dd120:u.mem_write(x(8),bytes(u.mem_read(x(20)+8,1)))
 elif a==0x120020:wx(0,u.mem_read(x(0),1)[0])
 elif a==0x2743dde70:wx(0,0 if x(0)==empty else 1)
 elif a==0x272253794:
  old=x(3);count=rq(old+0x10);target=heap;heap+=0x10000
  u.mem_write(target,bytes(u.mem_read(old,32+count*64)));wq(target+24,4096);wx(0,target)
 u.reg_write(UC_ARM64_REG_PC,u.reg_read(UC_ARM64_REG_LR))
u.hook_add(UC_HOOK_CODE,hook)
rng=random.Random(391);seqs=[[],[0],[1],[2],[3],[0,1,0,2,0,1,3,0,1,2],list(range(4))*5]+[[rng.randrange(4) for _ in range(rng.randrange(1,65))] for _ in range(64)]
rows=[]
for seq in seqs:
 heap=0x200000;source=0x140000;wq(source+0x10,len(seq));points=[]
 for i,tag in enumerate(seq):
  t=(-0.0 if i==0 else i*.125);u.mem_write(source+32+i*24,struct.pack('<dQd',t,tag,.99));points.append([t,tag])
 wx(0,source);u.reg_write(UC_ARM64_REG_SP,0x1f0000);u.reg_write(UC_ARM64_REG_LR,0x150000)
 u.emu_start(0x272225ef4,0x150000,count=100000)
 assert u.reg_read(UC_ARM64_REG_PC)==0x150000,hex(u.reg_read(UC_ARM64_REG_PC))
 out=x(0);count=rq(out+16);assert count==len(seq)
 records=[bytes(u.mem_read(out+32+i*64,64)).hex() for i in range(count)]
 rows.append({'input':points,'raw':records})
out=bytearray(struct.pack('<I',len(rows)))
for row in rows:
 out+=struct.pack('<I',len(row['input']))
 for (t,tag),raw in zip(row['input'],row['raw']):out+=struct.pack('<dI',t,tag)+bytes.fromhex(raw)
(fixtures/'initial_structure_events.bin').write_bytes(out)
print('Initial hierarchy:',len(rows),'cases')

from collections import deque
md.skipdata=True
# Preserve source control flow; replace Swift object/array storage with simple boxes.
for ins in md.disasm(bytes(u.mem_read(0x27220ff94,0x27221eb00-0x27220ff94)),0x27220ff94):
 if ins.mnemonic.startswith(('pac','aut','xpac')):u.mem_write(ins.address,struct.pack('<I',0xd503201f))
 if ins.mnemonic=='retab':u.mem_write(ins.address,struct.pack('<I',0xd65f03c0))
 if ins.mnemonic in ['blraa','braa']:
  reg=int(ins.op_str.split(',')[0][1:]);u.mem_write(ins.address,struct.pack('<I',(0xd63f0000 if ins.mnemonic=='blraa' else 0xd61f0000)|(reg<<5)))
for a in [0x280c99000,0x2884ab000]:u.mem_map(a,0x1000)
objmeta=0x111100;objvwt=0x111200;optmeta=0x111300;optvwt=0x111400
wq(objmeta-8,objvwt);wq(objvwt+0x40,40);wq(objvwt+0x10,0x121000);wq(objvwt+8,0x121030)
wq(optmeta-8,optvwt);wq(optvwt+0x40,40);wq(optvwt+0x10,0x121010);wq(optvwt+0x28,0x121010)
wits=[0x112000+i*0x100 for i in range(5)]
wq(wits[0]+8,0x121100)
for i in range(1,5):wq(wits[i]+8,wits[i-1]);wq(wits[i]+16,0x121100+i*16)
noop={0x272210e78,0x27221e520,0x27221e4bc,0x27221e650,0x2743ddce0,0x2743ddd00,0x2743ddd10,0x2743ddf20,0x2743ddf40,0x121030}
extra={0x27220ff94,0x27221361c,0x2722136e0,0x2722136f8,0x27221e404,0x27221e0d8,0x27221e048,0x27221bd50,0x27221e168,0x272213680,0x272271d7c,0x272263470,0x272263490,0x27225364c,0x272290d8c,0x121000,0x121010,0x2743ddc90,0x2743dde60,0x2743ddcb0}|noop|set(0x121100+i*16 for i in range(5))
alloc=0x240000
trace=deque(maxlen=10)
def malloc(size):
 global alloc
 p=alloc;alloc+=(size+31)&~31;assert alloc<0x3e0000;return p
def exthook(uc,a,s,d):
 global rebuilt
 trace.append(hex(a))
 if a not in extra:return
 if a==0x27221bd50:
  p=x(0);rebuilt=[bytes(u.mem_read(p+32+i*64,64)).hex() for i in range(rq(p+16))];u.mem_write(x(8),bytes(72))
 elif a in noop:pass
 elif a==0x27220ff94:wx(0,rq(x(0)))
 elif a in [0x27221361c,0x2722136e0,0x27221e0d8,0x27221e048]:u.mem_write(x(1),bytes(u.mem_read(x(0),40)));wx(0,x(1))
 elif a==0x27221e404:
  size=48 if x(2)==0x27221e38c else 40;u.mem_write(x(1),bytes(u.mem_read(x(0),size)));wx(0,x(1))
 elif a==0x2722136f8:
  p=malloc(64);wq(x(0),p);wx(0,p)
 elif a==0x121000:u.mem_write(x(0),bytes(u.mem_read(x(1),40)))
 elif a==0x121010:u.mem_write(x(0),bytes(u.mem_read(x(1),40)))
 elif a in [0x27221e168,0x272213680]:wx(0,optmeta)
 elif a==0x272271d7c:
  skip=x(0);src=x(1);count=rq(src+16);wx(0,src);wx(1,src+32);wx(2,min(skip,count));wx(3,count*2)
 elif a in [0x272263470,0x272263490]:
  stride=48 if a==0x272263470 else 40;old=rq(x(20));n=rq(old+16);p=malloc(0x4000);u.mem_write(p,bytes(u.mem_read(old,32+n*stride)));wq(p+24,512);wq(x(20),p)
 elif a==0x27225364c:
  old=x(3);n=rq(old+16);p=malloc(0x4000);u.mem_write(p,bytes(u.mem_read(old,32+n*40)));wq(p+24,512);wx(0,p)
 elif a in [0x2743ddc90,0x2743dde60]:wx(0,malloc(0x4000))
 elif a==0x2743ddcb0:u.mem_write(x(0),bytes(u.mem_read(x(1),x(2)*40)))
 elif a==0x272290d8c:u.mem_write(x(0),bytes(u.mem_read(x(1),x(2))))
 elif a==0x121100:u.mem_write(x(8),bytes(u.mem_read(x(20),8)))
 elif 0x121110<=a<=0x121140:wx(0,rq(x(20)+(a-0x121100)//16*8))
 else:raise Exception(hex(a))
 u.reg_write(UC_ARM64_REG_PC,u.reg_read(UC_ARM64_REG_LR))
u.hook_add(UC_HOOK_CODE,exthook)
def arr(items):
 p=malloc(0x4000);wq(p+16,len(items));wq(p+24,512)
 for i,item in enumerate(items):u.mem_write(p+32+i*40,item)
 return p
def run(tags, times=None):
 global alloc,heap
 heap=0x200000
 alloc=0x240000;all_events=[];down=[];sections=[];bi=-1;di=-1;si=-1;ci=-1
 for i,tag in enumerate(tags):
  bi+=1
  if tag>=1:di+=1
  if tag>=2:si+=1
  if tag==3:ci+=1
  p=malloc(40);u.mem_write(p,struct.pack('<d4q',times[i] if times is not None else i*.125,bi,di,si,ci))
  if tag>=1:down.append(struct.pack('<5Q',p,0,0,objmeta,wits[2]))
  if tag==3:sections.append(struct.pack('<5Q',p,0,0,objmeta,wits[4]))
 source=0x140000;wq(source+16,len(tags))
 for i,tag in enumerate(tags):u.mem_write(source+32+i*24,struct.pack('<dQd',times[i] if times is not None else i*.125,tag,.99))
 wx(0,source);u.reg_write(UC_ARM64_REG_SP,0x1f0000);u.reg_write(UC_ARM64_REG_LR,0x150000)
 u.emu_start(0x272225ef4,0x150000,count=100000)
 initial=x(0)
 st=malloc(72);wq(st,initial);wq(st+16,arr(down));wq(st+32,arr(sections))
 wx(20,st);wx(21,0);u.reg_write(UC_ARM64_REG_SP,0x1f0000);u.reg_write(UC_ARM64_REG_LR,0x150000)
 try:u.emu_start(0x27221cb94,0x150000,count=300000)
 except Exception as e:print('ins',[(z.mnemonic,z.op_str) for z in md.disasm(bytes(u.mem_read(u.reg_read(UC_ARM64_REG_PC),4)),u.reg_read(UC_ARM64_REG_PC))]);print('failed',e,list(trace),'x0',hex(x(0)),'x1',hex(x(1)),'x8',hex(x(8)),'x20',hex(x(20)));raise
 assert u.reg_read(UC_ARM64_REG_PC)==0x150000
 out=x(0);selected=[struct.unpack('<d4q',u.mem_read(rq(out+32+i*40),40)) for i in range(rq(out+16))]
 wx(0,out);wx(20,st);wx(21,0);wx(8,0x160000);u.reg_write(UC_ARM64_REG_SP,0x1f0000);u.reg_write(UC_ARM64_REG_LR,0x150000)
 try:u.emu_start(0x27221cca4,0x150000,count=300000)
 except Exception as e:print('rebuild fail',e,list(trace));raise
 return selected

def expected(tags,times):
 down=[];sections=[]
 for i,tag in enumerate(tags):
  if tag>=1:
   idx=len(down);down.append((times[i],i,idx))
   if tag==3:sections.append(down[-1])
 anchor=sections[0] if sections else None
 for left,right in zip(sections,sections[1:]):
  if (right[2]-left[2])%4==0:anchor=left;break
 phase=anchor[2]%4 if anchor else 0
 grid=[x for x in down if x[2]>=phase and (x[2]-phase)%4==0]
 sectionIds={x[2] for x in sections};result=[]
 for section in sections:
  before=next((x for x in reversed(grid) if x[0]<=section[0]),None)
  after=next((x for x in grid if x[0]>=section[0]),None)
  choice=before or after
  if before and after and after[0]-section[0]-1e-9<=section[0]-before[0]:choice=after
  if choice is None or choice[2] in sectionIds:choice=section
  if not result or result[-1][2]<choice[2]:result.append(choice)
 return result
rng=random.Random(512);records=[]
for c in range(160):
 tags=[rng.randrange(4) for _ in range(rng.randrange(0,70))]
 times=[];t=0
 for _ in tags:t+=rng.choice([.125,.25,.5,1.0]);times.append(t)
 actual=run(tags,times);want=expected(tags,times)
 assert [(x[0],x[1],x[2]) for x in actual]==want,(c,tags,actual,want)
 records.append({'tags':tags,'times':times,'selected':[x[2] for x in actual],'raw':rebuilt})

for times in [[0]*9,list(range(9)),[0,2,1,4,3,6,5,8,7],[i*.5 for i in range(9)]]:
 for tags in [[1,1,3,1,1,1,1,3,1],[3,1,1,1,3,1,1,1,3],[1,2,1,3,0,1,3,1,0]]:
  actual=run(tags,times)
  assert [(x[0],x[1],x[2]) for x in actual]==expected(tags,times)
  records.append({'tags':tags,'times':times,'raw':rebuilt})
out=bytearray(struct.pack('<I',len(records)))
for row in records:
 out+=struct.pack('<I',len(row['tags']))
 for t,tag,raw in zip(row['times'],row['tags'],row['raw']):out+=struct.pack('<dI',t,tag)+bytes.fromhex(raw)
(fixtures/'normalized_structure_events.bin').write_bytes(out)
print('Complete section normalization/rebuild:',len(records),'cases')

u.mem_map(0x2780e4000,0x1000);wq(0x2780e4a88,0x111800);wq(0x111800,0x1234)
more={0x272218e08,0x272219454,0x2722195d8,0x272219504,0x2722194a8,0x27222d08c,0x27222d090,0x272253504,0x2722535c4}
def morehook(uc,a,s,d):
 if a not in more:return
 if a in [0x272219454,0x272219504]:pass
 elif a==0x2722195d8:wx(0,objmeta)
 elif a in [0x272218e08,0x2722194a8]:u.mem_write(x(1),bytes(u.mem_read(x(0),80 if a==0x272218e08 else 97)));wx(0,x(1))
 elif a in [0x27222d08c,0x27222d090]:
  ptr=rq(x(20)+(0 if a==0x27222d08c else 40));u.mem_write(x(8),bytes(u.mem_read(ptr,8)))
 elif a in [0x272253504,0x2722535c4]:
  stride=104 if a==0x272253504 else 80;old=x(3);n=rq(old+16);p=malloc(0x4000);u.mem_write(p,bytes(u.mem_read(old,32+n*stride)));wq(p+24,512);wx(0,p)
 u.reg_write(UC_ARM64_REG_PC,u.reg_read(UC_ARM64_REG_LR))
u.hook_add(UC_HOOK_CODE,morehook)
def stability(durations,beats):
 global alloc
 alloc=0x240000;events=[];t=0;beat=0
 for i in range(len(durations)+1):
  p=malloc(40);u.mem_write(p,struct.pack('<d4q',t,beat,i,-1,-1));events.append(struct.pack('<5Q',p,0,0,objmeta,wits[2]))
  if i<len(durations):t+=durations[i];beat+=beats[i]
 arr=malloc(0x4000);wq(arr+16,len(durations));wq(arr+24,512)
 for i in range(len(durations)):u.mem_write(arr+32+i*80,events[i]+events[i+1])
 wx(0,arr);wx(21,0);u.reg_write(UC_ARM64_REG_SP,0x1f0000);u.reg_write(UC_ARM64_REG_LR,0x150000)
 try:u.emu_start(0x272218400,0x150000,count=300000)
 except Exception as e:print('stability fail',e,list(trace),'x0',hex(x(0)),'x1',hex(x(1)),'x3',hex(x(3)),'x20',hex(x(20)));raise
 assert u.reg_read(UC_ARM64_REG_PC)==0x150000
 out=x(0);result=[]
 for i in range(rq(out+16)):
  p=out+32+i*104;start=struct.unpack('<d4q',u.mem_read(rq(p),40));end=struct.unpack('<d4q',u.mem_read(rq(p+40),40));n,bpm,tag=struct.unpack('<qdB',u.mem_read(p+80,17));result.append([start[0],end[0],n,bpm,tag])
 return result

rng=random.Random(615);cases=[]
for c in range(128):
 n=rng.randrange(1,70);base=rng.choice([1.875,2.0,2.003,3.75])
 ds=[base+rng.choice([0,.01,.02,.039,.040000001,.05,.1]) for _ in range(n)]
 if c%4==0:ds=[base+i*.003 for i in range(n)]
 bs=[4 if c%3 else rng.choice([3,4,4,4,4]) for _ in ds]
 cases.append((ds,bs))
cases.extend([([2.0]*n,[4]*n) for n in [0,1,4,5,6,30]])
out=bytearray(struct.pack('<I',len(cases)))
for ds,bs in cases:
 rs=stability(ds,bs);ts=[0.0]
 for d in ds:ts.append(ts[-1]+d)
 out+=struct.pack('<I',len(ds))+struct.pack('<%dd'%len(ts),*ts)+struct.pack('<%dI'%len(bs),*bs)+struct.pack('<I',len(rs))
 for a,b,n,bpm,tag in rs:assert tag==0;out+=struct.pack('<ddqd',a,b,n,bpm)
(fixtures/'beat_stability.bin').write_bytes(out)
print(len(cases),'complete ARM stability builders executed')