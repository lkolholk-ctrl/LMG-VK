0x1b8f6188c: pacibsp 
0x1b8f61890: sub sp, sp, #0x110
0x1b8f61894: stp d15, d14, [sp, #0x70]
0x1b8f61898: stp d13, d12, [sp, #0x80]
0x1b8f6189c: stp d11, d10, [sp, #0x90]
0x1b8f618a0: stp d9, d8, [sp, #0xa0]
0x1b8f618a4: stp x28, x27, [sp, #0xb0]
0x1b8f618a8: stp x26, x25, [sp, #0xc0]
0x1b8f618ac: stp x24, x23, [sp, #0xd0]
0x1b8f618b0: stp x22, x21, [sp, #0xe0]
0x1b8f618b4: stp x20, x19, [sp, #0xf0]
0x1b8f618b8: stp x29, x30, [sp, #0x100]
0x1b8f618bc: add x29, sp, #0x100
0x1b8f618c0: mov x19, x3
0x1b8f618c4: mov x20, x2
0x1b8f618c8: mov x21, x1
0x1b8f618cc: mov x22, x0
0x1b8f618d0: mov w26, #0
0x1b8f618d4: mov x27, #0
0x1b8f618d8: adrp x8, #0x1e6bef000
0x1b8f618dc: ldr x8, [x8, #0x758]
0x1b8f618e0: ldr x8, [x8]
0x1b8f618e4: str x8, [sp, #0x60]
0x1b8f618e8: strb wzr, [x2]
0x1b8f618ec: ldr s8, [x0, #0x528]
0x1b8f618f0: fmov d10, #0.50000000
0x1b8f618f4: adrp x8, #0x1b916b000
0x1b8f618f8: ldr d11, [x8, #0xf00]
0x1b8f618fc: adrp x8, #0x1b916c000
0x1b8f61900: ldr s12, [x8, #0x8a8]
0x1b8f61904: adrp x8, #0x1b916c000
0x1b8f61908: ldr s13, [x8, #0x8ac]
0x1b8f6190c: adrp x8, #0x1b916c000
0x1b8f61910: ldr s14, [x8, #0x8b0]
0x1b8f61914: mov w28, #1
0x1b8f61918: adrp x23, #0x1ebd81000
0x1b8f6191c: adrp x8, #0x1b916b000
0x1b8f61920: ldr d0, [x8, #0xe20]
0x1b8f61924: str q0, [sp]
0x1b8f61928: mov w24, #0x400
0x1b8f6192c: fmov s9, s8
0x1b8f61930: add x8, x22, x27
0x1b8f61934: ldrb w9, [x8, #0x368]
0x1b8f61938: tbz w9, #0, #0x1b8f619c8
0x1b8f6193c: ldrb w9, [x8, #0x369]
0x1b8f61940: tbz w9, #0, #0x1b8f619d0
0x1b8f61944: add x9, x22, x27
0x1b8f61948: ldr x10, [x9, #0x378]
0x1b8f6194c: sub x11, x21, x10
0x1b8f61950: scvtf s0, x11
0x1b8f61954: ldr w11, [x9, #0x380]
0x1b8f61958: ucvtf s1, w11
0x1b8f6195c: fdiv s1, s0, s1
0x1b8f61960: movi d0, #0000000000000000
0x1b8f61964: fcmp s1, #0.0
0x1b8f61968: b.mi #0x1b8f61a60
0x1b8f6196c: fmov s0, #1.00000000
0x1b8f61970: fcmp s1, s0
0x1b8f61974: b.gt #0x1b8f61a60
0x1b8f61978: add x12, x22, x27
0x1b8f6197c: ldr w13, [x12, #0x38c]
0x1b8f61980: cmp w13, #1
0x1b8f61984: b.ne #0x1b8f61a20
0x1b8f61988: ldr s2, [x12, #0x388]
0x1b8f6198c: ldr s3, [x12, #0x384]
0x1b8f61990: fcvt d0, s1
0x1b8f61994: fcmp s2, s3
0x1b8f61998: b.le #0x1b8f61a28
0x1b8f6199c: fmul d0, d0, d10
0x1b8f619a0: fmul d0, d0, d11
0x1b8f619a4: fcvt s0, d0
0x1b8f619a8: fabs s1, s0
0x1b8f619ac: fmul s2, s0, s12
0x1b8f619b0: fmul s1, s2, s1
0x1b8f619b4: fmadd s0, s0, s13, s1
0x1b8f619b8: fabs s1, s0
0x1b8f619bc: fnmsub s1, s0, s1, s0
0x1b8f619c0: fmadd s0, s1, s14, s0
0x1b8f619c4: b #0x1b8f61a60
0x1b8f619c8: ldr s15, [x8, #0x36c]
0x1b8f619cc: b #0x1b8f61a9c
0x1b8f619d0: strb w28, [x8, #0x369]
0x1b8f619d4: add x9, x22, x27
0x1b8f619d8: ldrb w10, [x9, #0x36a]
0x1b8f619dc: ldr s15, [x9, #0x36c]
0x1b8f619e0: tbz w10, #0, #0x1b8f61a04
0x1b8f619e4: strb wzr, [x9, #0x36a]
0x1b8f619e8: str s15, [x9, #0x384]
0x1b8f619ec: ldr x10, [x9, #0x378]
0x1b8f619f0: ldr w9, [x9, #0x380]
0x1b8f619f4: add x9, x10, x9
0x1b8f619f8: cmp x9, x21
0x1b8f619fc: b.gt #0x1b8f61a7c
0x1b8f61a00: b #0x1b8f61a8c
0x1b8f61a04: str s15, [x9, #0x384]
0x1b8f61a08: ldr x10, [x9, #0x378]
0x1b8f61a0c: ldr w9, [x9, #0x380]
0x1b8f61a10: add x9, x10, x9
0x1b8f61a14: cmp x9, x21
0x1b8f61a18: b.gt #0x1b8f61a84
0x1b8f61a1c: b #0x1b8f61a8c
0x1b8f61a20: fmov s0, s1
0x1b8f61a24: b #0x1b8f61a60
0x1b8f61a28: fmov d1, #-1.00000000
0x1b8f61a2c: fadd d0, d0, d1
0x1b8f61a30: fmul d0, d0, d10
0x1b8f61a34: fmul d0, d0, d11
0x1b8f61a38: fcvt s0, d0
0x1b8f61a3c: fabs s1, s0
0x1b8f61a40: fmul s2, s0, s12
0x1b8f61a44: fmul s1, s2, s1
0x1b8f61a48: fmadd s0, s0, s13, s1
0x1b8f61a4c: fabs s1, s0
0x1b8f61a50: fnmsub s1, s0, s1, s0
0x1b8f61a54: fmadd s0, s1, s14, s0
0x1b8f61a58: fmov s1, #1.00000000
0x1b8f61a5c: fadd s0, s0, s1
0x1b8f61a60: add x10, x10, x11
0x1b8f61a64: cmp x10, x21
0x1b8f61a68: b.le #0x1b8f61a8c
0x1b8f61a6c: ldr s1, [x9, #0x384]
0x1b8f61a70: ldr s2, [x9, #0x388]
0x1b8f61a74: fsub s2, s2, s1
0x1b8f61a78: fmadd s15, s2, s0, s1
0x1b8f61a7c: add x8, x22, x27
0x1b8f61a80: str s15, [x8, #0x36c]
0x1b8f61a84: strb w28, [x20]
0x1b8f61a88: b #0x1b8f61a9c
0x1b8f61a8c: strb wzr, [x8, #0x368]
0x1b8f61a90: add x8, x22, x27
0x1b8f61a94: ldr s15, [x8, #0x388]
0x1b8f61a98: str s15, [x8, #0x36c]
0x1b8f61a9c: fmul s8, s8, s15
0x1b8f61aa0: cmp x27, #0x190
0x1b8f61aa4: cset w8, ne
0x1b8f61aa8: fmul s0, s9, s15
0x1b8f61aac: tst w8, w19
0x1b8f61ab0: fcsel s9, s0, s9, ne
0x1b8f61ab4: fcmp s8, #0.0
0x1b8f61ab8: b.ne #0x1b8f61b6c
0x1b8f61abc: ldr x8, [x23, #0x7d0]
0x1b8f61ac0: cbz x8, #0x1b8f61b6c
0x1b8f61ac4: ldrb w9, [x8, #8]
0x1b8f61ac8: tbz w9, #0, #0x1b8f61b6c
0x1b8f61acc: ldr x25, [x8]
0x1b8f61ad0: cbz x25, #0x1b8f61b6c
0x1b8f61ad4: mov x0, x25
0x1b8f61ad8: mov w1, #2
0x1b8f61adc: bl #0x1b9c46450
0x1b8f61ae0: cbz w0, #0x1b8f61b6c
0x1b8f61ae4: ldr x8, [x22, #0x10]
0x1b8f61ae8: fcvt d0, s8
0x1b8f61aec: fcvt d1, s15
0x1b8f61af0: ldrb w9, [x20]
0x1b8f61af4: ldr q2, [sp]
0x1b8f61af8: str s2, [sp, #0x10]
0x1b8f61afc: adrp x10, #0x1b919b000
0x1b8f61b00: add x10, x10, #0xf44
0x1b8f61b04: stur x10, [sp, #0x14]
0x1b8f61b08: strh w24, [sp, #0x1c]
0x1b8f61b0c: mov w10, #0x36b
0x1b8f61b10: stur w10, [sp, #0x1e]
0x1b8f61b14: mov w10, #0x800
0x1b8f61b18: strh w10, [sp, #0x22]
0x1b8f61b1c: stur x8, [sp, #0x24]
0x1b8f61b20: strh w10, [sp, #0x2c]
0x1b8f61b24: stur d0, [sp, #0x2e]
0x1b8f61b28: strh w24, [sp, #0x36]
0x1b8f61b2c: str w26, [sp, #0x38]
0x1b8f61b30: strh w10, [sp, #0x3c]
0x1b8f61b34: stur d1, [sp, #0x3e]
0x1b8f61b38: strh w24, [sp, #0x46]
0x1b8f61b3c: str w9, [sp, #0x48]
0x1b8f61b40: strh w24, [sp, #0x4c]
0x1b8f61b44: stur w19, [sp, #0x4e]
0x1b8f61b48: add x4, sp, #0x10
0x1b8f61b4c: adrp x0, #0x1b8ebb000
0x1b8f61b50: add x0, x0, #0
0x1b8f61b54: mov x1, x25
0x1b8f61b58: mov w2, #2
0x1b8f61b5c: adrp x3, #0x1b91c4000
0x1b8f61b60: add x3, x3, #0x241
0x1b8f61b64: mov w5, #0x42
0x1b8f61b68: bl #0x1b9c45880
0x1b8f61b6c: add x27, x27, #0x28
0x1b8f61b70: add w26, w26, #1
0x1b8f61b74: cmp x27, #0x1b8
0x1b8f61b78: b.ne #0x1b8f61930
0x1b8f61b7c: ldr x8, [sp, #0x60]
0x1b8f61b80: adrp x9, #0x1e6bef000
0x1b8f61b84: ldr x9, [x9, #0x758]
0x1b8f61b88: ldr x9, [x9]
0x1b8f61b8c: cmp x9, x8
0x1b8f61b90: b.ne #0x1b8f61bcc
0x1b8f61b94: fmov s0, s8
0x1b8f61b98: fmov s1, s9
0x1b8f61b9c: ldp x29, x30, [sp, #0x100]
0x1b8f61ba0: ldp x20, x19, [sp, #0xf0]
0x1b8f61ba4: ldp x22, x21, [sp, #0xe0]
0x1b8f61ba8: ldp x24, x23, [sp, #0xd0]
0x1b8f61bac: ldp x26, x25, [sp, #0xc0]
0x1b8f61bb0: ldp x28, x27, [sp, #0xb0]
0x1b8f61bb4: ldp d9, d8, [sp, #0xa0]
0x1b8f61bb8: ldp d11, d10, [sp, #0x90]
0x1b8f61bbc: ldp d13, d12, [sp, #0x80]
0x1b8f61bc0: ldp d15, d14, [sp, #0x70]
0x1b8f61bc4: add sp, sp, #0x110
0x1b8f61bc8: retab 
0x1b8f61bcc: bl #0x1b9c457f0
0x1b8f61bd0: pacibsp 
0x1b8f61bd4: sub sp, sp, #0x60
0x1b8f61bd8: stp x22, x21, [sp, #0x30]
0x1b8f61bdc: stp x20, x19, [sp, #0x40]
0x1b8f61be0: stp x29, x30, [sp, #0x50]
0x1b8f61be4: add x29, sp, #0x50
0x1b8f61be8: mov x20, x1
0x1b8f61bec: mov x19, x0
0x1b8f61bf0: ldp x8, x9, [x0, #8]
0x1b8f61bf4: cmp x8, x9
0x1b8f61bf8: b.hs #0x1b8f61c0c
0x1b8f61bfc: ldp q0, q1, [x20]
0x1b8f61c00: stp q0, q1, [x8]
0x1b8f61c04: add x21, x8, #0x20
0x1b8f61c08: b #0x1b8f61cc0
0x1b8f61c0c: ldr x10, [x19]
0x1b8f61c10: sub x8, x8, x10
0x1b8f61c14: asr x21, x8, #5
0x1b8f61c18: add x8, x21, #1
0x1b8f61c1c: lsr x11, x8, #0x3b
0x1b8f61c20: cbnz x11, #0x1b8f61cd8
0x1b8f61c24: sub x9, x9, x10
0x1b8f61c28: asr x10, x9, #4
0x1b8f61c2c: cmp x10, x8
0x1b8f61c30: csel x8, x10, x8, hi
0x1b8f61c34: mov x10, #0x7fffffffffffffe0
0x1b8f61c38: cmp x9, x10
0x1b8f61c3c: mov x9, #0x7ffffffffffffff
0x1b8f61c40: csel x22, x8, x9, lo
0x1b8f61c44: str x19, [sp, #0x28]
0x1b8f61c48: cbz x22, #0x1b8f61c74
0x1b8f61c4c: lsr x8, x22, #0x3b
0x1b8f61c50: cbnz x8, #0x1b8f61cdc
0x1b8f61c54: adrp x8, #0x1e6be9000
0x1b8f61c58: ldr x8, [x8, #0xb10]
0x1b8f61c5c: ldr x0, [x8]
0x1b8f61c60: cbz x0, #0x1b8f61cdc
0x1b8f61c64: lsl x1, x22, #5
0x1b8f61c68: mov w2, #4
0x1b8f61c6c: bl #0x1b9c44b00
0x1b8f61c70: b #0x1b8f61c78
0x1b8f61c74: mov x0, #0
0x1b8f61c78: add x8, x0, x21, lsl #5
0x1b8f61c7c: add x22, x0, x22, lsl #5
0x1b8f61c80: ldp q0, q1, [x20]
0x1b8f61c84: stp q0, q1, [x8]
0x1b8f61c88: add x21, x8, #0x20
0x1b8f61c8c: ldp x1, x9, [x19]
0x1b8f61c90: sub x2, x9, x1
0x1b8f61c94: sub x20, x8, x2
0x1b8f61c98: mov x0, x20
0x1b8f61c9c: bl #0x1b9168d50
0x1b8f61ca0: ldr x8, [x19]
0x1b8f61ca4: stp x20, x21, [x19]
0x1b8f61ca8: ldr x9, [x19, #0x10]
0x1b8f61cac: str x22, [x19, #0x10]
0x1b8f61cb0: stp x8, x9, [sp, #0x18]
0x1b8f61cb4: stp x8, x8, [sp, #8]
0x1b8f61cb8: add x0, sp, #8
0x1b8f61cbc: bl #0x1b8f5f000
0x1b8f61cc0: str x21, [x19, #8]
0x1b8f61cc4: ldp x29, x30, [sp, #0x50]
0x1b8f61cc8: ldp x20, x19, [sp, #0x40]
0x1b8f61ccc: ldp x22, x21, [sp, #0x30]
0x1b8f61cd0: add sp, sp, #0x60
0x1b8f61cd4: retab 
0x1b8f61cd8: bl #0x1b8ed24a0
0x1b8f61cdc: brk #1
0x1b8f61ce0: bl #0x1b8ed1514
0x1b8f61ce4: pacibsp 
0x1b8f61ce8: sub sp, sp, #0x60
0x1b8f61cec: stp x22, x21, [sp, #0x30]
0x1b8f61cf0: stp x20, x19, [sp, #0x40]
0x1b8f61cf4: stp x29, x30, [sp, #0x50]
0x1b8f61cf8: add x29, sp, #0x50
0x1b8f61cfc: mov x20, x2
0x1b8f61d00: mov x21, x1
0x1b8f61d04: mov x19, x0
0x1b8f61d08: ldp x8, x9, [x0, #8]
0x1b8f61d0c: cmp x8, x9
0x1b8f61d10: b.hs #0x1b8f61d2c
0x1b8f61d14: ldr s0, [x21]
0x1b8f61d18: str s0, [x8]
0x1b8f61d1c: ldr s0, [x20]
0x1b8f61d20: str s0, [x8, #4]
0x1b8f61d24: add x21, x8, #8
0x1b8f61d28: b #0x1b8f61dd4
0x1b8f61d2c: ldr x1, [x19]
0x1b8f61d30: sub x2, x8, x1
0x1b8f61d34: asr x22, x2, #3
0x1b8f61d38: add x8, x22, #1
0x1b8f61d3c: lsr x10, x8, #0x3d
0x1b8f61d40: cbnz x10, #0x1b8f61dec
0x1b8f61d44: sub x9, x9, x1
0x1b8f61d48: asr x10, x9, #2
0x1b8f61d4c: cmp x10, x8
0x1b8f61d50: csel x8, x10, x8, hi
0x1b8f61d54: mov x10, #0x7ffffffffffffff8
0x1b8f61d58: cmp x9, x10
0x1b8f61d5c: mov x9, #0x1fffffffffffffff
0x1b8f61d60: csel x0, x8, x9, lo
0x1b8f61d64: str x19, [sp, #0x28]
0x1b8f61d68: cbz x0, #0x1b8f61d84
0x1b8f61d6c: bl #0x1b8f61ed8
0x1b8f61d70: mov x8, x1
0x1b8f61d74: ldp x1, x9, [x19]
0x1b8f61d78: sub x2, x9, x1
0x1b8f61d7c: asr x9, x2, #3
0x1b8f61d80: b #0x1b8f61d8c
0x1b8f61d84: mov x8, #0
0x1b8f61d88: mov x9, x22
0x1b8f61d8c: add x10, x0, x22, lsl #3
0x1b8f61d90: add x22, x0, x8, lsl #3
0x1b8f61d94: ldr s0, [x21]
0x1b8f61d98: str s0, [x10]
0x1b8f61d9c: ldr s0, [x20]
0x1b8f61da0: str s0, [x10, #4]
0x1b8f61da4: add x21, x10, #8
0x1b8f61da8: sub x20, x10, x9, lsl #3
0x1b8f61dac: mov x0, x20
0x1b8f61db0: bl #0x1b9168d50
0x1b8f61db4: ldr x8, [x19]
0x1b8f61db8: stp x20, x21, [x19]
0x1b8f61dbc: ldr x9, [x19, #0x10]
0x1b8f61dc0: str x22, [x19, #0x10]
0x1b8f61dc4: stp x8, x9, [sp, #0x18]
0x1b8f61dc8: stp x8, x8, [sp, #8]
0x1b8f61dcc: add x0, sp, #8
0x1b8f61dd0: bl #0x1b8f61f24
0x1b8f61dd4: str x21, [x19, #8]
0x1b8f61dd8: ldp x29, x30, [sp, #0x50]
0x1b8f61ddc: ldp x20, x19, [sp, #0x40]
0x1b8f61de0: ldp x22, x21, [sp, #0x30]
0x1b8f61de4: add sp, sp, #0x60
0x1b8f61de8: retab 
0x1b8f61dec: bl #0x1b8ed24a0
0x1b8f61df0: brk #1
0x1b8f61df4: bl #0x1b8ed1514
0x1b8f61df8: pacibsp 
0x1b8f61dfc: stp x29, x30, [sp, #-0x10]!
0x1b8f61e00: mov x29, sp
0x1b8f61e04: mov x8, x2
0x1b8f61e08: ldr x9, [x0]
0x1b8f61e0c: ldr x12, [x9]
0x1b8f61e10: ldp x2, x10, [x12]
0x1b8f61e14: subs x10, x10, x2
0x1b8f61e18: b.eq #0x1b8f61e9c
0x1b8f61e1c: mov x13, #0
0x1b8f61e20: ldr x10, [x9, #8]
0x1b8f61e24: mov w11, #1
0x1b8f61e28: add x14, x2, x13, lsl #5
0x1b8f61e2c: str w8, [x14, #4]
0x1b8f61e30: ldr w15, [x14]
0x1b8f61e34: cmp w15, #1
0x1b8f61e38: b.ne #0x1b8f61e7c
0x1b8f61e3c: ldr w15, [x14, #8]
0x1b8f61e40: cbnz w15, #0x1b8f61e7c
0x1b8f61e44: ldr x12, [x10, #0x7e8]
0x1b8f61e48: add x12, x12, x13, lsl #3
0x1b8f61e4c: ldr w13, [x14, #0xc]
0x1b8f61e50: ldr s0, [x12]
0x1b8f61e54: cmp w13, #1
0x1b8f61e58: b.ne #0x1b8f61e64
0x1b8f61e5c: mov w12, #0x14
0x1b8f61e60: b #0x1b8f61e70
0x1b8f61e64: str s0, [x14, #0x18]
0x1b8f61e68: ldr s0, [x12, #4]
0x1b8f61e6c: mov w12, #0x1c
0x1b8f61e70: str s0, [x14, x12]
0x1b8f61e74: ldr x12, [x9]
0x1b8f61e78: ldr x2, [x12]
0x1b8f61e7c: mov w13, w11
0x1b8f61e80: ldr x14, [x12, #8]
0x1b8f61e84: sub x14, x14, x2
0x1b8f61e88: asr x3, x14, #5
0x1b8f61e8c: cmp x3, w11, uxtw
0x1b8f61e90: add w11, w11, #1
0x1b8f61e94: b.hi #0x1b8f61e28
0x1b8f61e98: b #0x1b8f61ea0
0x1b8f61e9c: asr x3, x10, #5
0x1b8f61ea0: ldr x0, [x1, #8]
0x1b8f61ea4: ldr x16, [x0]
0x1b8f61ea8: mov x17, x0
0x1b8f61eac: movk x17, #0x5b15, lsl #48
0x1b8f61eb0: autda x16, x17
0x1b8f61eb4: ldr x9, [x16, #0xc8]!
0x1b8f61eb8: mov x10, x16
0x1b8f61ebc: mov x1, x8
0x1b8f61ec0: mov x17, x10
0x1b8f61ec4: movk x17, #0x6f3f, lsl #48
0x1b8f61ec8: blraa x9, x17
0x1b8f61ecc: ldp x29, x30, [sp], #0x10
0x1b8f61ed0: retab 
0x1b8f61ed4: bl #0x1b8ed1514
0x1b8f61ed8: pacibsp 
0x1b8f61edc: stp x20, x19, [sp, #-0x20]!
0x1b8f61ee0: stp x29, x30, [sp, #0x10]
0x1b8f61ee4: add x29, sp, #0x10
0x1b8f61ee8: lsr x8, x0, #0x3d
0x1b8f61eec: cbnz x8, #0x1b8f61f20
0x1b8f61ef0: mov x19, x0
0x1b8f61ef4: adrp x8, #0x1e6be9000
0x1b8f61ef8: ldr x8, [x8, #0xb10]
0x1b8f61efc: ldr x0, [x8]
0x1b8f61f00: cbz x0, #0x1b8f61f20
0x1b8f61f04: lsl x1, x19, #3
0x1b8f61f08: mov w2, #4
0x1b8f61f0c: bl #0x1b9c44b00
0x1b8f61f10: mov x1, x19
0x1b8f61f14: ldp x29, x30, [sp, #0x10]
0x1b8f61f18: ldp x20, x19, [sp], #0x20
0x1b8f61f1c: retab 
0x1b8f61f20: brk #1
0x1b8f61f24: pacibsp 
0x1b8f61f28: stp x20, x19, [sp, #-0x20]!
0x1b8f61f2c: stp x29, x30, [sp, #0x10]
0x1b8f61f30: add x29, sp, #0x10
0x1b8f61f34: mov x19, x0
0x1b8f61f38: ldp x9, x8, [x0, #8]
0x1b8f61f3c: cmp x8, x9
0x1b8f61f40: b.eq #0x1b8f61f58
0x1b8f61f44: sub x9, x9, x8
0x1b8f61f48: add x9, x9, #7
0x1b8f61f4c: and x9, x9, #0xfffffffffffffff8
0x1b8f61f50: add x8, x8, x9
0x1b8f61f54: str x8, [x19, #0x10]
0x1b8f61f58: ldr x1, [x19]
0x1b8f61f5c: cbz x1, #0x1b8f61f84
0x1b8f61f60: ldr x8, [x19, #0x18]
0x1b8f61f64: subs x2, x8, x1
0x1b8f61f68: b.mi #0x1b8f61f94
0x1b8f61f6c: adrp x8, #0x1e6be9000
0x1b8f61f70: ldr x8, [x8, #0xb10]
0x1b8f61f74: ldr x0, [x8]
0x1b8f61f78: cbz x0, #0x1b8f61f94
0x1b8f61f7c: mov w3, #4
0x1b8f61f80: bl #0x1b9c44b10
0x1b8f61f84: mov x0, x19
0x1b8f61f88: ldp x29, x30, [sp, #0x10]
0x1b8f61f8c: ldp x20, x19, [sp], #0x20
0x1b8f61f90: retab 
0x1b8f61f94: brk #1
