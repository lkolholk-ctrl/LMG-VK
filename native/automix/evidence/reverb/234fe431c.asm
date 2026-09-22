; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe431c..0x234fe45b0
; Code SHA256: d1ea77961d12224381a6698684e3a6622fc6989bf3c2387a7bbd9275f1298b1a
0x234fe431c: 7f2303d5 pacibsp 
0x234fe4320: ff8301d1 sub sp, sp, #0x60
0x234fe4324: fd7b05a9 stp x29, x30, [sp, #0x50]
0x234fe4328: fd430191 add x29, sp, #0x50
0x234fe432c: 08882190 adrp x8, #0x2780e4000
0x234fe4330: 084545f9 ldr x8, [x8, #0xa88]
0x234fe4334: 080140f9 ldr x8, [x8]
0x234fe4338: a8831ff8 stur x8, [x29, #-8]
0x234fe433c: 61120034 cbz w1, #0x234fe4588
0x234fe4340: 080080d2 mov x8, #0
0x234fe4344: 09800291 add x9, x0, #0xa0
0x234fe4348: 00102c1e fmov s0, #0.50000000
0x234fe434c: ea230091 add x10, sp, #8
0x234fe4350: eb03012a mov w11, w1
0x234fe4354: 0c0080d2 mov x12, #0
0x234fe4358: ed230091 add x13, sp, #8
0x234fe435c: 0e000c8b add x14, x0, x12
0x234fe4360: d03d5329 ldp w16, w15, [x14, #0x98]
0x234fe4364: 10020f0a and w16, w16, w15
0x234fe4368: d13940f9 ldr x17, [x14, #0x70]
0x234fe436c: 215a70bc ldr s1, [x17, w16, uxtw #2]
0x234fe4370: c309512d ldp s3, s2, [x14, #0x88]
0x234fe4374: 2108221e fmul s1, s1, s2
0x234fe4378: c29140bd ldr s2, [x14, #0x90]
0x234fe437c: 6208221e fmul s2, s3, s2
0x234fe4380: 2128221e fadd s1, s1, s2
0x234fe4384: a14500bc str s1, [x13], #4
0x234fe4388: c19100bd str s1, [x14, #0x90]
0x234fe438c: ef050011 add w15, w15, #1
0x234fe4390: cf9d00b9 str w15, [x14, #0x9c]
0x234fe4394: 8c210191 add x12, x12, #0x48
0x234fe4398: 9f0112f1 cmp x12, #0x480
0x234fe439c: 01feff54 b.ne #0x234fe435c
0x234fe43a0: 0c0080d2 mov x12, #0
0x234fe43a4: e10b412d ldp s1, s2, [sp, #8]
0x234fe43a8: e313422d ldp s3, s4, [sp, #0x10]
0x234fe43ac: e51b432d ldp s5, s6, [sp, #0x18]
0x234fe43b0: e743442d ldp s7, s16, [sp, #0x20]
0x234fe43b4: f14b452d ldp s17, s18, [sp, #0x28]
0x234fe43b8: f353462d ldp s19, s20, [sp, #0x30]
0x234fe43bc: f55b472d ldp s21, s22, [sp, #0x38]
0x234fe43c0: f763482d ldp s23, s24, [sp, #0x40]
0x234fe43c4: 3928221e fadd s25, s1, s2
0x234fe43c8: 2138221e fsub s1, s1, s2
0x234fe43cc: 6228241e fadd s2, s3, s4
0x234fe43d0: 6338241e fsub s3, s3, s4
0x234fe43d4: a428261e fadd s4, s5, s6
0x234fe43d8: a538261e fsub s5, s5, s6
0x234fe43dc: e628301e fadd s6, s7, s16
0x234fe43e0: e738301e fsub s7, s7, s16
0x234fe43e4: 302a321e fadd s16, s17, s18
0x234fe43e8: 313a321e fsub s17, s17, s18
0x234fe43ec: 722a341e fadd s18, s19, s20
0x234fe43f0: 733a341e fsub s19, s19, s20
0x234fe43f4: b42a361e fadd s20, s21, s22
0x234fe43f8: b53a361e fsub s21, s21, s22
0x234fe43fc: f62a381e fadd s22, s23, s24
0x234fe4400: f73a381e fsub s23, s23, s24
0x234fe4404: 382b221e fadd s24, s25, s2
0x234fe4408: 3a28231e fadd s26, s1, s3
0x234fe440c: 223b221e fsub s2, s25, s2
0x234fe4410: 2138231e fsub s1, s1, s3
0x234fe4414: 8328261e fadd s3, s4, s6
0x234fe4418: b928271e fadd s25, s5, s7
0x234fe441c: 8438261e fsub s4, s4, s6
0x234fe4420: a538271e fsub s5, s5, s7
0x234fe4424: 062a321e fadd s6, s16, s18
0x234fe4428: 272a331e fadd s7, s17, s19
0x234fe442c: 103a321e fsub s16, s16, s18
0x234fe4430: 313a331e fsub s17, s17, s19
0x234fe4434: 922a361e fadd s18, s20, s22
0x234fe4438: b32a371e fadd s19, s21, s23
0x234fe443c: 943a361e fsub s20, s20, s22
0x234fe4440: b53a371e fsub s21, s21, s23
0x234fe4444: 162b231e fadd s22, s24, s3
0x234fe4448: 572b391e fadd s23, s26, s25
0x234fe444c: 5b28241e fadd s27, s2, s4
0x234fe4450: 3c28251e fadd s28, s1, s5
0x234fe4454: 033b231e fsub s3, s24, s3
0x234fe4458: 583b391e fsub s24, s26, s25
0x234fe445c: 4238241e fsub s2, s2, s4
0x234fe4460: 2138251e fsub s1, s1, s5
0x234fe4464: c428321e fadd s4, s6, s18
0x234fe4468: e528331e fadd s5, s7, s19
0x234fe446c: 192a341e fadd s25, s16, s20
0x234fe4470: 3a2a351e fadd s26, s17, s21
0x234fe4474: c638321e fsub s6, s6, s18
0x234fe4478: e738331e fsub s7, s7, s19
0x234fe447c: 103a341e fsub s16, s16, s20
0x234fe4480: 313a351e fsub s17, s17, s21
0x234fe4484: d22a241e fadd s18, s22, s4
0x234fe4488: f32a251e fadd s19, s23, s5
0x234fe448c: f24f012d stp s18, s19, [sp, #8]
0x234fe4490: 722b391e fadd s18, s27, s25
0x234fe4494: 942b3a1e fadd s20, s28, s26
0x234fe4498: f253022d stp s18, s20, [sp, #0x10]
0x234fe449c: 7428261e fadd s20, s3, s6
0x234fe44a0: 152b271e fadd s21, s24, s7
0x234fe44a4: f457032d stp s20, s21, [sp, #0x18]
0x234fe44a8: 5428301e fadd s20, s2, s16
0x234fe44ac: 3528311e fadd s21, s1, s17
0x234fe44b0: f457042d stp s20, s21, [sp, #0x20]
0x234fe44b4: c43a241e fsub s4, s22, s4
0x234fe44b8: e53a251e fsub s5, s23, s5
0x234fe44bc: e417052d stp s4, s5, [sp, #0x28]
0x234fe44c0: 643b391e fsub s4, s27, s25
0x234fe44c4: 853b3a1e fsub s5, s28, s26
0x234fe44c8: e417062d stp s4, s5, [sp, #0x30]
0x234fe44cc: 6338261e fsub s3, s3, s6
0x234fe44d0: 043b271e fsub s4, s24, s7
0x234fe44d4: e313072d stp s3, s4, [sp, #0x38]
0x234fe44d8: 4238301e fsub s2, s2, s16
0x234fe44dc: 2138311e fsub s1, s1, s17
0x234fe44e0: e207082d stp s2, s1, [sp, #0x40]
0x234fe44e4: 0108452d ldp s1, s2, [x0, #0x28]
0x234fe44e8: 630a211e fmul s3, s19, s1
0x234fe44ec: 0414462d ldp s4, s5, [x0, #0x30]
0x234fe44f0: 4408241e fmul s4, s2, s4
0x234fe44f4: 6328241e fadd s3, s3, s4
0x234fe44f8: 410a211e fmul s1, s18, s1
0x234fe44fc: 4208251e fmul s2, s2, s5
0x234fe4500: 2128221e fadd s1, s1, s2
0x234fe4504: 1348062d stp s19, s18, [x0, #0x30]
0x234fe4508: 0408442d ldp s4, s2, [x0, #0x20]
0x234fe450c: 6308221e fmul s3, s3, s2
0x234fe4510: 2108221e fmul s1, s1, s2
0x234fe4514: 427868bc ldr s2, [x2, x8, lsl #2]
0x234fe4518: 657868bc ldr s5, [x3, x8, lsl #2]
0x234fe451c: 6338221e fsub s3, s3, s2
0x234fe4520: 6308241e fmul s3, s3, s4
0x234fe4524: 4328231e fadd s3, s2, s3
0x234fe4528: 837828bc str s3, [x4, x8, lsl #2]
0x234fe452c: 032040bd ldr s3, [x0, #0x20]
0x234fe4530: 2138251e fsub s1, s1, s5
0x234fe4534: 2108231e fmul s1, s1, s3
0x234fe4538: a128211e fadd s1, s5, s1
0x234fe453c: a17828bc str s1, [x5, x8, lsl #2]
0x234fe4540: 4128251e fadd s1, s2, s5
0x234fe4544: 2108201e fmul s1, s1, s0
0x234fe4548: ed0309aa mov x13, x9
0x234fe454c: 42696cbc ldr s2, [x10, x12]
0x234fe4550: 2228221e fadd s2, s1, s2
0x234fe4554: ae0140b9 ldr w14, [x13]
0x234fe4558: af815fb8 ldur w15, [x13, #-8]
0x234fe455c: ef010e0a and w15, w15, w14
0x234fe4560: b0015df8 ldur x16, [x13, #-0x30]
0x234fe4564: 025a2fbc str s2, [x16, w15, uxtw #2]
0x234fe4568: ce050011 add w14, w14, #1
0x234fe456c: ae8504b8 str w14, [x13], #0x48
0x234fe4570: 8c110091 add x12, x12, #4
0x234fe4574: 9f0101f1 cmp x12, #0x40
0x234fe4578: a1feff54 b.ne #0x234fe454c
0x234fe457c: 08050091 add x8, x8, #1
0x234fe4580: 1f010beb cmp x8, x11
0x234fe4584: 81eeff54 b.ne #0x234fe4354
0x234fe4588: a8835ff8 ldur x8, [x29, #-8]
0x234fe458c: 09882190 adrp x9, #0x2780e4000
0x234fe4590: 294545f9 ldr x9, [x9, #0xa88]
0x234fe4594: 290140f9 ldr x9, [x9]
0x234fe4598: 3f0108eb cmp x9, x8
0x234fe459c: 81000054 b.ne #0x234fe45ac
0x234fe45a0: fd7b45a9 ldp x29, x30, [sp, #0x50]
0x234fe45a4: ff830191 add sp, sp, #0x60
0x234fe45a8: ff0f5fd6 retab 
0x234fe45ac: e9837d94 bl #0x236f45550
