234b16f20: pacibsp 
234b16f24: sub x9, sp, #0x60
234b16f28: sub sp, sp, #0x70
234b16f2c: st1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234b16f30: st1 {v12.4s, v13.4s}, [x9], #32
234b16f34: stp x29, x30, [x9, #-0x70]!
234b16f38: mov x29, x9
234b16f3c: sub x30, x4, #2
234b16f40: add x30, x30, #0
234b16f44: lsl x30, x2, x30
234b16f48: mov x9, x0
234b16f4c: mov x10, x1
234b16f50: ld1 {v0.4s}, [x3], #16
234b16f54: ld1 {v1.2s}, [x3], #8
234b16f58: add x5, x9, x30
234b16f5c: add x6, x10, x30
234b16f60: mov x7, x5
234b16f64: mov x13, x6
234b16f68: add x11, x9, x30, lsl #1
234b16f6c: add x8, x10, x30, lsl #1
234b16f70: mov x14, x11
234b16f74: mov x15, x8
234b16f78: add x12, x5, x30, lsl #1
234b16f7c: add x17, x6, x30, lsl #1
234b16f80: mov x16, x12
234b16f84: mov x3, x17
234b16f88: mov x30, #1
234b16f8c: lsl x4, x30, x4
234b16f90: lsl x4, x4, #2
234b16f94: subs x2, x2, #0x20
234b16f98: b.lt #0x234b17360
234b16f9c: ldr q2, [x11]
234b16fa0: ldr q3, [x11, x4]
234b16fa4: add x11, x11, x4, lsl #1
234b16fa8: ldr q26, [x8]
234b16fac: ldr q27, [x8, x4]
234b16fb0: add x8, x8, x4, lsl #1
234b16fb4: ldr q10, [x5]
234b16fb8: ldr q11, [x5, x4]
234b16fbc: add x5, x5, x4, lsl #1
234b16fc0: ldr q12, [x6]
234b16fc4: ldr q13, [x6, x4]
234b16fc8: add x6, x6, x4, lsl #1
234b16fcc: fmul v18.4s, v2.4s, v0.s[2]
234b16fd0: fmul v19.4s, v3.4s, v0.s[2]
234b16fd4: ldr q4, [x12]
234b16fd8: ldr q5, [x12, x4]
234b16fdc: fmul v16.4s, v2.4s, v0.s[3]
234b16fe0: fmul v17.4s, v3.4s, v0.s[3]
234b16fe4: ldr q2, [x17]
234b16fe8: ldr q3, [x17, x4]
234b16fec: fmul v20.4s, v26.4s, v0.s[3]
234b16ff0: fmul v21.4s, v27.4s, v0.s[3]
234b16ff4: ldr q6, [x9]
234b16ff8: ldr q7, [x9, x4]
234b16ffc: fmul v26.4s, v26.4s, v0.s[2]
234b17000: fmul v27.4s, v27.4s, v0.s[2]
234b17004: ldr q8, [x10]
234b17008: ldr q9, [x10, x4]
234b1700c: fmul v22.4s, v10.4s, v0.s[0]
234b17010: fmul v23.4s, v11.4s, v0.s[0]
234b17014: fmul v10.4s, v10.4s, v0.s[1]
234b17018: fmul v11.4s, v11.4s, v0.s[1]
234b1701c: fmul v24.4s, v12.4s, v0.s[1]
234b17020: fmul v25.4s, v13.4s, v0.s[1]
234b17024: fmul v28.4s, v12.4s, v0.s[0]
234b17028: fmul v29.4s, v13.4s, v0.s[0]
234b1702c: fmul v12.4s, v4.4s, v1.s[0]
234b17030: fmul v13.4s, v5.4s, v1.s[0]
234b17034: fsub v18.4s, v18.4s, v20.4s
234b17038: fsub v19.4s, v19.4s, v21.4s
234b1703c: fmul v4.4s, v4.4s, v1.s[1]
234b17040: fmul v5.4s, v5.4s, v1.s[1]
234b17044: fadd v20.4s, v16.4s, v26.4s
234b17048: fadd v21.4s, v17.4s, v27.4s
234b1704c: fmul v26.4s, v2.4s, v1.s[1]
234b17050: fmul v27.4s, v3.4s, v1.s[1]
234b17054: fsub v22.4s, v22.4s, v24.4s
234b17058: fsub v23.4s, v23.4s, v25.4s
234b1705c: fmul v2.4s, v2.4s, v1.s[0]
234b17060: fmul v3.4s, v3.4s, v1.s[0]
234b17064: fadd v24.4s, v10.4s, v28.4s
234b17068: fadd v25.4s, v11.4s, v29.4s
234b1706c: fadd v16.4s, v6.4s, v18.4s
234b17070: fadd v17.4s, v7.4s, v19.4s
234b17074: subs x2, x2, #0x20
234b17078: fsub v6.4s, v6.4s, v18.4s
234b1707c: fsub v7.4s, v7.4s, v19.4s
234b17080: add x12, x12, x4, lsl #1
234b17084: fsub v10.4s, v12.4s, v26.4s
234b17088: fsub v11.4s, v13.4s, v27.4s
234b1708c: add x17, x17, x4, lsl #1
234b17090: fadd v12.4s, v4.4s, v2.4s
234b17094: fadd v13.4s, v5.4s, v3.4s
234b17098: add x9, x9, x4, lsl #1
234b1709c: fadd v4.4s, v8.4s, v20.4s
234b170a0: fadd v5.4s, v9.4s, v21.4s
234b170a4: add x10, x10, x4, lsl #1
234b170a8: fsub v8.4s, v8.4s, v20.4s
234b170ac: fsub v9.4s, v9.4s, v21.4s
234b170b0: b.lt #0x234b17298
234b170b4: nop 
234b170b8: nop 
234b170bc: nop 
234b170c0: fadd v20.4s, v22.4s, v10.4s
234b170c4: fadd v21.4s, v23.4s, v11.4s
234b170c8: ldr q2, [x11]
234b170cc: ldr q3, [x11, x4]
234b170d0: add x11, x11, x4, lsl #1
234b170d4: fadd v18.4s, v24.4s, v12.4s
234b170d8: fadd v19.4s, v25.4s, v13.4s
234b170dc: ldr q26, [x8]
234b170e0: ldr q27, [x8, x4]
234b170e4: add x8, x8, x4, lsl #1
234b170e8: fsub v30.4s, v22.4s, v10.4s
234b170ec: fsub v31.4s, v23.4s, v11.4s
234b170f0: ldr q10, [x5]
234b170f4: ldr q11, [x5, x4]
234b170f8: add x5, x5, x4, lsl #1
234b170fc: fsub v28.4s, v24.4s, v12.4s
234b17100: fsub v29.4s, v25.4s, v13.4s
234b17104: ldr q12, [x6]
234b17108: ldr q13, [x6, x4]
234b1710c: add x6, x6, x4, lsl #1
234b17110: fadd v22.4s, v16.4s, v20.4s
234b17114: fadd v23.4s, v17.4s, v21.4s
234b17118: str q22, [x0]
234b1711c: str q23, [x0, x4]
234b17120: add x0, x0, x4, lsl #1
234b17124: fadd v22.4s, v4.4s, v18.4s
234b17128: fadd v23.4s, v5.4s, v19.4s
234b1712c: str q22, [x1]
234b17130: str q23, [x1, x4]
234b17134: add x1, x1, x4, lsl #1
234b17138: fsub v16.4s, v16.4s, v20.4s
234b1713c: fsub v17.4s, v17.4s, v21.4s
234b17140: str q16, [x7]
234b17144: str q17, [x7, x4]
234b17148: add x7, x7, x4, lsl #1
234b1714c: fsub v4.4s, v4.4s, v18.4s
234b17150: fsub v5.4s, v5.4s, v19.4s
234b17154: str q4, [x13]
234b17158: str q5, [x13, x4]
234b1715c: add x13, x13, x4, lsl #1
234b17160: fmul v18.4s, v2.4s, v0.s[2]
234b17164: fmul v19.4s, v3.4s, v0.s[2]
234b17168: fsub v22.4s, v6.4s, v28.4s
234b1716c: fsub v23.4s, v7.4s, v29.4s
234b17170: ldr q4, [x12]
234b17174: ldr q5, [x12, x4]
234b17178: fmul v16.4s, v2.4s, v0.s[3]
234b1717c: fmul v17.4s, v3.4s, v0.s[3]
234b17180: fadd v24.4s, v8.4s, v30.4s
234b17184: fadd v25.4s, v9.4s, v31.4s
234b17188: ldr q2, [x17]
234b1718c: ldr q3, [x17, x4]
234b17190: fmul v20.4s, v26.4s, v0.s[3]
234b17194: fmul v21.4s, v27.4s, v0.s[3]
234b17198: fadd v28.4s, v6.4s, v28.4s
234b1719c: fadd v29.4s, v7.4s, v29.4s
234b171a0: ldr q6, [x9]
234b171a4: ldr q7, [x9, x4]
234b171a8: fmul v26.4s, v26.4s, v0.s[2]
234b171ac: fmul v27.4s, v27.4s, v0.s[2]
234b171b0: fsub v30.4s, v8.4s, v30.4s
234b171b4: fsub v31.4s, v9.4s, v31.4s
234b171b8: ldr q8, [x10]
234b171bc: ldr q9, [x10, x4]
234b171c0: str q22, [x14]
234b171c4: str q23, [x14, x4]
234b171c8: fmul v22.4s, v10.4s, v0.s[0]
234b171cc: fmul v23.4s, v11.4s, v0.s[0]
234b171d0: add x14, x14, x4, lsl #1
234b171d4: str q24, [x15]
234b171d8: str q25, [x15, x4]
234b171dc: fmul v10.4s, v10.4s, v0.s[1]
234b171e0: fmul v11.4s, v11.4s, v0.s[1]
234b171e4: add x15, x15, x4, lsl #1
234b171e8: str q28, [x16]
234b171ec: str q29, [x16, x4]
234b171f0: fmul v24.4s, v12.4s, v0.s[1]
234b171f4: fmul v25.4s, v13.4s, v0.s[1]
234b171f8: add x16, x16, x4, lsl #1
234b171fc: str q30, [x3]
234b17200: str q31, [x3, x4]
234b17204: fmul v28.4s, v12.4s, v0.s[0]
234b17208: fmul v29.4s, v13.4s, v0.s[0]
234b1720c: add x3, x3, x4, lsl #1
234b17210: fmul v12.4s, v4.4s, v1.s[0]
234b17214: fmul v13.4s, v5.4s, v1.s[0]
234b17218: fsub v18.4s, v18.4s, v20.4s
234b1721c: fsub v19.4s, v19.4s, v21.4s
234b17220: fmul v4.4s, v4.4s, v1.s[1]
234b17224: fmul v5.4s, v5.4s, v1.s[1]
234b17228: fadd v20.4s, v16.4s, v26.4s
234b1722c: fadd v21.4s, v17.4s, v27.4s
234b17230: fmul v26.4s, v2.4s, v1.s[1]
234b17234: fmul v27.4s, v3.4s, v1.s[1]
234b17238: fsub v22.4s, v22.4s, v24.4s
234b1723c: fsub v23.4s, v23.4s, v25.4s
234b17240: fmul v2.4s, v2.4s, v1.s[0]
234b17244: fmul v3.4s, v3.4s, v1.s[0]
234b17248: fadd v24.4s, v10.4s, v28.4s
234b1724c: fadd v25.4s, v11.4s, v29.4s
234b17250: fadd v16.4s, v6.4s, v18.4s
234b17254: fadd v17.4s, v7.4s, v19.4s
234b17258: subs x2, x2, #0x20
234b1725c: fsub v6.4s, v6.4s, v18.4s
234b17260: fsub v7.4s, v7.4s, v19.4s
234b17264: add x12, x12, x4, lsl #1
234b17268: fsub v10.4s, v12.4s, v26.4s
234b1726c: fsub v11.4s, v13.4s, v27.4s
234b17270: add x17, x17, x4, lsl #1
234b17274: fadd v12.4s, v4.4s, v2.4s
234b17278: fadd v13.4s, v5.4s, v3.4s
234b1727c: add x9, x9, x4, lsl #1
234b17280: fadd v4.4s, v8.4s, v20.4s
234b17284: fadd v5.4s, v9.4s, v21.4s
234b17288: add x10, x10, x4, lsl #1
234b1728c: fsub v8.4s, v8.4s, v20.4s
234b17290: fsub v9.4s, v9.4s, v21.4s
234b17294: b.ge #0x234b170c0
234b17298: fadd v20.4s, v22.4s, v10.4s
234b1729c: fadd v21.4s, v23.4s, v11.4s
234b172a0: adds x2, x2, #0x20
234b172a4: fadd v18.4s, v24.4s, v12.4s
234b172a8: fadd v19.4s, v25.4s, v13.4s
234b172ac: fsub v30.4s, v22.4s, v10.4s
234b172b0: fsub v31.4s, v23.4s, v11.4s
234b172b4: fsub v28.4s, v24.4s, v12.4s
234b172b8: fsub v29.4s, v25.4s, v13.4s
234b172bc: fadd v22.4s, v16.4s, v20.4s
234b172c0: fadd v23.4s, v17.4s, v21.4s
234b172c4: str q22, [x0]
234b172c8: str q23, [x0, x4]
234b172cc: add x0, x0, x4, lsl #1
234b172d0: fadd v22.4s, v4.4s, v18.4s
234b172d4: fadd v23.4s, v5.4s, v19.4s
234b172d8: str q22, [x1]
234b172dc: str q23, [x1, x4]
234b172e0: add x1, x1, x4, lsl #1
234b172e4: fsub v16.4s, v16.4s, v20.4s
234b172e8: fsub v17.4s, v17.4s, v21.4s
234b172ec: str q16, [x7]
234b172f0: str q17, [x7, x4]
234b172f4: add x7, x7, x4, lsl #1
234b172f8: fsub v4.4s, v4.4s, v18.4s
234b172fc: fsub v5.4s, v5.4s, v19.4s
234b17300: str q4, [x13]
234b17304: str q5, [x13, x4]
234b17308: add x13, x13, x4, lsl #1
234b1730c: fsub v22.4s, v6.4s, v28.4s
234b17310: fsub v23.4s, v7.4s, v29.4s
234b17314: fadd v24.4s, v8.4s, v30.4s
234b17318: fadd v25.4s, v9.4s, v31.4s
234b1731c: fadd v28.4s, v6.4s, v28.4s
234b17320: fadd v29.4s, v7.4s, v29.4s
234b17324: fsub v30.4s, v8.4s, v30.4s
234b17328: fsub v31.4s, v9.4s, v31.4s
234b1732c: str q22, [x14]
234b17330: str q23, [x14, x4]
234b17334: add x14, x14, x4, lsl #1
234b17338: str q24, [x15]
234b1733c: str q25, [x15, x4]
234b17340: add x15, x15, x4, lsl #1
234b17344: str q28, [x16]
234b17348: str q29, [x16, x4]
234b1734c: add x16, x16, x4, lsl #1
234b17350: str q30, [x3]
234b17354: str q31, [x3, x4]
234b17358: add x3, x3, x4, lsl #1
234b1735c: b.eq #0x234b17428
234b17360: ldr q2, [x11]
234b17364: ldr q26, [x8]
234b17368: ldr q10, [x5]
234b1736c: ldr q12, [x6]
234b17370: fmul v18.4s, v2.4s, v0.s[2]
234b17374: ldr q4, [x12]
234b17378: fmul v16.4s, v2.4s, v0.s[3]
234b1737c: ldr q2, [x17]
234b17380: fmul v20.4s, v26.4s, v0.s[3]
234b17384: ldr q6, [x9]
234b17388: fmul v26.4s, v26.4s, v0.s[2]
234b1738c: ldr q8, [x10]
234b17390: fmul v22.4s, v10.4s, v0.s[0]
234b17394: fmul v10.4s, v10.4s, v0.s[1]
234b17398: fmul v24.4s, v12.4s, v0.s[1]
234b1739c: fmul v28.4s, v12.4s, v0.s[0]
234b173a0: fmul v12.4s, v4.4s, v1.s[0]
234b173a4: fsub v18.4s, v18.4s, v20.4s
234b173a8: fmul v4.4s, v4.4s, v1.s[1]
234b173ac: fadd v20.4s, v16.4s, v26.4s
234b173b0: fmul v26.4s, v2.4s, v1.s[1]
234b173b4: fsub v22.4s, v22.4s, v24.4s
234b173b8: fmul v2.4s, v2.4s, v1.s[0]
234b173bc: fadd v24.4s, v10.4s, v28.4s
234b173c0: fadd v16.4s, v6.4s, v18.4s
234b173c4: fsub v6.4s, v6.4s, v18.4s
234b173c8: fsub v10.4s, v12.4s, v26.4s
234b173cc: fadd v12.4s, v4.4s, v2.4s
234b173d0: fadd v4.4s, v8.4s, v20.4s
234b173d4: fsub v8.4s, v8.4s, v20.4s
234b173d8: fadd v20.4s, v22.4s, v10.4s
234b173dc: fadd v18.4s, v24.4s, v12.4s
234b173e0: fsub v30.4s, v22.4s, v10.4s
234b173e4: fsub v28.4s, v24.4s, v12.4s
234b173e8: fadd v22.4s, v16.4s, v20.4s
234b173ec: str q22, [x0]
234b173f0: fadd v22.4s, v4.4s, v18.4s
234b173f4: str q22, [x1]
234b173f8: fsub v16.4s, v16.4s, v20.4s
234b173fc: str q16, [x7]
234b17400: fsub v4.4s, v4.4s, v18.4s
234b17404: str q4, [x13]
234b17408: fsub v22.4s, v6.4s, v28.4s
234b1740c: fadd v24.4s, v8.4s, v30.4s
234b17410: fadd v28.4s, v6.4s, v28.4s
234b17414: fsub v30.4s, v8.4s, v30.4s
234b17418: str q22, [x14]
234b1741c: str q24, [x15]
234b17420: str q28, [x16]
234b17424: str q30, [x3]
234b17428: mov x9, x29
234b1742c: ldp x29, x30, [x9], #0x10
234b17430: ld1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234b17434: ld1 {v12.4s, v13.4s}, [x9]
234b17438: add sp, sp, #0x70
234b1743c: retab 
