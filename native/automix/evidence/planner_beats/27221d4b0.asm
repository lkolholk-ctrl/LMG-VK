; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27221d4b0
; Symbol: FUN_27221d4b0
; Signature: undefined FUN_27221d4b0()
27221d4b0: pacibsp
27221d4b4: sub sp,sp,#0xb0
27221d4b8: stp x26,x25,[sp, #0x60]
27221d4bc: stp x24,x23,[sp, #0x70]
27221d4c0: stp x22,x21,[sp, #0x80]
27221d4c4: stp x20,x19,[sp, #0x90]
27221d4c8: stp x29,x30,[sp, #0xa0]
27221d4cc: add x29,sp,#0xa0
27221d4d0: mov x19,x0
27221d4d4: ldr x8,[x20, #0x10]
27221d4d8: ldr x25,[x8, #0x10]
27221d4dc: add x21,x8,#0x20
27221d4e0: adrp x22,0x2780e3000
27221d4e4: ldr x22,[x22, #0xc50]
27221d4e8: mov w26,#0x28
27221d4ec: cbz x25,0x27221d5d0
27221d4f0: add x1,sp,#0x30
27221d4f4: mov x0,x21
27221d4f8: bl 0x27221361c
27221d4fc: ldp x23,x24,[sp, #0x48]
27221d500: add x0,sp,#0x30
27221d504: mov x1,x23
27221d508: bl 0x27220ff94
27221d50c: mov x8,x24
27221d510: ldr x9,[x8, #0x10]!
27221d514: mov x20,x0
27221d518: mov x0,x23
27221d51c: mov x1,x24
27221d520: mov x17,x8
27221d524: movk x17,#0xbc30, LSL #48
27221d528: blraa x9,x17
27221d52c: subs x8,x0,x19
27221d530: b.vs 0x27221d5f0
27221d534: add x0,sp,#0x30
27221d538: tst x8,#-0x7ffffffffffffffd
27221d53c: b.eq 0x27221d548
27221d540: bl 0x272210e78
27221d544: b 0x27221d5a0
27221d548: add x1,sp,#0x8
27221d54c: bl 0x2722136e0
27221d550: mov x0,x22
27221d554: bl 0x2743dde70
27221d558: stur x22,[x29, #-0x48]
27221d55c: tbnz w0,#0x0,0x27221d57c
27221d560: ldr x8,[x22, #0x10]
27221d564: add x1,x8,#0x1
27221d568: sub x20,x29,#0x48
27221d56c: mov w0,#0x0
27221d570: mov w2,#0x1
27221d574: bl 0x272263490
27221d578: ldur x22,[x29, #-0x48]
27221d57c: ldp x24,x8,[x22, #0x10]
27221d580: add x23,x24,#0x1
27221d584: cmp x24,x8, LSR #0x1
27221d588: b.cs 0x27221d5b0
27221d58c: str x23,[x22, #0x10]
27221d590: madd x8,x24,x26,x22
27221d594: add x0,sp,#0x8
27221d598: add x1,x8,#0x20
27221d59c: bl 0x2722136e0
27221d5a0: add x21,x21,#0x28
27221d5a4: sub x25,x25,#0x1
27221d5a8: cbnz x25,0x27221d4f0
27221d5ac: b 0x27221d5d0
27221d5b0: cmp x8,#0x1
27221d5b4: cset w0,hi
27221d5b8: sub x20,x29,#0x48
27221d5bc: mov x1,x23
27221d5c0: mov w2,#0x1
27221d5c4: bl 0x272263490
27221d5c8: ldur x22,[x29, #-0x48]
27221d5cc: b 0x27221d58c
27221d5d0: mov x0,x22
27221d5d4: ldp x29,x30,[sp, #0xa0]
27221d5d8: ldp x20,x19,[sp, #0x90]
27221d5dc: ldp x22,x21,[sp, #0x80]
27221d5e0: ldp x24,x23,[sp, #0x70]
27221d5e4: ldp x26,x25,[sp, #0x60]
27221d5e8: add sp,sp,#0xb0
27221d5ec: retab
27221d5f0: brk #0x1

