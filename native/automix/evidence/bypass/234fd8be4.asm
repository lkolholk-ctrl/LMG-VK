; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fd8be4..0x234fd8d10
; Code SHA256: 1ccaaa677b337137d915f48d7293e6d6cead36298611dde4dd4085cc0550d572
0x234fd8be4: 7f2303d5 pacibsp 
0x234fd8be8: ff0302d1 sub sp, sp, #0x80
0x234fd8bec: f65705a9 stp x22, x21, [sp, #0x50]
0x234fd8bf0: f44f06a9 stp x20, x19, [sp, #0x60]
0x234fd8bf4: fd7b07a9 stp x29, x30, [sp, #0x70]
0x234fd8bf8: fdc30191 add x29, sp, #0x70
0x234fd8bfc: f30304aa mov x19, x4
0x234fd8c00: 3fe00071 cmp w1, #0x38
0x234fd8c04: c1050054 b.ne #0x234fd8cbc
0x234fd8c08: a2050035 cbnz w2, #0x234fd8cbc
0x234fd8c0c: 140440f9 ldr x20, [x0, #8]
0x234fd8c10: e8c30091 add x8, sp, #0x30
0x234fd8c14: ff2303a9 stp xzr, x8, [sp, #0x30]
0x234fd8c18: 29010090 adrp x9, #0x234ffc000
0x234fd8c1c: 20ad46fd ldr d0, [x9, #0xd58]
0x234fd8c20: e02300fd str d0, [sp, #0x40]
0x234fd8c24: a98d24d0 adrp x9, #0x27e18e000
0x234fd8c28: 353d41f9 ldr x21, [x9, #0x278]
0x234fd8c2c: f52700f9 str x21, [sp, #0x48]
0x234fd8c30: f50200b5 cbnz x21, #0x234fd8c8c
0x234fd8c34: e9230091 add x9, sp, #8
0x234fd8c38: 70882190 adrp x16, #0x2780e4000
0x234fd8c3c: 103645f9 ldr x16, [x16, #0xa68]
0x234fd8c40: f10309aa mov x17, x9
0x234fd8c44: 315cedf2 movk x17, #0x6ae1, lsl #48
0x234fd8c48: 300ac1da pacda x16, x17
0x234fd8c4c: f00700f9 str x16, [sp, #8]
0x234fd8c50: 2a010090 adrp x10, #0x234ffc000
0x234fd8c54: 40a946fd ldr d0, [x10, #0xd50]
0x234fd8c58: e00b00fd str d0, [sp, #0x10]
0x234fd8c5c: 29410091 add x9, x9, #0x10
0x234fd8c60: 10000090 adrp x16, #0x234fd8000
0x234fd8c64: 10423491 add x16, x16, #0xd10
0x234fd8c68: 3001c1da pacia x16, x9
0x234fd8c6c: 29eb21f0 adrp x9, #0x278d3f000
0x234fd8c70: 29412a91 add x9, x9, #0xa90
0x234fd8c74: f0a701a9 stp x16, x9, [sp, #0x18]
0x234fd8c78: e81700f9 str x8, [sp, #0x28]
0x234fd8c7c: e0230091 add x0, sp, #8
0x234fd8c80: 24000094 bl #0x234fd8d10
0x234fd8c84: e81f40f9 ldr x8, [sp, #0x38]
0x234fd8c88: 150d40f9 ldr x21, [x8, #0x18]
0x234fd8c8c: e0c30091 add x0, sp, #0x30
0x234fd8c90: 01018052 mov w1, #8
0x234fd8c94: b3ad7d94 bl #0x236f44360
0x234fd8c98: 750200b4 cbz x21, #0x234fd8ce4
0x234fd8c9c: e00314aa mov x0, x20
0x234fd8ca0: e10313aa mov x1, x19
0x234fd8ca4: bf0a3fd6 blraaz x21
0x234fd8ca8: fd7b47a9 ldp x29, x30, [sp, #0x70]
0x234fd8cac: f44f46a9 ldp x20, x19, [sp, #0x60]
0x234fd8cb0: f65745a9 ldp x22, x21, [sp, #0x50]
0x234fd8cb4: ff030291 add sp, sp, #0x80
0x234fd8cb8: ff0f5fd6 retab 
0x234fd8cbc: e40313aa mov x4, x19
0x234fd8cc0: fd7b47a9 ldp x29, x30, [sp, #0x70]
0x234fd8cc4: f44f46a9 ldp x20, x19, [sp, #0x60]
0x234fd8cc8: f65745a9 ldp x22, x21, [sp, #0x50]
0x234fd8ccc: ff030291 add sp, sp, #0x80
0x234fd8cd0: ff2303d5 autibsp 
0x234fd8cd4: d0071eca eor x16, x30, x30, lsl #1
0x234fd8cd8: 5000f0b6 tbz x16, #0x3e, #0x234fd8ce0
0x234fd8cdc: 208e38d4 brk #0xc471
0x234fd8ce0: 17740014 b #0x234ff5d3c
0x234fd8ce4: 9fb27d94 bl #0x236f45760
0x234fd8ce8: e00300f9 str x0, [sp]
0x234fd8cec: e00100b0 adrp x0, #0x235015000
0x234fd8cf0: 00200491 add x0, x0, #0x108
0x234fd8cf4: 3bb27d94 bl #0x236f455e0
0x234fd8cf8: f30300aa mov x19, x0
0x234fd8cfc: e0c30091 add x0, sp, #0x30
0x234fd8d00: 01018052 mov w1, #8
0x234fd8d04: 97ad7d94 bl #0x236f44360
0x234fd8d08: e00313aa mov x0, x19
0x234fd8d0c: a1ad7d94 bl #0x236f44390
