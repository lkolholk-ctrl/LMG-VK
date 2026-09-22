; Binary: _SonicKit_MusicKit
; Address: 0x2721b640c
; Symbol: FUN_2721b640c
; Signature: undefined FUN_2721b640c()
2721b640c: pacibsp
2721b6410: str x28,[sp, #-0x60]!
2721b6414: stp x27,x26,[sp, #0x10]
2721b6418: stp x25,x24,[sp, #0x20]
2721b641c: stp x23,x22,[sp, #0x30]
2721b6420: stp x20,x19,[sp, #0x40]
2721b6424: stp x29,x30,[sp, #0x50]
2721b6428: add x29,sp,#0x50
2721b642c: str x21,[sp, #0x8]
2721b6430: cmp x2,x1
2721b6434: b.eq 0x2721b64ec
2721b6438: mov x20,x2
2721b643c: mov x22,x1
2721b6440: mov x28,#0x0
2721b6444: mov x27,#0x0
2721b6448: ldr x23,[x3]
2721b644c: mov w8,#0x18
2721b6450: madd x8,x2,x8,x23
2721b6454: sub x24,x8,#0x18
2721b6458: sub x19,x0,x2
2721b645c: mov w8,#0x18
2721b6460: madd x10,x20,x8,x23
2721b6464: ldr x0,[x10]
2721b6468: ldp w9,w8,[x10, #0x8]
2721b646c: ldr x2,[x10, #0x10]
2721b6470: mov x26,x19
2721b6474: mov x25,x24
2721b6478: ldr x3,[x25]
2721b647c: ldr x5,[x25, #0x10]
2721b6480: orr w1,w9,w27
2721b6484: lsl x27,x8,#0x20
2721b6488: bfm x1,x8,#0x20,#0x1f
2721b648c: ldp w8,w9,[x25, #0x8]
2721b6490: orr w4,w8,w28
2721b6494: lsl x28,x9,#0x20
2721b6498: bfm x4,x9,#0x20,#0x1f
2721b649c: bl 0x2743dc2f0
2721b64a0: tbz w0,#0x0,0x2721b64d8
2721b64a4: cbz x23,0x2721b650c
2721b64a8: ldp w9,w8,[x25, #0x20]
2721b64ac: ldr x2,[x25, #0x28]
2721b64b0: ldr q0,[x25]
2721b64b4: ldp x10,x0,[x25, #0x10]
2721b64b8: stur q0,[x25, #0x18]
2721b64bc: stur x10,[x25, #0x28]
2721b64c0: str x0,[x25]
2721b64c4: stp w9,w8,[x25, #0x8]
2721b64c8: str x2,[x25, #0x10]
2721b64cc: sub x25,x25,#0x18
2721b64d0: adds x26,x26,#0x1
2721b64d4: b.cc 0x2721b6478
2721b64d8: add x20,x20,#0x1
2721b64dc: add x24,x24,#0x18
2721b64e0: sub x19,x19,#0x1
2721b64e4: cmp x20,x22
2721b64e8: b.ne 0x2721b645c
2721b64ec: ldr x21,[sp, #0x8]
2721b64f0: ldp x29,x30,[sp, #0x50]
2721b64f4: ldp x20,x19,[sp, #0x40]
2721b64f8: ldp x23,x22,[sp, #0x30]
2721b64fc: ldp x25,x24,[sp, #0x20]
2721b6500: ldp x27,x26,[sp, #0x10]
2721b6504: ldr x28,[sp], #0x60
2721b6508: retab
2721b650c: brk #0x1

