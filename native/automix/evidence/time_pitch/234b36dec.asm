; vDSP_zvphas, reached from 0x236f46050
0x234b36dec: pacibsp 
0x234b36df0: stp d15, d14, [sp, #-0xa0]!
0x234b36df4: stp d13, d12, [sp, #0x10]
0x234b36df8: stp d11, d10, [sp, #0x20]
0x234b36dfc: stp d9, d8, [sp, #0x30]
0x234b36e00: stp x28, x27, [sp, #0x40]
0x234b36e04: stp x26, x25, [sp, #0x50]
0x234b36e08: stp x24, x23, [sp, #0x60]
0x234b36e0c: stp x22, x21, [sp, #0x70]
0x234b36e10: stp x20, x19, [sp, #0x80]
0x234b36e14: stp x29, x30, [sp, #0x90]
0x234b36e18: add x29, sp, #0x90
0x234b36e1c: sub sp, sp, #0x1f0
0x234b36e20: mov x20, x4
0x234b36e24: mov x26, x2
0x234b36e28: ldp x21, x22, [x0]
0x234b36e2c: eor x8, x1, #1
0x234b36e30: eor x9, x3, #1
0x234b36e34: orr x8, x9, x8
0x234b36e38: and x9, x2, #3
0x234b36e3c: orr x8, x8, x9
0x234b36e40: cbz x8, #0x234b36f3c
0x234b36e44: lsl x23, x1, #2
0x234b36e48: lsl x24, x3, #2
0x234b36e4c: cmp x20, #4
0x234b36e50: b.lo #0x234b36f10
0x234b36e54: lsl x8, x3, #4
0x234b36e58: stur x8, [x29, #-0xb0]
0x234b36e5c: lsl x25, x1, #4
0x234b36e60: mov x19, x26
0x234b36e64: mov x28, x21
0x234b36e68: mov x27, x22
0x234b36e6c: ldr s1, [x21]
0x234b36e70: ldr s0, [x22]
0x234b36e74: add x8, x21, x23
0x234b36e78: ldr s8, [x8]
0x234b36e7c: add x9, x22, x23
0x234b36e80: ldr s9, [x9]
0x234b36e84: add x8, x8, x23
0x234b36e88: ldr s10, [x8]
0x234b36e8c: add x9, x9, x23
0x234b36e90: ldr s11, [x9]
0x234b36e94: add x21, x8, x23
0x234b36e98: ldr s12, [x21]
0x234b36e9c: add x22, x9, x23
0x234b36ea0: ldr s13, [x22]
0x234b36ea4: bl #0x236f3d3d0
0x234b36ea8: str s0, [x26]
0x234b36eac: fmov s0, s9
0x234b36eb0: fmov s1, s8
0x234b36eb4: bl #0x236f3d3d0
0x234b36eb8: add x26, x26, x24
0x234b36ebc: str s0, [x26]
0x234b36ec0: fmov s0, s11
0x234b36ec4: fmov s1, s10
0x234b36ec8: bl #0x236f3d3d0
0x234b36ecc: add x26, x26, x24
0x234b36ed0: str s0, [x26]
0x234b36ed4: fmov s0, s13
0x234b36ed8: fmov s1, s12
0x234b36edc: bl #0x236f3d3d0
0x234b36ee0: add x8, x26, x24
0x234b36ee4: str s0, [x8]
0x234b36ee8: sub x20, x20, #4
0x234b36eec: add x21, x21, x23
0x234b36ef0: add x22, x22, x23
0x234b36ef4: add x26, x8, x24
0x234b36ef8: cmp x20, #3
0x234b36efc: b.hi #0x234b36e60
0x234b36f00: ldur x8, [x29, #-0xb0]
0x234b36f04: add x26, x19, x8
0x234b36f08: add x21, x28, x25
0x234b36f0c: add x22, x27, x25
0x234b36f10: cbz x20, #0x234b37e5c
0x234b36f14: mov x19, #0
0x234b36f18: ldr s1, [x21, x19]
0x234b36f1c: ldr s0, [x22, x19]
0x234b36f20: bl #0x236f3d3d0
0x234b36f24: str s0, [x26]
0x234b36f28: add x26, x26, x24
0x234b36f2c: add x19, x19, x23
0x234b36f30: subs x20, x20, #1
0x234b36f34: b.ne #0x234b36f18
0x234b36f38: b #0x234b37e5c
0x234b36f3c: tst x26, #0xf
0x234b36f40: b.eq #0x234b36f98
0x234b36f44: cbz x20, #0x234b36f98
0x234b36f48: mov x19, #0
0x234b36f4c: add x24, x26, #4
0x234b36f50: ldr s1, [x21, x19]
0x234b36f54: ldr s0, [x22, x19]
0x234b36f58: bl #0x236f3d3d0
0x234b36f5c: str s0, [x26, x19]
0x234b36f60: sub x23, x20, #1
0x234b36f64: add w8, w24, w19
0x234b36f68: add x19, x19, #4
0x234b36f6c: tst x8, #0xf
0x234b36f70: b.eq #0x234b36f80
0x234b36f74: cmp x20, #1
0x234b36f78: mov x20, x23
0x234b36f7c: b.ne #0x234b36f50
0x234b36f80: add x26, x26, x19
0x234b36f84: add x21, x21, x19
0x234b36f88: add x22, x22, x19
0x234b36f8c: cmp x23, #0x20
0x234b36f90: b.hs #0x234b36fa4
0x234b36f94: b #0x234b378d8
0x234b36f98: mov x23, x20
0x234b36f9c: cmp x20, #0x20
0x234b36fa0: b.lo #0x234b378d8
0x234b36fa4: mov w8, #0x13cd
0x234b36fa8: movk w8, #0x3ed4, lsl #16
0x234b36fac: dup v0.4s, w8
0x234b36fb0: stur q0, [x29, #-0xb0]
0x234b36fb4: mov w8, #0x827a
0x234b36fb8: movk w8, #0x401a, lsl #16
0x234b36fbc: dup v0.4s, w8
0x234b36fc0: stur q0, [x29, #-0xe0]
0x234b36fc4: fmov v0.4s, #-1.00000000
0x234b36fc8: str q0, [sp, #0x150]
0x234b36fcc: fmov v0.4s, #1.00000000
0x234b36fd0: str q0, [sp]
0x234b36fd4: mov w8, #0xfdb
0x234b36fd8: movk w8, #0x3f49, lsl #16
0x234b36fdc: dup v0.4s, w8
0x234b36fe0: stur q0, [x29, #-0xf0]
0x234b36fe4: mov w8, #0xfdb
0x234b36fe8: movk w8, #0x3fc9, lsl #16
0x234b36fec: dup v0.4s, w8
0x234b36ff0: str q0, [sp, #0x10]
0x234b36ff4: mov w8, #0xf0d1
0x234b36ff8: movk w8, #0x3da4, lsl #16
0x234b36ffc: dup v0.4s, w8
0x234b37000: stur q0, [x29, #-0xc0]
0x234b37004: mov w8, #0x1b85
0x234b37008: movk w8, #0xbe0e, lsl #16
0x234b3700c: dup v0.4s, w8
0x234b37010: stur q0, [x29, #-0x100]
0x234b37014: mov w8, #0x925f
0x234b37018: movk w8, #0x3e4c, lsl #16
0x234b3701c: dup v1.4s, w8
0x234b37020: mov w8, #0xaa2a
0x234b37024: movk w8, #0xbeaa, lsl #16
0x234b37028: dup v0.4s, w8
0x234b3702c: str q0, [sp, #0x20]
0x234b37030: mov w8, #0xfdb
0x234b37034: movk w8, #0x4049, lsl #16
0x234b37038: dup v0.4s, w8
0x234b3703c: stp q0, q1, [sp, #0x160]
0x234b37040: ldp q2, q17, [x21]
0x234b37044: frecpe v0.4s, v2.4s
0x234b37048: frecps v1.4s, v2.4s, v0.4s
0x234b3704c: fmul v0.4s, v0.4s, v1.4s
0x234b37050: ldp q1, q20, [x22]
0x234b37054: frecps v4.4s, v2.4s, v0.4s
0x234b37058: mov v25.16b, v2.16b
0x234b3705c: str q2, [sp, #0x130]
0x234b37060: fmul v0.4s, v1.4s, v0.4s
0x234b37064: fmul v19.4s, v4.4s, v0.4s
0x234b37068: fabs v0.4s, v19.4s
0x234b3706c: ldur q4, [x29, #-0xb0]
0x234b37070: fcmgt v6.4s, v0.4s, v4.4s
0x234b37074: mov v9.16b, v4.16b
0x234b37078: ldur q28, [x29, #-0xe0]
0x234b3707c: fcmgt v4.4s, v0.4s, v28.4s
0x234b37080: ldp q12, q2, [x21, #0x20]
0x234b37084: stur q2, [x29, #-0xd0]
0x234b37088: ldr q5, [sp, #0x150]
0x234b3708c: fadd v7.4s, v0.4s, v5.4s
0x234b37090: ldp q15, q8, [sp]
0x234b37094: fadd v21.4s, v0.4s, v15.4s
0x234b37098: ldp q26, q14, [x22, #0x20]
0x234b3709c: ldur q2, [x29, #-0xf0]
0x234b370a0: and v22.16b, v2.16b, v6.16b
0x234b370a4: mov v16.16b, v4.16b
0x234b370a8: bsl v16.16b, v8.16b, v22.16b
0x234b370ac: str q16, [sp, #0x100]
0x234b370b0: bit v7.16b, v5.16b, v4.16b
0x234b370b4: bsl v4.16b, v0.16b, v21.16b
0x234b370b8: frecpe v21.4s, v4.4s
0x234b370bc: fmul v22.4s, v21.4s, v7.4s
0x234b370c0: mov v27.16b, v15.16b
0x234b370c4: fmls v27.4s, v4.4s, v21.4s
0x234b370c8: fcmeq v29.4s, v21.4s, #0.0
0x234b370cc: ldur q3, [x29, #-0x100]
0x234b370d0: mov v23.16b, v3.16b
0x234b370d4: ldp q16, q24, [sp, #0x160]
0x234b370d8: mov v7.16b, v24.16b
0x234b370dc: fmla v22.4s, v22.4s, v27.4s
0x234b370e0: ldr q18, [sp, #0x20]
0x234b370e4: mov v4.16b, v18.16b
0x234b370e8: movi v13.4s, #0x80, lsl #24
0x234b370ec: and v31.16b, v19.16b, v13.16b
0x234b370f0: fcmlt v19.4s, v25.4s, #0.0
0x234b370f4: fcmlt v30.4s, v1.4s, #0.0
0x234b370f8: mov v25.16b, v19.16b
0x234b370fc: bsl v25.16b, v16.16b, v13.16b
0x234b37100: str q25, [sp, #0xd0]
0x234b37104: fmul v19.4s, v27.4s, v27.4s
0x234b37108: fneg v27.4s, v25.4s
0x234b3710c: bit v25.16b, v27.16b, v30.16b
0x234b37110: str q25, [sp, #0xe0]
0x234b37114: fcmeq v1.4s, v1.4s, #0.0
0x234b37118: stp q31, q1, [sp, #0xa0]
0x234b3711c: bsl v1.16b, v13.16b, v8.16b
0x234b37120: fneg v27.4s, v1.4s
0x234b37124: fmul v31.4s, v19.4s, v19.4s
0x234b37128: bit v1.16b, v27.16b, v30.16b
0x234b3712c: str q1, [sp, #0x140]
0x234b37130: frecpe v1.4s, v17.4s
0x234b37134: frecps v27.4s, v17.4s, v1.4s
0x234b37138: fmul v1.4s, v1.4s, v27.4s
0x234b3713c: frecps v27.4s, v17.4s, v1.4s
0x234b37140: mov v10.16b, v17.16b
0x234b37144: str q17, [sp, #0x110]
0x234b37148: fmla v22.4s, v22.4s, v19.4s
0x234b3714c: fmul v1.4s, v20.4s, v1.4s
0x234b37150: fmul v19.4s, v27.4s, v1.4s
0x234b37154: fabs v27.4s, v19.4s
0x234b37158: mov v25.16b, v9.16b
0x234b3715c: fcmgt v30.4s, v27.4s, v9.4s
0x234b37160: fcmgt v9.4s, v27.4s, v28.4s
0x234b37164: fmla v22.4s, v22.4s, v31.4s
0x234b37168: fadd v31.4s, v27.4s, v5.4s
0x234b3716c: fadd v11.4s, v27.4s, v15.4s
0x234b37170: and v1.16b, v2.16b, v30.16b
0x234b37174: bit v1.16b, v8.16b, v9.16b
0x234b37178: str q1, [sp, #0xc0]
0x234b3717c: bit v31.16b, v5.16b, v9.16b
0x234b37180: bif v21.16b, v22.16b, v29.16b
0x234b37184: mov v22.16b, v9.16b
0x234b37188: bsl v22.16b, v27.16b, v11.16b
0x234b3718c: frecpe v29.4s, v22.4s
0x234b37190: fmul v31.4s, v29.4s, v31.4s
0x234b37194: mov v9.16b, v15.16b
0x234b37198: fmls v9.4s, v22.4s, v29.4s
0x234b3719c: bsl v6.16b, v21.16b, v0.16b
0x234b371a0: fmla v31.4s, v31.4s, v9.4s
0x234b371a4: fmul v0.4s, v9.4s, v9.4s
0x234b371a8: fmla v31.4s, v31.4s, v0.4s
0x234b371ac: fmul v0.4s, v0.4s, v0.4s
0x234b371b0: fmla v31.4s, v31.4s, v0.4s
0x234b371b4: fmul v9.4s, v6.4s, v6.4s
0x234b371b8: fcmeq v0.4s, v29.4s, #0.0
0x234b371bc: bsl v0.16b, v29.16b, v31.16b
0x234b371c0: mov v22.16b, v30.16b
0x234b371c4: bsl v22.16b, v0.16b, v27.16b
0x234b371c8: fmul v27.4s, v22.4s, v22.4s
0x234b371cc: mov v29.16b, v3.16b
0x234b371d0: ldur q0, [x29, #-0xc0]
0x234b371d4: fmla v23.4s, v0.4s, v9.4s
0x234b371d8: mov v17.16b, v0.16b
0x234b371dc: mov v1.16b, v24.16b
0x234b371e0: mov v30.16b, v24.16b
0x234b371e4: mov v31.16b, v18.16b
0x234b371e8: and v21.16b, v19.16b, v13.16b
0x234b371ec: fcmlt v0.4s, v10.4s, #0.0
0x234b371f0: fcmlt v19.4s, v20.4s, #0.0
0x234b371f4: fmla v29.4s, v17.4s, v27.4s
0x234b371f8: mov v24.16b, v17.16b
0x234b371fc: mov v10.16b, v0.16b
0x234b37200: bsl v10.16b, v16.16b, v13.16b
0x234b37204: fneg v0.4s, v10.4s
0x234b37208: mov v11.16b, v19.16b
0x234b3720c: bsl v11.16b, v0.16b, v10.16b
0x234b37210: fcmeq v17.4s, v20.4s, #0.0
0x234b37214: mov v20.16b, v17.16b
0x234b37218: bsl v20.16b, v13.16b, v8.16b
0x234b3721c: fmla v7.4s, v23.4s, v9.4s
0x234b37220: fneg v23.4s, v20.4s
0x234b37224: bsl v19.16b, v23.16b, v20.16b
0x234b37228: str q19, [sp, #0x120]
0x234b3722c: mov v0.16b, v12.16b
0x234b37230: frecpe v19.4s, v12.4s
0x234b37234: frecps v20.4s, v12.4s, v19.4s
0x234b37238: fmul v19.4s, v19.4s, v20.4s
0x234b3723c: fmla v30.4s, v29.4s, v27.4s
0x234b37240: frecps v20.4s, v12.4s, v19.4s
0x234b37244: str q12, [sp, #0xf0]
0x234b37248: fmul v19.4s, v26.4s, v19.4s
0x234b3724c: fmul v19.4s, v20.4s, v19.4s
0x234b37250: fabs v20.4s, v19.4s
0x234b37254: fcmgt v23.4s, v20.4s, v25.4s
0x234b37258: fmla v4.4s, v7.4s, v9.4s
0x234b3725c: fcmgt v7.4s, v20.4s, v28.4s
0x234b37260: fadd v29.4s, v20.4s, v5.4s
0x234b37264: fadd v12.4s, v20.4s, v15.4s
0x234b37268: and v13.16b, v2.16b, v23.16b
0x234b3726c: mov v25.16b, v7.16b
0x234b37270: bsl v25.16b, v8.16b, v13.16b
0x234b37274: fmla v31.4s, v30.4s, v27.4s
0x234b37278: bit v29.16b, v5.16b, v7.16b
0x234b3727c: bsl v7.16b, v20.16b, v12.16b
0x234b37280: frecpe v30.4s, v7.4s
0x234b37284: fmul v29.4s, v30.4s, v29.4s
0x234b37288: mov v12.16b, v15.16b
0x234b3728c: fmul v4.4s, v9.4s, v4.4s
0x234b37290: fmls v12.4s, v7.4s, v30.4s
0x234b37294: fmla v29.4s, v29.4s, v12.4s
0x234b37298: fmul v7.4s, v12.4s, v12.4s
0x234b3729c: fmla v29.4s, v29.4s, v7.4s
0x234b372a0: fmul v7.4s, v7.4s, v7.4s
0x234b372a4: fmul v27.4s, v27.4s, v31.4s
0x234b372a8: fmla v29.4s, v29.4s, v7.4s
0x234b372ac: fcmeq v7.4s, v30.4s, #0.0
0x234b372b0: bsl v7.16b, v30.16b, v29.16b
0x234b372b4: bif v7.16b, v20.16b, v23.16b
0x234b372b8: fmul v20.4s, v7.4s, v7.4s
0x234b372bc: fmla v6.4s, v4.4s, v6.4s
0x234b372c0: fmla v3.4s, v24.4s, v20.4s
0x234b372c4: mov v23.16b, v1.16b
0x234b372c8: mov v12.16b, v1.16b
0x234b372cc: fmla v23.4s, v3.4s, v20.4s
0x234b372d0: mov v4.16b, v18.16b
0x234b372d4: mov v3.16b, v18.16b
0x234b372d8: fmla v22.4s, v27.4s, v22.4s
0x234b372dc: fmla v4.4s, v23.4s, v20.4s
0x234b372e0: fmul v4.4s, v20.4s, v4.4s
0x234b372e4: movi v29.4s, #0x80, lsl #24
0x234b372e8: and v19.16b, v19.16b, v29.16b
0x234b372ec: fcmlt v20.4s, v0.4s, #0.0
0x234b372f0: fcmlt v23.4s, v26.4s, #0.0
0x234b372f4: fmla v7.4s, v4.4s, v7.4s
0x234b372f8: mov v27.16b, v20.16b
0x234b372fc: bsl v27.16b, v16.16b, v29.16b
0x234b37300: fneg v4.4s, v27.4s
0x234b37304: bif v4.16b, v27.16b, v23.16b
0x234b37308: fcmeq v20.4s, v26.4s, #0.0
0x234b3730c: mov v24.16b, v20.16b
0x234b37310: bsl v24.16b, v29.16b, v8.16b
0x234b37314: ldr q0, [sp, #0x100]
0x234b37318: fadd v6.4s, v0.4s, v6.4s
0x234b3731c: fneg v29.4s, v24.4s
0x234b37320: mov v0.16b, v23.16b
0x234b37324: bsl v0.16b, v29.16b, v24.16b
0x234b37328: str q0, [sp, #0x100]
0x234b3732c: ldur q0, [x29, #-0xd0]
0x234b37330: frecpe v23.4s, v0.4s
0x234b37334: frecps v24.4s, v0.4s, v23.4s
0x234b37338: fmul v23.4s, v23.4s, v24.4s
0x234b3733c: ldr q1, [sp, #0xc0]
0x234b37340: fadd v1.4s, v1.4s, v22.4s
0x234b37344: frecps v22.4s, v0.4s, v23.4s
0x234b37348: fmul v23.4s, v14.4s, v23.4s
0x234b3734c: mov v29.16b, v14.16b
0x234b37350: fmul v0.4s, v22.4s, v23.4s
0x234b37354: str q0, [sp, #0xc0]
0x234b37358: fabs v13.4s, v0.4s
0x234b3735c: fcmgt v14.4s, v13.4s, v28.4s
0x234b37360: fadd v7.4s, v25.4s, v7.4s
0x234b37364: fadd v22.4s, v13.4s, v5.4s
0x234b37368: fadd v23.4s, v13.4s, v15.4s
0x234b3736c: bit v22.16b, v5.16b, v14.16b
0x234b37370: bit v23.16b, v13.16b, v14.16b
0x234b37374: frecpe v24.4s, v23.4s
0x234b37378: ldr q0, [sp, #0xa0]
0x234b3737c: orr v6.16b, v0.16b, v6.16b
0x234b37380: fmul v22.4s, v24.4s, v22.4s
0x234b37384: mov v25.16b, v15.16b
0x234b37388: fmls v25.4s, v23.4s, v24.4s
0x234b3738c: fmla v22.4s, v22.4s, v25.4s
0x234b37390: fmul v23.4s, v25.4s, v25.4s
0x234b37394: orr v21.16b, v21.16b, v1.16b
0x234b37398: fmla v22.4s, v22.4s, v23.4s
0x234b3739c: fmul v1.4s, v23.4s, v23.4s
0x234b373a0: fmla v22.4s, v22.4s, v1.4s
0x234b373a4: fcmeq v1.4s, v24.4s, #0.0
0x234b373a8: mov v0.16b, v1.16b
0x234b373ac: bsl v0.16b, v24.16b, v22.16b
0x234b373b0: str q0, [sp, #0x80]
0x234b373b4: orr v1.16b, v19.16b, v7.16b
0x234b373b8: ldp q2, q18, [x21, #0x40]
0x234b373bc: ldp q24, q16, [x22, #0x40]
0x234b373c0: str q16, [sp, #0x50]
0x234b373c4: ldr q0, [sp, #0xe0]
0x234b373c8: fadd v6.4s, v0.4s, v6.4s
0x234b373cc: ldp q31, q0, [x21, #0x60]
0x234b373d0: str q0, [sp, #0xe0]
0x234b373d4: str q2, [sp, #0x70]
0x234b373d8: frecpe v7.4s, v2.4s
0x234b373dc: frecps v19.4s, v2.4s, v7.4s
0x234b373e0: fmul v7.4s, v7.4s, v19.4s
0x234b373e4: frecps v19.4s, v2.4s, v7.4s
0x234b373e8: fadd v22.4s, v11.4s, v21.4s
0x234b373ec: fmul v0.4s, v24.4s, v7.4s
0x234b373f0: fmul v0.4s, v19.4s, v0.4s
0x234b373f4: str q0, [sp, #0x90]
0x234b373f8: fabs v26.4s, v0.4s
0x234b373fc: fcmgt v25.4s, v26.4s, v28.4s
0x234b37400: fadd v7.4s, v26.4s, v15.4s
0x234b37404: ldr q0, [sp, #0xd0]
0x234b37408: ldr q2, [sp, #0xb0]
0x234b3740c: bif v0.16b, v6.16b, v2.16b
0x234b37410: str q0, [sp, #0xd0]
0x234b37414: mov v6.16b, v25.16b
0x234b37418: bsl v6.16b, v26.16b, v7.16b
0x234b3741c: frecpe v7.4s, v6.4s
0x234b37420: mov v19.16b, v15.16b
0x234b37424: fmls v19.4s, v6.4s, v7.4s
0x234b37428: fadd v6.4s, v26.4s, v5.4s
0x234b3742c: bit v6.16b, v5.16b, v25.16b
0x234b37430: fmul v6.4s, v7.4s, v6.4s
0x234b37434: fmla v6.4s, v6.4s, v19.4s
0x234b37438: fmul v19.4s, v19.4s, v19.4s
0x234b3743c: fmla v6.4s, v6.4s, v19.4s
0x234b37440: fmul v19.4s, v19.4s, v19.4s
0x234b37444: fadd v1.4s, v4.4s, v1.4s
0x234b37448: fmla v6.4s, v6.4s, v19.4s
0x234b3744c: fcmeq v4.4s, v7.4s, #0.0
0x234b37450: mov v21.16b, v4.16b
0x234b37454: bsl v21.16b, v7.16b, v6.16b
0x234b37458: frecpe v4.4s, v18.4s
0x234b3745c: frecps v6.4s, v18.4s, v4.4s
0x234b37460: mov v0.16b, v17.16b
0x234b37464: bsl v0.16b, v10.16b, v22.16b
0x234b37468: str q0, [sp, #0xa0]
0x234b3746c: fmul v4.4s, v4.4s, v6.4s
0x234b37470: frecps v6.4s, v18.4s, v4.4s
0x234b37474: fmul v4.4s, v16.4s, v4.4s
0x234b37478: fmul v0.4s, v6.4s, v4.4s
0x234b3747c: stp q18, q0, [sp, #0x30]
0x234b37480: fabs v9.4s, v0.4s
0x234b37484: mov v0.16b, v20.16b
0x234b37488: bsl v0.16b, v27.16b, v1.16b
0x234b3748c: str q0, [sp, #0xb0]
0x234b37490: fcmgt v22.4s, v9.4s, v28.4s
0x234b37494: fadd v1.4s, v9.4s, v5.4s
0x234b37498: fadd v4.4s, v9.4s, v15.4s
0x234b3749c: bit v1.16b, v5.16b, v22.16b
0x234b374a0: bit v4.16b, v9.16b, v22.16b
0x234b374a4: frecpe v6.4s, v4.4s
0x234b374a8: fmul v1.4s, v6.4s, v1.4s
0x234b374ac: mov v7.16b, v15.16b
0x234b374b0: fmls v7.4s, v4.4s, v6.4s
0x234b374b4: fmla v1.4s, v1.4s, v7.4s
0x234b374b8: fmul v4.4s, v7.4s, v7.4s
0x234b374bc: fmla v1.4s, v1.4s, v4.4s
0x234b374c0: fmul v4.4s, v4.4s, v4.4s
0x234b374c4: fmla v1.4s, v1.4s, v4.4s
0x234b374c8: fcmeq v4.4s, v6.4s, #0.0
0x234b374cc: bsl v4.16b, v6.16b, v1.16b
0x234b374d0: frecpe v1.4s, v31.4s
0x234b374d4: frecps v6.4s, v31.4s, v1.4s
0x234b374d8: fmul v1.4s, v1.4s, v6.4s
0x234b374dc: ldp q23, q0, [x22, #0x60]
0x234b374e0: str q0, [sp, #0x60]
0x234b374e4: frecps v6.4s, v31.4s, v1.4s
0x234b374e8: fmul v1.4s, v23.4s, v1.4s
0x234b374ec: fmul v27.4s, v6.4s, v1.4s
0x234b374f0: fabs v7.4s, v27.4s
0x234b374f4: fcmgt v6.4s, v7.4s, v28.4s
0x234b374f8: fadd v19.4s, v7.4s, v15.4s
0x234b374fc: bit v19.16b, v7.16b, v6.16b
0x234b37500: mov v20.16b, v15.16b
0x234b37504: frecpe v16.4s, v19.4s
0x234b37508: fmls v20.4s, v19.4s, v16.4s
0x234b3750c: fadd v19.4s, v7.4s, v5.4s
0x234b37510: bit v19.16b, v5.16b, v6.16b
0x234b37514: fmul v19.4s, v16.4s, v19.4s
0x234b37518: fmla v19.4s, v19.4s, v20.4s
0x234b3751c: fmul v20.4s, v20.4s, v20.4s
0x234b37520: fmla v19.4s, v19.4s, v20.4s
0x234b37524: fmul v20.4s, v20.4s, v20.4s
0x234b37528: fmla v19.4s, v19.4s, v20.4s
0x234b3752c: fcmeq v20.4s, v16.4s, #0.0
0x234b37530: mov v30.16b, v20.16b
0x234b37534: bsl v30.16b, v16.16b, v19.16b
0x234b37538: ldp q11, q18, [x29, #-0xc0]
0x234b3753c: fcmgt v16.4s, v13.4s, v18.4s
0x234b37540: ldr q0, [sp, #0x80]
0x234b37544: mov v17.16b, v16.16b
0x234b37548: bsl v17.16b, v0.16b, v13.16b
0x234b3754c: ldp q28, q1, [x29, #-0x100]
0x234b37550: and v16.16b, v1.16b, v16.16b
0x234b37554: bit v16.16b, v8.16b, v14.16b
0x234b37558: fmul v20.4s, v17.4s, v17.4s
0x234b3755c: mov v13.16b, v28.16b
0x234b37560: fmla v13.4s, v11.4s, v20.4s
0x234b37564: mov v2.16b, v12.16b
0x234b37568: mov v14.16b, v12.16b
0x234b3756c: fmla v14.4s, v13.4s, v20.4s
0x234b37570: mov v12.16b, v3.16b
0x234b37574: mov v13.16b, v3.16b
0x234b37578: fmla v13.4s, v14.4s, v20.4s
0x234b3757c: fmul v20.4s, v20.4s, v13.4s
0x234b37580: fmla v17.4s, v20.4s, v17.4s
0x234b37584: fcmlt v0.4s, v29.4s, #0.0
0x234b37588: fcmeq v13.4s, v29.4s, #0.0
0x234b3758c: movi v3.4s, #0x80, lsl #24
0x234b37590: mov v20.16b, v13.16b
0x234b37594: bsl v20.16b, v3.16b, v8.16b
0x234b37598: mov v29.16b, v8.16b
0x234b3759c: fneg v14.4s, v20.4s
0x234b375a0: bit v20.16b, v14.16b, v0.16b
0x234b375a4: str q20, [sp, #0x80]
0x234b375a8: ldur q20, [x29, #-0xd0]
0x234b375ac: fcmlt v14.4s, v20.4s, #0.0
0x234b375b0: ldr q10, [sp, #0x160]
0x234b375b4: bsl v14.16b, v10.16b, v3.16b
0x234b375b8: fneg v8.4s, v14.4s
0x234b375bc: mov v19.16b, v0.16b
0x234b375c0: bsl v19.16b, v8.16b, v14.16b
0x234b375c4: fcmgt v8.4s, v26.4s, v18.4s
0x234b375c8: mov v3.16b, v18.16b
0x234b375cc: bif v21.16b, v26.16b, v8.16b
0x234b375d0: fadd v16.4s, v16.4s, v17.4s
0x234b375d4: mov v18.16b, v1.16b
0x234b375d8: and v17.16b, v1.16b, v8.16b
0x234b375dc: bit v17.16b, v29.16b, v25.16b
0x234b375e0: fmul v25.4s, v21.4s, v21.4s
0x234b375e4: mov v26.16b, v28.16b
0x234b375e8: fmla v26.4s, v11.4s, v25.4s
0x234b375ec: mov v0.16b, v11.16b
0x234b375f0: mov v8.16b, v2.16b
0x234b375f4: fmla v8.4s, v26.4s, v25.4s
0x234b375f8: mov v26.16b, v12.16b
0x234b375fc: fmla v26.4s, v8.4s, v25.4s
0x234b37600: fmul v25.4s, v25.4s, v26.4s
0x234b37604: movi v1.4s, #0x80, lsl #24
0x234b37608: ldr q26, [sp, #0xc0]
0x234b3760c: and v26.16b, v26.16b, v1.16b
0x234b37610: orr v16.16b, v26.16b, v16.16b
0x234b37614: fmla v21.4s, v25.4s, v21.4s
0x234b37618: fadd v17.4s, v17.4s, v21.4s
0x234b3761c: fcmlt v25.4s, v24.4s, #0.0
0x234b37620: fcmeq v24.4s, v24.4s, #0.0
0x234b37624: mov v21.16b, v24.16b
0x234b37628: bsl v21.16b, v1.16b, v29.16b
0x234b3762c: fneg v26.4s, v21.4s
0x234b37630: mov v5.16b, v25.16b
0x234b37634: bsl v5.16b, v26.16b, v21.16b
0x234b37638: str q5, [sp, #0xc0]
0x234b3763c: ldr q5, [sp, #0x70]
0x234b37640: fcmlt v26.4s, v5.4s, #0.0
0x234b37644: bsl v26.16b, v10.16b, v1.16b
0x234b37648: fneg v8.4s, v26.4s
0x234b3764c: bsl v25.16b, v8.16b, v26.16b
0x234b37650: mov v11.16b, v3.16b
0x234b37654: fcmgt v8.4s, v9.4s, v3.4s
0x234b37658: bif v4.16b, v9.16b, v8.16b
0x234b3765c: and v8.16b, v18.16b, v8.16b
0x234b37660: mov v21.16b, v18.16b
0x234b37664: bsl v22.16b, v29.16b, v8.16b
0x234b37668: fmul v8.4s, v4.4s, v4.4s
0x234b3766c: mov v9.16b, v28.16b
0x234b37670: mov v3.16b, v0.16b
0x234b37674: fmla v9.4s, v0.4s, v8.4s
0x234b37678: mov v18.16b, v2.16b
0x234b3767c: fmla v18.4s, v9.4s, v8.4s
0x234b37680: mov v9.16b, v12.16b
0x234b37684: fmla v9.4s, v18.4s, v8.4s
0x234b37688: ldr q18, [sp, #0x90]
0x234b3768c: and v18.16b, v18.16b, v1.16b
0x234b37690: orr v17.16b, v18.16b, v17.16b
0x234b37694: fmul v18.4s, v8.4s, v9.4s
0x234b37698: fmla v4.4s, v18.4s, v4.4s
0x234b3769c: fadd v4.4s, v22.4s, v4.4s
0x234b376a0: movi v9.4s, #0x80, lsl #24
0x234b376a4: ldp q1, q0, [sp, #0x40]
0x234b376a8: and v18.16b, v1.16b, v9.16b
0x234b376ac: orr v4.16b, v18.16b, v4.16b
0x234b376b0: fadd v16.4s, v19.4s, v16.4s
0x234b376b4: fcmlt v18.4s, v0.4s, #0.0
0x234b376b8: fcmeq v22.4s, v0.4s, #0.0
0x234b376bc: mov v0.16b, v22.16b
0x234b376c0: bsl v0.16b, v9.16b, v29.16b
0x234b376c4: fneg v8.4s, v0.4s
0x234b376c8: bit v0.16b, v8.16b, v18.16b
0x234b376cc: str q0, [sp, #0x90]
0x234b376d0: ldr q0, [sp, #0x30]
0x234b376d4: fcmlt v8.4s, v0.4s, #0.0
0x234b376d8: bsl v8.16b, v10.16b, v9.16b
0x234b376dc: fneg v9.4s, v8.4s
0x234b376e0: bsl v18.16b, v9.16b, v8.16b
0x234b376e4: fcmgt v9.4s, v7.4s, v11.4s
0x234b376e8: bit v7.16b, v30.16b, v9.16b
0x234b376ec: fadd v17.4s, v25.4s, v17.4s
0x234b376f0: and v19.16b, v21.16b, v9.16b
0x234b376f4: bsl v6.16b, v29.16b, v19.16b
0x234b376f8: fmul v19.4s, v7.4s, v7.4s
0x234b376fc: mov v25.16b, v28.16b
0x234b37700: fmla v25.4s, v3.4s, v19.4s
0x234b37704: fmla v2.4s, v25.4s, v19.4s
0x234b37708: mov v25.16b, v12.16b
0x234b3770c: fmla v25.4s, v2.4s, v19.4s
0x234b37710: fmul v19.4s, v19.4s, v25.4s
0x234b37714: fmla v7.4s, v19.4s, v7.4s
0x234b37718: fadd v4.4s, v18.4s, v4.4s
0x234b3771c: fadd v6.4s, v6.4s, v7.4s
0x234b37720: movi v2.4s, #0x80, lsl #24
0x234b37724: and v1.16b, v27.16b, v2.16b
0x234b37728: orr v6.16b, v1.16b, v6.16b
0x234b3772c: fcmlt v7.4s, v23.4s, #0.0
0x234b37730: fcmeq v18.4s, v23.4s, #0.0
0x234b37734: bit v16.16b, v14.16b, v13.16b
0x234b37738: mov v1.16b, v18.16b
0x234b3773c: bsl v1.16b, v2.16b, v29.16b
0x234b37740: fneg v19.4s, v1.4s
0x234b37744: bit v1.16b, v19.16b, v7.16b
0x234b37748: fcmlt v19.4s, v31.4s, #0.0
0x234b3774c: bsl v19.16b, v10.16b, v2.16b
0x234b37750: movi v14.4s, #0x80, lsl #24
0x234b37754: bit v17.16b, v26.16b, v24.16b
0x234b37758: fneg v23.4s, v19.4s
0x234b3775c: bsl v7.16b, v23.16b, v19.16b
0x234b37760: fadd v6.4s, v7.4s, v6.4s
0x234b37764: ldp q7, q30, [sp, #0x130]
0x234b37768: fcmeq v7.4s, v7.4s, #0.0
0x234b3776c: ldr q23, [sp, #0x110]
0x234b37770: fcmeq v23.4s, v23.4s, #0.0
0x234b37774: bit v4.16b, v8.16b, v22.16b
0x234b37778: ldp q11, q22, [sp, #0xe0]
0x234b3777c: fcmeq v22.4s, v22.4s, #0.0
0x234b37780: fcmeq v24.4s, v20.4s, #0.0
0x234b37784: fcmeq v25.4s, v5.4s, #0.0
0x234b37788: fcmeq v26.4s, v0.4s, #0.0
0x234b3778c: fcmeq v3.4s, v31.4s, #0.0
0x234b37790: bit v6.16b, v19.16b, v18.16b
0x234b37794: frecpe v18.4s, v11.4s
0x234b37798: frecps v19.4s, v11.4s, v18.4s
0x234b3779c: fmul v18.4s, v18.4s, v19.4s
0x234b377a0: frecps v19.4s, v11.4s, v18.4s
0x234b377a4: ldr q13, [sp, #0x60]
0x234b377a8: fmul v18.4s, v13.4s, v18.4s
0x234b377ac: ldr q20, [sp, #0xd0]
0x234b377b0: bsl v7.16b, v30.16b, v20.16b
0x234b377b4: fmul v18.4s, v19.4s, v18.4s
0x234b377b8: fabs v19.4s, v18.4s
0x234b377bc: ldur q20, [x29, #-0xe0]
0x234b377c0: fcmgt v30.4s, v19.4s, v20.4s
0x234b377c4: fadd v31.4s, v19.4s, v15.4s
0x234b377c8: bit v31.16b, v19.16b, v30.16b
0x234b377cc: ldr q27, [sp, #0x120]
0x234b377d0: ldp q2, q20, [sp, #0xa0]
0x234b377d4: bsl v23.16b, v27.16b, v2.16b
0x234b377d8: frecpe v8.4s, v31.4s
0x234b377dc: fmls v15.4s, v31.4s, v8.4s
0x234b377e0: ldr q0, [sp, #0x150]
0x234b377e4: fadd v31.4s, v19.4s, v0.4s
0x234b377e8: bit v31.16b, v0.16b, v30.16b
0x234b377ec: ldr q5, [sp, #0x100]
0x234b377f0: bsl v22.16b, v5.16b, v20.16b
0x234b377f4: fmul v27.4s, v8.4s, v31.4s
0x234b377f8: fmla v27.4s, v27.4s, v15.4s
0x234b377fc: fmul v31.4s, v15.4s, v15.4s
0x234b37800: fmla v27.4s, v27.4s, v31.4s
0x234b37804: fmul v31.4s, v31.4s, v31.4s
0x234b37808: ldr q5, [sp, #0x80]
0x234b3780c: bit v16.16b, v5.16b, v24.16b
0x234b37810: fmla v27.4s, v27.4s, v31.4s
0x234b37814: fcmeq v20.4s, v8.4s, #0.0
0x234b37818: bsl v20.16b, v8.16b, v27.16b
0x234b3781c: ldur q0, [x29, #-0xb0]
0x234b37820: fcmgt v24.4s, v19.4s, v0.4s
0x234b37824: and v27.16b, v21.16b, v24.16b
0x234b37828: ldr q0, [sp, #0xc0]
0x234b3782c: bit v17.16b, v0.16b, v25.16b
0x234b37830: mov v21.16b, v30.16b
0x234b37834: bsl v21.16b, v29.16b, v27.16b
0x234b37838: bit v19.16b, v20.16b, v24.16b
0x234b3783c: fmul v20.4s, v19.4s, v19.4s
0x234b37840: ldur q0, [x29, #-0xc0]
0x234b37844: fmla v28.4s, v0.4s, v20.4s
0x234b37848: ldr q0, [sp, #0x90]
0x234b3784c: bif v0.16b, v4.16b, v26.16b
0x234b37850: ldr q4, [sp, #0x170]
0x234b37854: fmla v4.4s, v28.4s, v20.4s
0x234b37858: fmla v12.4s, v4.4s, v20.4s
0x234b3785c: fmul v4.4s, v20.4s, v12.4s
0x234b37860: bif v1.16b, v6.16b, v3.16b
0x234b37864: fmla v19.4s, v4.4s, v19.4s
0x234b37868: fadd v4.4s, v21.4s, v19.4s
0x234b3786c: and v6.16b, v18.16b, v14.16b
0x234b37870: orr v4.16b, v6.16b, v4.16b
0x234b37874: stp q7, q23, [x26]
0x234b37878: fcmlt v6.4s, v11.4s, #0.0
0x234b3787c: fcmlt v7.4s, v13.4s, #0.0
0x234b37880: bsl v6.16b, v10.16b, v14.16b
0x234b37884: fneg v18.4s, v6.4s
0x234b37888: stp q22, q16, [x26, #0x20]
0x234b3788c: mov v16.16b, v7.16b
0x234b37890: bsl v16.16b, v18.16b, v6.16b
0x234b37894: fadd v4.4s, v16.4s, v4.4s
0x234b37898: fcmeq v16.4s, v13.4s, #0.0
0x234b3789c: bit v4.16b, v6.16b, v16.16b
0x234b378a0: mov v6.16b, v16.16b
0x234b378a4: bsl v6.16b, v14.16b, v29.16b
0x234b378a8: stp q17, q0, [x26, #0x40]
0x234b378ac: fneg v0.4s, v6.4s
0x234b378b0: bif v0.16b, v6.16b, v7.16b
0x234b378b4: fcmeq v6.4s, v11.4s, #0.0
0x234b378b8: bif v0.16b, v4.16b, v6.16b
0x234b378bc: stp q1, q0, [x26, #0x60]
0x234b378c0: add x21, x21, #0x80
0x234b378c4: add x22, x22, #0x80
0x234b378c8: add x26, x26, #0x80
0x234b378cc: sub x23, x23, #0x20
0x234b378d0: cmp x23, #0x1f
0x234b378d4: b.hi #0x234b37040
0x234b378d8: cmp x23, #0x10
0x234b378dc: b.lo #0x234b37cd4
0x234b378e0: mov w8, #0x13cd
0x234b378e4: movk w8, #0x3ed4, lsl #16
0x234b378e8: dup v0.4s, w8
0x234b378ec: mov w8, #0x827a
0x234b378f0: movk w8, #0x401a, lsl #16
0x234b378f4: dup v1.4s, w8
0x234b378f8: fmov v2.4s, #-1.00000000
0x234b378fc: fmov v3.4s, #1.00000000
0x234b37900: mov w8, #0xfdb
0x234b37904: movk w8, #0x3f49, lsl #16
0x234b37908: dup v4.4s, w8
0x234b3790c: mov w8, #0xfdb
0x234b37910: movk w8, #0x3fc9, lsl #16
0x234b37914: dup v5.4s, w8
0x234b37918: mov w8, #0xf0d1
0x234b3791c: movk w8, #0x3da4, lsl #16
0x234b37920: dup v6.4s, w8
0x234b37924: mov w8, #0x1b85
0x234b37928: movk w8, #0xbe0e, lsl #16
0x234b3792c: dup v7.4s, w8
0x234b37930: mov w8, #0x925f
0x234b37934: movk w8, #0x3e4c, lsl #16
0x234b37938: dup v16.4s, w8
0x234b3793c: mov w8, #0xaa2a
0x234b37940: movk w8, #0xbeaa, lsl #16
0x234b37944: dup v17.4s, w8
0x234b37948: movi v18.4s, #0x80, lsl #24
0x234b3794c: mov w8, #0xfdb
0x234b37950: movk w8, #0x4049, lsl #16
0x234b37954: dup v19.4s, w8
0x234b37958: ldp q23, q20, [x21]
0x234b3795c: ldp q26, q21, [x22]
0x234b37960: frecpe v22.4s, v23.4s
0x234b37964: frecps v24.4s, v23.4s, v22.4s
0x234b37968: fmul v22.4s, v22.4s, v24.4s
0x234b3796c: frecps v24.4s, v23.4s, v22.4s
0x234b37970: fmul v22.4s, v26.4s, v22.4s
0x234b37974: fmul v24.4s, v24.4s, v22.4s
0x234b37978: fabs v27.4s, v24.4s
0x234b3797c: fcmgt v28.4s, v27.4s, v0.4s
0x234b37980: fcmgt v25.4s, v27.4s, v1.4s
0x234b37984: fadd v29.4s, v27.4s, v2.4s
0x234b37988: fadd v30.4s, v27.4s, v3.4s
0x234b3798c: and v22.16b, v4.16b, v28.16b
0x234b37990: bit v22.16b, v5.16b, v25.16b
0x234b37994: bit v29.16b, v2.16b, v25.16b
0x234b37998: bsl v25.16b, v27.16b, v30.16b
0x234b3799c: frecpe v30.4s, v25.4s
0x234b379a0: fmul v29.4s, v30.4s, v29.4s
0x234b379a4: mov v31.16b, v3.16b
0x234b379a8: fmls v31.4s, v25.4s, v30.4s
0x234b379ac: fmla v29.4s, v29.4s, v31.4s
0x234b379b0: fmul v25.4s, v31.4s, v31.4s
0x234b379b4: fmul v31.4s, v25.4s, v25.4s
0x234b379b8: fmla v29.4s, v29.4s, v25.4s
0x234b379bc: fmla v29.4s, v29.4s, v31.4s
0x234b379c0: fcmeq v25.4s, v30.4s, #0.0
0x234b379c4: mov v31.16b, v7.16b
0x234b379c8: mov v8.16b, v16.16b
0x234b379cc: mov v9.16b, v17.16b
0x234b379d0: bit v29.16b, v30.16b, v25.16b
0x234b379d4: fcmlt v25.4s, v23.4s, #0.0
0x234b379d8: fcmlt v30.4s, v26.4s, #0.0
0x234b379dc: bsl v25.16b, v19.16b, v18.16b
0x234b379e0: fneg v10.4s, v25.4s
0x234b379e4: bif v10.16b, v25.16b, v30.16b
0x234b379e8: bsl v28.16b, v29.16b, v27.16b
0x234b379ec: fcmeq v27.4s, v26.4s, #0.0
0x234b379f0: mov v26.16b, v27.16b
0x234b379f4: bsl v26.16b, v18.16b, v5.16b
0x234b379f8: fneg v29.4s, v26.4s
0x234b379fc: bit v26.16b, v29.16b, v30.16b
0x234b37a00: frecpe v29.4s, v20.4s
0x234b37a04: fmul v30.4s, v28.4s, v28.4s
0x234b37a08: frecps v11.4s, v20.4s, v29.4s
0x234b37a0c: fmul v29.4s, v29.4s, v11.4s
0x234b37a10: frecps v11.4s, v20.4s, v29.4s
0x234b37a14: fmul v29.4s, v21.4s, v29.4s
0x234b37a18: fmul v29.4s, v11.4s, v29.4s
0x234b37a1c: fmla v31.4s, v6.4s, v30.4s
0x234b37a20: fabs v11.4s, v29.4s
0x234b37a24: fcmgt v12.4s, v11.4s, v0.4s
0x234b37a28: fcmgt v13.4s, v11.4s, v1.4s
0x234b37a2c: fadd v14.4s, v11.4s, v2.4s
0x234b37a30: fadd v15.4s, v11.4s, v3.4s
0x234b37a34: fmla v8.4s, v31.4s, v30.4s
0x234b37a38: and v31.16b, v4.16b, v12.16b
0x234b37a3c: bit v31.16b, v5.16b, v13.16b
0x234b37a40: bit v14.16b, v2.16b, v13.16b
0x234b37a44: bsl v13.16b, v11.16b, v15.16b
0x234b37a48: frecpe v15.4s, v13.4s
0x234b37a4c: fmla v9.4s, v8.4s, v30.4s
0x234b37a50: mov v8.16b, v3.16b
0x234b37a54: fmls v8.4s, v13.4s, v15.4s
0x234b37a58: and v24.16b, v24.16b, v18.16b
0x234b37a5c: fcmeq v23.4s, v23.4s, #0.0
0x234b37a60: fmul v13.4s, v15.4s, v14.4s
0x234b37a64: fmul v30.4s, v30.4s, v9.4s
0x234b37a68: fmla v13.4s, v13.4s, v8.4s
0x234b37a6c: fmul v8.4s, v8.4s, v8.4s
0x234b37a70: fmla v13.4s, v13.4s, v8.4s
0x234b37a74: fmul v8.4s, v8.4s, v8.4s
0x234b37a78: fmla v13.4s, v13.4s, v8.4s
0x234b37a7c: fmla v28.4s, v30.4s, v28.4s
0x234b37a80: fcmeq v30.4s, v15.4s, #0.0
0x234b37a84: bsl v30.16b, v15.16b, v13.16b
0x234b37a88: bif v30.16b, v11.16b, v12.16b
0x234b37a8c: fmul v8.4s, v30.4s, v30.4s
0x234b37a90: mov v9.16b, v7.16b
0x234b37a94: fadd v22.4s, v22.4s, v28.4s
0x234b37a98: fmla v9.4s, v6.4s, v8.4s
0x234b37a9c: mov v28.16b, v16.16b
0x234b37aa0: fmla v28.4s, v9.4s, v8.4s
0x234b37aa4: mov v9.16b, v17.16b
0x234b37aa8: fmla v9.4s, v28.4s, v8.4s
0x234b37aac: orr v22.16b, v24.16b, v22.16b
0x234b37ab0: fmul v24.4s, v8.4s, v9.4s
0x234b37ab4: fmla v30.4s, v24.4s, v30.4s
0x234b37ab8: fadd v24.4s, v31.4s, v30.4s
0x234b37abc: and v28.16b, v29.16b, v18.16b
0x234b37ac0: orr v24.16b, v28.16b, v24.16b
0x234b37ac4: fadd v22.4s, v10.4s, v22.4s
0x234b37ac8: fcmlt v28.4s, v20.4s, #0.0
0x234b37acc: fcmlt v29.4s, v21.4s, #0.0
0x234b37ad0: bsl v28.16b, v19.16b, v18.16b
0x234b37ad4: fneg v30.4s, v28.4s
0x234b37ad8: bif v30.16b, v28.16b, v29.16b
0x234b37adc: bit v22.16b, v25.16b, v27.16b
0x234b37ae0: fadd v24.4s, v30.4s, v24.4s
0x234b37ae4: fcmeq v21.4s, v21.4s, #0.0
0x234b37ae8: bit v24.16b, v28.16b, v21.16b
0x234b37aec: bsl v21.16b, v18.16b, v5.16b
0x234b37af0: fneg v25.4s, v21.4s
0x234b37af4: bit v22.16b, v26.16b, v23.16b
0x234b37af8: bit v21.16b, v25.16b, v29.16b
0x234b37afc: fcmeq v20.4s, v20.4s, #0.0
0x234b37b00: bsl v20.16b, v21.16b, v24.16b
0x234b37b04: stp q22, q20, [x26]
0x234b37b08: ldp q22, q20, [x21, #0x20]
0x234b37b0c: ldp q24, q21, [x22, #0x20]
0x234b37b10: frecpe v23.4s, v22.4s
0x234b37b14: frecps v25.4s, v22.4s, v23.4s
0x234b37b18: fmul v23.4s, v23.4s, v25.4s
0x234b37b1c: frecps v25.4s, v22.4s, v23.4s
0x234b37b20: fmul v23.4s, v24.4s, v23.4s
0x234b37b24: fmul v23.4s, v25.4s, v23.4s
0x234b37b28: fabs v26.4s, v23.4s
0x234b37b2c: fcmgt v27.4s, v26.4s, v0.4s
0x234b37b30: fcmgt v25.4s, v26.4s, v1.4s
0x234b37b34: fadd v28.4s, v26.4s, v2.4s
0x234b37b38: fadd v29.4s, v26.4s, v3.4s
0x234b37b3c: and v30.16b, v4.16b, v27.16b
0x234b37b40: bit v28.16b, v2.16b, v25.16b
0x234b37b44: bit v29.16b, v26.16b, v25.16b
0x234b37b48: bsl v25.16b, v5.16b, v30.16b
0x234b37b4c: frecpe v30.4s, v29.4s
0x234b37b50: fmul v28.4s, v30.4s, v28.4s
0x234b37b54: mov v31.16b, v3.16b
0x234b37b58: fmls v31.4s, v29.4s, v30.4s
0x234b37b5c: fmla v28.4s, v28.4s, v31.4s
0x234b37b60: fmul v29.4s, v31.4s, v31.4s
0x234b37b64: fmla v28.4s, v28.4s, v29.4s
0x234b37b68: fmul v29.4s, v29.4s, v29.4s
0x234b37b6c: fmla v28.4s, v28.4s, v29.4s
0x234b37b70: fcmeq v29.4s, v30.4s, #0.0
0x234b37b74: mov v31.16b, v7.16b
0x234b37b78: bit v28.16b, v30.16b, v29.16b
0x234b37b7c: mov v29.16b, v16.16b
0x234b37b80: mov v30.16b, v17.16b
0x234b37b84: fcmlt v8.4s, v22.4s, #0.0
0x234b37b88: frecpe v9.4s, v20.4s
0x234b37b8c: frecps v10.4s, v20.4s, v9.4s
0x234b37b90: bit v26.16b, v28.16b, v27.16b
0x234b37b94: fmul v27.4s, v9.4s, v10.4s
0x234b37b98: frecps v28.4s, v20.4s, v27.4s
0x234b37b9c: fmul v27.4s, v21.4s, v27.4s
0x234b37ba0: fmul v27.4s, v28.4s, v27.4s
0x234b37ba4: fabs v28.4s, v27.4s
0x234b37ba8: fmul v9.4s, v26.4s, v26.4s
0x234b37bac: fcmgt v10.4s, v28.4s, v1.4s
0x234b37bb0: fadd v11.4s, v28.4s, v3.4s
0x234b37bb4: bit v11.16b, v28.16b, v10.16b
0x234b37bb8: frecpe v12.4s, v11.4s
0x234b37bbc: mov v13.16b, v3.16b
0x234b37bc0: fmla v31.4s, v6.4s, v9.4s
0x234b37bc4: fmls v13.4s, v11.4s, v12.4s
0x234b37bc8: fadd v11.4s, v28.4s, v2.4s
0x234b37bcc: bit v11.16b, v2.16b, v10.16b
0x234b37bd0: fmul v11.4s, v12.4s, v11.4s
0x234b37bd4: fmla v11.4s, v11.4s, v13.4s
0x234b37bd8: fmla v29.4s, v31.4s, v9.4s
0x234b37bdc: fmul v31.4s, v13.4s, v13.4s
0x234b37be0: fmla v11.4s, v11.4s, v31.4s
0x234b37be4: fmul v31.4s, v31.4s, v31.4s
0x234b37be8: fmla v11.4s, v11.4s, v31.4s
0x234b37bec: fcmeq v31.4s, v12.4s, #0.0
0x234b37bf0: fmla v30.4s, v29.4s, v9.4s
0x234b37bf4: mov v29.16b, v31.16b
0x234b37bf8: bsl v29.16b, v12.16b, v11.16b
0x234b37bfc: fcmlt v31.4s, v24.4s, #0.0
0x234b37c00: bsl v8.16b, v19.16b, v18.16b
0x234b37c04: fcmeq v24.4s, v24.4s, #0.0
0x234b37c08: mov v11.16b, v24.16b
0x234b37c0c: bsl v11.16b, v18.16b, v5.16b
0x234b37c10: fmul v30.4s, v9.4s, v30.4s
0x234b37c14: fneg v9.4s, v11.4s
0x234b37c18: bif v9.16b, v11.16b, v31.16b
0x234b37c1c: fneg v11.4s, v8.4s
0x234b37c20: bsl v31.16b, v11.16b, v8.16b
0x234b37c24: fcmgt v11.4s, v28.4s, v0.4s
0x234b37c28: fmla v26.4s, v30.4s, v26.4s
0x234b37c2c: and v30.16b, v4.16b, v11.16b
0x234b37c30: bit v30.16b, v5.16b, v10.16b
0x234b37c34: and v23.16b, v23.16b, v18.16b
0x234b37c38: bit v28.16b, v29.16b, v11.16b
0x234b37c3c: fmul v29.4s, v28.4s, v28.4s
0x234b37c40: fadd v25.4s, v25.4s, v26.4s
0x234b37c44: mov v26.16b, v7.16b
0x234b37c48: fmla v26.4s, v6.4s, v29.4s
0x234b37c4c: mov v10.16b, v16.16b
0x234b37c50: fmla v10.4s, v26.4s, v29.4s
0x234b37c54: mov v26.16b, v17.16b
0x234b37c58: orr v23.16b, v23.16b, v25.16b
0x234b37c5c: fmla v26.4s, v10.4s, v29.4s
0x234b37c60: fmul v25.4s, v29.4s, v26.4s
0x234b37c64: fcmeq v22.4s, v22.4s, #0.0
0x234b37c68: fmla v28.4s, v25.4s, v28.4s
0x234b37c6c: fadd v25.4s, v30.4s, v28.4s
0x234b37c70: fadd v23.4s, v31.4s, v23.4s
0x234b37c74: and v26.16b, v27.16b, v18.16b
0x234b37c78: orr v25.16b, v26.16b, v25.16b
0x234b37c7c: fcmlt v26.4s, v20.4s, #0.0
0x234b37c80: fcmlt v27.4s, v21.4s, #0.0
0x234b37c84: bsl v26.16b, v19.16b, v18.16b
0x234b37c88: bit v23.16b, v8.16b, v24.16b
0x234b37c8c: fneg v24.4s, v26.4s
0x234b37c90: bif v24.16b, v26.16b, v27.16b
0x234b37c94: fadd v24.4s, v24.4s, v25.4s
0x234b37c98: fcmeq v21.4s, v21.4s, #0.0
0x234b37c9c: bit v24.16b, v26.16b, v21.16b
0x234b37ca0: bsl v22.16b, v9.16b, v23.16b
0x234b37ca4: bsl v21.16b, v18.16b, v5.16b
0x234b37ca8: fneg v23.4s, v21.4s
0x234b37cac: bit v21.16b, v23.16b, v27.16b
0x234b37cb0: fcmeq v20.4s, v20.4s, #0.0
0x234b37cb4: bsl v20.16b, v21.16b, v24.16b
0x234b37cb8: stp q22, q20, [x26, #0x20]
0x234b37cbc: add x21, x21, #0x40
0x234b37cc0: add x22, x22, #0x40
0x234b37cc4: add x26, x26, #0x40
0x234b37cc8: sub x23, x23, #0x10
0x234b37ccc: cmp x23, #0xf
0x234b37cd0: b.hi #0x234b37958
0x234b37cd4: cmp x23, #4
0x234b37cd8: b.lo #0x234b37e40
0x234b37cdc: mov w8, #0x13cd
0x234b37ce0: movk w8, #0x3ed4, lsl #16
0x234b37ce4: dup v0.4s, w8
0x234b37ce8: mov w8, #0x827a
0x234b37cec: movk w8, #0x401a, lsl #16
0x234b37cf0: dup v1.4s, w8
0x234b37cf4: fmov v2.4s, #-1.00000000
0x234b37cf8: fmov v3.4s, #1.00000000
0x234b37cfc: mov w8, #0xfdb
0x234b37d00: movk w8, #0x3f49, lsl #16
0x234b37d04: dup v4.4s, w8
0x234b37d08: mov w8, #0xfdb
0x234b37d0c: movk w8, #0x3fc9, lsl #16
0x234b37d10: dup v5.4s, w8
0x234b37d14: mov w8, #0xf0d1
0x234b37d18: movk w8, #0x3da4, lsl #16
0x234b37d1c: dup v6.4s, w8
0x234b37d20: mov w8, #0x1b85
0x234b37d24: movk w8, #0xbe0e, lsl #16
0x234b37d28: dup v7.4s, w8
0x234b37d2c: mov w8, #0x925f
0x234b37d30: movk w8, #0x3e4c, lsl #16
0x234b37d34: dup v16.4s, w8
0x234b37d38: mov w8, #0xaa2a
0x234b37d3c: movk w8, #0xbeaa, lsl #16
0x234b37d40: dup v17.4s, w8
0x234b37d44: movi v18.4s, #0x80, lsl #24
0x234b37d48: mov w8, #0xfdb
0x234b37d4c: movk w8, #0x4049, lsl #16
0x234b37d50: dup v19.4s, w8
0x234b37d54: ldr q20, [x21], #0x10
0x234b37d58: frecpe v21.4s, v20.4s
0x234b37d5c: frecps v22.4s, v20.4s, v21.4s
0x234b37d60: ldr q23, [x22], #0x10
0x234b37d64: fmul v21.4s, v21.4s, v22.4s
0x234b37d68: frecps v22.4s, v20.4s, v21.4s
0x234b37d6c: fmul v21.4s, v23.4s, v21.4s
0x234b37d70: fmul v21.4s, v22.4s, v21.4s
0x234b37d74: fabs v22.4s, v21.4s
0x234b37d78: fcmgt v24.4s, v22.4s, v1.4s
0x234b37d7c: fadd v25.4s, v22.4s, v2.4s
0x234b37d80: fadd v26.4s, v22.4s, v3.4s
0x234b37d84: bit v25.16b, v2.16b, v24.16b
0x234b37d88: bit v26.16b, v22.16b, v24.16b
0x234b37d8c: frecpe v27.4s, v26.4s
0x234b37d90: fmul v25.4s, v27.4s, v25.4s
0x234b37d94: mov v28.16b, v3.16b
0x234b37d98: fmls v28.4s, v26.4s, v27.4s
0x234b37d9c: fmla v25.4s, v25.4s, v28.4s
0x234b37da0: fmul v26.4s, v28.4s, v28.4s
0x234b37da4: fmla v25.4s, v25.4s, v26.4s
0x234b37da8: fcmgt v28.4s, v22.4s, v0.4s
0x234b37dac: fmul v26.4s, v26.4s, v26.4s
0x234b37db0: fmla v25.4s, v25.4s, v26.4s
0x234b37db4: fcmeq v26.4s, v27.4s, #0.0
0x234b37db8: bit v25.16b, v27.16b, v26.16b
0x234b37dbc: bit v22.16b, v25.16b, v28.16b
0x234b37dc0: and v25.16b, v4.16b, v28.16b
0x234b37dc4: fmul v26.4s, v22.4s, v22.4s
0x234b37dc8: mov v27.16b, v7.16b
0x234b37dcc: fmla v27.4s, v6.4s, v26.4s
0x234b37dd0: mov v28.16b, v16.16b
0x234b37dd4: fmla v28.4s, v27.4s, v26.4s
0x234b37dd8: bsl v24.16b, v5.16b, v25.16b
0x234b37ddc: mov v25.16b, v17.16b
0x234b37de0: fmla v25.4s, v28.4s, v26.4s
0x234b37de4: fmul v25.4s, v26.4s, v25.4s
0x234b37de8: fmla v22.4s, v25.4s, v22.4s
0x234b37dec: and v21.16b, v21.16b, v18.16b
0x234b37df0: fadd v22.4s, v24.4s, v22.4s
0x234b37df4: fcmlt v24.4s, v20.4s, #0.0
0x234b37df8: fcmlt v25.4s, v23.4s, #0.0
0x234b37dfc: bsl v24.16b, v19.16b, v18.16b
0x234b37e00: fneg v26.4s, v24.4s
0x234b37e04: bif v26.16b, v24.16b, v25.16b
0x234b37e08: orr v21.16b, v21.16b, v22.16b
0x234b37e0c: fadd v21.4s, v26.4s, v21.4s
0x234b37e10: fcmeq v22.4s, v23.4s, #0.0
0x234b37e14: mov v23.16b, v22.16b
0x234b37e18: bsl v23.16b, v18.16b, v5.16b
0x234b37e1c: fneg v26.4s, v23.4s
0x234b37e20: bit v23.16b, v26.16b, v25.16b
0x234b37e24: bit v21.16b, v24.16b, v22.16b
0x234b37e28: fcmeq v20.4s, v20.4s, #0.0
0x234b37e2c: bsl v20.16b, v23.16b, v21.16b
0x234b37e30: str q20, [x26], #0x10
0x234b37e34: sub x23, x23, #4
0x234b37e38: cmp x23, #3
0x234b37e3c: b.hi #0x234b37d54
0x234b37e40: cbz x23, #0x234b37e5c
0x234b37e44: ldr s1, [x21], #4
0x234b37e48: ldr s0, [x22], #4
0x234b37e4c: bl #0x236f3d3d0
0x234b37e50: str s0, [x26], #4
0x234b37e54: subs x23, x23, #1
0x234b37e58: b.ne #0x234b37e44
0x234b37e5c: add sp, sp, #0x1f0
0x234b37e60: ldp x29, x30, [sp, #0x90]
0x234b37e64: ldp x20, x19, [sp, #0x80]
0x234b37e68: ldp x22, x21, [sp, #0x70]
0x234b37e6c: ldp x24, x23, [sp, #0x60]
0x234b37e70: ldp x26, x25, [sp, #0x50]
0x234b37e74: ldp x28, x27, [sp, #0x40]
0x234b37e78: ldp d9, d8, [sp, #0x30]
0x234b37e7c: ldp d11, d10, [sp, #0x20]
0x234b37e80: ldp d13, d12, [sp, #0x10]
0x234b37e84: ldp d15, d14, [sp], #0xa0
0x234b37e88: retab 
