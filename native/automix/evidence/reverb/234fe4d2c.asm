; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe4d2c..0x234fe4ec8
; Code SHA256: c7c889b2950a95f38f469b4b4b498d7f6b521589b9046fd7d6a517423598acf1
0x234fe4d2c: 7f2303d5 pacibsp 
0x234fe4d30: ff0302d1 sub sp, sp, #0x80
0x234fe4d34: f65705a9 stp x22, x21, [sp, #0x50]
0x234fe4d38: f44f06a9 stp x20, x19, [sp, #0x60]
0x234fe4d3c: fd7b07a9 stp x29, x30, [sp, #0x70]
0x234fe4d40: fdc30191 add x29, sp, #0x70
0x234fe4d44: e2000034 cbz w2, #0x234fe4d60
0x234fe4d48: c04f8512 mov w0, #-0x2a7f
0x234fe4d4c: fd7b47a9 ldp x29, x30, [sp, #0x70]
0x234fe4d50: f44f46a9 ldp x20, x19, [sp, #0x60]
0x234fe4d54: f65745a9 ldp x22, x21, [sp, #0x50]
0x234fe4d58: ff030291 add sp, sp, #0x80
0x234fe4d5c: ff0f5fd6 retab 
0x234fe4d60: f30304aa mov x19, x4
0x234fe4d64: f40300aa mov x20, x0
0x234fe4d68: 3f280071 cmp w1, #0xa
0x234fe4d6c: a0060054 b.eq #0x234fe4e40
0x234fe4d70: 3fe00071 cmp w1, #0x38
0x234fe4d74: 60010054 b.eq #0x234fe4da0
0x234fe4d78: 3f540071 cmp w1, #0x15
0x234fe4d7c: 61feff54 b.ne #0x234fe4d48
0x234fe4d80: bf100071 cmp w5, #4
0x234fe4d84: 23060054 b.lo #0x234fe4e48
0x234fe4d88: 00008052 mov w0, #0
0x234fe4d8c: 680240b9 ldr w8, [x19]
0x234fe4d90: 1f010071 cmp w8, #0
0x234fe4d94: e8079f1a cset w8, ne
0x234fe4d98: 88c20839 strb w8, [x20, #0x230]
0x234fe4d9c: ecffff17 b #0x234fe4d4c
0x234fe4da0: 940640f9 ldr x20, [x20, #8]
0x234fe4da4: e8c30091 add x8, sp, #0x30
0x234fe4da8: ff2303a9 stp xzr, x8, [sp, #0x30]
0x234fe4dac: c9000090 adrp x9, #0x234ffc000
0x234fe4db0: 20ad46fd ldr d0, [x9, #0xd58]
0x234fe4db4: e02300fd str d0, [sp, #0x40]
0x234fe4db8: 498d24d0 adrp x9, #0x27e18e000
0x234fe4dbc: 356541f9 ldr x21, [x9, #0x2c8]
0x234fe4dc0: f52700f9 str x21, [sp, #0x48]
0x234fe4dc4: f50200b5 cbnz x21, #0x234fe4e20
0x234fe4dc8: e9230091 add x9, sp, #8
0x234fe4dcc: 10882190 adrp x16, #0x2780e4000
0x234fe4dd0: 103645f9 ldr x16, [x16, #0xa68]
0x234fe4dd4: f10309aa mov x17, x9
0x234fe4dd8: 315cedf2 movk x17, #0x6ae1, lsl #48
0x234fe4ddc: 300ac1da pacda x16, x17
0x234fe4de0: f00700f9 str x16, [sp, #8]
0x234fe4de4: ca000090 adrp x10, #0x234ffc000
0x234fe4de8: 40a946fd ldr d0, [x10, #0xd50]
0x234fe4dec: e00b00fd str d0, [sp, #0x10]
0x234fe4df0: 29410091 add x9, x9, #0x10
0x234fe4df4: 10000090 adrp x16, #0x234fe4000
0x234fe4df8: 10223b91 add x16, x16, #0xec8
0x234fe4dfc: 3001c1da pacia x16, x9
0x234fe4e00: c9ea21f0 adrp x9, #0x278d3f000
0x234fe4e04: 29c12e91 add x9, x9, #0xbb0
0x234fe4e08: f0a701a9 stp x16, x9, [sp, #0x18]
0x234fe4e0c: e81700f9 str x8, [sp, #0x28]
0x234fe4e10: e0230091 add x0, sp, #8
0x234fe4e14: 2d000094 bl #0x234fe4ec8
0x234fe4e18: e81f40f9 ldr x8, [sp, #0x38]
0x234fe4e1c: 150d40f9 ldr x21, [x8, #0x18]
0x234fe4e20: e0c30091 add x0, sp, #0x30
0x234fe4e24: 01018052 mov w1, #8
0x234fe4e28: 4e7d7d94 bl #0x236f44360
0x234fe4e2c: 950300b4 cbz x21, #0x234fe4e9c
0x234fe4e30: e00314aa mov x0, x20
0x234fe4e34: e10313aa mov x1, x19
0x234fe4e38: bf0a3fd6 blraaz x21
0x234fe4e3c: c4ffff17 b #0x234fe4d4c
0x234fe4e40: bf100071 cmp w5, #4
0x234fe4e44: 62000054 b.hs #0x234fe4e50
0x234fe4e48: 404c8512 mov w0, #-0x2a63
0x234fe4e4c: c0ffff17 b #0x234fe4d4c
0x234fe4e50: 610240b9 ldr w1, [x19]
0x234fe4e54: 538d24b0 adrp x19, #0x27e18d000
0x234fe4e58: 73622791 add x19, x19, #0x9d8
0x234fe4e5c: a8018052 mov w8, #0xd
0x234fe4e60: 404c8512 mov w0, #-0x2a63
0x234fe4e64: 690240b9 ldr w9, [x19]
0x234fe4e68: 3f00096b cmp w1, w9
0x234fe4e6c: a0000054 b.eq #0x234fe4e80
0x234fe4e70: 73420091 add x19, x19, #0x10
0x234fe4e74: 080500f1 subs x8, x8, #1
0x234fe4e78: 61ffff54 b.ne #0x234fe4e64
0x234fe4e7c: b4ffff17 b #0x234fe4d4c
0x234fe4e80: e00314aa mov x0, x20
0x234fe4e84: dbf8ff97 bl #0x234fe31f0
0x234fe4e88: e00314aa mov x0, x20
0x234fe4e8c: e10313aa mov x1, x19
0x234fe4e90: 994e0094 bl #0x234ff88f4
0x234fe4e94: 00008052 mov w0, #0
0x234fe4e98: adffff17 b #0x234fe4d4c
0x234fe4e9c: 31827d94 bl #0x236f45760
0x234fe4ea0: e00300f9 str x0, [sp]
0x234fe4ea4: 800100b0 adrp x0, #0x235015000
0x234fe4ea8: 00200491 add x0, x0, #0x108
0x234fe4eac: cd817d94 bl #0x236f455e0
0x234fe4eb0: f30300aa mov x19, x0
0x234fe4eb4: e0c30091 add x0, sp, #0x30
0x234fe4eb8: 01018052 mov w1, #8
0x234fe4ebc: 297d7d94 bl #0x236f44360
0x234fe4ec0: e00313aa mov x0, x19
0x234fe4ec4: 337d7d94 bl #0x236f44390
