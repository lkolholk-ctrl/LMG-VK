; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f9d194..0x234f9d234
; Code SHA256: 4604c9aa3826d217af940055425a331296a1eaf7c4b93240c1dce662eaea99c1
0x234f9d194: 7f2303d5 pacibsp 
0x234f9d198: ff4301d1 sub sp, sp, #0x50
0x234f9d19c: fd7b04a9 stp x29, x30, [sp, #0x40]
0x234f9d1a0: fd030191 add x29, sp, #0x40
0x234f9d1a4: 298a21f0 adrp x9, #0x2780e4000
0x234f9d1a8: 294545f9 ldr x9, [x9, #0xa88]
0x234f9d1ac: 290140f9 ldr x9, [x9]
0x234f9d1b0: a9831ff8 stur x9, [x29, #-8]
0x234f9d1b4: 290c0051 sub w9, w1, #3
0x234f9d1b8: 3f0d0031 cmn w9, #3
0x234f9d1bc: 29030054 b.ls #0x234f9d220
0x234f9d1c0: 42030035 cbnz w2, #0x234f9d228
0x234f9d1c4: 490300b0 adrp x9, #0x235006000
0x234f9d1c8: 29513e91 add x9, x9, #0xf94
0x234f9d1cc: 200540ad ldp q0, q1, [x9]
0x234f9d1d0: e00700ad stp q0, q1, [sp]
0x234f9d1d4: 2009c03d ldr q0, [x9, #0x20]
0x234f9d1d8: e00b803d str q0, [sp, #0x20]
0x234f9d1dc: e9030091 mov x9, sp
0x234f9d1e0: 1ffd00a9 stp xzr, xzr, [x8, #8]
0x234f9d1e4: 1f0100f9 str xzr, [x8]
0x234f9d1e8: e1030091 mov x1, sp
0x234f9d1ec: 22c10091 add x2, x9, #0x30
0x234f9d1f0: e00308aa mov x0, x8
0x234f9d1f4: 83018052 mov w3, #0xc
0x234f9d1f8: fb3efe97 bl #0x234f2cde4
0x234f9d1fc: a8835ff8 ldur x8, [x29, #-8]
0x234f9d200: 298a21f0 adrp x9, #0x2780e4000
0x234f9d204: 294545f9 ldr x9, [x9, #0xa88]
0x234f9d208: 290140f9 ldr x9, [x9]
0x234f9d20c: 3f0108eb cmp x9, x8
0x234f9d210: 01010054 b.ne #0x234f9d230
0x234f9d214: fd7b44a9 ldp x29, x30, [sp, #0x40]
0x234f9d218: ff430191 add sp, sp, #0x50
0x234f9d21c: ff0f5fd6 retab 
0x234f9d220: 204e8512 mov w0, #-0x2a72
0x234f9d224: bc3cfe97 bl #0x234f2c514
0x234f9d228: 804f8512 mov w0, #-0x2a7d
0x234f9d22c: ba3cfe97 bl #0x234f2c514
0x234f9d230: c8a07e94 bl #0x236f45550
