; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f459ac..0x234f45c58
; Code SHA256: bfa4e56d1cab6d8022e6c78486ff65f443687a01e311844c91c90a2efe112bcb
0x234f459ac: 280040b9 ldr w8, [x1]
0x234f459b0: e80c0034 cbz w8, #0x234f45b4c
0x234f459b4: 7f2303d5 pacibsp 
0x234f459b8: ff8302d1 sub sp, sp, #0xa0
0x234f459bc: e923036d stp d9, d8, [sp, #0x30]
0x234f459c0: fc6f04a9 stp x28, x27, [sp, #0x40]
0x234f459c4: fa6705a9 stp x26, x25, [sp, #0x50]
0x234f459c8: f85f06a9 stp x24, x23, [sp, #0x60]
0x234f459cc: f65707a9 stp x22, x21, [sp, #0x70]
0x234f459d0: f44f08a9 stp x20, x19, [sp, #0x80]
0x234f459d4: fd7b09a9 stp x29, x30, [sp, #0x90]
0x234f459d8: fd430291 add x29, sp, #0x90
0x234f459dc: f60301aa mov x22, x1
0x234f459e0: f30300aa mov x19, x0
0x234f459e4: 09b848b9 ldr w9, [x0, #0x8b8]
0x234f459e8: 3f01086b cmp w9, w8
0x234f459ec: 3b31881a csel w27, w9, w8, lo
0x234f459f0: 009c44fd ldr d0, [x0, #0x938]
0x234f459f4: e01700fd str d0, [sp, #0x28]
0x234f459f8: 083845f9 ldr x8, [x0, #0xa70]
0x234f459fc: 023c45f9 ldr x2, [x0, #0xa78]
0x234f45a00: 01a02991 add x1, x0, #0xa68
0x234f45a04: e3a30091 add x3, sp, #0x28
0x234f45a08: e0031baa mov x0, x27
0x234f45a0c: 1f093fd6 blraaz x8
0x234f45a10: 00110035 cbnz w0, #0x234f45c30
0x234f45a14: e00700b9 str w0, [sp, #4]
0x234f45a18: c80240b9 ldr w8, [x22]
0x234f45a1c: 08011b4b sub w8, w8, w27
0x234f45a20: c80200b9 str w8, [x22]
0x234f45a24: 693645f9 ldr x9, [x19, #0xa68]
0x234f45a28: e81740fd ldr d8, [sp, #0x28]
0x234f45a2c: 68aa44f9 ldr x8, [x19, #0x950]
0x234f45a30: 6a9244f9 ldr x10, [x19, #0x920]
0x234f45a34: 4801088a and x8, x10, x8
0x234f45a38: 75771e53 lsl w21, w27, #2
0x234f45a3c: 6a1a49b9 ldr w10, [x19, #0x918]
0x234f45a40: 5701084b sub w23, w10, w8
0x234f45a44: 6a03176b subs w10, w27, w23
0x234f45a48: ea1f00b9 str w10, [sp, #0x1c]
0x234f45a4c: 49080054 b.ls #0x234f45b54
0x234f45a50: 6a7a48b9 ldr w10, [x19, #0x878]
0x234f45a54: 2a0d0034 cbz w10, #0x234f45bf8
0x234f45a58: 190080d2 mov x25, #0
0x234f45a5c: 180080d2 mov x24, #0
0x234f45a60: ea761e53 lsl w10, w23, #2
0x234f45a64: 34210091 add x20, x9, #8
0x234f45a68: e803082a mov w8, w8
0x234f45a6c: e81300f9 str x8, [sp, #0x20]
0x234f45a70: 1c068052 mov w28, #0x30
0x234f45a74: a80a174b sub w8, w21, w23, lsl #2
0x234f45a78: e8ab00a9 stp x8, x10, [sp, #8]
0x234f45a7c: 683244f9 ldr x8, [x19, #0x860]
0x234f45a80: 0901198b add x9, x8, x25
0x234f45a84: 2b0940b9 ldr w11, [x9, #8]
0x234f45a88: 280180b9 ldrsw x8, [x9]
0x234f45a8c: 8a12088b add x10, x20, x8, lsl #4
0x234f45a90: 7f090071 cmp w11, #2
0x234f45a94: 61030054 b.ne #0x234f45b00
0x234f45a98: 290580b9 ldrsw x9, [x9, #4]
0x234f45a9c: 5a0540f9 ldr x26, [x10, #8]
0x234f45aa0: 8a12098b add x10, x20, x9, lsl #4
0x234f45aa4: 6b2a45f9 ldr x11, [x19, #0xa50]
0x234f45aa8: 087d3c9b smull x8, w8, w28
0x234f45aac: f6031baa mov x22, x27
0x234f45ab0: 7b6968f8 ldr x27, [x11, x8]
0x234f45ab4: 287d3c9b smull x8, w9, w28
0x234f45ab8: 7c6968f8 ldr x28, [x11, x8]
0x234f45abc: 550540f9 ldr x21, [x10, #8]
0x234f45ac0: e81340f9 ldr x8, [sp, #0x20]
0x234f45ac4: 630b088b add x3, x27, x8, lsl #2
0x234f45ac8: 840b088b add x4, x28, x8, lsl #2
0x234f45acc: e00317aa mov x0, x23
0x234f45ad0: e1031aaa mov x1, x26
0x234f45ad4: e20315aa mov x2, x21
0x234f45ad8: 60000094 bl #0x234f45c58
0x234f45adc: 410b178b add x1, x26, x23, lsl #2
0x234f45ae0: a20a178b add x2, x21, x23, lsl #2
0x234f45ae4: e01f40b9 ldr w0, [sp, #0x1c]
0x234f45ae8: e3031baa mov x3, x27
0x234f45aec: fb0316aa mov x27, x22
0x234f45af0: e4031caa mov x4, x28
0x234f45af4: 1c068052 mov w28, #0x30
0x234f45af8: 58000094 bl #0x234f45c58
0x234f45afc: 0e000014 b #0x234f45b34
0x234f45b00: 550540f9 ldr x21, [x10, #8]
0x234f45b04: 692a45f9 ldr x9, [x19, #0xa50]
0x234f45b08: 087d3c9b smull x8, w8, w28
0x234f45b0c: 3a6968f8 ldr x26, [x9, x8]
0x234f45b10: e81340f9 ldr x8, [sp, #0x20]
0x234f45b14: 400b088b add x0, x26, x8, lsl #2
0x234f45b18: e10315aa mov x1, x21
0x234f45b1c: e20b40f9 ldr x2, [sp, #0x10]
0x234f45b20: 77da0294 bl #0x234ffc4fc
0x234f45b24: a10a178b add x1, x21, x23, lsl #2
0x234f45b28: e0031aaa mov x0, x26
0x234f45b2c: e20740f9 ldr x2, [sp, #8]
0x234f45b30: 73da0294 bl #0x234ffc4fc
0x234f45b34: 18070091 add x24, x24, #1
0x234f45b38: 687a48b9 ldr w8, [x19, #0x878]
0x234f45b3c: 39330091 add x25, x25, #0xc
0x234f45b40: 1f0308eb cmp x24, x8
0x234f45b44: c3f9ff54 b.lo #0x234f45a7c
0x234f45b48: 2c000014 b #0x234f45bf8
0x234f45b4c: 00008052 mov w0, #0
0x234f45b50: c0035fd6 ret 
0x234f45b54: 6a7a48b9 ldr w10, [x19, #0x878]
0x234f45b58: 0a050034 cbz w10, #0x234f45bf8
0x234f45b5c: 140080d2 mov x20, #0
0x234f45b60: 160080d2 mov x22, #0
0x234f45b64: 37210091 add x23, x9, #8
0x234f45b68: f803082a mov w24, w8
0x234f45b6c: 19068052 mov w25, #0x30
0x234f45b70: 683244f9 ldr x8, [x19, #0x860]
0x234f45b74: 0901148b add x9, x8, x20
0x234f45b78: 2a0940b9 ldr w10, [x9, #8]
0x234f45b7c: 280180b9 ldrsw x8, [x9]
0x234f45b80: 5f090071 cmp w10, #2
0x234f45b84: 01020054 b.ne #0x234f45bc4
0x234f45b88: 290580b9 ldrsw x9, [x9, #4]
0x234f45b8c: ea12088b add x10, x23, x8, lsl #4
0x234f45b90: 410540f9 ldr x1, [x10, #8]
0x234f45b94: ea12098b add x10, x23, x9, lsl #4
0x234f45b98: 6b2a45f9 ldr x11, [x19, #0xa50]
0x234f45b9c: 087d399b smull x8, w8, w25
0x234f45ba0: 686968f8 ldr x8, [x11, x8]
0x234f45ba4: 420540f9 ldr x2, [x10, #8]
0x234f45ba8: 297d399b smull x9, w9, w25
0x234f45bac: 696969f8 ldr x9, [x11, x9]
0x234f45bb0: 0309188b add x3, x8, x24, lsl #2
0x234f45bb4: 2409188b add x4, x9, x24, lsl #2
0x234f45bb8: e0031baa mov x0, x27
0x234f45bbc: 27000094 bl #0x234f45c58
0x234f45bc0: 09000014 b #0x234f45be4
0x234f45bc4: e912088b add x9, x23, x8, lsl #4
0x234f45bc8: 210540f9 ldr x1, [x9, #8]
0x234f45bcc: 692a45f9 ldr x9, [x19, #0xa50]
0x234f45bd0: 087d399b smull x8, w8, w25
0x234f45bd4: 286968f8 ldr x8, [x9, x8]
0x234f45bd8: 0009188b add x0, x8, x24, lsl #2
0x234f45bdc: e20315aa mov x2, x21
0x234f45be0: 47da0294 bl #0x234ffc4fc
0x234f45be4: d6060091 add x22, x22, #1
0x234f45be8: 687a48b9 ldr w8, [x19, #0x878]
0x234f45bec: 94320091 add x20, x20, #0xc
0x234f45bf0: df0208eb cmp x22, x8
0x234f45bf4: e3fbff54 b.lo #0x234f45b70
0x234f45bf8: 680640f9 ldr x8, [x19, #8]
0x234f45bfc: 69aa44f9 ldr x9, [x19, #0x950]
0x234f45c00: 2a008052 mov w10, #1
0x234f45c04: 6a620039 strb w10, [x19, #0x18]
0x234f45c08: 681200fd str d8, [x19, #0x20]
0x234f45c0c: 68a602a9 stp x8, x9, [x19, #0x28]
0x234f45c10: 08413b8b add x8, x8, w27, uxtw
0x234f45c14: 680600f9 str x8, [x19, #8]
0x234f45c18: 28413b8b add x8, x9, w27, uxtw
0x234f45c1c: 68aa04f9 str x8, [x19, #0x950]
0x234f45c20: 6003631e ucvtf d0, w27
0x234f45c24: 0029601e fadd d0, d8, d0
0x234f45c28: 609e04fd str d0, [x19, #0x938]
0x234f45c2c: e00740b9 ldr w0, [sp, #4]
0x234f45c30: fd7b49a9 ldp x29, x30, [sp, #0x90]
0x234f45c34: f44f48a9 ldp x20, x19, [sp, #0x80]
0x234f45c38: f65747a9 ldp x22, x21, [sp, #0x70]
0x234f45c3c: f85f46a9 ldp x24, x23, [sp, #0x60]
0x234f45c40: fa6745a9 ldp x26, x25, [sp, #0x50]
0x234f45c44: fc6f44a9 ldp x28, x27, [sp, #0x40]
0x234f45c48: e923436d ldp d9, d8, [sp, #0x30]
0x234f45c4c: ff830291 add sp, sp, #0xa0
0x234f45c50: ff2303d5 autibsp 
0x234f45c54: c0035fd6 ret 
