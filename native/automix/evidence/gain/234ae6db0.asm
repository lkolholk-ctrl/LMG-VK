0x234ae6db0: 471300b4 cbz x7, #0x234ae7018
0x234ae6db4: 50c8404d ld1r {v16.4s}, [x2]
0x234ae6db8: 3f0004eb cmp x1, x4
0x234ae6dbc: a1110054 b.ne #0x234ae6ff0
0x234ae6dc0: 3f0006eb cmp x1, x6
0x234ae6dc4: 61110054 b.ne #0x234ae6ff0
0x234ae6dc8: 3f0400b1 cmn x1, #1
0x234ae6dcc: 01010054 b.ne #0x234ae6dec
0x234ae6dd0: e80400d1 sub x8, x7, #1
0x234ae6dd4: 000808cb sub x0, x0, x8, lsl #2
0x234ae6dd8: 630808cb sub x3, x3, x8, lsl #2
0x234ae6ddc: a50808cb sub x5, x5, x8, lsl #2
0x234ae6de0: 210080d2 mov x1, #1
0x234ae6de4: 240080d2 mov x4, #1
0x234ae6de8: 260080d2 mov x6, #1
0x234ae6dec: 3f0400f1 cmp x1, #1
0x234ae6df0: 01100054 b.ne #0x234ae6ff0
0x234ae6df4: bf0c40f2 tst x5, #0xf
0x234ae6df8: 00010054 b.eq #0x234ae6e18
0x234ae6dfc: e70400f1 subs x7, x7, #1
0x234ae6e00: c4100054 b.mi #0x234ae7018
0x234ae6e04: 024440bc ldr s2, [x0], #4
0x234ae6e08: 634440bc ldr s3, [x3], #4
0x234ae6e0c: 410c101f fmadd s1, s2, s16, s3
0x234ae6e10: a14400bc str s1, [x5], #4
0x234ae6e14: f8ffff17 b #0x234ae6df4
0x234ae6e18: ea8000f1 subs x10, x7, #0x20
0x234ae6e1c: cb080054 b.lt #0x234ae6f34
0x234ae6e20: 0004c83c ldr q0, [x0], #0x80
0x234ae6e24: 7804c83c ldr q24, [x3], #0x80
0x234ae6e28: 0100d93c ldur q1, [x0, #-0x70]
0x234ae6e2c: 7900d93c ldur q25, [x3, #-0x70]
0x234ae6e30: 0200da3c ldur q2, [x0, #-0x60]
0x234ae6e34: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ae6e38: 7a00da3c ldur q26, [x3, #-0x60]
0x234ae6e3c: 0300db3c ldur q3, [x0, #-0x50]
0x234ae6e40: 39cc304e fmla v25.4s, v1.4s, v16.4s
0x234ae6e44: 7b00db3c ldur q27, [x3, #-0x50]
0x234ae6e48: e70001f1 subs x7, x7, #0x40
0x234ae6e4c: 6b040054 b.lt #0x234ae6ed8
0x234ae6e50: 0400dc3c ldur q4, [x0, #-0x40]
0x234ae6e54: 5acc304e fmla v26.4s, v2.4s, v16.4s
0x234ae6e58: 7c00dc3c ldur q28, [x3, #-0x40]
0x234ae6e5c: b804883c str q24, [x5], #0x80
0x234ae6e60: 0500dd3c ldur q5, [x0, #-0x30]
0x234ae6e64: 7bcc304e fmla v27.4s, v3.4s, v16.4s
0x234ae6e68: 7d00dd3c ldur q29, [x3, #-0x30]
0x234ae6e6c: b900993c stur q25, [x5, #-0x70]
0x234ae6e70: 0600de3c ldur q6, [x0, #-0x20]
0x234ae6e74: 9ccc304e fmla v28.4s, v4.4s, v16.4s
0x234ae6e78: 7e00de3c ldur q30, [x3, #-0x20]
0x234ae6e7c: ba009a3c stur q26, [x5, #-0x60]
0x234ae6e80: 0700df3c ldur q7, [x0, #-0x10]
0x234ae6e84: bdcc304e fmla v29.4s, v5.4s, v16.4s
0x234ae6e88: 7f00df3c ldur q31, [x3, #-0x10]
0x234ae6e8c: bb009b3c stur q27, [x5, #-0x50]
0x234ae6e90: 0004c83c ldr q0, [x0], #0x80
0x234ae6e94: decc304e fmla v30.4s, v6.4s, v16.4s
0x234ae6e98: 7804c83c ldr q24, [x3], #0x80
0x234ae6e9c: bc009c3c stur q28, [x5, #-0x40]
0x234ae6ea0: 0100d93c ldur q1, [x0, #-0x70]
0x234ae6ea4: ffcc304e fmla v31.4s, v7.4s, v16.4s
0x234ae6ea8: 7900d93c ldur q25, [x3, #-0x70]
0x234ae6eac: bd009d3c stur q29, [x5, #-0x30]
0x234ae6eb0: 0200da3c ldur q2, [x0, #-0x60]
0x234ae6eb4: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ae6eb8: 7a00da3c ldur q26, [x3, #-0x60]
0x234ae6ebc: be009e3c stur q30, [x5, #-0x20]
0x234ae6ec0: 0300db3c ldur q3, [x0, #-0x50]
0x234ae6ec4: 39cc304e fmla v25.4s, v1.4s, v16.4s
0x234ae6ec8: 7b00db3c ldur q27, [x3, #-0x50]
0x234ae6ecc: bf009f3c stur q31, [x5, #-0x10]
0x234ae6ed0: e78000f1 subs x7, x7, #0x20
0x234ae6ed4: eafbff54 b.ge #0x234ae6e50
0x234ae6ed8: e78000b1 adds x7, x7, #0x20
0x234ae6edc: 0400dc3c ldur q4, [x0, #-0x40]
0x234ae6ee0: 5acc304e fmla v26.4s, v2.4s, v16.4s
0x234ae6ee4: 7c00dc3c ldur q28, [x3, #-0x40]
0x234ae6ee8: b804883c str q24, [x5], #0x80
0x234ae6eec: 0500dd3c ldur q5, [x0, #-0x30]
0x234ae6ef0: 7bcc304e fmla v27.4s, v3.4s, v16.4s
0x234ae6ef4: 7d00dd3c ldur q29, [x3, #-0x30]
0x234ae6ef8: b900993c stur q25, [x5, #-0x70]
0x234ae6efc: 0600de3c ldur q6, [x0, #-0x20]
0x234ae6f00: 9ccc304e fmla v28.4s, v4.4s, v16.4s
0x234ae6f04: 7e00de3c ldur q30, [x3, #-0x20]
0x234ae6f08: ba009a3c stur q26, [x5, #-0x60]
0x234ae6f0c: 0700df3c ldur q7, [x0, #-0x10]
0x234ae6f10: bdcc304e fmla v29.4s, v5.4s, v16.4s
0x234ae6f14: 7f00df3c ldur q31, [x3, #-0x10]
0x234ae6f18: bb009b3c stur q27, [x5, #-0x50]
0x234ae6f1c: decc304e fmla v30.4s, v6.4s, v16.4s
0x234ae6f20: bc009c3c stur q28, [x5, #-0x40]
0x234ae6f24: ffcc304e fmla v31.4s, v7.4s, v16.4s
0x234ae6f28: bd009d3c stur q29, [x5, #-0x30]
0x234ae6f2c: be009e3c stur q30, [x5, #-0x20]
0x234ae6f30: bf009f3c stur q31, [x5, #-0x10]
0x234ae6f34: ea4000f1 subs x10, x7, #0x10
0x234ae6f38: cb040054 b.lt #0x234ae6fd0
0x234ae6f3c: 0004c43c ldr q0, [x0], #0x40
0x234ae6f40: 7804c43c ldr q24, [x3], #0x40
0x234ae6f44: 0100dd3c ldur q1, [x0, #-0x30]
0x234ae6f48: 7900dd3c ldur q25, [x3, #-0x30]
0x234ae6f4c: 0200de3c ldur q2, [x0, #-0x20]
0x234ae6f50: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ae6f54: 7a00de3c ldur q26, [x3, #-0x20]
0x234ae6f58: 0300df3c ldur q3, [x0, #-0x10]
0x234ae6f5c: 39cc304e fmla v25.4s, v1.4s, v16.4s
0x234ae6f60: 7b00df3c ldur q27, [x3, #-0x10]
0x234ae6f64: e78000f1 subs x7, x7, #0x20
0x234ae6f68: 6b020054 b.lt #0x234ae6fb4
0x234ae6f6c: 0004c43c ldr q0, [x0], #0x40
0x234ae6f70: 5acc304e fmla v26.4s, v2.4s, v16.4s
0x234ae6f74: b804843c str q24, [x5], #0x40
0x234ae6f78: 7804c43c ldr q24, [x3], #0x40
0x234ae6f7c: 0100dd3c ldur q1, [x0, #-0x30]
0x234ae6f80: 7bcc304e fmla v27.4s, v3.4s, v16.4s
0x234ae6f84: b9009d3c stur q25, [x5, #-0x30]
0x234ae6f88: 7900dd3c ldur q25, [x3, #-0x30]
0x234ae6f8c: 0200de3c ldur q2, [x0, #-0x20]
0x234ae6f90: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ae6f94: ba009e3c stur q26, [x5, #-0x20]
0x234ae6f98: 7a00de3c ldur q26, [x3, #-0x20]
0x234ae6f9c: 0300df3c ldur q3, [x0, #-0x10]
0x234ae6fa0: 39cc304e fmla v25.4s, v1.4s, v16.4s
0x234ae6fa4: bb009f3c stur q27, [x5, #-0x10]
0x234ae6fa8: 7b00df3c ldur q27, [x3, #-0x10]
0x234ae6fac: e74000f1 subs x7, x7, #0x10
0x234ae6fb0: eafdff54 b.ge #0x234ae6f6c
0x234ae6fb4: e7400091 add x7, x7, #0x10
0x234ae6fb8: 5acc304e fmla v26.4s, v2.4s, v16.4s
0x234ae6fbc: b804843c str q24, [x5], #0x40
0x234ae6fc0: 7bcc304e fmla v27.4s, v3.4s, v16.4s
0x234ae6fc4: b9009d3c stur q25, [x5, #-0x30]
0x234ae6fc8: ba009e3c stur q26, [x5, #-0x20]
0x234ae6fcc: bb009f3c stur q27, [x5, #-0x10]
0x234ae6fd0: e71000f1 subs x7, x7, #4
0x234ae6fd4: cb000054 b.lt #0x234ae6fec
0x234ae6fd8: 0078df4c ld1 {v0.4s}, [x0], #16
0x234ae6fdc: 7878df4c ld1 {v24.4s}, [x3], #16
0x234ae6fe0: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ae6fe4: b8789f4c st1 {v24.4s}, [x5], #16
0x234ae6fe8: faffff17 b #0x234ae6fd0
0x234ae6fec: e7100091 add x7, x7, #4
0x234ae6ff0: e70400f1 subs x7, x7, #1
0x234ae6ff4: 2b010054 b.lt #0x234ae7018
0x234ae6ff8: 020040bd ldr s2, [x0]
0x234ae6ffc: 630040bd ldr s3, [x3]
0x234ae7000: 0008018b add x0, x0, x1, lsl #2
0x234ae7004: 6308048b add x3, x3, x4, lsl #2
0x234ae7008: 410c101f fmadd s1, s2, s16, s3
0x234ae700c: a10000bd str s1, [x5]
0x234ae7010: a508068b add x5, x5, x6, lsl #2
0x234ae7014: f7ffff17 b #0x234ae6ff0
0x234ae7018: c0035fd6 ret 
0x234ae701c: 00000000 udf #0
