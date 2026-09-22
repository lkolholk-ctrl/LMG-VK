234b21c8c: ldr x8, [x0]
234b21c90: cbz x8, #0x234b21c9c
234b21c94: mov w0, #0
234b21c98: ret 
234b21c9c: pacibsp 
234b21ca0: sub sp, sp, #0xe0
234b21ca4: stp d15, d14, [sp, #0x60]
234b21ca8: stp d13, d12, [sp, #0x70]
234b21cac: stp d11, d10, [sp, #0x80]
234b21cb0: stp d9, d8, [sp, #0x90]
234b21cb4: stp x24, x23, [sp, #0xa0]
234b21cb8: stp x22, x21, [sp, #0xb0]
234b21cbc: stp x20, x19, [sp, #0xc0]
234b21cc0: stp x29, x30, [sp, #0xd0]
234b21cc4: add x29, sp, #0xd0
234b21cc8: mov x22, x0
234b21ccc: mov x20, x1
234b21cd0: lsr x21, x1, #4
234b21cd4: add x8, x21, x21, lsl #1
234b21cd8: lsl x1, x8, #5
234b21cdc: mov x2, #0xdbd2
234b21ce0: movk x2, #0x565e, lsl #16
234b21ce4: movk x2, #0x40, lsl #32
234b21ce8: movk x2, #0x100, lsl #48
234b21cec: mov x0, #0
234b21cf0: bl #0x236f3d4c0
234b21cf4: cbz x0, #0x234b21e98
234b21cf8: mov x19, x0
234b21cfc: str x0, [x22]
234b21d00: ucvtf d0, x20
234b21d04: adrp x8, #0x234bd1000
234b21d08: ldr d1, [x8, #0xa38]
234b21d0c: fdiv d8, d1, d0
234b21d10: cmp x20, #0x20
234b21d14: b.hs #0x234b21ea0
234b21d18: movi d0, #0000000000000000
234b21d1c: fmul d11, d8, d0
234b21d20: fadd d10, d8, d8
234b21d24: fmov d14, #3.00000000
234b21d28: fmul d9, d8, d14
234b21d2c: fmov d0, d11
234b21d30: bl #0x236f3d370
234b21d34: fmov d12, d0
234b21d38: fcvt s0, d1
234b21d3c: str q0, [sp, #0x40]
234b21d40: fmov d0, d8
234b21d44: bl #0x236f3d370
234b21d48: fmov d8, d0
234b21d4c: fcvt s0, d1
234b21d50: str q0, [sp, #0x30]
234b21d54: fmov d0, d10
234b21d58: bl #0x236f3d370
234b21d5c: fmov d13, d0
234b21d60: fcvt s0, d1
234b21d64: str q0, [sp, #0x50]
234b21d68: fmov d0, d9
234b21d6c: bl #0x236f3d370
234b21d70: ldp q2, q3, [sp, #0x30]
234b21d74: mov v3.s[1], v2.s[0]
234b21d78: fcvt s1, d1
234b21d7c: str q1, [sp, #0x40]
234b21d80: ldr q2, [sp, #0x50]
234b21d84: mov v3.s[2], v2.s[0]
234b21d88: mov v3.s[3], v1.s[0]
234b21d8c: mov v4.16b, v3.16b
234b21d90: fcvt s1, d12
234b21d94: fcvt s2, d8
234b21d98: fcvt s3, d13
234b21d9c: mov v1.s[1], v2.s[0]
234b21da0: mov v1.s[2], v3.s[0]
234b21da4: fcvt s0, d0
234b21da8: stp q3, q0, [sp, #0x20]
234b21dac: mov v1.s[3], v0.s[0]
234b21db0: stp q4, q1, [x19]
234b21db4: fadd d0, d11, d11
234b21db8: bl #0x236f3d370
234b21dbc: fmov d8, d0
234b21dc0: fcvt s0, d1
234b21dc4: str q0, [sp, #0x10]
234b21dc8: fadd d0, d10, d10
234b21dcc: bl #0x236f3d370
234b21dd0: fmov d12, d0
234b21dd4: fcvt s0, d1
234b21dd8: str q0, [sp]
234b21ddc: fadd d0, d9, d9
234b21de0: bl #0x236f3d370
234b21de4: fcvt s1, d1
234b21de8: ldr q2, [sp, #0x50]
234b21dec: ldr q3, [sp, #0x10]
234b21df0: mov v3.s[1], v2.s[0]
234b21df4: ldr q2, [sp]
234b21df8: mov v3.s[2], v2.s[0]
234b21dfc: mov v3.s[3], v1.s[0]
234b21e00: mov v4.16b, v3.16b
234b21e04: fcvt s1, d8
234b21e08: fcvt s2, d12
234b21e0c: ldr q3, [sp, #0x20]
234b21e10: mov v1.s[1], v3.s[0]
234b21e14: mov v1.s[2], v2.s[0]
234b21e18: fcvt s0, d0
234b21e1c: mov v1.s[3], v0.s[0]
234b21e20: stp q4, q1, [x19, #0x20]
234b21e24: fmul d0, d11, d14
234b21e28: bl #0x236f3d370
234b21e2c: fmov d8, d0
234b21e30: fcvt s0, d1
234b21e34: str q0, [sp, #0x50]
234b21e38: fmul d0, d10, d14
234b21e3c: bl #0x236f3d370
234b21e40: fmov d10, d0
234b21e44: fcvt s0, d1
234b21e48: str q0, [sp, #0x20]
234b21e4c: fmul d0, d9, d14
234b21e50: bl #0x236f3d370
234b21e54: mov w0, #0
234b21e58: fcvt s1, d1
234b21e5c: ldp q2, q3, [sp, #0x40]
234b21e60: mov v3.s[1], v2.s[0]
234b21e64: ldr q2, [sp, #0x20]
234b21e68: mov v3.s[2], v2.s[0]
234b21e6c: mov v3.s[3], v1.s[0]
234b21e70: mov v4.16b, v3.16b
234b21e74: fcvt s1, d8
234b21e78: fcvt s2, d10
234b21e7c: fcvt s0, d0
234b21e80: ldr q3, [sp, #0x30]
234b21e84: mov v1.s[1], v3.s[0]
234b21e88: mov v1.s[2], v2.s[0]
234b21e8c: mov v1.s[3], v0.s[0]
234b21e90: stp q4, q1, [x19, #0x40]
234b21e94: b #0x234b22454
234b21e98: mov w0, #1
234b21e9c: b #0x234b22454
234b21ea0: str d8, [sp, #0x20]
234b21ea4: mov x22, #0
234b21ea8: mov x20, #0
234b21eac: clz x8, x21
234b21eb0: add x21, x8, #1
234b21eb4: b #0x234b22098
234b21eb8: add x23, x19, #0x60
234b21ebc: lsl x8, x22, #2
234b21ec0: ucvtf d0, x8
234b21ec4: mov w8, #1
234b21ec8: bfi x8, x22, #2, #0x3e
234b21ecc: ucvtf d1, x8
234b21ed0: ldr d2, [sp, #0x20]
234b21ed4: fmul d12, d2, d0
234b21ed8: fmul d11, d2, d1
234b21edc: mov w8, #2
234b21ee0: bfi x8, x22, #2, #0x3e
234b21ee4: ucvtf d0, x8
234b21ee8: fmul d10, d2, d0
234b21eec: mov w8, #3
234b21ef0: bfi x8, x22, #2, #0x3e
234b21ef4: ucvtf d0, x8
234b21ef8: fmul d9, d2, d0
234b21efc: fmov d0, d12
234b21f00: bl #0x236f3d370
234b21f04: fmov d13, d0
234b21f08: fcvt s0, d1
234b21f0c: str q0, [sp, #0x50]
234b21f10: fmov d0, d11
234b21f14: bl #0x236f3d370
234b21f18: fmov d14, d0
234b21f1c: fcvt s0, d1
234b21f20: str q0, [sp, #0x40]
234b21f24: fmov d0, d10
234b21f28: bl #0x236f3d370
234b21f2c: fmov d15, d0
234b21f30: fcvt s0, d1
234b21f34: str q0, [sp, #0x30]
234b21f38: fmov d0, d9
234b21f3c: bl #0x236f3d370
234b21f40: ldp q3, q2, [sp, #0x40]
234b21f44: mov v2.s[1], v3.s[0]
234b21f48: fcvt s1, d1
234b21f4c: ldr q3, [sp, #0x30]
234b21f50: mov v2.s[2], v3.s[0]
234b21f54: mov v2.s[3], v1.s[0]
234b21f58: mov v4.16b, v2.16b
234b21f5c: fcvt s1, d13
234b21f60: fcvt s2, d14
234b21f64: fcvt s3, d15
234b21f68: mov v1.s[1], v2.s[0]
234b21f6c: mov v1.s[2], v3.s[0]
234b21f70: fcvt s0, d0
234b21f74: mov v1.s[3], v0.s[0]
234b21f78: stp q4, q1, [x19]
234b21f7c: fadd d0, d12, d12
234b21f80: bl #0x236f3d370
234b21f84: fmov d13, d0
234b21f88: fcvt s0, d1
234b21f8c: str q0, [sp, #0x50]
234b21f90: fadd d0, d11, d11
234b21f94: bl #0x236f3d370
234b21f98: fmov d14, d0
234b21f9c: fcvt s0, d1
234b21fa0: str q0, [sp, #0x40]
234b21fa4: fadd d0, d10, d10
234b21fa8: bl #0x236f3d370
234b21fac: fmov d15, d0
234b21fb0: fcvt s0, d1
234b21fb4: str q0, [sp, #0x30]
234b21fb8: fadd d0, d9, d9
234b21fbc: bl #0x236f3d370
234b21fc0: ldp q3, q2, [sp, #0x40]
234b21fc4: mov v2.s[1], v3.s[0]
234b21fc8: fcvt s1, d1
234b21fcc: ldr q3, [sp, #0x30]
234b21fd0: mov v2.s[2], v3.s[0]
234b21fd4: mov v2.s[3], v1.s[0]
234b21fd8: mov v4.16b, v2.16b
234b21fdc: fcvt s1, d13
234b21fe0: fcvt s2, d14
234b21fe4: fcvt s3, d15
234b21fe8: mov v1.s[1], v2.s[0]
234b21fec: mov v1.s[2], v3.s[0]
234b21ff0: fcvt s0, d0
234b21ff4: mov v1.s[3], v0.s[0]
234b21ff8: stp q4, q1, [x19, #0x20]
234b21ffc: fmov d8, #3.00000000
234b22000: fmul d0, d12, d8
234b22004: bl #0x236f3d370
234b22008: fmov d12, d0
234b2200c: fcvt s0, d1
234b22010: str q0, [sp, #0x50]
234b22014: fmul d0, d11, d8
234b22018: bl #0x236f3d370
234b2201c: fmov d11, d0
234b22020: fcvt s0, d1
234b22024: str q0, [sp, #0x40]
234b22028: fmul d0, d10, d8
234b2202c: bl #0x236f3d370
234b22030: fmov d10, d0
234b22034: fcvt s0, d1
234b22038: str q0, [sp, #0x30]
234b2203c: fmul d0, d9, d8
234b22040: bl #0x236f3d370
234b22044: fcvt s1, d1
234b22048: ldp q3, q2, [sp, #0x40]
234b2204c: mov v2.s[1], v3.s[0]
234b22050: ldr q3, [sp, #0x30]
234b22054: mov v2.s[2], v3.s[0]
234b22058: mov v2.s[3], v1.s[0]
234b2205c: mov v4.16b, v2.16b
234b22060: fcvt s1, d12
234b22064: fcvt s2, d11
234b22068: fcvt s3, d10
234b2206c: mov v1.s[1], v2.s[0]
234b22070: fcvt s0, d0
234b22074: mov v1.s[2], v3.s[0]
234b22078: mov v1.s[3], v0.s[0]
234b2207c: stp q4, q1, [x19, #0x40]
234b22080: add x20, x20, #1
234b22084: rbit x8, x20
234b22088: lsr x22, x8, x21
234b2208c: mov x19, x23
234b22090: cmp x20, x22
234b22094: b.hi #0x234b22450
234b22098: cmp x20, x22
234b2209c: b.eq #0x234b21eb8
234b220a0: lsl x23, x22, #2
234b220a4: mov x8, x20
234b220a8: lsl x9, x8, #2
234b220ac: ucvtf d0, x9
234b220b0: mov w9, #1
234b220b4: bfi x9, x8, #2, #0x3e
234b220b8: ucvtf d1, x9
234b220bc: ldr d8, [sp, #0x20]
234b220c0: fmul d12, d8, d0
234b220c4: fmul d11, d8, d1
234b220c8: mov w9, #2
234b220cc: bfi x9, x8, #2, #0x3e
234b220d0: ucvtf d0, x9
234b220d4: fmul d10, d8, d0
234b220d8: mov w9, #3
234b220dc: bfi x9, x8, #2, #0x3e
234b220e0: ucvtf d0, x9
234b220e4: fmul d9, d8, d0
234b220e8: fmov d0, d12
234b220ec: bl #0x236f3d370
234b220f0: fmov d13, d0
234b220f4: fcvt s0, d1
234b220f8: str q0, [sp, #0x50]
234b220fc: fmov d0, d11
234b22100: bl #0x236f3d370
234b22104: fmov d14, d0
234b22108: fcvt s0, d1
234b2210c: str q0, [sp, #0x40]
234b22110: fmov d0, d10
234b22114: bl #0x236f3d370
234b22118: fmov d15, d0
234b2211c: fcvt s0, d1
234b22120: str q0, [sp, #0x30]
234b22124: fmov d0, d9
234b22128: bl #0x236f3d370
234b2212c: ldp q3, q2, [sp, #0x40]
234b22130: mov v2.s[1], v3.s[0]
234b22134: fcvt s1, d1
234b22138: ldr q3, [sp, #0x30]
234b2213c: mov v2.s[2], v3.s[0]
234b22140: mov v2.s[3], v1.s[0]
234b22144: mov v4.16b, v2.16b
234b22148: fcvt s1, d13
234b2214c: fcvt s2, d14
234b22150: fcvt s3, d15
234b22154: mov v1.s[1], v2.s[0]
234b22158: mov v1.s[2], v3.s[0]
234b2215c: fcvt s0, d0
234b22160: mov v1.s[3], v0.s[0]
234b22164: stp q4, q1, [x19]
234b22168: fadd d0, d12, d12
234b2216c: bl #0x236f3d370
234b22170: fmov d13, d0
234b22174: fcvt s0, d1
234b22178: str q0, [sp, #0x50]
234b2217c: fadd d0, d11, d11
234b22180: bl #0x236f3d370
234b22184: fmov d14, d0
234b22188: fcvt s0, d1
234b2218c: str q0, [sp, #0x40]
234b22190: fadd d0, d10, d10
234b22194: bl #0x236f3d370
234b22198: fmov d15, d0
234b2219c: fcvt s0, d1
234b221a0: str q0, [sp, #0x30]
234b221a4: fadd d0, d9, d9
234b221a8: bl #0x236f3d370
234b221ac: ldp q3, q2, [sp, #0x40]
234b221b0: mov v2.s[1], v3.s[0]
234b221b4: fcvt s1, d1
234b221b8: ldr q3, [sp, #0x30]
234b221bc: mov v2.s[2], v3.s[0]
234b221c0: mov v2.s[3], v1.s[0]
234b221c4: mov v4.16b, v2.16b
234b221c8: fcvt s1, d13
234b221cc: fcvt s2, d14
234b221d0: fcvt s3, d15
234b221d4: mov v1.s[1], v2.s[0]
234b221d8: mov v1.s[2], v3.s[0]
234b221dc: fcvt s0, d0
234b221e0: mov v1.s[3], v0.s[0]
234b221e4: stp q4, q1, [x19, #0x20]
234b221e8: fmov d0, #3.00000000
234b221ec: fmul d0, d12, d0
234b221f0: bl #0x236f3d370
234b221f4: fmov d12, d0
234b221f8: fcvt s0, d1
234b221fc: str q0, [sp, #0x50]
234b22200: fmov d0, #3.00000000
234b22204: fmul d0, d11, d0
234b22208: bl #0x236f3d370
234b2220c: fmov d11, d0
234b22210: fcvt s0, d1
234b22214: str q0, [sp, #0x40]
234b22218: fmov d0, #3.00000000
234b2221c: fmul d0, d10, d0
234b22220: bl #0x236f3d370
234b22224: fmov d10, d0
234b22228: fcvt s0, d1
234b2222c: str q0, [sp, #0x30]
234b22230: fmov d0, #3.00000000
234b22234: fmul d0, d9, d0
234b22238: bl #0x236f3d370
234b2223c: fcvt s1, d1
234b22240: ldp q3, q2, [sp, #0x40]
234b22244: mov v2.s[1], v3.s[0]
234b22248: ldr q3, [sp, #0x30]
234b2224c: mov v2.s[2], v3.s[0]
234b22250: mov v2.s[3], v1.s[0]
234b22254: mov v4.16b, v2.16b
234b22258: fcvt s1, d12
234b2225c: fcvt s2, d11
234b22260: fcvt s3, d10
234b22264: fcvt s0, d0
234b22268: mov v1.s[1], v2.s[0]
234b2226c: mov v1.s[2], v3.s[0]
234b22270: mov v1.s[3], v0.s[0]
234b22274: stp q4, q1, [x19, #0x40]
234b22278: add x24, x19, #0xc0
234b2227c: ucvtf d0, x23
234b22280: fmul d12, d8, d0
234b22284: add x8, x23, #1
234b22288: ucvtf d0, x8
234b2228c: add x8, x23, #2
234b22290: ucvtf d1, x8
234b22294: fmul d11, d8, d0
234b22298: fmul d10, d8, d1
234b2229c: add x8, x23, #3
234b222a0: ucvtf d0, x8
234b222a4: fmul d9, d8, d0
234b222a8: fmov d0, d12
234b222ac: bl #0x236f3d370
234b222b0: fmov d13, d0
234b222b4: fcvt s0, d1
234b222b8: str q0, [sp, #0x50]
234b222bc: fmov d0, d11
234b222c0: bl #0x236f3d370
234b222c4: fmov d14, d0
234b222c8: fcvt s0, d1
234b222cc: str q0, [sp, #0x40]
234b222d0: fmov d0, d10
234b222d4: bl #0x236f3d370
234b222d8: fmov d15, d0
234b222dc: fcvt s0, d1
234b222e0: str q0, [sp, #0x30]
234b222e4: fmov d0, d9
234b222e8: bl #0x236f3d370
234b222ec: ldp q3, q2, [sp, #0x40]
234b222f0: mov v2.s[1], v3.s[0]
234b222f4: fcvt s1, d1
234b222f8: ldr q3, [sp, #0x30]
234b222fc: mov v2.s[2], v3.s[0]
234b22300: mov v2.s[3], v1.s[0]
234b22304: mov v4.16b, v2.16b
234b22308: fcvt s1, d13
234b2230c: fcvt s2, d14
234b22310: fcvt s3, d15
234b22314: mov v1.s[1], v2.s[0]
234b22318: mov v1.s[2], v3.s[0]
234b2231c: fcvt s0, d0
234b22320: mov v1.s[3], v0.s[0]
234b22324: stp q4, q1, [x19, #0x60]
234b22328: fadd d0, d12, d12
234b2232c: bl #0x236f3d370
234b22330: fmov d13, d0
234b22334: fcvt s0, d1
234b22338: str q0, [sp, #0x50]
234b2233c: fadd d0, d11, d11
234b22340: bl #0x236f3d370
234b22344: fmov d14, d0
234b22348: fcvt s0, d1
234b2234c: str q0, [sp, #0x40]
234b22350: fadd d0, d10, d10
234b22354: bl #0x236f3d370
234b22358: fmov d15, d0
234b2235c: fcvt s0, d1
234b22360: str q0, [sp, #0x30]
234b22364: fadd d0, d9, d9
234b22368: bl #0x236f3d370
234b2236c: ldp q3, q2, [sp, #0x40]
234b22370: mov v2.s[1], v3.s[0]
234b22374: fcvt s1, d1
234b22378: ldr q3, [sp, #0x30]
234b2237c: mov v2.s[2], v3.s[0]
234b22380: mov v2.s[3], v1.s[0]
234b22384: mov v4.16b, v2.16b
234b22388: fcvt s1, d13
234b2238c: fmov d8, #3.00000000
234b22390: fcvt s2, d14
234b22394: fcvt s3, d15
234b22398: mov v1.s[1], v2.s[0]
234b2239c: mov v1.s[2], v3.s[0]
234b223a0: fcvt s0, d0
234b223a4: mov v1.s[3], v0.s[0]
234b223a8: stp q4, q1, [x19, #0x80]
234b223ac: fmul d0, d12, d8
234b223b0: bl #0x236f3d370
234b223b4: fmov d12, d0
234b223b8: fcvt s0, d1
234b223bc: str q0, [sp, #0x50]
234b223c0: fmul d0, d11, d8
234b223c4: bl #0x236f3d370
234b223c8: fmov d11, d0
234b223cc: fcvt s0, d1
234b223d0: str q0, [sp, #0x40]
234b223d4: fmul d0, d10, d8
234b223d8: bl #0x236f3d370
234b223dc: fmov d10, d0
234b223e0: fcvt s0, d1
234b223e4: str q0, [sp, #0x30]
234b223e8: fmul d0, d9, d8
234b223ec: bl #0x236f3d370
234b223f0: fcvt s1, d1
234b223f4: ldp q3, q2, [sp, #0x40]
234b223f8: mov v2.s[1], v3.s[0]
234b223fc: ldr q3, [sp, #0x30]
234b22400: mov v2.s[2], v3.s[0]
234b22404: mov v2.s[3], v1.s[0]
234b22408: mov v4.16b, v2.16b
234b2240c: fcvt s1, d12
234b22410: fcvt s2, d11
234b22414: fcvt s3, d10
234b22418: fcvt s0, d0
234b2241c: mov v1.s[1], v2.s[0]
234b22420: mov v1.s[2], v3.s[0]
234b22424: mov v1.s[3], v0.s[0]
234b22428: stp q4, q1, [x19, #0xa0]
234b2242c: add x22, x22, #1
234b22430: rbit x8, x22
234b22434: lsr x8, x8, x21
234b22438: add x23, x23, #4
234b2243c: mov x19, x24
234b22440: cmp x22, x8
234b22444: b.ne #0x234b220a8
234b22448: mov x19, x24
234b2244c: b #0x234b21eb8
234b22450: mov w0, #0
234b22454: ldp x29, x30, [sp, #0xd0]
234b22458: ldp x20, x19, [sp, #0xc0]
234b2245c: ldp x22, x21, [sp, #0xb0]
234b22460: ldp x24, x23, [sp, #0xa0]
234b22464: ldp d9, d8, [sp, #0x90]
234b22468: ldp d11, d10, [sp, #0x80]
234b2246c: ldp d13, d12, [sp, #0x70]
234b22470: ldp d15, d14, [sp, #0x60]
234b22474: add sp, sp, #0xe0
234b22478: autibsp 
234b2247c: ret 
