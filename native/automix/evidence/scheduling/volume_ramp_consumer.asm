; Binary: _SonicKit_MusicKit
; Address: 0x2721b2a04
; Symbol: FUN_2721b2a04
; Signature: undefined FUN_2721b2a04()
2721b2a04: pacibsp
2721b2a08: stp d11,d10,[sp, #-0x80]!
2721b2a0c: stp d9,d8,[sp, #0x10]
2721b2a10: stp x28,x27,[sp, #0x20]
2721b2a14: stp x26,x25,[sp, #0x30]
2721b2a18: stp x24,x23,[sp, #0x40]
2721b2a1c: stp x22,x21,[sp, #0x50]
2721b2a20: stp x20,x19,[sp, #0x60]
2721b2a24: stp x29,x30,[sp, #0x70]
2721b2a28: add x29,sp,#0x70
2721b2a2c: sub sp,sp,#0x170
2721b2a30: sub x8,x29,#0xb8
2721b2a34: stur x2,[x8, #-0x100]
2721b2a38: sub x8,x29,#0xc0
2721b2a3c: stur x1,[x8, #-0x100]
2721b2a40: mov x22,x0
2721b2a44: mov x0,#0x0
2721b2a48: bl 0x2743dace0
2721b2a4c: sub x8,x29,#0x58
2721b2a50: stur x0,[x8, #-0x100]
2721b2a54: mov x9,x0
2721b2a58: ldr x8,[x9, #-0x8]!
2721b2a5c: mov x16,x8
2721b2a60: mov x17,x9
2721b2a64: movk x17,#0x2e3f, LSL #48
2721b2a68: autda x16,x17
2721b2a6c: mov x17,x16
2721b2a70: xpacd x17
2721b2a74: cmp x16,x17
2721b2a78: b.eq 0x2721b2a80
2721b2a7c: brk #0xc472
2721b2a80: mov x10,x16
2721b2a84: mov x16,x8
2721b2a88: sub x8,x29,#0xa0
2721b2a8c: stur x10,[x8, #-0x100]
2721b2a90: sub x8,x29,#0x88
2721b2a94: stur x10,[x8, #-0x100]
2721b2a98: mov x17,x9
2721b2a9c: movk x17,#0x2e3f, LSL #48
2721b2aa0: autda x16,x17
2721b2aa4: ldr x8,[x16, #0x40]
2721b2aa8: mov x9,x8
2721b2aac: adrp x17,0x2824d0000
2721b2ab0: add x17,x17,#0xf28
2721b2ab4: ldr x16,[x17]
2721b2ab8: blraa x16,x17
2721b2abc: mov x9,sp
2721b2ac0: add x8,x8,#0xf
2721b2ac4: and x8,x8,#-0x10
2721b2ac8: sub x8,x9,x8
2721b2acc: sub x9,x29,#0x60
2721b2ad0: stur x8,[x9, #-0x100]
2721b2ad4: mov sp,x8
2721b2ad8: mov x0,#0x0
2721b2adc: bl 0x2721b4b04
2721b2ae0: sub x8,x29,#0x78
2721b2ae4: stur x0,[x8, #-0x100]
2721b2ae8: mov x8,x0
2721b2aec: ldr x16,[x8, #-0x8]!
2721b2af0: mov x17,x8
2721b2af4: movk x17,#0x2e3f, LSL #48
2721b2af8: autda x16,x17
2721b2afc: ldr x8,[x16, #0x40]
2721b2b00: mov x9,x8
2721b2b04: adrp x17,0x2824d0000
2721b2b08: add x17,x17,#0xf28
2721b2b0c: ldr x16,[x17]
2721b2b10: blraa x16,x17
2721b2b14: mov x9,sp
2721b2b18: add x8,x8,#0xf
2721b2b1c: and x8,x8,#-0x10
2721b2b20: sub x8,x9,x8
2721b2b24: stur x8,[x29, #-0xe8]
2721b2b28: mov sp,x8
2721b2b2c: mov x0,#0x0
2721b2b30: bl 0x2743daa70
2721b2b34: sub x8,x29,#0x68
2721b2b38: stur x0,[x8, #-0x100]
2721b2b3c: mov x9,x0
2721b2b40: ldr x8,[x9, #-0x8]!
2721b2b44: mov x16,x8
2721b2b48: mov x17,x9
2721b2b4c: movk x17,#0x2e3f, LSL #48
2721b2b50: autda x16,x17
2721b2b54: mov x17,x16
2721b2b58: xpacd x17
2721b2b5c: cmp x16,x17
2721b2b60: b.eq 0x2721b2b68
2721b2b64: brk #0xc472
2721b2b68: mov x27,x16
2721b2b6c: mov x16,x8
2721b2b70: sub x8,x29,#0x90
2721b2b74: stur x27,[x8, #-0x100]
2721b2b78: mov x17,x9
2721b2b7c: movk x17,#0x2e3f, LSL #48
2721b2b80: autda x16,x17
2721b2b84: ldr x8,[x16, #0x40]
2721b2b88: mov x9,x8
2721b2b8c: adrp x17,0x2824d0000
2721b2b90: add x17,x17,#0xf28
2721b2b94: ldr x16,[x17]
2721b2b98: blraa x16,x17
2721b2b9c: mov x9,sp
2721b2ba0: add x8,x8,#0xf
2721b2ba4: and x8,x8,#-0x10
2721b2ba8: sub x8,x9,x8
2721b2bac: sub x9,x29,#0x70
2721b2bb0: stur x8,[x9, #-0x100]
2721b2bb4: mov sp,x8
2721b2bb8: adrp x1,0x280c81000
2721b2bbc: add x1,x1,#0x438
2721b2bc0: adrp x16,0x2780dc000
2721b2bc4: ldr x16,[x16, #0xed0]
2721b2bc8: mov x17,#0x8ca4
2721b2bcc: pacia x16,x17
2721b2bd0: mov x2,x16
2721b2bd4: mov x0,#0x0
2721b2bd8: bl 0x2721b4f48
2721b2bdc: ldr x16,[x0, #-0x8]!
2721b2be0: mov x17,x0
2721b2be4: movk x17,#0x2e3f, LSL #48
2721b2be8: autda x16,x17
2721b2bec: ldr x9,[x16, #0x40]
2721b2bf0: add x8,x9,#0xf
2721b2bf4: and x8,x8,#-0x10
2721b2bf8: adrp x17,0x2824d0000
2721b2bfc: add x17,x17,#0xf28
2721b2c00: ldr x16,[x17]
2721b2c04: blraa x16,x17
2721b2c08: mov x9,sp
2721b2c0c: sub x8,x9,x8
2721b2c10: sub x9,x29,#0x28
2721b2c14: stur x8,[x9, #-0x100]
2721b2c18: mov sp,x8
2721b2c1c: mov x0,#0x0
2721b2c20: bl 0x2743daa00
2721b2c24: stur x0,[x29, #-0xf0]
2721b2c28: mov x9,x0
2721b2c2c: ldr x8,[x9, #-0x8]!
2721b2c30: mov x16,x8
2721b2c34: mov x17,x9
2721b2c38: movk x17,#0x2e3f, LSL #48
2721b2c3c: autda x16,x17
2721b2c40: mov x17,x16
2721b2c44: xpacd x17
2721b2c48: cmp x16,x17
2721b2c4c: b.eq 0x2721b2c54
2721b2c50: brk #0xc472
2721b2c54: sub x10,x29,#0x50
2721b2c58: stur x16,[x10, #-0x100]
2721b2c5c: mov x16,x8
2721b2c60: mov x17,x9
2721b2c64: movk x17,#0x2e3f, LSL #48
2721b2c68: autda x16,x17
2721b2c6c: ldr x8,[x16, #0x40]
2721b2c70: mov x9,x8
2721b2c74: adrp x17,0x2824d0000
2721b2c78: add x17,x17,#0xf28
2721b2c7c: ldr x16,[x17]
2721b2c80: blraa x16,x17
2721b2c84: mov x9,sp
2721b2c88: add x10,x8,#0xf
2721b2c8c: and x12,x10,#-0x10
2721b2c90: sub x9,x9,x12
2721b2c94: sub x10,x29,#0x80
2721b2c98: stur x9,[x10, #-0x100]
2721b2c9c: mov sp,x9
2721b2ca0: mov x9,x8
2721b2ca4: adrp x17,0x2824d0000
2721b2ca8: add x17,x17,#0xf28
2721b2cac: ldr x16,[x17]
2721b2cb0: blraa x16,x17
2721b2cb4: mov x8,sp
2721b2cb8: sub x26,x8,x12
2721b2cbc: mov sp,x26
2721b2cc0: mov x0,#0x0
2721b2cc4: bl 0x2743dabd0
2721b2cc8: sub x8,x29,#0x8
2721b2ccc: stur x0,[x8, #-0x100]
2721b2cd0: mov x9,x0
2721b2cd4: ldr x8,[x9, #-0x8]!
2721b2cd8: mov x16,x8
2721b2cdc: mov x17,x9
2721b2ce0: movk x17,#0x2e3f, LSL #48
2721b2ce4: autda x16,x17
2721b2ce8: mov x17,x16
2721b2cec: xpacd x17
2721b2cf0: cmp x16,x17
2721b2cf4: b.eq 0x2721b2cfc
2721b2cf8: brk #0xc472
2721b2cfc: mov x21,x16
2721b2d00: mov x16,x8
2721b2d04: mov x17,x9
2721b2d08: movk x17,#0x2e3f, LSL #48
2721b2d0c: autda x16,x17
2721b2d10: ldr x8,[x16, #0x40]
2721b2d14: mov x9,x8
2721b2d18: adrp x17,0x2824d0000
2721b2d1c: add x17,x17,#0xf28
2721b2d20: ldr x16,[x17]
2721b2d24: blraa x16,x17
2721b2d28: mov x9,sp
2721b2d2c: add x10,x8,#0xf
2721b2d30: and x12,x10,#-0x10
2721b2d34: sub x9,x9,x12
2721b2d38: sub x10,x29,#0x10
2721b2d3c: stur x9,[x10, #-0x100]
2721b2d40: mov sp,x9
2721b2d44: mov x9,x8
2721b2d48: adrp x17,0x2824d0000
2721b2d4c: add x17,x17,#0xf28
2721b2d50: ldr x16,[x17]
2721b2d54: blraa x16,x17
2721b2d58: mov x8,sp
2721b2d5c: sub x8,x8,x12
2721b2d60: sub x9,x29,#0x18
2721b2d64: stur x8,[x9, #-0x100]
2721b2d68: mov sp,x8
2721b2d6c: mov x0,#0x0
2721b2d70: bl 0x2743da9a0
2721b2d74: mov x23,x0
2721b2d78: mov x9,x0
2721b2d7c: ldr x8,[x9, #-0x8]!
2721b2d80: mov x16,x8
2721b2d84: mov x17,x9
2721b2d88: movk x17,#0x2e3f, LSL #48
2721b2d8c: autda x16,x17
2721b2d90: mov x17,x16
2721b2d94: xpacd x17
2721b2d98: cmp x16,x17
2721b2d9c: b.eq 0x2721b2da4
2721b2da0: brk #0xc472
2721b2da4: mov x19,x16
2721b2da8: mov x16,x8
2721b2dac: mov x17,x9
2721b2db0: movk x17,#0x2e3f, LSL #48
2721b2db4: autda x16,x17
2721b2db8: ldr x8,[x16, #0x40]
2721b2dbc: mov x9,x8
2721b2dc0: adrp x17,0x2824d0000
2721b2dc4: add x17,x17,#0xf28
2721b2dc8: ldr x16,[x17]
2721b2dcc: blraa x16,x17
2721b2dd0: mov x9,sp
2721b2dd4: add x10,x8,#0xf
2721b2dd8: and x12,x10,#-0x10
2721b2ddc: sub x20,x9,x12
2721b2de0: mov sp,x20
2721b2de4: mov x9,x8
2721b2de8: adrp x17,0x2824d0000
2721b2dec: add x17,x17,#0xf28
2721b2df0: ldr x16,[x17]
2721b2df4: blraa x16,x17
2721b2df8: mov x9,sp
2721b2dfc: sub x9,x9,x12
2721b2e00: sub x10,x29,#0x30
2721b2e04: stur x9,[x10, #-0x100]
2721b2e08: mov sp,x9
2721b2e0c: mov x9,x8
2721b2e10: adrp x17,0x2824d0000
2721b2e14: add x17,x17,#0xf28
2721b2e18: ldr x16,[x17]
2721b2e1c: blraa x16,x17
2721b2e20: mov x8,sp
2721b2e24: sub x8,x8,x12
2721b2e28: sub x9,x29,#0xd0
2721b2e2c: stur x8,[x9, #-0x100]
2721b2e30: mov sp,x8
2721b2e34: adrp x8,0x278046000
2721b2e38: ldr x0,[x8, #0x230]
2721b2e3c: bl 0x2743dc9a0
2721b2e40: adrp x8,0x1fb07b000
2721b2e44: add x1,x8,#0x720
2721b2e48: bl 0x2743dc9d0
2721b2e4c: sub x8,x29,#0xc8
2721b2e50: stur x0,[x8, #-0x100]
2721b2e54: adrp x8,0x278046000
2721b2e58: ldr x0,[x8, #0x238]
2721b2e5c: bl 0x2743dc9f0
2721b2e60: adrp x8,0x1fc12e000
2721b2e64: add x1,x8,#0x5f1
2721b2e68: mov x2,#0x0
2721b2e6c: bl 0x2743dc9d0
2721b2e70: mov x29,x29
2721b2e74: bl 0x2743dcad0
2721b2e78: adrp x8,0x1fb8e1000
2721b2e7c: add x1,x8,#0x767
2721b2e80: sub x8,x29,#0x48
2721b2e84: stur x0,[x8, #-0x100]
2721b2e88: mov w2,#0x0
2721b2e8c: bl 0x2743dc9d0
2721b2e90: mov x24,#0x0
2721b2e94: sub x8,x29,#0x38
2721b2e98: stur x22,[x8, #-0x100]
2721b2e9c: ldr x8,[x22, #0x10]
2721b2ea0: stur x8,[x29, #-0xf8]
2721b2ea4: add x8,x19,#0x10
2721b2ea8: sub x9,x29,#0x20
2721b2eac: stur x8,[x9, #-0x100]
2721b2eb0: add x25,x21,#0x8
2721b2eb4: stur x19,[x29, #-0x100]
2721b2eb8: add x8,x19,#0x8
2721b2ebc: sub x9,x29,#0x40
2721b2ec0: stur x8,[x9, #-0x100]
2721b2ec4: ldur x8,[x29, #-0xf8]
2721b2ec8: cmp x8,x24
2721b2ecc: b.eq 0x2721b3614
2721b2ed0: ldur x10,[x29, #-0x100]
2721b2ed4: ldrb w8,[x10, #0x50]
2721b2ed8: add x9,x8,#0x20
2721b2edc: bic x8,x9,x8
2721b2ee0: sub x9,x29,#0x38
2721b2ee4: ldur x9,[x9, #-0x100]
2721b2ee8: add x8,x9,x8
2721b2eec: ldr x9,[x10, #0x48]
2721b2ef0: madd x1,x9,x24,x8
2721b2ef4: ldr x8,[x10, #0x10]
2721b2ef8: mov x0,x20
2721b2efc: mov x2,x23
2721b2f00: sub x9,x29,#0x20
2721b2f04: ldur x9,[x9, #-0x100]
2721b2f08: mov x17,x9
2721b2f0c: movk x17,#0xe3ba, LSL #48
2721b2f10: blraa x8,x17
2721b2f14: sub x8,x29,#0x18
2721b2f18: ldur x21,[x8, #-0x100]
2721b2f1c: mov x8,x21
2721b2f20: bl 0x2743da990
2721b2f24: sub x8,x29,#0x10
2721b2f28: ldur x19,[x8, #-0x100]
2721b2f2c: mov x8,x19
2721b2f30: bl 0x2743dab90
2721b2f34: mov x0,x21
2721b2f38: mov x1,x19
2721b2f3c: bl 0x2743daba0
2721b2f40: mov x28,x0
2721b2f44: ldr x22,[x25]
2721b2f48: mov x0,x19
2721b2f4c: sub x8,x29,#0x8
2721b2f50: ldur x19,[x8, #-0x100]
2721b2f54: mov x1,x19
2721b2f58: mov x17,x25
2721b2f5c: movk x17,#0x4f8, LSL #48
2721b2f60: blraa x22,x17
2721b2f64: mov x0,x21
2721b2f68: mov x1,x19
2721b2f6c: mov x17,x25
2721b2f70: movk x17,#0x4f8, LSL #48
2721b2f74: blraa x22,x17
2721b2f78: tbnz w28,#0x0,0x2721b2fa4
2721b2f7c: add x24,x24,#0x1
2721b2f80: sub x8,x29,#0x40
2721b2f84: ldur x9,[x8, #-0x100]
2721b2f88: ldr x8,[x9]
2721b2f8c: mov x0,x20
2721b2f90: mov x1,x23
2721b2f94: mov x17,x9
2721b2f98: movk x17,#0x4f8, LSL #48
2721b2f9c: blraa x8,x17
2721b2fa0: b 0x2721b2ec4
2721b2fa4: ldur x21,[x29, #-0x100]
2721b2fa8: ldr x19,[x21, #0x20]!
2721b2fac: sub x8,x29,#0x30
2721b2fb0: ldur x22,[x8, #-0x100]
2721b2fb4: mov x0,x22
2721b2fb8: mov x1,x20
2721b2fbc: mov x2,x23
2721b2fc0: mov x17,x21
2721b2fc4: movk x17,#0x48d8, LSL #48
2721b2fc8: blraa x19,x17
2721b2fcc: sub x8,x29,#0xd0
2721b2fd0: ldur x20,[x8, #-0x100]
2721b2fd4: mov x0,x20
2721b2fd8: mov x1,x22
2721b2fdc: sub x8,x29,#0xe0
2721b2fe0: stur x23,[x8, #-0x100]
2721b2fe4: mov x2,x23
2721b2fe8: mov x17,x21
2721b2fec: movk x17,#0x48d8, LSL #48
2721b2ff0: blraa x19,x17
2721b2ff4: bl 0x2743da970
2721b2ff8: mov x20,x0
2721b2ffc: sub x8,x29,#0x28
2721b3000: ldur x22,[x8, #-0x100]
2721b3004: mov x8,x22
2721b3008: bl 0x2721b0728
2721b300c: mov x0,x22
2721b3010: mov w1,#0x1
2721b3014: ldur x19,[x29, #-0xf0]
2721b3018: mov x2,x19
2721b301c: bl 0x2720f8804
2721b3020: cmp w0,#0x1
2721b3024: sub x8,x29,#0xd8
2721b3028: stur x20,[x8, #-0x100]
2721b302c: b.ne 0x2721b306c
2721b3030: adrp x1,0x280c81000
2721b3034: add x1,x1,#0x438
2721b3038: adrp x16,0x2780dc000
2721b303c: ldr x16,[x16, #0xed0]
2721b3040: mov x17,#0x8ca4
2721b3044: pacia x16,x17
2721b3048: mov x2,x16
2721b304c: mov x0,x22
2721b3050: bl 0x2721b4e10
2721b3054: ldur x22,[x29, #-0xe8]
2721b3058: sub x8,x29,#0x70
2721b305c: ldur x23,[x8, #-0x100]
2721b3060: sub x8,x29,#0x50
2721b3064: ldur x11,[x8, #-0x100]
2721b3068: b 0x2721b32c0
2721b306c: sub x8,x29,#0x50
2721b3070: ldur x8,[x8, #-0x100]
2721b3074: ldr x9,[x8, #0x20]!
2721b3078: mov x21,x26
2721b307c: mov x0,x26
2721b3080: mov x1,x22
2721b3084: mov x2,x19
2721b3088: mov x17,x8
2721b308c: movk x17,#0x48d8, LSL #48
2721b3090: blraa x9,x17
2721b3094: sub x8,x29,#0x70
2721b3098: ldur x23,[x8, #-0x100]
2721b309c: mov x8,x23
2721b30a0: mov x20,x26
2721b30a4: bl 0x2743da9f0
2721b30a8: ldur x22,[x29, #-0xe8]
2721b30ac: mov x8,x22
2721b30b0: mov x20,x23
2721b30b4: bl 0x2743daa50
2721b30b8: mov x26,x27
2721b30bc: ldr x8,[x26, #0x8]!
2721b30c0: mov x0,x23
2721b30c4: sub x9,x29,#0x68
2721b30c8: ldur x19,[x9, #-0x100]
2721b30cc: mov x1,x19
2721b30d0: stur x8,[x29, #-0xf8]
2721b30d4: mov x17,x26
2721b30d8: movk x17,#0x4f8, LSL #48
2721b30dc: blraa x8,x17
2721b30e0: sub x8,x29,#0x88
2721b30e4: ldur x27,[x8, #-0x100]
2721b30e8: mov x24,x27
2721b30ec: ldr x8,[x24, #0x10]!
2721b30f0: sub x9,x29,#0x60
2721b30f4: ldur x28,[x9, #-0x100]
2721b30f8: mov x0,x28
2721b30fc: mov x1,x22
2721b3100: sub x9,x29,#0x58
2721b3104: ldur x25,[x9, #-0x100]
2721b3108: mov x2,x25
2721b310c: stur x8,[x29, #-0x100]
2721b3110: mov x17,x24
2721b3114: movk x17,#0xe3ba, LSL #48
2721b3118: blraa x8,x17
2721b311c: adrp x16,0x2721b4000
2721b3120: add x16,x16,#0xb04
2721b3124: mov x17,#0x8bb3
2721b3128: pacia x16,x17
2721b312c: mov x1,x16
2721b3130: mov x0,x22
2721b3134: bl 0x2721b4b98
2721b3138: mov x20,x28
2721b313c: bl 0x2743dacd0
2721b3140: mov x20,x27
2721b3144: fmov d8,d0
2721b3148: ldr x27,[x20, #0x8]!
2721b314c: mov x0,x28
2721b3150: mov x1,x25
2721b3154: mov x17,x20
2721b3158: movk x17,#0x4f8, LSL #48
2721b315c: blraa x27,x17
2721b3160: fcmp d8,#0.0
2721b3164: b.eq 0x2721b3290
2721b3168: sub x8,x29,#0x88
2721b316c: stur x20,[x8, #-0x100]
2721b3170: mov x20,x21
2721b3174: bl 0x2743da9d0
2721b3178: fcvt s8,d0
2721b317c: bl 0x2743da9d0
2721b3180: fcvt s9,d0
2721b3184: movi d0,#0x0
2721b3188: mov w0,#0xca00
2721b318c: movk w0,#0x3b9a, LSL #16
2721b3190: bl 0x2743dc320
2721b3194: sub x8,x29,#0x8
2721b3198: stur x0,[x8, #-0x100]
2721b319c: mov x22,x1
2721b31a0: sub x8,x29,#0x10
2721b31a4: stur x2,[x8, #-0x100]
2721b31a8: mov x8,x23
2721b31ac: bl 0x2743da9f0
2721b31b0: ldur x8,[x29, #-0xe8]
2721b31b4: mov x20,x23
2721b31b8: bl 0x2743daa50
2721b31bc: mov x0,x23
2721b31c0: mov x1,x19
2721b31c4: ldur x8,[x29, #-0xf8]
2721b31c8: mov x17,x26
2721b31cc: movk x17,#0x4f8, LSL #48
2721b31d0: blraa x8,x17
2721b31d4: mov x0,x28
2721b31d8: ldur x1,[x29, #-0xe8]
2721b31dc: mov x2,x25
2721b31e0: ldur x8,[x29, #-0x100]
2721b31e4: mov x17,x24
2721b31e8: movk x17,#0xe3ba, LSL #48
2721b31ec: blraa x8,x17
2721b31f0: adrp x16,0x2721b4000
2721b31f4: add x16,x16,#0xb04
2721b31f8: mov x17,#0x8bb3
2721b31fc: pacia x16,x17
2721b3200: mov x1,x16
2721b3204: ldur x0,[x29, #-0xe8]
2721b3208: bl 0x2721b4b98
2721b320c: mov x20,x28
2721b3210: bl 0x2743dacd0
2721b3214: fmov d10,d0
2721b3218: mov x0,x28
2721b321c: mov x1,x25
2721b3220: sub x8,x29,#0x88
2721b3224: ldur x8,[x8, #-0x100]
2721b3228: mov x17,x8
2721b322c: movk x17,#0x4f8, LSL #48
2721b3230: blraa x27,x17
2721b3234: fmov d0,d10
2721b3238: mov w0,#0xca00
2721b323c: movk w0,#0x3b9a, LSL #16
2721b3240: bl 0x2743dc320
2721b3244: mov x3,x0
2721b3248: mov x4,x1
2721b324c: mov x5,x2
2721b3250: sub x8,x29,#0xa8
2721b3254: sub x9,x29,#0x8
2721b3258: ldur x0,[x9, #-0x100]
2721b325c: mov x1,x22
2721b3260: ldur x22,[x29, #-0xe8]
2721b3264: sub x9,x29,#0x10
2721b3268: ldur x2,[x9, #-0x100]
2721b326c: bl 0x2743dc2a0
2721b3270: adrp x8,0x1fc13b000
2721b3274: add x1,x8,#0xb7b
2721b3278: sub x2,x29,#0xa8
2721b327c: sub x8,x29,#0x48
2721b3280: ldur x0,[x8, #-0x100]
2721b3284: fmov s0,s8
2721b3288: fmov s1,s9
2721b328c: bl 0x2743dc9d0
2721b3290: sub x8,x29,#0x50
2721b3294: ldur x19,[x8, #-0x100]
2721b3298: mov x8,x19
2721b329c: ldr x9,[x8, #0x8]!
2721b32a0: mov x0,x21
2721b32a4: ldur x1,[x29, #-0xf0]
2721b32a8: mov x17,x8
2721b32ac: movk x17,#0x4f8, LSL #48
2721b32b0: blraa x9,x17
2721b32b4: mov x11,x19
2721b32b8: sub x8,x29,#0xd8
2721b32bc: ldur x20,[x8, #-0x100]
2721b32c0: ldr x8,[x20, #0x10]
2721b32c4: cbz x8,0x2721b35e4
2721b32c8: ldr x9,[x11, #0x10]!
2721b32cc: sub x10,x29,#0x88
2721b32d0: stur x9,[x10, #-0x100]
2721b32d4: ldrb w9,[x11, #0x40]
2721b32d8: add x10,x9,#0x20
2721b32dc: bic x9,x10,x9
2721b32e0: add x1,x20,x9
2721b32e4: sub x9,x29,#0x90
2721b32e8: ldur x9,[x9, #-0x100]
2721b32ec: add x9,x9,#0x8
2721b32f0: sub x10,x29,#0x90
2721b32f4: stur x9,[x10, #-0x100]
2721b32f8: ldr x9,[x11, #0x38]
2721b32fc: sub x10,x29,#0xb0
2721b3300: stur x9,[x10, #-0x100]
2721b3304: sub x9,x29,#0xa0
2721b3308: ldur x9,[x9, #-0x100]
2721b330c: add x10,x9,#0x10
2721b3310: sub x12,x29,#0x98
2721b3314: stur x10,[x12, #-0x100]
2721b3318: add x9,x9,#0x8
2721b331c: sub x10,x29,#0xa0
2721b3320: stur x9,[x10, #-0x100]
2721b3324: sub x9,x11,#0x8
2721b3328: sub x10,x29,#0xa8
2721b332c: stur x9,[x10, #-0x100]
2721b3330: sub x8,x8,#0x1
2721b3334: stur x8,[x29, #-0xf8]
2721b3338: sub x8,x29,#0x50
2721b333c: stur x11,[x8, #-0x100]
2721b3340: sub x8,x29,#0x80
2721b3344: ldur x27,[x8, #-0x100]
2721b3348: sub x8,x29,#0x60
2721b334c: ldur x28,[x8, #-0x100]
2721b3350: sub x8,x29,#0xa0
2721b3354: ldur x26,[x8, #-0x100]
2721b3358: mov x0,x27
2721b335c: sub x8,x29,#0x30
2721b3360: stur x1,[x8, #-0x100]
2721b3364: ldur x2,[x29, #-0xf0]
2721b3368: sub x8,x29,#0x88
2721b336c: ldur x8,[x8, #-0x100]
2721b3370: mov x17,x11
2721b3374: movk x17,#0xe3ba, LSL #48
2721b3378: blraa x8,x17
2721b337c: mov x20,x27
2721b3380: bl 0x2743da9d0
2721b3384: fcvt s8,d0
2721b3388: bl 0x2743da9e0
2721b338c: fcvt s9,d0
2721b3390: mov x8,x23
2721b3394: bl 0x2743da9f0
2721b3398: mov x8,x22
2721b339c: mov x20,x23
2721b33a0: bl 0x2743daa50
2721b33a4: sub x8,x29,#0x90
2721b33a8: ldur x22,[x8, #-0x100]
2721b33ac: ldr x8,[x22]
2721b33b0: sub x9,x29,#0x20
2721b33b4: stur x8,[x9, #-0x100]
2721b33b8: mov x0,x23
2721b33bc: sub x9,x29,#0x68
2721b33c0: ldur x25,[x9, #-0x100]
2721b33c4: mov x1,x25
2721b33c8: mov x17,x22
2721b33cc: movk x17,#0x4f8, LSL #48
2721b33d0: blraa x8,x17
2721b33d4: sub x8,x29,#0x98
2721b33d8: ldur x24,[x8, #-0x100]
2721b33dc: ldr x8,[x24]
2721b33e0: sub x9,x29,#0x18
2721b33e4: stur x8,[x9, #-0x100]
2721b33e8: mov x0,x28
2721b33ec: ldur x1,[x29, #-0xe8]
2721b33f0: sub x9,x29,#0x58
2721b33f4: ldur x21,[x9, #-0x100]
2721b33f8: mov x2,x21
2721b33fc: mov x17,x24
2721b3400: movk x17,#0xe3ba, LSL #48
2721b3404: blraa x8,x17
2721b3408: adrp x16,0x2721b4000
2721b340c: add x16,x16,#0xb04
2721b3410: mov x17,#0x8bb3
2721b3414: pacia x16,x17
2721b3418: mov x19,x16
2721b341c: ldur x0,[x29, #-0xe8]
2721b3420: mov x1,x16
2721b3424: bl 0x2721b4b98
2721b3428: mov x20,x28
2721b342c: bl 0x2743dacd0
2721b3430: fmov d10,d0
2721b3434: ldr x8,[x26]
2721b3438: sub x9,x29,#0x28
2721b343c: stur x8,[x9, #-0x100]
2721b3440: mov x0,x28
2721b3444: mov x1,x21
2721b3448: mov x17,x26
2721b344c: movk x17,#0x4f8, LSL #48
2721b3450: blraa x8,x17
2721b3454: fmov d0,d10
2721b3458: mov w0,#0xca00
2721b345c: movk w0,#0x3b9a, LSL #16
2721b3460: bl 0x2743dc320
2721b3464: stur x0,[x29, #-0x100]
2721b3468: sub x8,x29,#0x8
2721b346c: stur x1,[x8, #-0x100]
2721b3470: sub x8,x29,#0x10
2721b3474: stur x2,[x8, #-0x100]
2721b3478: mov x8,x23
2721b347c: mov x20,x27
2721b3480: bl 0x2743da9f0
2721b3484: sub x8,x29,#0xa8
2721b3488: ldur x9,[x8, #-0x100]
2721b348c: ldr x8,[x9]
2721b3490: mov x0,x27
2721b3494: ldur x1,[x29, #-0xf0]
2721b3498: mov x17,x9
2721b349c: movk x17,#0x4f8, LSL #48
2721b34a0: blraa x8,x17
2721b34a4: ldur x8,[x29, #-0xe8]
2721b34a8: mov x20,x23
2721b34ac: bl 0x2743daa50
2721b34b0: mov x0,x23
2721b34b4: mov x1,x25
2721b34b8: sub x8,x29,#0x20
2721b34bc: ldur x8,[x8, #-0x100]
2721b34c0: mov x17,x22
2721b34c4: movk x17,#0x4f8, LSL #48
2721b34c8: blraa x8,x17
2721b34cc: sub x8,x29,#0x78
2721b34d0: ldur x8,[x8, #-0x100]
2721b34d4: ldrsw x8,[x8, #0x24]
2721b34d8: ldur x9,[x29, #-0xe8]
2721b34dc: add x1,x9,x8
2721b34e0: mov x0,x28
2721b34e4: mov x2,x21
2721b34e8: sub x8,x29,#0x18
2721b34ec: ldur x8,[x8, #-0x100]
2721b34f0: mov x17,x24
2721b34f4: movk x17,#0xe3ba, LSL #48
2721b34f8: blraa x8,x17
2721b34fc: ldur x22,[x29, #-0xe8]
2721b3500: mov x0,x22
2721b3504: mov x1,x19
2721b3508: bl 0x2721b4b98
2721b350c: mov x20,x28
2721b3510: bl 0x2743dacd0
2721b3514: fmov d10,d0
2721b3518: mov x0,x28
2721b351c: mov x1,x21
2721b3520: sub x8,x29,#0x28
2721b3524: ldur x8,[x8, #-0x100]
2721b3528: mov x17,x26
2721b352c: movk x17,#0x4f8, LSL #48
2721b3530: blraa x8,x17
2721b3534: fmov d0,d10
2721b3538: mov w0,#0xca00
2721b353c: movk w0,#0x3b9a, LSL #16
2721b3540: bl 0x2743dc320
2721b3544: mov x3,x0
2721b3548: mov x4,x1
2721b354c: mov x5,x2
2721b3550: sub x8,x29,#0xa8
2721b3554: ldur x0,[x29, #-0x100]
2721b3558: sub x9,x29,#0x8
2721b355c: ldur x1,[x9, #-0x100]
2721b3560: sub x9,x29,#0x10
2721b3564: ldur x2,[x9, #-0x100]
2721b3568: bl 0x2743dc2a0
2721b356c: adrp x8,0x1fc13b000
2721b3570: add x1,x8,#0xb7b
2721b3574: ldur q0,[x29, #-0xa8]
2721b3578: ldur q1,[x29, #-0x98]
2721b357c: stp q0,q1,[x29, #-0xe0]
2721b3580: ldur q0,[x29, #-0x88]
2721b3584: stur q0,[x29, #-0xc0]
2721b3588: sub x2,x29,#0xe0
2721b358c: sub x8,x29,#0x48
2721b3590: ldur x0,[x8, #-0x100]
2721b3594: fmov s0,s8
2721b3598: fmov s1,s9
2721b359c: bl 0x2743dc9d0
2721b35a0: ldur x8,[x29, #-0xf8]
2721b35a4: cbz x8,0x2721b35d8
2721b35a8: sub x8,x8,#0x1
2721b35ac: stur x8,[x29, #-0xf8]
2721b35b0: sub x8,x29,#0x30
2721b35b4: ldur x1,[x8, #-0x100]
2721b35b8: sub x8,x29,#0xb0
2721b35bc: ldur x8,[x8, #-0x100]
2721b35c0: add x1,x1,x8
2721b35c4: sub x8,x29,#0x70
2721b35c8: ldur x23,[x8, #-0x100]
2721b35cc: sub x8,x29,#0x50
2721b35d0: ldur x11,[x8, #-0x100]
2721b35d4: b 0x2721b3358
2721b35d8: sub x8,x29,#0xd8
2721b35dc: ldur x0,[x8, #-0x100]
2721b35e0: b 0x2721b35e8
2721b35e4: mov x0,x20
2721b35e8: bl 0x2743dcc60
2721b35ec: sub x8,x29,#0x40
2721b35f0: ldur x9,[x8, #-0x100]
2721b35f4: ldr x8,[x9]
2721b35f8: sub x10,x29,#0xd0
2721b35fc: ldur x0,[x10, #-0x100]
2721b3600: sub x10,x29,#0xe0
2721b3604: ldur x1,[x10, #-0x100]
2721b3608: mov x17,x9
2721b360c: movk x17,#0x4f8, LSL #48
2721b3610: blraa x8,x17
2721b3614: sub x8,x29,#0x38
2721b3618: ldur x0,[x8, #-0x100]
2721b361c: sub x8,x29,#0xc0
2721b3620: ldur x1,[x8, #-0x100]
2721b3624: sub x8,x29,#0xb8
2721b3628: ldur x2,[x8, #-0x100]
2721b362c: bl 0x2721b5368
2721b3630: sub x8,x29,#0x48
2721b3634: ldur x21,[x8, #-0x100]
2721b3638: cbz x0,0x2721b3658
2721b363c: mov x19,x0
2721b3640: adrp x8,0x1fae51000
2721b3644: add x1,x8,#0xdf4
2721b3648: mov x0,x21
2721b364c: mov x2,x19
2721b3650: bl 0x2743dc9d0
2721b3654: bl 0x2743dca10
2721b3658: mov x0,#0x0
2721b365c: bl 0x2721b4bf0
2721b3660: mov w1,#0x28
2721b3664: mov w2,#0x7
2721b3668: bl 0x2743dcbc0
2721b366c: mov x19,x0
2721b3670: adrp x8,0x2721ed000
2721b3674: ldr q0,[x8, #0x7a0]
2721b3678: str q0,[x0, #0x10]
2721b367c: str x21,[x0, #0x20]
2721b3680: mov x0,#0x0
2721b3684: bl 0x2721b4c44
2721b3688: mov x20,x0
2721b368c: bl 0x2743dcb00
2721b3690: mov x21,x0
2721b3694: mov x0,x19
2721b3698: mov x1,x20
2721b369c: bl 0x2743dc040
2721b36a0: mov x20,x0
2721b36a4: mov x0,x19
2721b36a8: bl 0x2743dcc60
2721b36ac: adrp x8,0x1fc13a000
2721b36b0: add x1,x8,#0x5ac
2721b36b4: sub x8,x29,#0xc8
2721b36b8: ldur x19,[x8, #-0x100]
2721b36bc: mov x0,x19
2721b36c0: mov x2,x20
2721b36c4: bl 0x2743dc9d0
2721b36c8: bl 0x2743dca20
2721b36cc: bl 0x2743dca30
2721b36d0: mov x0,x19
2721b36d4: sub sp,x29,#0x70
2721b36d8: ldp x29,x30,[sp, #0x70]
2721b36dc: ldp x20,x19,[sp, #0x60]
2721b36e0: ldp x22,x21,[sp, #0x50]
2721b36e4: ldp x24,x23,[sp, #0x40]
2721b36e8: ldp x26,x25,[sp, #0x30]
2721b36ec: ldp x28,x27,[sp, #0x20]
2721b36f0: ldp d9,d8,[sp, #0x10]
2721b36f4: ldp d11,d10,[sp], #0x80
2721b36f8: retab

