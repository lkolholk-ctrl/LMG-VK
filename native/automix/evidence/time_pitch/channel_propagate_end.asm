1b8f635b8: pacibsp 
1b8f635bc: sub sp, sp, #0x80
1b8f635c0: stp d9, d8, [sp, #0x40]
1b8f635c4: stp x22, x21, [sp, #0x50]
1b8f635c8: stp x20, x19, [sp, #0x60]
1b8f635cc: stp x29, x30, [sp, #0x70]
1b8f635d0: add x29, sp, #0x70
1b8f635d4: mov x19, x2
1b8f635d8: mov x20, x0
1b8f635dc: adrp x8, #0x1e6bef000
1b8f635e0: ldr x8, [x8, #0x758]
1b8f635e4: ldr x8, [x8]
1b8f635e8: str x8, [sp, #0x38]
1b8f635ec: cmp w1, #3
1b8f635f0: b.eq #0x1b8f63630
1b8f635f4: mov x21, x1
1b8f635f8: cmp w1, #2
1b8f635fc: b.eq #0x1b8f63620
1b8f63600: cmp w21, #1
1b8f63604: b.ne #0x1b8f63650
1b8f63608: ldr x8, [x20, #0x330]
1b8f6360c: cbz x8, #0x1b8f63620
1b8f63610: ldrb w8, [x20, #0x320]
1b8f63614: tbz w8, #0, #0x1b8f63620
1b8f63618: mov w21, #1
1b8f6361c: b #0x1b8f63650
1b8f63620: ldr x8, [x20, #0x570]
1b8f63624: cbz x8, #0x1b8f63630
1b8f63628: mov w21, #2
1b8f6362c: b #0x1b8f63650
1b8f63630: ldr x8, [x20, #0x330]
1b8f63634: cbz x8, #0x1b8f63644
1b8f63638: ldrb w8, [x20, #0x320]
1b8f6363c: cmp w8, #1
1b8f63640: b.ne #0x1b8f6364c
1b8f63644: mov w21, #4
1b8f63648: b #0x1b8f63650
1b8f6364c: mov w21, #3
1b8f63650: ldr w8, [x20, #0x538]
1b8f63654: cmp w8, w21
1b8f63658: b.ge #0x1b8f637d8
1b8f6365c: str w21, [x20, #0x538]
1b8f63660: adrp x8, #0x1ebd81000
1b8f63664: ldr x8, [x8, #0x7d0]
1b8f63668: cbz x8, #0x1b8f636f8
1b8f6366c: ldrb w9, [x8, #8]
1b8f63670: tbz w9, #0, #0x1b8f636f8
1b8f63674: ldr x22, [x8]
1b8f63678: cbz x22, #0x1b8f636f8
1b8f6367c: mov x0, x22
1b8f63680: mov w1, #2
1b8f63684: bl #0x1b9c46450
1b8f63688: cbz w0, #0x1b8f636f8
1b8f6368c: ldr x8, [x20, #0x10]
1b8f63690: adrp x9, #0x1b916b000
1b8f63694: ldr d0, [x9, #0xc60]
1b8f63698: str s0, [sp]
1b8f6369c: adrp x9, #0x1b919b000
1b8f636a0: add x9, x9, #0x98e
1b8f636a4: stur x9, [sp, #4]
1b8f636a8: mov w9, #0x400
1b8f636ac: strh w9, [sp, #0xc]
1b8f636b0: mov w10, #0x23b
1b8f636b4: stur w10, [sp, #0xe]
1b8f636b8: mov w10, #0x800
1b8f636bc: strh w10, [sp, #0x12]
1b8f636c0: stur x8, [sp, #0x14]
1b8f636c4: strh w9, [sp, #0x1c]
1b8f636c8: stur w21, [sp, #0x1e]
1b8f636cc: strh w10, [sp, #0x22]
1b8f636d0: stur x19, [sp, #0x24]
1b8f636d4: adrp x0, #0x1b8ebb000
1b8f636d8: add x0, x0, #0
1b8f636dc: adrp x3, #0x1b91c1000
1b8f636e0: add x3, x3, #0x20b
1b8f636e4: mov x4, sp
1b8f636e8: mov x1, x22
1b8f636ec: mov w2, #2
1b8f636f0: mov w5, #0x2c
1b8f636f4: bl #0x1b9c45880
1b8f636f8: cmp w21, #4
1b8f636fc: b.ne #0x1b8f637d8
1b8f63700: ldr x8, [x20, #0x10]
1b8f63704: ldr x8, [x8, #0x20]
1b8f63708: cbnz x8, #0x1b8f637d8
1b8f6370c: ldr x0, [x20, #0x2c8]
1b8f63710: ldr x16, [x0]
1b8f63714: mov x17, x0
1b8f63718: movk x17, #0x5b15, lsl #48
1b8f6371c: autda x16, x17
1b8f63720: ldr x8, [x16, #0x20]!
1b8f63724: mov x9, x16
1b8f63728: mov x17, x9
1b8f6372c: movk x17, #0xda7b, lsl #48
1b8f63730: blraa x8, x17
1b8f63734: ldr x16, [x0]
1b8f63738: mov x17, x0
1b8f6373c: movk x17, #0x95f8, lsl #48
1b8f63740: autda x16, x17
1b8f63744: ldr x8, [x16, #0xb0]!
1b8f63748: mov x9, x16
1b8f6374c: mov x17, x9
1b8f63750: movk x17, #0x6ef5, lsl #48
1b8f63754: blraa x8, x17
1b8f63758: ucvtf d8, w0
1b8f6375c: ldr x0, [x20, #0x2c8]
1b8f63760: ldr x16, [x0]
1b8f63764: mov x17, x0
1b8f63768: movk x17, #0x5b15, lsl #48
1b8f6376c: autda x16, x17
1b8f63770: ldr x8, [x16, #0x20]!
1b8f63774: mov x9, x16
1b8f63778: mov x17, x9
1b8f6377c: movk x17, #0xda7b, lsl #48
1b8f63780: blraa x8, x17
1b8f63784: ldr x16, [x0]
1b8f63788: mov x17, x0
1b8f6378c: movk x17, #0x95f8, lsl #48
1b8f63790: autda x16, x17
1b8f63794: ldr x8, [x16, #0x30]!
1b8f63798: mov x9, x16
1b8f6379c: mov x17, x9
1b8f637a0: movk x17, #0x71f0, lsl #48
1b8f637a4: blraa x8, x17
1b8f637a8: ldr d0, [x0]
1b8f637ac: fdiv d0, d8, d0
1b8f637b0: adrp x8, #0x1b916b000
1b8f637b4: ldr d1, [x8, #0xf08]
1b8f637b8: fadd d0, d0, d1
1b8f637bc: adrp x8, #0x1b916b000
1b8f637c0: ldr d1, [x8, #0xd30]
1b8f637c4: fmul d0, d0, d1
1b8f637c8: fcvtzu x8, d0
1b8f637cc: ldr x9, [x20, #0x10]
1b8f637d0: add x8, x8, x19
1b8f637d4: str x8, [x9, #0x20]
1b8f637d8: ldr x8, [sp, #0x38]
1b8f637dc: adrp x9, #0x1e6bef000
1b8f637e0: ldr x9, [x9, #0x758]
1b8f637e4: ldr x9, [x9]
1b8f637e8: cmp x9, x8
1b8f637ec: b.ne #0x1b8f63808
1b8f637f0: ldp x29, x30, [sp, #0x70]
1b8f637f4: ldp x20, x19, [sp, #0x60]
1b8f637f8: ldp x22, x21, [sp, #0x50]
1b8f637fc: ldp d9, d8, [sp, #0x40]
1b8f63800: add sp, sp, #0x80
1b8f63804: retab 
1b8f63808: bl #0x1b9c457f0
1b8f6380c: pacibsp 
