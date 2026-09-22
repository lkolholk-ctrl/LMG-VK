; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe45b0..0x234fe4834
; Code SHA256: a2c9a587c8fa51ce91974b25165c937df57ec10da1fbe57b15c003e2272f24ca
0x234fe45b0: 7f2303d5 pacibsp 
0x234fe45b4: ff8301d1 sub sp, sp, #0x60
0x234fe45b8: fd7b05a9 stp x29, x30, [sp, #0x50]
0x234fe45bc: fd430191 add x29, sp, #0x50
0x234fe45c0: 08882190 adrp x8, #0x2780e4000
0x234fe45c4: 084545f9 ldr x8, [x8, #0xa88]
0x234fe45c8: 080140f9 ldr x8, [x8]
0x234fe45cc: a8831ff8 stur x8, [x29, #-8]
0x234fe45d0: e1110034 cbz w1, #0x234fe480c
0x234fe45d4: 080080d2 mov x8, #0
0x234fe45d8: 09800291 add x9, x0, #0xa0
0x234fe45dc: ea230091 add x10, sp, #8
0x234fe45e0: eb03012a mov w11, w1
0x234fe45e4: 0c0080d2 mov x12, #0
0x234fe45e8: ed230091 add x13, sp, #8
0x234fe45ec: 0e000c8b add x14, x0, x12
0x234fe45f0: d03d5329 ldp w16, w15, [x14, #0x98]
0x234fe45f4: 10020f0a and w16, w16, w15
0x234fe45f8: d13940f9 ldr x17, [x14, #0x70]
0x234fe45fc: 205a70bc ldr s0, [x17, w16, uxtw #2]
0x234fe4600: c205512d ldp s2, s1, [x14, #0x88]
0x234fe4604: 0008211e fmul s0, s0, s1
0x234fe4608: c19140bd ldr s1, [x14, #0x90]
0x234fe460c: 4108211e fmul s1, s2, s1
0x234fe4610: 0028211e fadd s0, s0, s1
0x234fe4614: a04500bc str s0, [x13], #4
0x234fe4618: c09100bd str s0, [x14, #0x90]
0x234fe461c: ef050011 add w15, w15, #1
0x234fe4620: cf9d00b9 str w15, [x14, #0x9c]
0x234fe4624: 8c210191 add x12, x12, #0x48
0x234fe4628: 9f0112f1 cmp x12, #0x480
0x234fe462c: 01feff54 b.ne #0x234fe45ec
0x234fe4630: 0c0080d2 mov x12, #0
0x234fe4634: e007412d ldp s0, s1, [sp, #8]
0x234fe4638: e20f422d ldp s2, s3, [sp, #0x10]
0x234fe463c: e417432d ldp s4, s5, [sp, #0x18]
0x234fe4640: e61f442d ldp s6, s7, [sp, #0x20]
0x234fe4644: f047452d ldp s16, s17, [sp, #0x28]
0x234fe4648: f24f462d ldp s18, s19, [sp, #0x30]
0x234fe464c: f457472d ldp s20, s21, [sp, #0x38]
0x234fe4650: f65f482d ldp s22, s23, [sp, #0x40]
0x234fe4654: 1828211e fadd s24, s0, s1
0x234fe4658: 0038211e fsub s0, s0, s1
0x234fe465c: 4128231e fadd s1, s2, s3
0x234fe4660: 4238231e fsub s2, s2, s3
0x234fe4664: 8328251e fadd s3, s4, s5
0x234fe4668: 8438251e fsub s4, s4, s5
0x234fe466c: c528271e fadd s5, s6, s7
0x234fe4670: c638271e fsub s6, s6, s7
0x234fe4674: 072a311e fadd s7, s16, s17
0x234fe4678: 103a311e fsub s16, s16, s17
0x234fe467c: 512a331e fadd s17, s18, s19
0x234fe4680: 523a331e fsub s18, s18, s19
0x234fe4684: 932a351e fadd s19, s20, s21
0x234fe4688: 943a351e fsub s20, s20, s21
0x234fe468c: d52a371e fadd s21, s22, s23
0x234fe4690: d63a371e fsub s22, s22, s23
0x234fe4694: 172b211e fadd s23, s24, s1
0x234fe4698: 1928221e fadd s25, s0, s2
0x234fe469c: 013b211e fsub s1, s24, s1
0x234fe46a0: 0038221e fsub s0, s0, s2
0x234fe46a4: 6228251e fadd s2, s3, s5
0x234fe46a8: 9828261e fadd s24, s4, s6
0x234fe46ac: 6338251e fsub s3, s3, s5
0x234fe46b0: 8438261e fsub s4, s4, s6
0x234fe46b4: e528311e fadd s5, s7, s17
0x234fe46b8: 062a321e fadd s6, s16, s18
0x234fe46bc: e738311e fsub s7, s7, s17
0x234fe46c0: 103a321e fsub s16, s16, s18
0x234fe46c4: 712a351e fadd s17, s19, s21
0x234fe46c8: 922a361e fadd s18, s20, s22
0x234fe46cc: 733a351e fsub s19, s19, s21
0x234fe46d0: 943a361e fsub s20, s20, s22
0x234fe46d4: f52a221e fadd s21, s23, s2
0x234fe46d8: 362b381e fadd s22, s25, s24
0x234fe46dc: 3a28231e fadd s26, s1, s3
0x234fe46e0: 1b28241e fadd s27, s0, s4
0x234fe46e4: e23a221e fsub s2, s23, s2
0x234fe46e8: 373b381e fsub s23, s25, s24
0x234fe46ec: 2138231e fsub s1, s1, s3
0x234fe46f0: 0038241e fsub s0, s0, s4
0x234fe46f4: a328311e fadd s3, s5, s17
0x234fe46f8: c428321e fadd s4, s6, s18
0x234fe46fc: f828331e fadd s24, s7, s19
0x234fe4700: 192a341e fadd s25, s16, s20
0x234fe4704: a538311e fsub s5, s5, s17
0x234fe4708: c638321e fsub s6, s6, s18
0x234fe470c: e738331e fsub s7, s7, s19
0x234fe4710: 103a341e fsub s16, s16, s20
0x234fe4714: b12a231e fadd s17, s21, s3
0x234fe4718: d22a241e fadd s18, s22, s4
0x234fe471c: f14b012d stp s17, s18, [sp, #8]
0x234fe4720: 512b381e fadd s17, s26, s24
0x234fe4724: 732b391e fadd s19, s27, s25
0x234fe4728: f14f022d stp s17, s19, [sp, #0x10]
0x234fe472c: 5328251e fadd s19, s2, s5
0x234fe4730: f42a261e fadd s20, s23, s6
0x234fe4734: f353032d stp s19, s20, [sp, #0x18]
0x234fe4738: 3328271e fadd s19, s1, s7
0x234fe473c: 1428301e fadd s20, s0, s16
0x234fe4740: f353042d stp s19, s20, [sp, #0x20]
0x234fe4744: a33a231e fsub s3, s21, s3
0x234fe4748: c43a241e fsub s4, s22, s4
0x234fe474c: e313052d stp s3, s4, [sp, #0x28]
0x234fe4750: 433b381e fsub s3, s26, s24
0x234fe4754: 643b391e fsub s4, s27, s25
0x234fe4758: e313062d stp s3, s4, [sp, #0x30]
0x234fe475c: 4238251e fsub s2, s2, s5
0x234fe4760: e33a261e fsub s3, s23, s6
0x234fe4764: e20f072d stp s2, s3, [sp, #0x38]
0x234fe4768: 2138271e fsub s1, s1, s7
0x234fe476c: 0038301e fsub s0, s0, s16
0x234fe4770: e103082d stp s1, s0, [sp, #0x40]
0x234fe4774: 0004452d ldp s0, s1, [x0, #0x28]
0x234fe4778: 420a201e fmul s2, s18, s0
0x234fe477c: 0310462d ldp s3, s4, [x0, #0x30]
0x234fe4780: 2308231e fmul s3, s1, s3
0x234fe4784: 4228231e fadd s2, s2, s3
0x234fe4788: 200a201e fmul s0, s17, s0
0x234fe478c: 2108241e fmul s1, s1, s4
0x234fe4790: 0028211e fadd s0, s0, s1
0x234fe4794: 1244062d stp s18, s17, [x0, #0x30]
0x234fe4798: 0304442d ldp s3, s1, [x0, #0x20]
0x234fe479c: 4208211e fmul s2, s2, s1
0x234fe47a0: 0108211e fmul s1, s0, s1
0x234fe47a4: 407868bc ldr s0, [x2, x8, lsl #2]
0x234fe47a8: 4238201e fsub s2, s2, s0
0x234fe47ac: 6208221e fmul s2, s3, s2
0x234fe47b0: 0228221e fadd s2, s0, s2
0x234fe47b4: 627828bc str s2, [x3, x8, lsl #2]
0x234fe47b8: 022040bd ldr s2, [x0, #0x20]
0x234fe47bc: 2138201e fsub s1, s1, s0
0x234fe47c0: 2108221e fmul s1, s1, s2
0x234fe47c4: 0128211e fadd s1, s0, s1
0x234fe47c8: 817828bc str s1, [x4, x8, lsl #2]
0x234fe47cc: ed0309aa mov x13, x9
0x234fe47d0: 41696cbc ldr s1, [x10, x12]
0x234fe47d4: 0128211e fadd s1, s0, s1
0x234fe47d8: ae0140b9 ldr w14, [x13]
0x234fe47dc: af815fb8 ldur w15, [x13, #-8]
0x234fe47e0: ef010e0a and w15, w15, w14
0x234fe47e4: b0015df8 ldur x16, [x13, #-0x30]
0x234fe47e8: 015a2fbc str s1, [x16, w15, uxtw #2]
0x234fe47ec: ce050011 add w14, w14, #1
0x234fe47f0: ae8504b8 str w14, [x13], #0x48
0x234fe47f4: 8c110091 add x12, x12, #4
0x234fe47f8: 9f0101f1 cmp x12, #0x40
0x234fe47fc: a1feff54 b.ne #0x234fe47d0
0x234fe4800: 08050091 add x8, x8, #1
0x234fe4804: 1f010beb cmp x8, x11
0x234fe4808: e1eeff54 b.ne #0x234fe45e4
0x234fe480c: a8835ff8 ldur x8, [x29, #-8]
0x234fe4810: 09882190 adrp x9, #0x2780e4000
0x234fe4814: 294545f9 ldr x9, [x9, #0xa88]
0x234fe4818: 290140f9 ldr x9, [x9]
0x234fe481c: 3f0108eb cmp x9, x8
0x234fe4820: 81000054 b.ne #0x234fe4830
0x234fe4824: fd7b45a9 ldp x29, x30, [sp, #0x50]
0x234fe4828: ff830191 add sp, sp, #0x60
0x234fe482c: ff0f5fd6 retab 
0x234fe4830: 48837d94 bl #0x236f45550
