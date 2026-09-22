; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f3ec74..0x234f3efac
; Code SHA256: 8dfc4ba3c1e4582655d813c3402eac6a20908223d833549610db203ca6a680a8
0x234f3ec74: 7f2303d5 pacibsp 
0x234f3ec78: ff8301d1 sub sp, sp, #0x60
0x234f3ec7c: f65703a9 stp x22, x21, [sp, #0x30]
0x234f3ec80: f44f04a9 stp x20, x19, [sp, #0x40]
0x234f3ec84: fd7b05a9 stp x29, x30, [sp, #0x50]
0x234f3ec88: fd430191 add x29, sp, #0x50
0x234f3ec8c: f30300aa mov x19, x0
0x234f3ec90: 34008052 mov w20, #1
0x234f3ec94: 22008052 mov w2, #1
0x234f3ec98: 23008052 mov w3, #1
0x234f3ec9c: 04008052 mov w4, #0
0x234f3eca0: 8d1cff97 bl #0x234f05ed4
0x234f3eca4: d0dc27d0 adrp x16, #0x284ad8000
0x234f3eca8: 10a20391 add x16, x16, #0xe8
0x234f3ecac: 10420091 add x16, x16, #0x10
0x234f3ecb0: f10300aa mov x17, x0
0x234f3ecb4: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ecb8: 300ac1da pacda x16, x17
0x234f3ecbc: 100000f9 str x16, [x0]
0x234f3ecc0: 010440f9 ldr x1, [x0, #8]
0x234f3ecc4: e0230091 add x0, sp, #8
0x234f3ecc8: 001eff97 bl #0x234f064c8
0x234f3eccc: e80b40b9 ldr w8, [sp, #8]
0x234f3ecd0: 89ed8d52 mov w9, #0x6f6c
0x234f3ecd4: a92eac72 movk w9, #0x6175, lsl #16
0x234f3ecd8: 1f01096b cmp w8, w9
0x234f3ecdc: e8179f1a cset w8, eq
0x234f3ece0: 68420839 strb w8, [x19, #0x210]
0x234f3ece4: 7f0e01f9 str xzr, [x19, #0x218]
0x234f3ece8: 7f2202b9 str wzr, [x19, #0x220]
0x234f3ecec: 68a20891 add x8, x19, #0x228
0x234f3ecf0: 7f8601f9 str xzr, [x19, #0x308]
0x234f3ecf4: 74020679 strh w20, [x19, #0x300]
0x234f3ecf8: 7f0e0c39 strb wzr, [x19, #0x303]
0x234f3ecfc: 7f820c39 strb wzr, [x19, #0x320]
0x234f3ed00: 00e4006f movi v0.2d, #0000000000000000
0x234f3ed04: 000100ad stp q0, q0, [x8]
0x234f3ed08: 000101ad stp q0, q0, [x8, #0x20]
0x234f3ed0c: 000102ad stp q0, q0, [x8, #0x40]
0x234f3ed10: 000103ad stp q0, q0, [x8, #0x60]
0x234f3ed14: 000104ad stp q0, q0, [x8, #0x80]
0x234f3ed18: 000105ad stp q0, q0, [x8, #0xa0]
0x234f3ed1c: 0031803d str q0, [x8, #0xc0]
0x234f3ed20: 08fee7d2 mov x8, #0x3ff0000000000000
0x234f3ed24: 688e01f9 str x8, [x19, #0x318]
0x234f3ed28: 74a20c39 strb w20, [x19, #0x328]
0x234f3ed2c: 60c20c91 add x0, x19, #0x330
0x234f3ed30: 10178094 bl #0x236f44970
0x234f3ed34: 08108052 mov w8, #0x80
0x234f3ed38: 683a03b9 str w8, [x19, #0x338]
0x234f3ed3c: 08208052 mov w8, #0x100
0x234f3ed40: 687a0679 strh w8, [x19, #0x33c]
0x234f3ed44: 08fef7d2 mov x8, #-0x4010000000000000
0x234f3ed48: 68a201f9 str x8, [x19, #0x340]
0x234f3ed4c: 68220d91 add x8, x19, #0x348
0x234f3ed50: 74820d91 add x20, x19, #0x360
0x234f3ed54: 00e4006f movi v0.2d, #0000000000000000
0x234f3ed58: 60021bad stp q0, q0, [x19, #0x360]
0x234f3ed5c: 60021cad stp q0, q0, [x19, #0x380]
0x234f3ed60: 7fd201f9 str xzr, [x19, #0x3a0]
0x234f3ed64: 0001803d str q0, [x8]
0x234f3ed68: 28008052 mov w8, #1
0x234f3ed6c: 68a20e39 strb w8, [x19, #0x3a8]
0x234f3ed70: 68c20e91 add x8, x19, #0x3b0
0x234f3ed74: 68da01f9 str x8, [x19, #0x3b0]
0x234f3ed78: 74de01f9 str x20, [x19, #0x3b8]
0x234f3ed7c: 68020f91 add x8, x19, #0x3c0
0x234f3ed80: 68e201f9 str x8, [x19, #0x3c0]
0x234f3ed84: 74e601f9 str x20, [x19, #0x3c8]
0x234f3ed88: 7fea01f9 str xzr, [x19, #0x3d0]
0x234f3ed8c: e00313aa mov x0, x19
0x234f3ed90: 411fff97 bl #0x234f06a94
0x234f3ed94: 0810d1d2 mov x8, #0x888000000000
0x234f3ed98: a81ce8f2 movk x8, #0x40e5, lsl #48
0x234f3ed9c: e80700f9 str x8, [sp, #8]
0x234f3eda0: e80500f0 adrp x8, #0x234ffd000
0x234f3eda4: 01c9c23d ldr q1, [x8, #0xb20]
0x234f3eda8: e80500f0 adrp x8, #0x234ffd000
0x234f3edac: 00cdc23d ldr q0, [x8, #0xb30]
0x234f3edb0: e18300ad stp q1, q0, [sp, #0x10]
0x234f3edb4: 60420191 add x0, x19, #0x50
0x234f3edb8: 01008052 mov w1, #0
0x234f3edbc: 7420ff97 bl #0x234f06f8c
0x234f3edc0: 600d00b4 cbz x0, #0x234f3ef6c
0x234f3edc4: 100040f9 ldr x16, [x0]
0x234f3edc8: f10300aa mov x17, x0
0x234f3edcc: 9133fff2 movk x17, #0xf99c, lsl #48
0x234f3edd0: 301ac1da autda x16, x17
0x234f3edd4: 088e43f8 ldr x8, [x16, #0x38]!
0x234f3edd8: e90310aa mov x9, x16
0x234f3eddc: e1230091 add x1, sp, #8
0x234f3ede0: f10309aa mov x17, x9
0x234f3ede4: 51b0f5f2 movk x17, #0xad82, lsl #48
0x234f3ede8: 11093fd7 blraa x8, x17
0x234f3edec: 60020291 add x0, x19, #0x80
0x234f3edf0: 01008052 mov w1, #0
0x234f3edf4: 6620ff97 bl #0x234f06f8c
0x234f3edf8: a00b00b4 cbz x0, #0x234f3ef6c
0x234f3edfc: 100040f9 ldr x16, [x0]
0x234f3ee00: f10300aa mov x17, x0
0x234f3ee04: 9133fff2 movk x17, #0xf99c, lsl #48
0x234f3ee08: 301ac1da autda x16, x17
0x234f3ee0c: 088e43f8 ldr x8, [x16, #0x38]!
0x234f3ee10: e90310aa mov x9, x16
0x234f3ee14: e1230091 add x1, sp, #8
0x234f3ee18: f10309aa mov x17, x9
0x234f3ee1c: 51b0f5f2 movk x17, #0xad82, lsl #48
0x234f3ee20: 11093fd7 blraa x8, x17
0x234f3ee24: 700240f9 ldr x16, [x19]
0x234f3ee28: f10313aa mov x17, x19
0x234f3ee2c: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ee30: 301ac1da autda x16, x17
0x234f3ee34: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3ee38: e90310aa mov x9, x16
0x234f3ee3c: 00102e1e fmov s0, #1.00000000
0x234f3ee40: e00313aa mov x0, x19
0x234f3ee44: 01008052 mov w1, #0
0x234f3ee48: 02008052 mov w2, #0
0x234f3ee4c: 03008052 mov w3, #0
0x234f3ee50: 04008052 mov w4, #0
0x234f3ee54: f10309aa mov x17, x9
0x234f3ee58: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ee5c: 11093fd7 blraa x8, x17
0x234f3ee60: 700240f9 ldr x16, [x19]
0x234f3ee64: f10313aa mov x17, x19
0x234f3ee68: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ee6c: 301ac1da autda x16, x17
0x234f3ee70: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3ee74: e90310aa mov x9, x16
0x234f3ee78: 00e4002f movi d0, #0000000000000000
0x234f3ee7c: e00313aa mov x0, x19
0x234f3ee80: 21008052 mov w1, #1
0x234f3ee84: 02008052 mov w2, #0
0x234f3ee88: 03008052 mov w3, #0
0x234f3ee8c: 04008052 mov w4, #0
0x234f3ee90: f10309aa mov x17, x9
0x234f3ee94: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ee98: 11093fd7 blraa x8, x17
0x234f3ee9c: 700240f9 ldr x16, [x19]
0x234f3eea0: f10313aa mov x17, x19
0x234f3eea4: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3eea8: 301ac1da autda x16, x17
0x234f3eeac: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3eeb0: e90310aa mov x9, x16
0x234f3eeb4: 0010241e fmov s0, #8.00000000
0x234f3eeb8: e00313aa mov x0, x19
0x234f3eebc: 81008052 mov w1, #4
0x234f3eec0: 02008052 mov w2, #0
0x234f3eec4: 03008052 mov w3, #0
0x234f3eec8: 04008052 mov w4, #0
0x234f3eecc: f10309aa mov x17, x9
0x234f3eed0: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3eed4: 11093fd7 blraa x8, x17
0x234f3eed8: 700240f9 ldr x16, [x19]
0x234f3eedc: f10313aa mov x17, x19
0x234f3eee0: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3eee4: 301ac1da autda x16, x17
0x234f3eee8: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3eeec: e90310aa mov x9, x16
0x234f3eef0: 00102e1e fmov s0, #1.00000000
0x234f3eef4: e00313aa mov x0, x19
0x234f3eef8: c1008052 mov w1, #6
0x234f3eefc: 02008052 mov w2, #0
0x234f3ef00: 03008052 mov w3, #0
0x234f3ef04: 04008052 mov w4, #0
0x234f3ef08: f10309aa mov x17, x9
0x234f3ef0c: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ef10: 11093fd7 blraa x8, x17
0x234f3ef14: 700240f9 ldr x16, [x19]
0x234f3ef18: f10313aa mov x17, x19
0x234f3ef1c: 11aef5f2 movk x17, #0xad70, lsl #48
0x234f3ef20: 301ac1da autda x16, x17
0x234f3ef24: 088e49f8 ldr x8, [x16, #0x98]!
0x234f3ef28: e90310aa mov x9, x16
0x234f3ef2c: 00102e1e fmov s0, #1.00000000
0x234f3ef30: e00313aa mov x0, x19
0x234f3ef34: e1008052 mov w1, #7
0x234f3ef38: 02008052 mov w2, #0
0x234f3ef3c: 03008052 mov w3, #0
0x234f3ef40: 04008052 mov w4, #0
0x234f3ef44: f10309aa mov x17, x9
0x234f3ef48: f1f3f7f2 movk x17, #0xbf9f, lsl #48
0x234f3ef4c: 11093fd7 blraa x8, x17
0x234f3ef50: 7f020679 strh wzr, [x19, #0x300]
0x234f3ef54: e00313aa mov x0, x19
0x234f3ef58: fd7b45a9 ldp x29, x30, [sp, #0x50]
0x234f3ef5c: f44f44a9 ldp x20, x19, [sp, #0x40]
0x234f3ef60: f65743a9 ldp x22, x21, [sp, #0x30]
0x234f3ef64: ff830191 add sp, sp, #0x60
0x234f3ef68: ff0f5fd6 retab 
0x234f3ef6c: 804f8512 mov w0, #-0x2a7d
0x234f3ef70: 69b5ff97 bl #0x234f2c514
0x234f3ef74: 200020d4 brk #1
0x234f3ef78: 04000014 b #0x234f3ef88
0x234f3ef7c: 01000014 b #0x234f3ef80
0x234f3ef80: f50300aa mov x21, x0
0x234f3ef84: 06000014 b #0x234f3ef9c
0x234f3ef88: f50300aa mov x21, x0
0x234f3ef8c: e00314aa mov x0, x20
0x234f3ef90: 80178094 bl #0x236f44d90
0x234f3ef94: 60c20c91 add x0, x19, #0x330
0x234f3ef98: 7a168094 bl #0x236f44980
0x234f3ef9c: e00313aa mov x0, x19
0x234f3efa0: fe64ff97 bl #0x234f18398
0x234f3efa4: e00315aa mov x0, x21
0x234f3efa8: fa148094 bl #0x236f44390
