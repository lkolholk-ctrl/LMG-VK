; Binary: _SonicKit_MusicKit
; Address: 0x2721b5788
; Symbol: FUN_2721b5788
; Signature: undefined FUN_2721b5788()
2721b5788: pacibsp
2721b578c: stp d9,d8,[sp, #-0x70]!
2721b5790: stp x28,x27,[sp, #0x10]
2721b5794: stp x26,x25,[sp, #0x20]
2721b5798: stp x24,x23,[sp, #0x30]
2721b579c: stp x22,x21,[sp, #0x40]
2721b57a0: stp x20,x19,[sp, #0x50]
2721b57a4: stp x29,x30,[sp, #0x60]
2721b57a8: add x29,sp,#0x60
2721b57ac: sub sp,sp,#0x100
2721b57b0: mov x19,x0
2721b57b4: mov x0,#0x0
2721b57b8: bl 0x2743dace0
2721b57bc: stur x0,[x29, #-0xa0]
2721b57c0: bl 0x2721098a0
2721b57c4: movk x17,#0x2e3f, LSL #48
2721b57c8: autda x16,x17
2721b57cc: mov x17,x16
2721b57d0: xpacd x17
2721b57d4: cmp x16,x17
2721b57d8: b.eq 0x2721b57e0
2721b57dc: brk #0xc472
2721b57e0: mov x20,x16
2721b57e4: mov x16,x8
2721b57e8: mov x17,x9
2721b57ec: movk x17,#0x2e3f, LSL #48
2721b57f0: autda x16,x17
2721b57f4: ldr x8,[x16, #0x40]
2721b57f8: mov x9,x8
2721b57fc: adrp x17,0x2824d0000
2721b5800: add x17,x17,#0xf28
2721b5804: ldr x16,[x17]
2721b5808: blraa x16,x17
2721b580c: bl 0x272109954
2721b5810: sub x8,x9,x8
2721b5814: stur x8,[x29, #-0xa8]
2721b5818: bl 0x2721b5008
2721b581c: bl 0x2743da9c0
2721b5820: stur x0,[x29, #-0xb0]
2721b5824: bl 0x2721098a0
2721b5828: movk x17,#0x2e3f, LSL #48
2721b582c: autda x16,x17
2721b5830: mov x17,x16
2721b5834: xpacd x17
2721b5838: cmp x16,x17
2721b583c: b.eq 0x2721b5844
2721b5840: brk #0xc472
2721b5844: mov x22,x16
2721b5848: mov x16,x8
2721b584c: mov x17,x9
2721b5850: movk x17,#0x2e3f, LSL #48
2721b5854: autda x16,x17
2721b5858: ldr x8,[x16, #0x40]
2721b585c: mov x9,x8
2721b5860: adrp x17,0x2824d0000
2721b5864: add x17,x17,#0xf28
2721b5868: ldr x16,[x17]
2721b586c: blraa x16,x17
2721b5870: bl 0x272109954
2721b5874: sub x8,x9,x8
2721b5878: stur x8,[x29, #-0xb8]
2721b587c: bl 0x2721b5008
2721b5880: bl 0x2743daa30
2721b5884: stur x0,[x29, #-0xc0]
2721b5888: bl 0x2721098a0
2721b588c: movk x17,#0x2e3f, LSL #48
2721b5890: autda x16,x17
2721b5894: mov x17,x16
2721b5898: xpacd x17
2721b589c: cmp x16,x17
2721b58a0: b.eq 0x2721b58a8
2721b58a4: brk #0xc472
2721b58a8: stur x16,[x29, #-0x80]
2721b58ac: mov x16,x8
2721b58b0: mov x17,x9
2721b58b4: movk x17,#0x2e3f, LSL #48
2721b58b8: autda x16,x17
2721b58bc: ldr x8,[x16, #0x40]
2721b58c0: mov x9,x8
2721b58c4: adrp x17,0x2824d0000
2721b58c8: add x17,x17,#0xf28
2721b58cc: ldr x16,[x17]
2721b58d0: blraa x16,x17
2721b58d4: bl 0x272109954
2721b58d8: sub x8,x9,x8
2721b58dc: stur x8,[x29, #-0xc8]
2721b58e0: bl 0x2721b5008
2721b58e4: bl 0x2743dabd0
2721b58e8: mov x23,x0
2721b58ec: bl 0x2721098a0
2721b58f0: movk x17,#0x2e3f, LSL #48
2721b58f4: autda x16,x17
2721b58f8: mov x17,x16
2721b58fc: xpacd x17
2721b5900: cmp x16,x17
2721b5904: b.eq 0x2721b590c
2721b5908: brk #0xc472
2721b590c: mov x21,x16
2721b5910: mov x16,x8
2721b5914: mov x17,x9
2721b5918: movk x17,#0x2e3f, LSL #48
2721b591c: autda x16,x17
2721b5920: ldr x8,[x16, #0x40]
2721b5924: mov x9,x8
2721b5928: adrp x17,0x2824d0000
2721b592c: add x17,x17,#0xf28
2721b5930: ldr x16,[x17]
2721b5934: blraa x16,x17
2721b5938: mov x9,sp
2721b593c: add x10,x8,#0xf
2721b5940: and x12,x10,#-0x10
2721b5944: sub x9,x9,x12
2721b5948: sub x10,x29,#0x58
2721b594c: stur x9,[x10, #-0x100]
2721b5950: mov sp,x9
2721b5954: mov x9,x8
2721b5958: adrp x17,0x2824d0000
2721b595c: add x17,x17,#0xf28
2721b5960: ldr x16,[x17]
2721b5964: blraa x16,x17
2721b5968: mov x9,sp
2721b596c: sub x9,x9,x12
2721b5970: sub x10,x29,#0x8
2721b5974: stur x9,[x10, #-0x100]
2721b5978: mov sp,x9
2721b597c: mov x9,x8
2721b5980: adrp x17,0x2824d0000
2721b5984: add x17,x17,#0xf28
2721b5988: ldr x16,[x17]
2721b598c: blraa x16,x17
2721b5990: mov x8,sp
2721b5994: sub x8,x8,x12
2721b5998: sub x9,x29,#0x10
2721b599c: stur x8,[x9, #-0x100]
2721b59a0: bl 0x2721b5008
2721b59a4: bl 0x2743da9a0
2721b59a8: stur x0,[x29, #-0x100]
2721b59ac: bl 0x2721098a0
2721b59b0: movk x17,#0x2e3f, LSL #48
2721b59b4: autda x16,x17
2721b59b8: mov x17,x16
2721b59bc: xpacd x17
2721b59c0: cmp x16,x17
2721b59c4: b.eq 0x2721b59cc
2721b59c8: brk #0xc472
2721b59cc: mov x24,x16
2721b59d0: mov x16,x8
2721b59d4: mov x17,x9
2721b59d8: movk x17,#0x2e3f, LSL #48
2721b59dc: autda x16,x17
2721b59e0: ldr x8,[x16, #0x40]
2721b59e4: mov x9,x8
2721b59e8: adrp x17,0x2824d0000
2721b59ec: add x17,x17,#0xf28
2721b59f0: ldr x16,[x17]
2721b59f4: blraa x16,x17
2721b59f8: bl 0x272109954
2721b59fc: sub x25,x9,x8
2721b5a00: mov sp,x25
2721b5a04: adrp x8,0x280c7e000
2721b5a08: ldr x8,[x8, #0x548]
2721b5a0c: cmn x8,#0x1
2721b5a10: b.ne 0x2721b5fb8
2721b5a14: adrp x8,0x280c81000
2721b5a18: ldr x28,[x8, #0x3d0]
2721b5a1c: ldr x8,[x19, #0x10]
2721b5a20: sub x9,x29,#0x18
2721b5a24: stur x8,[x9, #-0x100]
2721b5a28: cbz x8,0x2721b5f74
2721b5a2c: sub x8,x29,#0x20
2721b5a30: stur x23,[x8, #-0x100]
2721b5a34: ldr x8,[x24, #0x10]!
2721b5a38: sub x9,x29,#0x30
2721b5a3c: stur x8,[x9, #-0x100]
2721b5a40: ldrb w8,[x24, #0x40]
2721b5a44: add x9,x8,#0x20
2721b5a48: bic x8,x9,x8
2721b5a4c: add x8,x19,x8
2721b5a50: sub x9,x29,#0x38
2721b5a54: stur x8,[x9, #-0x100]
2721b5a58: add x26,x21,#0x8
2721b5a5c: ldur x8,[x29, #-0x80]
2721b5a60: add x10,x8,#0x10
2721b5a64: add x9,x22,#0x8
2721b5a68: stp x9,x10,[x29, #-0xd8]
2721b5a6c: add x9,x20,#0x8
2721b5a70: add x8,x8,#0x8
2721b5a74: stp x8,x9,[x29, #-0xe8]
2721b5a78: sub x8,x24,#0x8
2721b5a7c: sub x9,x29,#0x50
2721b5a80: stur x8,[x9, #-0x100]
2721b5a84: sub x8,x29,#0x28
2721b5a88: stur x24,[x8, #-0x100]
2721b5a8c: ldr x8,[x24, #0x38]
2721b5a90: sub x9,x29,#0x40
2721b5a94: stur x8,[x9, #-0x100]
2721b5a98: mov x0,x28
2721b5a9c: bl 0x2743dcc80
2721b5aa0: mov x27,#0x0
2721b5aa4: mov x10,#0x0
2721b5aa8: ldur x19,[x29, #-0x100]
2721b5aac: sub x8,x29,#0x48
2721b5ab0: stur x25,[x8, #-0x100]
2721b5ab4: stur x26,[x29, #-0xf0]
2721b5ab8: sub x8,x29,#0x38
2721b5abc: ldur x8,[x8, #-0x100]
2721b5ac0: sub x9,x29,#0x40
2721b5ac4: ldur x9,[x9, #-0x100]
2721b5ac8: stur x10,[x29, #-0xf8]
2721b5acc: madd x1,x9,x10,x8
2721b5ad0: mov x0,x25
2721b5ad4: mov x2,x19
2721b5ad8: sub x8,x29,#0x28
2721b5adc: ldur x8,[x8, #-0x100]
2721b5ae0: sub x9,x29,#0x30
2721b5ae4: ldur x9,[x9, #-0x100]
2721b5ae8: mov x17,x8
2721b5aec: movk x17,#0xe3ba, LSL #48
2721b5af0: blraa x9,x17
2721b5af4: sub x8,x29,#0x10
2721b5af8: ldur x24,[x8, #-0x100]
2721b5afc: mov x8,x24
2721b5b00: mov x20,x25
2721b5b04: bl 0x2743da990
2721b5b08: sub x8,x29,#0x8
2721b5b0c: ldur x23,[x8, #-0x100]
2721b5b10: mov x8,x23
2721b5b14: bl 0x2743dab90
2721b5b18: adrp x16,0x2780dc000
2721b5b1c: ldr x16,[x16, #0xf00]
2721b5b20: mov x17,#0x7099
2721b5b24: pacia x16,x17
2721b5b28: mov x1,x16
2721b5b2c: adrp x16,0x2780dc000
2721b5b30: ldr x16,[x16, #0xf08]
2721b5b34: mov x17,#0xc6eb
2721b5b38: pacda x16,x17
2721b5b3c: mov x2,x16
2721b5b40: adrp x0,0x280c81000
2721b5b44: add x0,x0,#0x3e8
2721b5b48: bl 0x2721af72c
2721b5b4c: mov x3,x0
2721b5b50: mov x0,x24
2721b5b54: mov x1,x23
2721b5b58: sub x8,x29,#0x20
2721b5b5c: ldur x22,[x8, #-0x100]
2721b5b60: mov x20,x22
2721b5b64: mov x2,x22
2721b5b68: bl 0x2743dbd90
2721b5b6c: mov x19,x0
2721b5b70: ldr x21,[x26]
2721b5b74: mov x0,x23
2721b5b78: mov x1,x22
2721b5b7c: mov x17,x26
2721b5b80: movk x17,#0x4f8, LSL #48
2721b5b84: blraa x21,x17
2721b5b88: mov x0,x24
2721b5b8c: mov x1,x22
2721b5b90: mov x17,x26
2721b5b94: movk x17,#0x4f8, LSL #48
2721b5b98: blraa x21,x17
2721b5b9c: tbnz w19,#0x0,0x2721b5f20
2721b5ba0: sub x8,x29,#0x58
2721b5ba4: ldur x23,[x8, #-0x100]
2721b5ba8: mov x8,x23
2721b5bac: mov x20,x25
2721b5bb0: bl 0x2743da990
2721b5bb4: mov x20,x23
2721b5bb8: bl 0x2743dabb0
2721b5bbc: mov x19,x0
2721b5bc0: mov x20,x1
2721b5bc4: mov x0,x23
2721b5bc8: mov x1,x22
2721b5bcc: mov x17,x26
2721b5bd0: movk x17,#0x4f8, LSL #48
2721b5bd4: blraa x21,x17
2721b5bd8: mov x0,x19
2721b5bdc: mov x1,x20
2721b5be0: bl 0x2743dbe40
2721b5be4: mov x19,x0
2721b5be8: mov x0,x20
2721b5bec: bl 0x2743dcc60
2721b5bf0: stur x19,[x29, #-0x78]
2721b5bf4: tbnz x19,#0x20,0x2721b5f20
2721b5bf8: mov x20,x25
2721b5bfc: bl 0x2743da980
2721b5c00: mov x20,x0
2721b5c04: ldr x8,[x0, #0x10]
2721b5c08: stur x8,[x29, #-0x88]
2721b5c0c: cbz x8,0x2721b5f18
2721b5c10: mov x0,#0x0
2721b5c14: bl 0x2720f6f68
2721b5c18: sub x8,x29,#0x60
2721b5c1c: stur x0,[x8, #-0x100]
2721b5c20: mov x19,#0x0
2721b5c24: ldur x8,[x29, #-0x80]
2721b5c28: ldrb w8,[x8, #0x50]
2721b5c2c: add x9,x8,#0x20
2721b5c30: bic x8,x9,x8
2721b5c34: add x8,x20,x8
2721b5c38: stp x20,x8,[x29, #-0x98]
2721b5c3c: ldr x8,[x20, #0x10]
2721b5c40: cmp x19,x8
2721b5c44: b.cs 0x2721b5fa4
2721b5c48: ldur x25,[x29, #-0x80]
2721b5c4c: ldr x8,[x25, #0x48]
2721b5c50: ldur x9,[x29, #-0x90]
2721b5c54: madd x1,x8,x19,x9
2721b5c58: ldr x8,[x25, #0x10]
2721b5c5c: mov x26,x27
2721b5c60: ldp x27,x23,[x29, #-0xc8]
2721b5c64: mov x0,x27
2721b5c68: mov x2,x23
2721b5c6c: ldur x9,[x29, #-0xd0]
2721b5c70: mov x17,x9
2721b5c74: movk x17,#0xe3ba, LSL #48
2721b5c78: blraa x8,x17
2721b5c7c: ldur x22,[x29, #-0xb8]
2721b5c80: mov x8,x22
2721b5c84: mov x20,x27
2721b5c88: bl 0x2743daa10
2721b5c8c: ldur x21,[x29, #-0xa8]
2721b5c90: mov x8,x21
2721b5c94: mov x20,x22
2721b5c98: bl 0x2743da9b0
2721b5c9c: ldur x9,[x29, #-0xd8]
2721b5ca0: ldr x8,[x9]
2721b5ca4: mov x0,x22
2721b5ca8: ldur x1,[x29, #-0xb0]
2721b5cac: mov x17,x9
2721b5cb0: movk x17,#0x4f8, LSL #48
2721b5cb4: blraa x8,x17
2721b5cb8: mov x20,x21
2721b5cbc: bl 0x2743dacd0
2721b5cc0: fmov d8,d0
2721b5cc4: ldur x9,[x29, #-0xe0]
2721b5cc8: ldr x8,[x9]
2721b5ccc: mov x0,x21
2721b5cd0: ldur x1,[x29, #-0xa0]
2721b5cd4: mov x17,x9
2721b5cd8: movk x17,#0x4f8, LSL #48
2721b5cdc: blraa x8,x17
2721b5ce0: fmov d0,d8
2721b5ce4: mov w0,#0xca00
2721b5ce8: movk w0,#0x3b9a, LSL #16
2721b5cec: bl 0x2743dc320
2721b5cf0: mov x22,x0
2721b5cf4: mov x21,x1
2721b5cf8: mov x24,x2
2721b5cfc: mov x20,x27
2721b5d00: bl 0x2743daa20
2721b5d04: fmov d8,d0
2721b5d08: ldr x8,[x25, #0x8]
2721b5d0c: mov x0,x27
2721b5d10: mov x1,x23
2721b5d14: ldur x9,[x29, #-0xe8]
2721b5d18: mov x17,x9
2721b5d1c: movk x17,#0x4f8, LSL #48
2721b5d20: blraa x8,x17
2721b5d24: mov x0,x26
2721b5d28: mov x1,#0x0
2721b5d2c: bl 0x2721af774
2721b5d30: mov x0,x28
2721b5d34: bl 0x2743dcf60
2721b5d38: mov x25,x0
2721b5d3c: stur x28,[x29, #-0x70]
2721b5d40: bl 0x2721b6e28
2721b5d44: ldr x8,[x28, #0x10]
2721b5d48: mvn w9,w1
2721b5d4c: and x9,x9,#0x1
2721b5d50: adds x28,x8,x9
2721b5d54: b.vs 0x2721b5fa8
2721b5d58: mov x27,x0
2721b5d5c: mov x23,x1
2721b5d60: mov x0,#0x0
2721b5d64: bl 0x2721af784
2721b5d68: mov x2,x0
2721b5d6c: sub x20,x29,#0x70
2721b5d70: mov x0,x25
2721b5d74: mov x1,x28
2721b5d78: bl 0x2743dc500
2721b5d7c: ldur x28,[x29, #-0x70]
2721b5d80: tbz w0,#0x0,0x2721b5d9c
2721b5d84: bl 0x2721b6e28
2721b5d88: and w8,w1,#0x1
2721b5d8c: and w9,w23,#0x1
2721b5d90: cmp w9,w8
2721b5d94: b.ne 0x2721b5fe4
2721b5d98: mov x27,x0
2721b5d9c: tbnz w23,#0x0,0x2721b5dfc
2721b5da0: lsr x20,x21,#0x20
2721b5da4: sub x8,x29,#0x70
2721b5da8: bl 0x2721af5b4
2721b5dac: ldur x8,[x29, #-0x70]
2721b5db0: lsr x9,x27,#0x6
2721b5db4: add x9,x28,x9, LSL #0x3
2721b5db8: mov w10,#0x1
2721b5dbc: lsl x10,x10,x27
2721b5dc0: ldr x11,[x9, #0x40]
2721b5dc4: orr x10,x11,x10
2721b5dc8: str x10,[x9, #0x40]
2721b5dcc: ldr x9,[x28, #0x30]
2721b5dd0: mov w10,#0x18
2721b5dd4: madd x9,x27,x10,x9
2721b5dd8: str x22,[x9]
2721b5ddc: stp w21,w20,[x9, #0x8]
2721b5de0: str x24,[x9, #0x10]
2721b5de4: ldr x9,[x28, #0x38]
2721b5de8: str x8,[x9, x27, LSL #0x3]
2721b5dec: ldr x8,[x28, #0x10]
2721b5df0: adds x8,x8,#0x1
2721b5df4: b.vs 0x2721b5fb0
2721b5df8: str x8,[x28, #0x10]
2721b5dfc: ldr x25,[x28, #0x38]
2721b5e00: ldr x0,[x25, x27, LSL #0x3]
2721b5e04: bl 0x2743dcf60
2721b5e08: mov x23,x0
2721b5e0c: ldr x20,[x25, x27, LSL #0x3]
2721b5e10: stur x20,[x29, #-0x70]
2721b5e14: ldur x0,[x29, #-0x78]
2721b5e18: bl 0x272196a9c
2721b5e1c: ldr x8,[x20, #0x10]
2721b5e20: mvn w9,w1
2721b5e24: and x9,x9,#0x1
2721b5e28: adds x24,x8,x9
2721b5e2c: b.vs 0x2721b5fac
2721b5e30: mov x21,x0
2721b5e34: mov x22,x1
2721b5e38: adrp x16,0x2780e3000
2721b5e3c: ldr x16,[x16, #0x40]
2721b5e40: mov x17,#0xb4e8
2721b5e44: pacia x16,x17
2721b5e48: mov x2,x16
2721b5e4c: mov x0,#0x0
2721b5e50: adrp x1,0x280c81000
2721b5e54: add x1,x1,#0x3f8
2721b5e58: bl 0x2721af988
2721b5e5c: mov x2,x0
2721b5e60: sub x20,x29,#0x70
2721b5e64: mov x0,x23
2721b5e68: mov x1,x24
2721b5e6c: bl 0x2743dc500
2721b5e70: tbz w0,#0x0,0x2721b5e90
2721b5e74: ldp x0,x20,[x29, #-0x78]
2721b5e78: bl 0x272196a9c
2721b5e7c: and w8,w1,#0x1
2721b5e80: and w9,w22,#0x1
2721b5e84: cmp w9,w8
2721b5e88: b.ne 0x2721b5fd8
2721b5e8c: mov x21,x0
2721b5e90: fcvt s0,d8
2721b5e94: ldur x8,[x29, #-0x70]
2721b5e98: tbz w22,#0x0,0x2721b5ea8
2721b5e9c: ldr x9,[x8, #0x38]
2721b5ea0: str s0,[x9,x21, lsl #2]
2721b5ea4: b 0x2721b5ee8
2721b5ea8: lsr x9,x21,#0x6
2721b5eac: add x9,x8,x9, LSL #0x3
2721b5eb0: mov w10,#0x1
2721b5eb4: lsl x10,x10,x21
2721b5eb8: ldr x11,[x9, #0x40]
2721b5ebc: orr x10,x11,x10
2721b5ec0: str x10,[x9, #0x40]
2721b5ec4: ldr x9,[x8, #0x30]
2721b5ec8: ldur x10,[x29, #-0x78]
2721b5ecc: str w10,[x9, x21, LSL #0x2]
2721b5ed0: ldr x9,[x8, #0x38]
2721b5ed4: str s0,[x9,x21, lsl #2]
2721b5ed8: ldr x9,[x8, #0x10]
2721b5edc: adds x9,x9,#0x1
2721b5ee0: b.vs 0x2721b5fb4
2721b5ee4: str x9,[x8, #0x10]
2721b5ee8: add x19,x19,#0x1
2721b5eec: str x8,[x25, x27, LSL #0x3]
2721b5ef0: adrp x16,0x2721af000
2721b5ef4: add x16,x16,#0x5b4
2721b5ef8: mov x17,#0x720f
2721b5efc: pacia x16,x17
2721b5f00: mov x27,x16
2721b5f04: ldur x8,[x29, #-0x88]
2721b5f08: cmp x8,x19
2721b5f0c: ldur x26,[x29, #-0xf0]
2721b5f10: ldur x20,[x29, #-0x98]
2721b5f14: b.ne 0x2721b5c3c
2721b5f18: mov x0,x20
2721b5f1c: bl 0x2743dcc60
2721b5f20: ldp x19,x20,[x29, #-0x100]
2721b5f24: add x20,x20,#0x1
2721b5f28: sub x8,x29,#0x50
2721b5f2c: ldur x9,[x8, #-0x100]
2721b5f30: ldr x8,[x9]
2721b5f34: sub x10,x29,#0x48
2721b5f38: ldur x25,[x10, #-0x100]
2721b5f3c: mov x0,x25
2721b5f40: mov x1,x19
2721b5f44: mov x17,x9
2721b5f48: movk x17,#0x4f8, LSL #48
2721b5f4c: blraa x8,x17
2721b5f50: mov x10,x20
2721b5f54: sub x8,x29,#0x18
2721b5f58: ldur x8,[x8, #-0x100]
2721b5f5c: cmp x20,x8
2721b5f60: b.ne 0x2721b5ab8
2721b5f64: mov x0,x27
2721b5f68: mov x1,#0x0
2721b5f6c: bl 0x2721af774
2721b5f70: b 0x2721b5f7c
2721b5f74: mov x0,x28
2721b5f78: bl 0x2743dcc80
2721b5f7c: mov x0,x28
2721b5f80: sub sp,x29,#0x60
2721b5f84: ldp x29,x30,[sp, #0x60]
2721b5f88: ldp x20,x19,[sp, #0x50]
2721b5f8c: ldp x22,x21,[sp, #0x40]
2721b5f90: ldp x24,x23,[sp, #0x30]
2721b5f94: ldp x26,x25,[sp, #0x20]
2721b5f98: ldp x28,x27,[sp, #0x10]
2721b5f9c: ldp d9,d8,[sp], #0x70
2721b5fa0: retab
2721b5fa4: brk #0x1
2721b5fa8: brk #0x1
2721b5fac: brk #0x1
2721b5fb0: brk #0x1
2721b5fb4: brk #0x1
2721b5fb8: adrp x0,0x280c7e000
2721b5fbc: add x0,x0,#0x548
2721b5fc0: adrp x16,0x2721af000
2721b5fc4: add x16,x16,#0x158
2721b5fc8: paciza x16
2721b5fcc: mov x1,x16
2721b5fd0: bl 0x2743dcf80
2721b5fd4: b 0x2721b5a14
2721b5fd8: adrp x0,0x2780e3000
2721b5fdc: ldr x0,[x0, #0x970]
2721b5fe0: b 0x2721b5fec
2721b5fe4: sub x8,x29,#0x60
2721b5fe8: ldur x0,[x8, #-0x100]
2721b5fec: bl 0x2743dc7f0
2721b5ff0: brk #0x1

