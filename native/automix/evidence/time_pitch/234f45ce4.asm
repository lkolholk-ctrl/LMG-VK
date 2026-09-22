; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f45ce4..0x234f4608c
; Code SHA256: f7ec513424310281965e7cf2a41fff34dca752687d872905e858c083978c0467
0x234f45ce4: 096849b9 ldr w9, [x0, #0x968]
0x234f45ce8: 08b844f9 ldr x8, [x0, #0x970]
0x234f45cec: 2901084b sub w9, w9, w8
0x234f45cf0: 2a0040b9 ldr w10, [x1]
0x234f45cf4: 5f01096b cmp w10, w9
0x234f45cf8: 4cb1891a csel w12, w10, w9, lt
0x234f45cfc: 9f050071 cmp w12, #1
0x234f45d00: 4b1c0054 b.lt #0x234f46088
0x234f45d04: 7f2303d5 pacibsp 
0x234f45d08: ff0303d1 sub sp, sp, #0xc0
0x234f45d0c: fc6f06a9 stp x28, x27, [sp, #0x60]
0x234f45d10: fa6707a9 stp x26, x25, [sp, #0x70]
0x234f45d14: f85f08a9 stp x24, x23, [sp, #0x80]
0x234f45d18: f65709a9 stp x22, x21, [sp, #0x90]
0x234f45d1c: f44f0aa9 stp x20, x19, [sp, #0xa0]
0x234f45d20: fd7b0ba9 stp x29, x30, [sp, #0xb0]
0x234f45d24: fdc30291 add x29, sp, #0xb0
0x234f45d28: f40300aa mov x20, x0
0x234f45d2c: e10f00a9 stp x1, x3, [sp]
0x234f45d30: 600040fd ldr d0, [x3]
0x234f45d34: 00a404fd str d0, [x0, #0x948]
0x234f45d38: 099844f9 ldr x9, [x0, #0x930]
0x234f45d3c: 2801088a and x8, x9, x8
0x234f45d40: 0abc44f9 ldr x10, [x0, #0x978]
0x234f45d44: 4901098a and x9, x10, x9
0x234f45d48: 188849b9 ldr w24, [x0, #0x988]
0x234f45d4c: 8a751e53 lsl w10, w12, #2
0x234f45d50: 0b2849b9 ldr w11, [x0, #0x928]
0x234f45d54: 6d01084b sub w13, w11, w8
0x234f45d58: edb304a9 stp x13, x12, [sp, #0x48]
0x234f45d5c: 8c010d6b subs w12, w12, w13
0x234f45d60: ec4300b9 str w12, [sp, #0x40]
0x234f45d64: 8d0c0054 b.le #0x234f45ef4
0x234f45d68: 8c7a48b9 ldr w12, [x20, #0x878]
0x234f45d6c: ac140034 cbz w12, #0x234f46000
0x234f45d70: 170080d2 mov x23, #0
0x234f45d74: 150080d2 mov x21, #0
0x234f45d78: ec2740f9 ldr x12, [sp, #0x48]
0x234f45d7c: 967d4093 sxtw x22, w12
0x234f45d80: 6b01094b sub w11, w11, w9
0x234f45d84: 6b751e53 lsl w11, w11, #2
0x234f45d88: eb1f00f9 str x11, [sp, #0x38]
0x234f45d8c: 99751e53 lsl w25, w12, #2
0x234f45d90: 0b7d7e93 sbfiz x11, x8, #2, #0x20
0x234f45d94: f4af02a9 stp x20, x11, [sp, #0x28]
0x234f45d98: 5b200091 add x27, x2, #8
0x234f45d9c: 2b7d7e93 sbfiz x11, x9, #2, #0x20
0x234f45da0: 087d4093 sxtw x8, w8
0x234f45da4: e8af01a9 stp x8, x11, [sp, #0x18]
0x234f45da8: 287d4093 sxtw x8, w9
0x234f45dac: e80b00f9 str x8, [sp, #0x10]
0x234f45db0: 1a068052 mov w26, #0x30
0x234f45db4: 53090c4b sub w19, w10, w12, lsl #2
0x234f45db8: f32f00f9 str x19, [sp, #0x58]
0x234f45dbc: 883244f9 ldr x8, [x20, #0x860]
0x234f45dc0: 0a01178b add x10, x8, x23
0x234f45dc4: 4b0940b9 ldr w11, [x10, #8]
0x234f45dc8: 490180b9 ldrsw x9, [x10]
0x234f45dcc: 6813098b add x8, x27, x9, lsl #4
0x234f45dd0: 7f090071 cmp w11, #2
0x234f45dd4: 81050054 b.ne #0x234f45e84
0x234f45dd8: 4a0580b9 ldrsw x10, [x10, #4]
0x234f45ddc: 8b2a45f9 ldr x11, [x20, #0xa50]
0x234f45de0: 292d3a9b smaddl x9, w9, w26, x11
0x234f45de4: 330540f9 ldr x19, [x9, #8]
0x234f45de8: 492d3a9b smaddl x9, w10, w26, x11
0x234f45dec: 3c0540f9 ldr x28, [x9, #8]
0x234f45df0: 080540f9 ldr x8, [x8, #8]
0x234f45df4: fa0316aa mov x26, x22
0x234f45df8: 1609188b add x22, x8, x24, lsl #2
0x234f45dfc: 68130a8b add x8, x27, x10, lsl #4
0x234f45e00: 080540f9 ldr x8, [x8, #8]
0x234f45e04: f4031baa mov x20, x27
0x234f45e08: fb0319aa mov x27, x25
0x234f45e0c: 1909188b add x25, x8, x24, lsl #2
0x234f45e10: e80f40f9 ldr x8, [sp, #0x18]
0x234f45e14: 610a088b add x1, x19, x8, lsl #2
0x234f45e18: 820b088b add x2, x28, x8, lsl #2
0x234f45e1c: e02740f9 ldr x0, [sp, #0x48]
0x234f45e20: e30316aa mov x3, x22
0x234f45e24: e40319aa mov x4, x25
0x234f45e28: 8cffff97 bl #0x234f45c58
0x234f45e2c: c30a1a8b add x3, x22, x26, lsl #2
0x234f45e30: f6031aaa mov x22, x26
0x234f45e34: 240b1a8b add x4, x25, x26, lsl #2
0x234f45e38: f9031baa mov x25, x27
0x234f45e3c: fb0314aa mov x27, x20
0x234f45e40: f41740f9 ldr x20, [sp, #0x28]
0x234f45e44: e04340b9 ldr w0, [sp, #0x40]
0x234f45e48: e10313aa mov x1, x19
0x234f45e4c: e2031caa mov x2, x28
0x234f45e50: 82ffff97 bl #0x234f45c58
0x234f45e54: fa0b40f9 ldr x26, [sp, #0x10]
0x234f45e58: 600a1a8b add x0, x19, x26, lsl #2
0x234f45e5c: e10319aa mov x1, x25
0x234f45e60: 04fe7f94 bl #0x236f45670
0x234f45e64: e00313aa mov x0, x19
0x234f45e68: f32f40f9 ldr x19, [sp, #0x58]
0x234f45e6c: e10313aa mov x1, x19
0x234f45e70: 00fe7f94 bl #0x236f45670
0x234f45e74: 800b1a8b add x0, x28, x26, lsl #2
0x234f45e78: 1a068052 mov w26, #0x30
0x234f45e7c: e11f40f9 ldr x1, [sp, #0x38]
0x234f45e80: 13000014 b #0x234f45ecc
0x234f45e84: 8a2a45f9 ldr x10, [x20, #0xa50]
0x234f45e88: 29293a9b smaddl x9, w9, w26, x10
0x234f45e8c: 3c0540f9 ldr x28, [x9, #8]
0x234f45e90: 080540f9 ldr x8, [x8, #8]
0x234f45e94: 1309188b add x19, x8, x24, lsl #2
0x234f45e98: e81b40f9 ldr x8, [sp, #0x30]
0x234f45e9c: 8103088b add x1, x28, x8
0x234f45ea0: e00313aa mov x0, x19
0x234f45ea4: e20319aa mov x2, x25
0x234f45ea8: 95d90294 bl #0x234ffc4fc
0x234f45eac: 600a168b add x0, x19, x22, lsl #2
0x234f45eb0: e1031caa mov x1, x28
0x234f45eb4: f32f40f9 ldr x19, [sp, #0x58]
0x234f45eb8: e20313aa mov x2, x19
0x234f45ebc: 90d90294 bl #0x234ffc4fc
0x234f45ec0: e81340f9 ldr x8, [sp, #0x20]
0x234f45ec4: 8003088b add x0, x28, x8
0x234f45ec8: e10319aa mov x1, x25
0x234f45ecc: e9fd7f94 bl #0x236f45670
0x234f45ed0: e0031caa mov x0, x28
0x234f45ed4: e10313aa mov x1, x19
0x234f45ed8: e6fd7f94 bl #0x236f45670
0x234f45edc: b5060091 add x21, x21, #1
0x234f45ee0: 887a48b9 ldr w8, [x20, #0x878]
0x234f45ee4: f7320091 add x23, x23, #0xc
0x234f45ee8: bf0208eb cmp x21, x8
0x234f45eec: 83f6ff54 b.lo #0x234f45dbc
0x234f45ef0: 44000014 b #0x234f46000
0x234f45ef4: 8b7a48b9 ldr w11, [x20, #0x878]
0x234f45ef8: 4b080034 cbz w11, #0x234f46000
0x234f45efc: 150080d2 mov x21, #0
0x234f45f00: 160080d2 mov x22, #0
0x234f45f04: eb2b40f9 ldr x11, [sp, #0x50]
0x234f45f08: 6b01080b add w11, w11, w8
0x234f45f0c: 6b01094b sub w11, w11, w9
0x234f45f10: 77751e53 lsl w23, w11, #2
0x234f45f14: 0b7d7e93 sbfiz x11, x8, #2, #0x20
0x234f45f18: eb2f00f9 str x11, [sp, #0x58]
0x234f45f1c: 4b7d4093 sxtw x11, w10
0x234f45f20: 2a7d7e93 sbfiz x10, x9, #2, #0x20
0x234f45f24: ea2f04a9 stp x10, x11, [sp, #0x40]
0x234f45f28: 5b200091 add x27, x2, #8
0x234f45f2c: 087d4093 sxtw x8, w8
0x234f45f30: e81f00f9 str x8, [sp, #0x38]
0x234f45f34: 397d4093 sxtw x25, w9
0x234f45f38: 1c068052 mov w28, #0x30
0x234f45f3c: 883244f9 ldr x8, [x20, #0x860]
0x234f45f40: 0901158b add x9, x8, x21
0x234f45f44: 2a0940b9 ldr w10, [x9, #8]
0x234f45f48: 280180b9 ldrsw x8, [x9]
0x234f45f4c: 5f090071 cmp w10, #2
0x234f45f50: 21030054 b.ne #0x234f45fb4
0x234f45f54: 290580b9 ldrsw x9, [x9, #4]
0x234f45f58: 8a2a45f9 ldr x10, [x20, #0xa50]
0x234f45f5c: 0c068052 mov w12, #0x30
0x234f45f60: 0b292c9b smaddl x11, w8, w12, x10
0x234f45f64: 7a0540f9 ldr x26, [x11, #8]
0x234f45f68: 1c068052 mov w28, #0x30
0x234f45f6c: 2a292c9b smaddl x10, w9, w12, x10
0x234f45f70: 530540f9 ldr x19, [x10, #8]
0x234f45f74: 6813088b add x8, x27, x8, lsl #4
0x234f45f78: 080540f9 ldr x8, [x8, #8]
0x234f45f7c: 0309188b add x3, x8, x24, lsl #2
0x234f45f80: 6813098b add x8, x27, x9, lsl #4
0x234f45f84: 080540f9 ldr x8, [x8, #8]
0x234f45f88: 0409188b add x4, x8, x24, lsl #2
0x234f45f8c: e81f40f9 ldr x8, [sp, #0x38]
0x234f45f90: 410b088b add x1, x26, x8, lsl #2
0x234f45f94: 620a088b add x2, x19, x8, lsl #2
0x234f45f98: e02b40f9 ldr x0, [sp, #0x50]
0x234f45f9c: 2fffff97 bl #0x234f45c58
0x234f45fa0: 400b198b add x0, x26, x25, lsl #2
0x234f45fa4: e10317aa mov x1, x23
0x234f45fa8: b2fd7f94 bl #0x236f45670
0x234f45fac: 600a198b add x0, x19, x25, lsl #2
0x234f45fb0: 0d000014 b #0x234f45fe4
0x234f45fb4: 892a45f9 ldr x9, [x20, #0xa50]
0x234f45fb8: 09253c9b smaddl x9, w8, w28, x9
0x234f45fbc: 330540f9 ldr x19, [x9, #8]
0x234f45fc0: 6813088b add x8, x27, x8, lsl #4
0x234f45fc4: 080540f9 ldr x8, [x8, #8]
0x234f45fc8: 0009188b add x0, x8, x24, lsl #2
0x234f45fcc: e82f40f9 ldr x8, [sp, #0x58]
0x234f45fd0: 6102088b add x1, x19, x8
0x234f45fd4: e22740f9 ldr x2, [sp, #0x48]
0x234f45fd8: 49d90294 bl #0x234ffc4fc
0x234f45fdc: e82340f9 ldr x8, [sp, #0x40]
0x234f45fe0: 6002088b add x0, x19, x8
0x234f45fe4: e10317aa mov x1, x23
0x234f45fe8: a2fd7f94 bl #0x236f45670
0x234f45fec: d6060091 add x22, x22, #1
0x234f45ff0: 887a48b9 ldr w8, [x20, #0x878]
0x234f45ff4: b5320091 add x21, x21, #0xc
0x234f45ff8: df0208eb cmp x22, x8
0x234f45ffc: 03faff54 b.lo #0x234f45f3c
0x234f46000: 88ba44f9 ldr x8, [x20, #0x970]
0x234f46004: eb2b40f9 ldr x11, [sp, #0x50]
0x234f46008: 08010b8b add x8, x8, x11
0x234f4600c: 88ba04f9 str x8, [x20, #0x970]
0x234f46010: 6001631e ucvtf d0, w11
0x234f46014: 81a644fd ldr d1, [x20, #0x948]
0x234f46018: 2128601e fadd d1, d1, d0
0x234f4601c: 81a604fd str d1, [x20, #0x948]
0x234f46020: 88be04f9 str x8, [x20, #0x978]
0x234f46024: e92b40a9 ldp x9, x10, [sp]
0x234f46028: 280140b9 ldr w8, [x9]
0x234f4602c: 08010b4b sub w8, w8, w11
0x234f46030: 280100b9 str w8, [x9]
0x234f46034: 888a49b9 ldr w8, [x20, #0x988]
0x234f46038: 08010b0b add w8, w8, w11
0x234f4603c: 888a09b9 str w8, [x20, #0x988]
0x234f46040: 410140fd ldr d1, [x10]
0x234f46044: 880a40f9 ldr x8, [x20, #0x10]
0x234f46048: 29008052 mov w9, #1
0x234f4604c: 89e20039 strb w9, [x20, #0x38]
0x234f46050: 812200fd str d1, [x20, #0x40]
0x234f46054: 88fe04a9 stp x8, xzr, [x20, #0x48]
0x234f46058: 08010b8b add x8, x8, x11
0x234f4605c: 880a00f9 str x8, [x20, #0x10]
0x234f46060: 2028601e fadd d0, d1, d0
0x234f46064: 400100fd str d0, [x10]
0x234f46068: fd7b4ba9 ldp x29, x30, [sp, #0xb0]
0x234f4606c: f44f4aa9 ldp x20, x19, [sp, #0xa0]
0x234f46070: f65749a9 ldp x22, x21, [sp, #0x90]
0x234f46074: f85f48a9 ldp x24, x23, [sp, #0x80]
0x234f46078: fa6747a9 ldp x26, x25, [sp, #0x70]
0x234f4607c: fc6f46a9 ldp x28, x27, [sp, #0x60]
0x234f46080: ff030391 add sp, sp, #0xc0
0x234f46084: ff2303d5 autibsp 
0x234f46088: c0035fd6 ret 
