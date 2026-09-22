import re,struct
from pathlib import Path
root=Path(__file__).resolve().parents[1]
nodes=[];regs={};mem={};ints={}
def node(op,*args):
 nodes.append((op,args));return len(nodes)-1
def reg(s):return re.sub(r'^[qv]','v',s.split('.')[0])
def val(s):
 if s.startswith('#'):return node('const',struct.unpack('<I',struct.pack('<f',float(s[1:])))[0])
 return regs[reg(s)]
def key(base,offset):return ('stack',offset+(0x280 if base=='x29' else 0)) if base in ['sp','x29'] else (base,offset)
result=None
for line in (root/'evidence/time_pitch/234b36dec.asm').read_text().splitlines():
 m=re.match(r'0x([0-9a-f]+): (\w+) (.*)',line)
 if not m:continue
 addr=int(m[1],16);op=m[2];s=m[3]
 if addr<0x234b36fa4:continue
 if op in ['mov','movk'] and s.startswith('w'):
  dest,imm,*_=s.split(', ');v=int(imm[1:],0)
  ints[dest]=v if op=='mov' else ints[dest]|v<<16;continue
 if op in ['ldr','ldur','ldp','str','stur','stp']:
  mm=re.match(r'(.*), \[(\w+)(?:, #(-?0x[0-9a-f]+|\d+))?\]',s); assert mm,(op,s)
  rs=mm[1].split(', ');base=mm[2];off=int(mm[3] or '0',0)
  for j,q in enumerate(rs):
   k=key(base,off+16*j)
   if op.startswith('ld'):
    regs[reg(q)]=node('input',base,(off+16*j)//4) if base in ['x21','x22'] else mem[k]
   else:
    mem[k]=regs[reg(q)]
    if k==('x26',0):result=mem[k]
  if result is not None:break
  continue
 parts=s.split(', ');d=reg(parts[0]);args=parts[1:]
 if op=='dup':regs[d]=node('const',ints[args[0]])
 elif op=='movi':regs[d]=node('const',int(args[0][1:],0)<<int(args[1].split('#')[1]))
 elif op=='fmov':regs[d]=val(args[0])
 elif op=='mov':regs[d]=val(args[0])
 elif op in ['fmul','fadd','fsub','frecps','fcmgt','fcmlt','fcmeq','and','orr']:regs[d]=node(op,*map(val,args))
 elif op in ['fabs','fneg','frecpe']:regs[d]=node(op,val(args[0]))
 elif op in ['fmla','fmls','bit','bif','bsl']:regs[d]=node(op,regs[d],*map(val,args))
 else:raise Exception((hex(addr),op,s))
used=set()
def visit(i):
 if i in used:return
 used.add(i);op,args=nodes[i]
 if op not in ['const','input']:
  for a in args:visit(a)
visit(result)
lines=[]
for i in sorted(used):
 op,args=nodes[i];v=[f'v{x}' for x in args]
 if op=='const':e=f'0x{args[0]:08x}u'
 elif op=='input':
  assert args[1]==0,args
  e='real' if args[0]=='x21' else 'imaginary'
 else:e=('bitsAnd' if op=='and' else op)+'('+', '.join(v)+')'
 lines.append(f'  const std::uint32_t v{i} = {e};')
lines.append(f'  return v{result};')
(root/'src/time_pitch_seed_phase_ops.inc').write_text('// First SIMD lane, direct dependency slice of 234b36fa4..234b37874.\n'+'\n'.join(lines)+'\n')
print('Live operations',len(used))
