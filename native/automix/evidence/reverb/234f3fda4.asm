; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f3fda4..0x234f400c0
; Code SHA256: a6ae4af09f32d7ab0251438b52f7e5728e831a7cb7a0bd1a49c1c34e9f87b070
0x234f3fda4: 7f2303d5 pacibsp 
0x234f3fda8: ff4301d1 sub sp, sp, #0x50
0x234f3fdac: f44f03a9 stp x20, x19, [sp, #0x30]
0x234f3fdb0: fd7b04a9 stp x29, x30, [sp, #0x40]
0x234f3fdb4: fd030191 add x29, sp, #0x40
0x234f3fdb8: f30300aa mov x19, x0
0x234f3fdbc: 22008052 mov w2, #1
0x234f3fdc0: 23008052 mov w3, #1
0x234f3fdc4: 04008052 mov w4, #0
0x234f3fdc8: 4318ff97 bl #0x234f05ed4
0x234f3fdcc: b0dc27d0 adrp x16, #0x284ad5000
0x234f3fdd0: 10e21e91 add x16, x16, #0x7b8
0x234f3fdd4: 10420091 add x16, x16, #0x10
0x234f3fdd8: f10300aa mov x17, x0
0x234f3fddc: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3fde0: 300ac1da pacda x16, x17
0x234f3fde4: 100000f9 str x16, [x0]
0x234f3fde8: 1f0801f9 str xzr, [x0, #0x210]
0x234f3fdec: 1f1001f9 str xzr, [x0, #0x220]
0x234f3fdf0: 1f0c01f9 str xzr, [x0, #0x218]
0x234f3fdf4: 6800c0d2 mov x8, #0x300000000
0x234f3fdf8: 081401f9 str x8, [x0, #0x228]
0x234f3fdfc: 1f600479 strh wzr, [x0, #0x230]
0x234f3fe00: 251bff97 bl #0x234f06a94
0x234f3fe04: 60820091 add x0, x19, #0x20
0x234f3fe08: 01008052 mov w1, #0
0x234f3fe0c: 4b37ff97 bl #0x234f0db38
0x234f3fe10: 41140036 tbz w1, #0, #0x234f40098
0x234f3fe14: 100040f9 ldr x16, [x0]
0x234f3fe18: f10300aa mov x17, x0
0x234f3fe1c: 9133fff2 movk x17, #0xf99c, lsl #48
0x234f3fe20: 301ac1da autda x16, x17
0x234f3fe24: 088e42f8 ldr x8, [x16, #0x28]!
0x234f3fe28: e90310aa mov x9, x16
0x234f3fe2c: e1008052 mov w1, #7
0x234f3fe30: f10309aa mov x17, x9
0x234f3fe34: b19fe2f2 movk x17, #0x14fd, lsl #48
0x234f3fe38: 11093fd7 blraa x8, x17
0x234f3fe3c: 700240f9 ldr x16, [x19]
0x234f3fe40: f10313aa mov x17, x19
0x234f3fe44: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3fe48: 301ac1da autda x16, x17
0x234f3fe4c: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3fe50: e90310aa mov x9, x16
0x234f3fe54: 00102c1e fmov s0, #0.50000000
0x234f3fe58: e00313aa mov x0, x19
0x234f3fe5c: 01008052 mov w1, #0
0x234f3fe60: 02008052 mov w2, #0
0x234f3fe64: 03008052 mov w3, #0
0x234f3fe68: 04008052 mov w4, #0
0x234f3fe6c: f10309aa mov x17, x9
0x234f3fe70: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3fe74: 11093fd7 blraa x8, x17
0x234f3fe78: 700240f9 ldr x16, [x19]
0x234f3fe7c: f10313aa mov x17, x19
0x234f3fe80: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3fe84: 301ac1da autda x16, x17
0x234f3fe88: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3fe8c: e90310aa mov x9, x16
0x234f3fe90: 00102e1e fmov s0, #1.00000000
0x234f3fe94: e00313aa mov x0, x19
0x234f3fe98: 21008052 mov w1, #1
0x234f3fe9c: 02008052 mov w2, #0
0x234f3fea0: 03008052 mov w3, #0
0x234f3fea4: 04008052 mov w4, #0
0x234f3fea8: f10309aa mov x17, x9
0x234f3feac: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3feb0: 11093fd7 blraa x8, x17
0x234f3feb4: 700240f9 ldr x16, [x19]
0x234f3feb8: f10313aa mov x17, x19
0x234f3febc: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3fec0: 301ac1da autda x16, x17
0x234f3fec4: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3fec8: e90310aa mov x9, x16
0x234f3fecc: ea0500d0 adrp x10, #0x234ffd000
0x234f3fed0: 40d147bd ldr s0, [x10, #0x7d0]
0x234f3fed4: e00313aa mov x0, x19
0x234f3fed8: 41008052 mov w1, #2
0x234f3fedc: 02008052 mov w2, #0
0x234f3fee0: 03008052 mov w3, #0
0x234f3fee4: 04008052 mov w4, #0
0x234f3fee8: f10309aa mov x17, x9
0x234f3feec: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3fef0: 11093fd7 blraa x8, x17
0x234f3fef4: 700240f9 ldr x16, [x19]
0x234f3fef8: f10313aa mov x17, x19
0x234f3fefc: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ff00: 301ac1da autda x16, x17
0x234f3ff04: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3ff08: e90310aa mov x9, x16
0x234f3ff0c: ea0500d0 adrp x10, #0x234ffd000
0x234f3ff10: 40d547bd ldr s0, [x10, #0x7d4]
0x234f3ff14: e00313aa mov x0, x19
0x234f3ff18: 61008052 mov w1, #3
0x234f3ff1c: 02008052 mov w2, #0
0x234f3ff20: 03008052 mov w3, #0
0x234f3ff24: 04008052 mov w4, #0
0x234f3ff28: f10309aa mov x17, x9
0x234f3ff2c: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ff30: 11093fd7 blraa x8, x17
0x234f3ff34: 700240f9 ldr x16, [x19]
0x234f3ff38: f10313aa mov x17, x19
0x234f3ff3c: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ff40: 301ac1da autda x16, x17
0x234f3ff44: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3ff48: e90310aa mov x9, x16
0x234f3ff4c: 00102e1e fmov s0, #1.00000000
0x234f3ff50: e00313aa mov x0, x19
0x234f3ff54: 81008052 mov w1, #4
0x234f3ff58: 02008052 mov w2, #0
0x234f3ff5c: 03008052 mov w3, #0
0x234f3ff60: 04008052 mov w4, #0
0x234f3ff64: f10309aa mov x17, x9
0x234f3ff68: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ff6c: 11093fd7 blraa x8, x17
0x234f3ff70: 700240f9 ldr x16, [x19]
0x234f3ff74: f10313aa mov x17, x19
0x234f3ff78: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ff7c: 301ac1da autda x16, x17
0x234f3ff80: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3ff84: e90310aa mov x9, x16
0x234f3ff88: 00102c1e fmov s0, #0.50000000
0x234f3ff8c: e00313aa mov x0, x19
0x234f3ff90: a1008052 mov w1, #5
0x234f3ff94: 02008052 mov w2, #0
0x234f3ff98: 03008052 mov w3, #0
0x234f3ff9c: 04008052 mov w4, #0
0x234f3ffa0: f10309aa mov x17, x9
0x234f3ffa4: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ffa8: 11093fd7 blraa x8, x17
0x234f3ffac: 700240f9 ldr x16, [x19]
0x234f3ffb0: f10313aa mov x17, x19
0x234f3ffb4: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ffb8: 301ac1da autda x16, x17
0x234f3ffbc: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3ffc0: e90310aa mov x9, x16
0x234f3ffc4: 00102e1e fmov s0, #1.00000000
0x234f3ffc8: e00313aa mov x0, x19
0x234f3ffcc: c1008052 mov w1, #6
0x234f3ffd0: 02008052 mov w2, #0
0x234f3ffd4: 03008052 mov w3, #0
0x234f3ffd8: 04008052 mov w4, #0
0x234f3ffdc: f10309aa mov x17, x9
0x234f3ffe0: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ffe4: 11093fd7 blraa x8, x17
0x234f3ffe8: 0810d1d2 mov x8, #0x888000000000
0x234f3ffec: a81ce8f2 movk x8, #0x40e5, lsl #48
0x234f3fff0: e80700f9 str x8, [sp, #8]
0x234f3fff4: e80500d0 adrp x8, #0x234ffd000
0x234f3fff8: 01c9c23d ldr q1, [x8, #0xb20]
0x234f3fffc: e80500d0 adrp x8, #0x234ffd000
0x234f40000: 00cdc23d ldr q0, [x8, #0xb30]
0x234f40004: e18300ad stp q1, q0, [sp, #0x10]
0x234f40008: 60420191 add x0, x19, #0x50
0x234f4000c: 01008052 mov w1, #0
0x234f40010: df1bff97 bl #0x234f06f8c
0x234f40014: c00300b4 cbz x0, #0x234f4008c
0x234f40018: 100040f9 ldr x16, [x0]
0x234f4001c: f10300aa mov x17, x0
0x234f40020: 9133fff2 movk x17, #0xf99c, lsl #48
0x234f40024: 301ac1da autda x16, x17
0x234f40028: 088e43f8 ldr x8, [x16, #0x38]!
0x234f4002c: e90310aa mov x9, x16
0x234f40030: e1230091 add x1, sp, #8
0x234f40034: f10309aa mov x17, x9
0x234f40038: 51b0f5f2 movk x17, #0xad82, lsl #48
0x234f4003c: 11093fd7 blraa x8, x17
0x234f40040: 60020291 add x0, x19, #0x80
0x234f40044: 01008052 mov w1, #0
0x234f40048: d11bff97 bl #0x234f06f8c
0x234f4004c: 000200b4 cbz x0, #0x234f4008c
0x234f40050: 100040f9 ldr x16, [x0]
0x234f40054: f10300aa mov x17, x0
0x234f40058: 9133fff2 movk x17, #0xf99c, lsl #48
0x234f4005c: 301ac1da autda x16, x17
0x234f40060: 088e43f8 ldr x8, [x16, #0x38]!
0x234f40064: e90310aa mov x9, x16
0x234f40068: e1230091 add x1, sp, #8
0x234f4006c: f10309aa mov x17, x9
0x234f40070: 51b0f5f2 movk x17, #0xad82, lsl #48
0x234f40074: 11093fd7 blraa x8, x17
0x234f40078: e00313aa mov x0, x19
0x234f4007c: fd7b44a9 ldp x29, x30, [sp, #0x40]
0x234f40080: f44f43a9 ldp x20, x19, [sp, #0x30]
0x234f40084: ff430191 add sp, sp, #0x50
0x234f40088: ff0f5fd6 retab 
0x234f4008c: 804f8512 mov w0, #-0x2a7d
0x234f40090: 21b1ff97 bl #0x234f2c514
0x234f40094: 200020d4 brk #1
0x234f40098: 4e158094 bl #0x236f455d0
0x234f4009c: 01000014 b #0x234f400a0
0x234f400a0: f40300aa mov x20, x0
0x234f400a4: 60420891 add x0, x19, #0x210
0x234f400a8: 010080d2 mov x1, #0
0x234f400ac: 37940294 bl #0x234fe5188
0x234f400b0: e00313aa mov x0, x19
0x234f400b4: b960ff97 bl #0x234f18398
0x234f400b8: e00314aa mov x0, x20
0x234f400bc: b5108094 bl #0x236f44390
