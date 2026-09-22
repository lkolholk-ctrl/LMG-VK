1b8f68980: pacibsp 
1b8f68984: sub sp, sp, #0xc0
1b8f68988: stp d9, d8, [sp, #0x60]
1b8f6898c: stp x26, x25, [sp, #0x70]
1b8f68990: stp x24, x23, [sp, #0x80]
1b8f68994: stp x22, x21, [sp, #0x90]
1b8f68998: stp x20, x19, [sp, #0xa0]
1b8f6899c: stp x29, x30, [sp, #0xb0]
1b8f689a0: add x29, sp, #0xb0
1b8f689a4: fmov d8, d1
1b8f689a8: fmov d9, d0
1b8f689ac: mov x21, x1
1b8f689b0: mov x20, x0
1b8f689b4: adrp x8, #0x1e6bef000
1b8f689b8: ldr x8, [x8, #0x758]
1b8f689bc: ldr x8, [x8]
1b8f689c0: str x8, [sp, #0x58]
1b8f689c4: fcvtzs x19, d0
1b8f689c8: str x19, [x0, #0x5a8]
1b8f689cc: fcvtzu w8, d1
1b8f689d0: str w8, [x0, #0x5b0]
1b8f689d4: ldr w8, [x0, #0x538]
1b8f689d8: cmp w8, #2
1b8f689dc: b.ne #0x1b8f68a08
1b8f689e0: ldr d0, [x20, #0x620]
1b8f689e4: fcmp d0, d9
1b8f689e8: b.hi #0x1b8f68a08
1b8f689ec: ldr w8, [x21, #0x38]
1b8f689f0: ldr x9, [x21, #8]
1b8f689f4: tst w8, #2
1b8f689f8: csinc x2, x9, xzr, ne
1b8f689fc: mov x0, x20
1b8f68a00: mov w1, #3
1b8f68a04: bl #0x1b8f635b8
1b8f68a08: ldr d0, [x20, #0x610]
1b8f68a0c: adrp x8, #0x1b916b000
1b8f68a10: ldr d1, [x8, #0xed0]
1b8f68a14: fcmp d0, d1
1b8f68a18: b.eq #0x1b8f68a40
1b8f68a1c: ldr x8, [x20, #0x5a8]
1b8f68a20: ldr w9, [x20, #0x5b0]
1b8f68a24: add x8, x8, x9
1b8f68a28: scvtf d1, x8
1b8f68a2c: fsub d0, d0, d1
1b8f68a30: fcmp d0, #0.0
1b8f68a34: b.le #0x1b8f68a40
1b8f68a38: fcvtmu w8, d0
1b8f68a3c: b #0x1b8f68a44
1b8f68a40: mov w8, #0
1b8f68a44: str w8, [x20, #0x618]
1b8f68a48: adrp x8, #0x1ebd81000
1b8f68a4c: ldr x8, [x8, #0x7d0]
1b8f68a50: cbz x8, #0x1b8f68b94
1b8f68a54: ldrb w9, [x8, #8]
1b8f68a58: tbz w9, #0, #0x1b8f68b94
1b8f68a5c: ldr x22, [x8]
1b8f68a60: cbz x22, #0x1b8f68b94
1b8f68a64: mov x0, x22
1b8f68a68: mov w1, #2
1b8f68a6c: bl #0x1b9c46450
1b8f68a70: cbz w0, #0x1b8f68b94
1b8f68a74: ldr x23, [x20, #0x10]
1b8f68a78: fcvtzu x24, d9
1b8f68a7c: fadd d0, d9, d8
1b8f68a80: fcvtzu x25, d0
1b8f68a84: ldr d0, [x21]
1b8f68a88: fcvtzu x26, d0
1b8f68a8c: ldr x16, [x23]
1b8f68a90: mov x17, x23
1b8f68a94: movk x17, #0xcfff, lsl #48
1b8f68a98: autda x16, x17
1b8f68a9c: mov x17, #0x138
1b8f68aa0: add x16, x16, x17
1b8f68aa4: ldr x8, [x16]
1b8f68aa8: mov x9, x16
1b8f68aac: mov x0, x23
1b8f68ab0: mov x1, x19
1b8f68ab4: mov w2, #1
1b8f68ab8: mov x17, x9
1b8f68abc: movk x17, #0xba32, lsl #48
1b8f68ac0: blraa x8, x17
1b8f68ac4: mov x21, x0
1b8f68ac8: ldr x0, [x20, #0x10]
1b8f68acc: mov x17, x0
1b8f68ad0: ldr x16, [x0]
1b8f68ad4: movk x17, #0xcfff, lsl #48
1b8f68ad8: autda x16, x17
1b8f68adc: mov x17, x16
1b8f68ae0: xpacd x17
1b8f68ae4: cmp x16, x17
1b8f68ae8: b.eq #0x1b8f68af0
1b8f68aec: brk #0xc472
1b8f68af0: add x8, x16, #0x138
1b8f68af4: ldr x9, [x16, #0x138]
1b8f68af8: mov x1, x19
1b8f68afc: mov w2, #1
1b8f68b00: mov x17, x8
1b8f68b04: movk x17, #0xba32, lsl #48
1b8f68b08: blraa x9, x17
1b8f68b0c: scvtf d0, x0
1b8f68b10: fadd d0, d0, d8
1b8f68b14: adrp x8, #0x1b916b000
1b8f68b18: ldr d1, [x8, #0xe20]
1b8f68b1c: str s1, [sp]
1b8f68b20: adrp x8, #0x1b919b000
1b8f68b24: add x8, x8, #0x98e
1b8f68b28: stur x8, [sp, #4]
1b8f68b2c: mov w8, #0x400
1b8f68b30: strh w8, [sp, #0xc]
1b8f68b34: mov w8, #0x323
1b8f68b38: stur w8, [sp, #0xe]
1b8f68b3c: mov w8, #0x800
1b8f68b40: strh w8, [sp, #0x12]
1b8f68b44: stur x23, [sp, #0x14]
1b8f68b48: strh w8, [sp, #0x1c]
1b8f68b4c: stur x24, [sp, #0x1e]
1b8f68b50: strh w8, [sp, #0x26]
1b8f68b54: str x25, [sp, #0x28]
1b8f68b58: strh w8, [sp, #0x30]
1b8f68b5c: stur x26, [sp, #0x32]
1b8f68b60: strh w8, [sp, #0x3a]
1b8f68b64: stur x21, [sp, #0x3c]
1b8f68b68: strh w8, [sp, #0x44]
1b8f68b6c: stur d0, [sp, #0x46]
1b8f68b70: adrp x0, #0x1b8ebb000
1b8f68b74: add x0, x0, #0
1b8f68b78: adrp x3, #0x1b91c1000
1b8f68b7c: add x3, x3, #0x47f
1b8f68b80: mov x4, sp
1b8f68b84: mov x1, x22
1b8f68b88: mov w2, #2
1b8f68b8c: mov w5, #0x4e
1b8f68b90: bl #0x1b9c45880
1b8f68b94: ldr x8, [sp, #0x58]
1b8f68b98: adrp x9, #0x1e6bef000
1b8f68b9c: ldr x9, [x9, #0x758]
1b8f68ba0: ldr x9, [x9]
1b8f68ba4: cmp x9, x8
1b8f68ba8: b.ne #0x1b8f68bcc
1b8f68bac: ldp x29, x30, [sp, #0xb0]
1b8f68bb0: ldp x20, x19, [sp, #0xa0]
1b8f68bb4: ldp x22, x21, [sp, #0x90]
1b8f68bb8: ldp x24, x23, [sp, #0x80]
1b8f68bbc: ldp x26, x25, [sp, #0x70]
1b8f68bc0: ldp d9, d8, [sp, #0x60]
1b8f68bc4: add sp, sp, #0xc0
1b8f68bc8: retab 
