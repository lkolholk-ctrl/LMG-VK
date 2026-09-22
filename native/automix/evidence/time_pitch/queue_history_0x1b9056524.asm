1b9056524: pacibsp 
1b9056528: stp d9, d8, [sp, #-0x30]!
1b905652c: stp x20, x19, [sp, #0x10]
1b9056530: stp x29, x30, [sp, #0x20]
1b9056534: add x29, sp, #0x20
1b9056538: fmov d8, d0
1b905653c: mov x19, x0
1b9056540: bl #0x1b9c44f20
1b9056544: add x8, x19, w0, uxtw
1b9056548: ldrb w8, [x8, #8]
1b905654c: tbz w8, #0, #0x1b9056610
1b9056550: mov w8, w0
1b9056554: mov w9, #0x18
1b9056558: umaddl x8, w8, w9, x19
1b905655c: ldr q0, [x8, #0x10]
1b9056560: mov x9, v0.d[1]
1b9056564: fmov x8, d0
1b9056568: cmp x8, x9
1b905656c: b.eq #0x1b90565dc
1b9056570: sub x9, x9, x8
1b9056574: asr x12, x9, #5
1b9056578: fcvtas x10, d8
1b905657c: mov x11, #-1
1b9056580: mov x9, x8
1b9056584: lsr x13, x12, #1
1b9056588: add x14, x9, x13, lsl #5
1b905658c: ldr x15, [x14, #8]
1b9056590: add x14, x14, #0x20
1b9056594: eor x16, x11, x12, lsr #1
1b9056598: add x12, x12, x16
1b905659c: cmp x15, x10
1b90565a0: csel x9, x9, x14, gt
1b90565a4: csel x12, x13, x12, gt
1b90565a8: cbnz x12, #0x1b9056584
1b90565ac: cmp x9, x8
1b90565b0: mov x8, #-0x20
1b90565b4: csel x8, x8, xzr, hi
1b90565b8: add x8, x9, x8
1b90565bc: ldp d1, d0, [x8]
1b90565c0: scvtf d0, d0
1b90565c4: fsub d0, d8, d0
1b90565c8: ldr d2, [x8, #0x18]
1b90565cc: scvtf d1, d1
1b90565d0: fmadd d0, d0, d2, d1
1b90565d4: fcvtas x20, d0
1b90565d8: b #0x1b90565e0
1b90565dc: fcvtzs x20, d8
1b90565e0: ldr x8, [x19, #0x40]
1b90565e4: cmp x8, x20
1b90565e8: b.ge #0x1b90565f4
1b90565ec: add x8, x19, #0x40
1b90565f0: stlr x20, [x8]
1b90565f4: mov x0, x19
1b90565f8: bl #0x1b9c44f10
1b90565fc: mov x0, x20
1b9056600: ldp x29, x30, [sp, #0x20]
1b9056604: ldp x20, x19, [sp, #0x10]
1b9056608: ldp d9, d8, [sp], #0x30
1b905660c: retab 
1b9056610: brk #1
