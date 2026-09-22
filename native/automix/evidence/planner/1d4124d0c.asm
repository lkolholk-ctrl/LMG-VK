; Binary: MusicKitInternal
; Address: 0x1d4124d0c
; Symbol: FUN_1d4124d0c
; Signature: undefined FUN_1d4124d0c()
1d4124d0c: pacibsp
1d4124d10: mov x0,x30
1d4124d14: bl 0x1d3cee450
1d4124d18: mov x30,x0
1d4124d1c: stp x29,x30,[sp, #0x50]
1d4124d20: add x29,sp,#0x50
1d4124d24: sub sp,sp,#0x20
1d4124d28: mov x23,x20
1d4124d2c: stur x8,[x29, #-0x60]
1d4124d30: mov x0,#0x0
1d4124d34: bl 0x1d4a287a0
1d4124d38: mov x21,x0
1d4124d3c: bl 0x1d3cef1a4
1d4124d40: movk x17,#0x2e3f, LSL #48
1d4124d44: autda x16,x17
1d4124d48: mov x17,x16
1d4124d4c: xpacd x17
1d4124d50: cmp x16,x17
1d4124d54: b.eq 0x1d4124d5c
1d4124d58: brk #0xc472
1d4124d5c: mov x28,x16
1d4124d60: mov x16,x8
1d4124d64: mov x17,x9
1d4124d68: movk x17,#0x2e3f, LSL #48
1d4124d6c: autda x16,x17
1d4124d70: ldr x8,[x16, #0x40]
1d4124d74: mov x9,x8
1d4124d78: adrp x17,0x1ef12d000
1d4124d7c: add x17,x17,#0xd40
1d4124d80: ldr x16,[x17]
1d4124d84: blraa x16,x17
1d4124d88: bl 0x1d3cef208
1d4124d8c: stur x9,[x29, #-0x68]
1d4124d90: bl 0x1d3cf4610
1d4124d94: adrp x17,0x1ef12d000
1d4124d98: add x17,x17,#0xd40
1d4124d9c: ldr x16,[x17]
1d4124da0: blraa x16,x17
1d4124da4: mov x9,sp
1d4124da8: bl 0x1d3d0f474
1d4124dac: adrp x17,0x1ef12d000
1d4124db0: add x17,x17,#0xd40
1d4124db4: ldr x16,[x17]
1d4124db8: blraa x16,x17
1d4124dbc: bl 0x1d3cfec44
1d4124dc0: mov x19,x28
1d4124dc4: ldr x22,[x19, #0x10]!
1d4124dc8: mov x0,x24
1d4124dcc: mov x1,x20
1d4124dd0: mov x2,x21
1d4124dd4: mov x17,x19
1d4124dd8: movk x17,#0xe3ba, LSL #48
1d4124ddc: blraa x22,x17
1d4124de0: mov x8,x25
1d4124de4: bl 0x1d4a28780
1d4124de8: adrp x0,0x1ec963000
1d4124dec: add x0,x0,#0xec0
1d4124df0: bl 0x1d3d1e8f8
1d4124df4: adrp x16,0x1e6b7a000
1d4124df8: ldr x16,[x16, #0x1a0]
1d4124dfc: mov x17,#0xc6eb
1d4124e00: pacda x16,x17
1d4124e04: mov x2,x16
1d4124e08: bl 0x1d4125a28
1d4124e0c: mov x26,x0
1d4124e10: bl 0x1d3d08324
1d4124e14: mov x20,x0
1d4124e18: ldr x27,[x28, #0x8]!
1d4124e1c: mov x0,x25
1d4124e20: mov x1,x21
1d4124e24: mov x17,x28
1d4124e28: movk x17,#0x4f8, LSL #48
1d4124e2c: blraa x27,x17
1d4124e30: tbz w20,#0x0,0x1d4124e4c
1d4124e34: bl 0x1d3cef7cc
1d4124e38: mov x17,x28
1d4124e3c: movk x17,#0x4f8, LSL #48
1d4124e40: blraa x27,x17
1d4124e44: mov w8,#0x0
1d4124e48: b 0x1d4125064
1d4124e4c: mov x8,x25
1d4124e50: bl 0x1d4a28750
1d4124e54: bl 0x1d3d08324
1d4124e58: bl 0x1d3cffae0
1d4124e5c: mov x17,x28
1d4124e60: movk x17,#0x4f8, LSL #48
1d4124e64: blraa x27,x17
1d4124e68: tbz w20,#0x0,0x1d4124e84
1d4124e6c: bl 0x1d3cef7cc
1d4124e70: mov x17,x28
1d4124e74: movk x17,#0x4f8, LSL #48
1d4124e78: blraa x27,x17
1d4124e7c: mov w8,#0x1
1d4124e80: b 0x1d4125064
1d4124e84: mov x8,x25
1d4124e88: bl 0x1d4a28770
1d4124e8c: bl 0x1d3d08324
1d4124e90: bl 0x1d3cffae0
1d4124e94: mov x17,x28
1d4124e98: movk x17,#0x4f8, LSL #48
1d4124e9c: blraa x27,x17
1d4124ea0: tbz w20,#0x0,0x1d4124ebc
1d4124ea4: bl 0x1d3cef7cc
1d4124ea8: mov x17,x28
1d4124eac: movk x17,#0x4f8, LSL #48
1d4124eb0: blraa x27,x17
1d4124eb4: mov w8,#0x2
1d4124eb8: b 0x1d4125064
1d4124ebc: mov x8,x25
1d4124ec0: bl 0x1d4a28760
1d4124ec4: bl 0x1d3d08324
1d4124ec8: bl 0x1d3cffae0
1d4124ecc: mov x17,x28
1d4124ed0: movk x17,#0x4f8, LSL #48
1d4124ed4: blraa x27,x17
1d4124ed8: tbz w20,#0x0,0x1d4124ef4
1d4124edc: bl 0x1d3cef7cc
1d4124ee0: mov x17,x28
1d4124ee4: movk x17,#0x4f8, LSL #48
1d4124ee8: blraa x27,x17
1d4124eec: mov w8,#0x3
1d4124ef0: b 0x1d4125064
1d4124ef4: mov x8,x25
1d4124ef8: bl 0x1d4a28790
1d4124efc: bl 0x1d3d08324
1d4124f00: bl 0x1d3cffae0
1d4124f04: mov x17,x28
1d4124f08: movk x17,#0x4f8, LSL #48
1d4124f0c: blraa x27,x17
1d4124f10: bl 0x1d3cef7cc
1d4124f14: mov x17,x28
1d4124f18: movk x17,#0x4f8, LSL #48
1d4124f1c: blraa x27,x17
1d4124f20: tbz w20,#0x0,0x1d4124f2c
1d4124f24: mov w8,#0x4
1d4124f28: b 0x1d4125064
1d4124f2c: adrp x8,0x1edf76000
1d4124f30: ldr x8,[x8, #0x818]
1d4124f34: cmn x8,#0x1
1d4124f38: b.ne 0x1d4125084
1d4124f3c: mov x0,#0x0
1d4124f40: bl 0x1d4a23b50
1d4124f44: adrp x1,0x1edf8e000
1d4124f48: add x1,x1,#0xc08
1d4124f4c: bl 0x1d3d14634
1d4124f50: bl 0x1d4125adc
1d4124f54: mov x17,x19
1d4124f58: movk x17,#0xe3ba, LSL #48
1d4124f5c: blraa x22,x17
1d4124f60: bl 0x1d4a23b30
1d4124f64: mov x23,x0
1d4124f68: bl 0x1d4a2ca40
1d4124f6c: mov x25,x0
1d4124f70: and w1,w0,#0xff
1d4124f74: mov x0,x23
1d4124f78: bl 0x1d4a2de60
1d4124f7c: cbz w0,0x1d412504c
1d4124f80: mov w0,#0xc
1d4124f84: mov x1,#-0x1
1d4124f88: bl 0x1d4a2e730
1d4124f8c: mov x24,x0
1d4124f90: mov w0,#0x20
1d4124f94: mov x1,#-0x1
1d4124f98: bl 0x1d4a2e730
1d4124f9c: mov x19,x0
1d4124fa0: stur x0,[x29, #-0x58]
1d4124fa4: adrp x8,0x1d4490000
1d4124fa8: ldr d0,[x8, #0xe38]
1d4124fac: str s0,[x24]
1d4124fb0: adrp x0,0x1ec963000
1d4124fb4: add x0,x0,#0xec8
1d4124fb8: bl 0x1d3d1e8f8
1d4124fbc: adrp x16,0x1e6b7a000
1d4124fc0: ldr x16,[x16, #0x1a8]
1d4124fc4: mov x17,#0xc6eb
1d4124fc8: pacda x16,x17
1d4124fcc: mov x2,x16
1d4124fd0: bl 0x1d4125a28
1d4124fd4: mov x1,x0
1d4124fd8: mov x20,x26
1d4124fdc: mov x0,x21
1d4124fe0: bl 0x1d4a2d470
1d4124fe4: bl 0x1d4125b00
1d4124fe8: mov x26,x1
1d4124fec: mov x1,x21
1d4124ff0: mov x17,x28
1d4124ff4: movk x17,#0x4f8, LSL #48
1d4124ff8: blraa x27,x17
1d4124ffc: sub x2,x29,#0x58
1d4125000: mov x0,x20
1d4125004: mov x1,x26
1d4125008: bl 0x1d3d14f44
1d412500c: bl 0x1d4125b00
1d4125010: bl 0x1d4a2e2c0
1d4125014: stur x20,[x24, #0x4]
1d4125018: adrp x0,0x1d3cec000
1d412501c: add x0,x0,#0x0
1d4125020: adrp x3,0x1d4559000
1d4125024: add x3,x3,#0x660
1d4125028: bl 0x1d4125b18
1d412502c: mov x0,x19
1d4125030: bl 0x1d3d01070
1d4125034: mov x0,x19
1d4125038: bl 0x1d3cf5584
1d412503c: mov x0,x24
1d4125040: bl 0x1d3cf5584
1d4125044: bl 0x1d4a2dbf0
1d4125048: b 0x1d4125060
1d412504c: bl 0x1d4a2dbf0
1d4125050: bl 0x1d3d10d00
1d4125054: mov x17,x28
1d4125058: movk x17,#0x4f8, LSL #48
1d412505c: blraa x27,x17
1d4125060: mov w8,#0x5
1d4125064: ldur x9,[x29, #-0x60]
1d4125068: strb w8,[x9]
1d412506c: sub sp,x29,#0x50
1d4125070: ldp x29,x30,[sp, #0x50]
1d4125074: mov x0,x30
1d4125078: bl 0x1d3cf54f4
1d412507c: mov x30,x0
1d4125080: retab
1d4125084: adrp x0,0x1edf76000
1d4125088: add x0,x0,#0x818
1d412508c: bl 0x1d3cefb9c
1d4125090: b 0x1d4124f3c

