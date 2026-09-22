; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe4834..0x234fe4a94
; Code SHA256: c3dd75936839b857fb3ab0987efdd315f6cba3282f529f7c1e593a18af17c2c0
0x234fe4834: 7f2303d5 pacibsp 
0x234fe4838: ff8301d1 sub sp, sp, #0x60
0x234fe483c: fd7b05a9 stp x29, x30, [sp, #0x50]
0x234fe4840: fd430191 add x29, sp, #0x50
0x234fe4844: 08882190 adrp x8, #0x2780e4000
0x234fe4848: 084545f9 ldr x8, [x8, #0xa88]
0x234fe484c: 080140f9 ldr x8, [x8]
0x234fe4850: a8831ff8 stur x8, [x29, #-8]
0x234fe4854: c1100034 cbz w1, #0x234fe4a6c
0x234fe4858: 080080d2 mov x8, #0
0x234fe485c: 09800291 add x9, x0, #0xa0
0x234fe4860: ea230091 add x10, sp, #8
0x234fe4864: eb03012a mov w11, w1
0x234fe4868: 0c0080d2 mov x12, #0
0x234fe486c: ed230091 add x13, sp, #8
0x234fe4870: 0e000c8b add x14, x0, x12
0x234fe4874: d03d5329 ldp w16, w15, [x14, #0x98]
0x234fe4878: 10020f0a and w16, w16, w15
0x234fe487c: d13940f9 ldr x17, [x14, #0x70]
0x234fe4880: 205a70bc ldr s0, [x17, w16, uxtw #2]
0x234fe4884: c205512d ldp s2, s1, [x14, #0x88]
0x234fe4888: 0008211e fmul s0, s0, s1
0x234fe488c: c19140bd ldr s1, [x14, #0x90]
0x234fe4890: 4108211e fmul s1, s2, s1
0x234fe4894: 0028211e fadd s0, s0, s1
0x234fe4898: a04500bc str s0, [x13], #4
0x234fe489c: c09100bd str s0, [x14, #0x90]
0x234fe48a0: ef050011 add w15, w15, #1
0x234fe48a4: cf9d00b9 str w15, [x14, #0x9c]
0x234fe48a8: 8c210191 add x12, x12, #0x48
0x234fe48ac: 9f0112f1 cmp x12, #0x480
0x234fe48b0: 01feff54 b.ne #0x234fe4870
0x234fe48b4: 0c0080d2 mov x12, #0
0x234fe48b8: e007412d ldp s0, s1, [sp, #8]
0x234fe48bc: e20f422d ldp s2, s3, [sp, #0x10]
0x234fe48c0: e417432d ldp s4, s5, [sp, #0x18]
0x234fe48c4: e61f442d ldp s6, s7, [sp, #0x20]
0x234fe48c8: f047452d ldp s16, s17, [sp, #0x28]
0x234fe48cc: f24f462d ldp s18, s19, [sp, #0x30]
0x234fe48d0: f457472d ldp s20, s21, [sp, #0x38]
0x234fe48d4: f65f482d ldp s22, s23, [sp, #0x40]
0x234fe48d8: 1828211e fadd s24, s0, s1
0x234fe48dc: 0038211e fsub s0, s0, s1
0x234fe48e0: 4128231e fadd s1, s2, s3
0x234fe48e4: 4238231e fsub s2, s2, s3
0x234fe48e8: 8328251e fadd s3, s4, s5
0x234fe48ec: 8438251e fsub s4, s4, s5
0x234fe48f0: c528271e fadd s5, s6, s7
0x234fe48f4: c638271e fsub s6, s6, s7
0x234fe48f8: 072a311e fadd s7, s16, s17
0x234fe48fc: 103a311e fsub s16, s16, s17
0x234fe4900: 512a331e fadd s17, s18, s19
0x234fe4904: 523a331e fsub s18, s18, s19
0x234fe4908: 932a351e fadd s19, s20, s21
0x234fe490c: 943a351e fsub s20, s20, s21
0x234fe4910: d52a371e fadd s21, s22, s23
0x234fe4914: d63a371e fsub s22, s22, s23
0x234fe4918: 172b211e fadd s23, s24, s1
0x234fe491c: 1928221e fadd s25, s0, s2
0x234fe4920: 013b211e fsub s1, s24, s1
0x234fe4924: 0038221e fsub s0, s0, s2
0x234fe4928: 6228251e fadd s2, s3, s5
0x234fe492c: 9828261e fadd s24, s4, s6
0x234fe4930: 6338251e fsub s3, s3, s5
0x234fe4934: 8438261e fsub s4, s4, s6
0x234fe4938: e528311e fadd s5, s7, s17
0x234fe493c: 062a321e fadd s6, s16, s18
0x234fe4940: e738311e fsub s7, s7, s17
0x234fe4944: 103a321e fsub s16, s16, s18
0x234fe4948: 712a351e fadd s17, s19, s21
0x234fe494c: 922a361e fadd s18, s20, s22
0x234fe4950: 733a351e fsub s19, s19, s21
0x234fe4954: 943a361e fsub s20, s20, s22
0x234fe4958: f52a221e fadd s21, s23, s2
0x234fe495c: 362b381e fadd s22, s25, s24
0x234fe4960: 3a28231e fadd s26, s1, s3
0x234fe4964: 1b28241e fadd s27, s0, s4
0x234fe4968: e23a221e fsub s2, s23, s2
0x234fe496c: 373b381e fsub s23, s25, s24
0x234fe4970: 2138231e fsub s1, s1, s3
0x234fe4974: 0038241e fsub s0, s0, s4
0x234fe4978: a328311e fadd s3, s5, s17
0x234fe497c: c428321e fadd s4, s6, s18
0x234fe4980: f828331e fadd s24, s7, s19
0x234fe4984: 192a341e fadd s25, s16, s20
0x234fe4988: a538311e fsub s5, s5, s17
0x234fe498c: c638321e fsub s6, s6, s18
0x234fe4990: e738331e fsub s7, s7, s19
0x234fe4994: 103a341e fsub s16, s16, s20
0x234fe4998: b12a231e fadd s17, s21, s3
0x234fe499c: d22a241e fadd s18, s22, s4
0x234fe49a0: f14b012d stp s17, s18, [sp, #8]
0x234fe49a4: 512b381e fadd s17, s26, s24
0x234fe49a8: 732b391e fadd s19, s27, s25
0x234fe49ac: f14f022d stp s17, s19, [sp, #0x10]
0x234fe49b0: 5128251e fadd s17, s2, s5
0x234fe49b4: f32a261e fadd s19, s23, s6
0x234fe49b8: f14f032d stp s17, s19, [sp, #0x18]
0x234fe49bc: 3128271e fadd s17, s1, s7
0x234fe49c0: 1328301e fadd s19, s0, s16
0x234fe49c4: f14f042d stp s17, s19, [sp, #0x20]
0x234fe49c8: a33a231e fsub s3, s21, s3
0x234fe49cc: c43a241e fsub s4, s22, s4
0x234fe49d0: e313052d stp s3, s4, [sp, #0x28]
0x234fe49d4: 433b381e fsub s3, s26, s24
0x234fe49d8: 643b391e fsub s4, s27, s25
0x234fe49dc: e313062d stp s3, s4, [sp, #0x30]
0x234fe49e0: 4238251e fsub s2, s2, s5
0x234fe49e4: e33a261e fsub s3, s23, s6
0x234fe49e8: e20f072d stp s2, s3, [sp, #0x38]
0x234fe49ec: 2138271e fsub s1, s1, s7
0x234fe49f0: 0038301e fsub s0, s0, s16
0x234fe49f4: e103082d stp s1, s0, [sp, #0x40]
0x234fe49f8: 0004452d ldp s0, s1, [x0, #0x28]
0x234fe49fc: 400a201e fmul s0, s18, s0
0x234fe4a00: 023040bd ldr s2, [x0, #0x30]
0x234fe4a04: 2108221e fmul s1, s1, s2
0x234fe4a08: 0028211e fadd s0, s0, s1
0x234fe4a0c: 123000bd str s18, [x0, #0x30]
0x234fe4a10: 0204442d ldp s2, s1, [x0, #0x20]
0x234fe4a14: 0108211e fmul s1, s0, s1
0x234fe4a18: 407868bc ldr s0, [x2, x8, lsl #2]
0x234fe4a1c: 2138201e fsub s1, s1, s0
0x234fe4a20: 4108211e fmul s1, s2, s1
0x234fe4a24: 0128211e fadd s1, s0, s1
0x234fe4a28: 617828bc str s1, [x3, x8, lsl #2]
0x234fe4a2c: ed0309aa mov x13, x9
0x234fe4a30: 41696cbc ldr s1, [x10, x12]
0x234fe4a34: 0128211e fadd s1, s0, s1
0x234fe4a38: ae0140b9 ldr w14, [x13]
0x234fe4a3c: af815fb8 ldur w15, [x13, #-8]
0x234fe4a40: ef010e0a and w15, w15, w14
0x234fe4a44: b0015df8 ldur x16, [x13, #-0x30]
0x234fe4a48: 015a2fbc str s1, [x16, w15, uxtw #2]
0x234fe4a4c: ce050011 add w14, w14, #1
0x234fe4a50: ae8504b8 str w14, [x13], #0x48
0x234fe4a54: 8c110091 add x12, x12, #4
0x234fe4a58: 9f0101f1 cmp x12, #0x40
0x234fe4a5c: a1feff54 b.ne #0x234fe4a30
0x234fe4a60: 08050091 add x8, x8, #1
0x234fe4a64: 1f010beb cmp x8, x11
0x234fe4a68: 01f0ff54 b.ne #0x234fe4868
0x234fe4a6c: a8835ff8 ldur x8, [x29, #-8]
0x234fe4a70: 09882190 adrp x9, #0x2780e4000
0x234fe4a74: 294545f9 ldr x9, [x9, #0xa88]
0x234fe4a78: 290140f9 ldr x9, [x9]
0x234fe4a7c: 3f0108eb cmp x9, x8
0x234fe4a80: 81000054 b.ne #0x234fe4a90
0x234fe4a84: fd7b45a9 ldp x29, x30, [sp, #0x50]
0x234fe4a88: ff830191 add sp, sp, #0x60
0x234fe4a8c: ff0f5fd6 retab 
0x234fe4a90: b0827d94 bl #0x236f45550
