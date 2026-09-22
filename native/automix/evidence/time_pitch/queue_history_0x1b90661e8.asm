1b90661e8: pacibsp 
1b90661ec: stp d9, d8, [sp, #-0x40]!
1b90661f0: stp x22, x21, [sp, #0x10]
1b90661f4: stp x20, x19, [sp, #0x20]
1b90661f8: stp x29, x30, [sp, #0x30]
1b90661fc: add x29, sp, #0x30
1b9066200: mov x20, x2
1b9066204: mov x21, x1
1b9066208: mov x19, x0
1b906620c: bl #0x1b9c44f20
1b9066210: add x8, x19, w0, uxtw
1b9066214: ldrb w8, [x8, #8]
1b9066218: tbz w8, #0, #0x1b90662ec
1b906621c: mov w8, w0
1b9066220: mov w9, #0x18
1b9066224: umaddl x8, w8, w9, x19
1b9066228: ldr x9, [x19, #0x40]
1b906622c: cmp x9, x21
1b9066230: b.ge #0x1b906623c
1b9066234: add x9, x19, #0x40
1b9066238: stlr x21, [x9]
1b906623c: ldur q0, [x8, #0x10]
1b9066240: mov x9, v0.d[1]
1b9066244: fmov x8, d0
1b9066248: cmp x8, x9
1b906624c: b.eq #0x1b90662bc
1b9066250: sub x9, x9, x8
1b9066254: asr x11, x9, #5
1b9066258: mov x10, #-1
1b906625c: mov x9, x8
1b9066260: lsr x12, x11, #1
1b9066264: add x13, x9, x12, lsl #5
1b9066268: ldr x14, [x13], #0x20
1b906626c: eor x15, x10, x11, lsr #1
1b9066270: add x11, x11, x15
1b9066274: cmp x14, x21
1b9066278: csel x9, x9, x13, gt
1b906627c: csel x11, x12, x11, gt
1b9066280: cbnz x11, #0x1b9066260
1b9066284: cmp x9, x8
1b9066288: mov x8, #-0x20
1b906628c: csel x8, x8, xzr, hi
1b9066290: add x8, x9, x8
1b9066294: ldr x9, [x8]
1b9066298: sub x9, x21, x9
1b906629c: scvtf d1, x9
1b90662a0: ldp d2, d0, [x8, #0x10]
1b90662a4: fdiv d1, d1, d0
1b90662a8: fadd d1, d2, d1
1b90662ac: frinta d8, d1
1b90662b0: cbz x20, #0x1b90662cc
1b90662b4: str d0, [x20]
1b90662b8: b #0x1b90662cc
1b90662bc: cbz x20, #0x1b90662c8
1b90662c0: mov x8, #0x3ff0000000000000
1b90662c4: str x8, [x20]
1b90662c8: scvtf d8, x21
1b90662cc: mov x0, x19
1b90662d0: bl #0x1b9c44f10
1b90662d4: fmov d0, d8
1b90662d8: ldp x29, x30, [sp, #0x30]
1b90662dc: ldp x20, x19, [sp, #0x20]
1b90662e0: ldp x22, x21, [sp, #0x10]
1b90662e4: ldp d9, d8, [sp], #0x40
1b90662e8: retab 
1b90662ec: brk #1
