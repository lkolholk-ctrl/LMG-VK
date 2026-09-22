; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fde9b8..0x234fdeabc
; Code SHA256: ea67aae6fb6d4ea39c02caa626ff8b95f61a3ce2647c5086ff318fc08433c7cb
0x234fde9b8: 7f2303d5 pacibsp 
0x234fde9bc: f85fbca9 stp x24, x23, [sp, #-0x40]!
0x234fde9c0: f65701a9 stp x22, x21, [sp, #0x10]
0x234fde9c4: f44f02a9 stp x20, x19, [sp, #0x20]
0x234fde9c8: fd7b03a9 stp x29, x30, [sp, #0x30]
0x234fde9cc: fdc30091 add x29, sp, #0x30
0x234fde9d0: f70300aa mov x23, x0
0x234fde9d4: f50308aa mov x21, x8
0x234fde9d8: 94e59ed2 mov x20, #0xf72c
0x234fde9dc: f484bff2 movk x20, #0xfc27, lsl #16
0x234fde9e0: 1488c3f2 movk x20, #0x1c40, lsl #32
0x234fde9e4: 5421e0f2 movk x20, #0x10a, lsl #48
0x234fde9e8: 00088052 mov w0, #0x40
0x234fde9ec: e10314aa mov x1, x20
0x234fde9f0: 23750094 bl #0x234ffbe7c
0x234fde9f4: f30300aa mov x19, x0
0x234fde9f8: 1f1000b9 str wzr, [x0, #0x10]
0x234fde9fc: 90d727d0 adrp x16, #0x284ad0000
0x234fdea00: 10021c91 add x16, x16, #0x700
0x234fdea04: 10420091 add x16, x16, #0x10
0x234fdea08: f10300aa mov x17, x0
0x234fdea0c: 9179e7f2 movk x17, #0x3bcc, lsl #48
0x234fdea10: 300ac1da pacda x16, x17
0x234fdea14: 105c00a9 stp x16, x23, [x0]
0x234fdea18: f60300aa mov x22, x0
0x234fdea1c: df8e01f8 str xzr, [x22, #0x18]!
0x234fdea20: 1f1000f9 str xzr, [x0, #0x20]
0x234fdea24: 1f2800b9 str wzr, [x0, #0x28]
0x234fdea28: e00317aa mov x0, x23
0x234fdea2c: d26afd97 bl #0x234f39574
0x234fdea30: 0190641e fmov d1, #10.00000000
0x234fdea34: 0210601e fmov d2, #2.00000000
0x234fdea38: 0004421f fmadd d0, d0, d2, d1
0x234fdea3c: 0800791e fcvtzu w8, d0
0x234fdea40: 08050051 sub w8, w8, #1
0x234fdea44: 0811c05a clz w8, w8
0x234fdea48: e803084b neg w8, w8
0x234fdea4c: 29008052 mov w9, #1
0x234fdea50: 2121c81a lsl w1, w9, w8
0x234fdea54: 613e00b9 str w1, [x19, #0x3c]
0x234fdea58: e00316aa mov x0, x22
0x234fdea5c: ab64fd97 bl #0x234f37d08
0x234fdea60: 601240f9 ldr x0, [x19, #0x20]
0x234fdea64: 612a40b9 ldr w1, [x19, #0x28]
0x234fdea68: 029b7d94 bl #0x236f45670
0x234fdea6c: 683e40b9 ldr w8, [x19, #0x3c]
0x234fdea70: 08050051 sub w8, w8, #1
0x234fdea74: 68fe0629 stp w8, wzr, [x19, #0x34]
0x234fdea78: b30200f9 str x19, [x21]
0x234fdea7c: fd7b43a9 ldp x29, x30, [sp, #0x30]
0x234fdea80: f44f42a9 ldp x20, x19, [sp, #0x20]
0x234fdea84: f65741a9 ldp x22, x21, [sp, #0x10]
0x234fdea88: f85fc4a8 ldp x24, x23, [sp], #0x40
0x234fdea8c: ff0f5fd6 retab 
0x234fdea90: f50300aa mov x21, x0
0x234fdea94: c00240f9 ldr x0, [x22]
0x234fdea98: 800000b4 cbz x0, #0x234fdeaa8
0x234fdea9c: 04760094 bl #0x234ffc2ac
0x234fdeaa0: df7e00a9 stp xzr, xzr, [x22]
0x234fdeaa4: df1200b9 str wzr, [x22, #0x10]
0x234fdeaa8: e00313aa mov x0, x19
0x234fdeaac: e10314aa mov x1, x20
0x234fdeab0: 289a7d94 bl #0x236f45350
0x234fdeab4: e00315aa mov x0, x21
0x234fdeab8: 36967d94 bl #0x236f44390
