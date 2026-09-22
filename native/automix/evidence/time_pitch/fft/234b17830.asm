234b17830: pacibsp 
234b17834: sub x9, sp, #0x50
234b17838: sub sp, sp, #0x70
234b1783c: st1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234b17840: st1 {v12.4s}, [x9], #16
234b17844: stp x29, x30, [x9, #-0x60]!
234b17848: mov x29, x9
234b1784c: stp x19, x20, [x9, #-0x10]
234b17850: mov x20, #1
234b17854: lsl x4, x20, x4
234b17858: lsl x4, x4, #2
234b1785c: add x13, x0, x4
234b17860: add x14, x1, x4
234b17864: add x15, x0, x4, lsl #1
234b17868: add x16, x1, x4, lsl #1
234b1786c: add x17, x13, x4, lsl #1
234b17870: add x19, x14, x4, lsl #1
234b17874: mov x5, x0
234b17878: mov x6, x1
234b1787c: mov x7, x13
234b17880: mov x8, x14
234b17884: mov x9, x15
234b17888: mov x10, x16
234b1788c: mov x11, x17
234b17890: mov x12, x19
234b17894: lsl x4, x4, #2
234b17898: ld1 {v10.4s, v11.4s, v12.4s}, [x3]
234b1789c: add x3, x3, #0x30
234b178a0: subs x2, x2, #8
234b178a4: ldr q2, [x9]
234b178a8: ldr q3, [x9, x4]
234b178ac: add x9, x9, x4, lsl #1
234b178b0: ldr q6, [x10]
234b178b4: ldr q7, [x10, x4]
234b178b8: add x10, x10, x4, lsl #1
234b178bc: fmul v22.4s, v2.4s, v10.s[2]
234b178c0: fmul v23.4s, v3.4s, v12.s[0]
234b178c4: fmul v4.4s, v6.4s, v10.s[3]
234b178c8: fmul v5.4s, v7.4s, v12.s[1]
234b178cc: fmul v2.4s, v2.4s, v10.s[3]
234b178d0: fmul v3.4s, v3.4s, v12.s[1]
234b178d4: ldr q28, [x7]
234b178d8: ldr q29, [x7, x4]
234b178dc: add x7, x7, x4, lsl #1
234b178e0: fmul v6.4s, v6.4s, v10.s[2]
234b178e4: fmul v7.4s, v7.4s, v12.s[0]
234b178e8: ldr q24, [x8]
234b178ec: ldr q25, [x8, x4]
234b178f0: add x8, x8, x4, lsl #1
234b178f4: fsub v22.4s, v22.4s, v4.4s
234b178f8: fsub v23.4s, v23.4s, v5.4s
234b178fc: ldr q0, [x11]
234b17900: ldr q1, [x11, x4]
234b17904: add x11, x11, x4, lsl #1
234b17908: fmul v4.4s, v28.4s, v10.s[0]
234b1790c: fmul v5.4s, v29.4s, v11.s[2]
234b17910: ldr q30, [x12]
234b17914: ldr q31, [x12, x4]
234b17918: add x12, x12, x4, lsl #1
234b1791c: fmul v20.4s, v24.4s, v10.s[1]
234b17920: fmul v21.4s, v25.4s, v11.s[3]
234b17924: fmul v16.4s, v0.4s, v11.s[0]
234b17928: fmul v17.4s, v1.4s, v12.s[2]
234b1792c: fmul v18.4s, v30.4s, v11.s[1]
234b17930: fmul v19.4s, v31.4s, v12.s[3]
234b17934: fmul v28.4s, v28.4s, v10.s[1]
234b17938: fmul v29.4s, v29.4s, v11.s[3]
234b1793c: fmul v8.4s, v24.4s, v10.s[0]
234b17940: fmul v9.4s, v25.4s, v11.s[2]
234b17944: ldr q24, [x5]
234b17948: ldr q25, [x5, x4]
234b1794c: add x5, x5, x4, lsl #1
234b17950: fmul v26.4s, v0.4s, v11.s[1]
234b17954: fmul v27.4s, v1.4s, v12.s[3]
234b17958: ldr q0, [x6]
234b1795c: ldr q1, [x6, x4]
234b17960: add x6, x6, x4, lsl #1
234b17964: fmul v30.4s, v30.4s, v11.s[0]
234b17968: fmul v31.4s, v31.4s, v12.s[2]
234b1796c: b.le #0x234b17b50
234b17970: fsub v4.4s, v4.4s, v20.4s
234b17974: fsub v5.4s, v5.4s, v21.4s
234b17978: ld1 {v10.4s, v11.4s, v12.4s}, [x3]
234b1797c: add x3, x3, #0x30
234b17980: fsub v16.4s, v16.4s, v18.4s
234b17984: fsub v17.4s, v17.4s, v19.4s
234b17988: fadd v18.4s, v24.4s, v22.4s
234b1798c: fadd v19.4s, v25.4s, v23.4s
234b17990: subs x2, x2, #8
234b17994: fadd v20.4s, v4.4s, v16.4s
234b17998: fadd v21.4s, v5.4s, v17.4s
234b1799c: fadd v28.4s, v28.4s, v8.4s
234b179a0: fadd v29.4s, v29.4s, v9.4s
234b179a4: fadd v8.4s, v18.4s, v20.4s
234b179a8: fadd v9.4s, v19.4s, v21.4s
234b179ac: fsub v18.4s, v18.4s, v20.4s
234b179b0: fsub v19.4s, v19.4s, v21.4s
234b179b4: fadd v26.4s, v26.4s, v30.4s
234b179b8: fadd v27.4s, v27.4s, v31.4s
234b179bc: str q8, [x0]
234b179c0: str q9, [x0, x4]
234b179c4: add x0, x0, x4, lsl #1
234b179c8: fsub v22.4s, v24.4s, v22.4s
234b179cc: fsub v23.4s, v25.4s, v23.4s
234b179d0: fsub v24.4s, v28.4s, v26.4s
234b179d4: fsub v25.4s, v29.4s, v27.4s
234b179d8: str q18, [x13]
234b179dc: str q19, [x13, x4]
234b179e0: add x13, x13, x4, lsl #1
234b179e4: fadd v30.4s, v2.4s, v6.4s
234b179e8: fadd v31.4s, v3.4s, v7.4s
234b179ec: fsub v8.4s, v22.4s, v24.4s
234b179f0: fsub v9.4s, v23.4s, v25.4s
234b179f4: ldr q2, [x9]
234b179f8: ldr q3, [x9, x4]
234b179fc: add x9, x9, x4, lsl #1
234b17a00: fadd v26.4s, v28.4s, v26.4s
234b17a04: fadd v27.4s, v29.4s, v27.4s
234b17a08: ldr q6, [x10]
234b17a0c: ldr q7, [x10, x4]
234b17a10: add x10, x10, x4, lsl #1
234b17a14: fadd v24.4s, v22.4s, v24.4s
234b17a18: fadd v25.4s, v23.4s, v25.4s
234b17a1c: str q8, [x15]
234b17a20: str q9, [x15, x4]
234b17a24: add x15, x15, x4, lsl #1
234b17a28: fadd v8.4s, v0.4s, v30.4s
234b17a2c: fadd v9.4s, v1.4s, v31.4s
234b17a30: fmul v22.4s, v2.4s, v10.s[2]
234b17a34: fmul v23.4s, v3.4s, v12.s[0]
234b17a38: fadd v28.4s, v8.4s, v26.4s
234b17a3c: fadd v29.4s, v9.4s, v27.4s
234b17a40: str q24, [x17]
234b17a44: str q25, [x17, x4]
234b17a48: add x17, x17, x4, lsl #1
234b17a4c: fsub v0.4s, v0.4s, v30.4s
234b17a50: fsub v1.4s, v1.4s, v31.4s
234b17a54: fsub v24.4s, v8.4s, v26.4s
234b17a58: fsub v25.4s, v9.4s, v27.4s
234b17a5c: str q28, [x1]
234b17a60: str q29, [x1, x4]
234b17a64: add x1, x1, x4, lsl #1
234b17a68: fsub v26.4s, v4.4s, v16.4s
234b17a6c: fsub v27.4s, v5.4s, v17.4s
234b17a70: fmul v4.4s, v6.4s, v10.s[3]
234b17a74: fmul v5.4s, v7.4s, v12.s[1]
234b17a78: fadd v16.4s, v0.4s, v26.4s
234b17a7c: fadd v17.4s, v1.4s, v27.4s
234b17a80: str q24, [x14]
234b17a84: str q25, [x14, x4]
234b17a88: add x14, x14, x4, lsl #1
234b17a8c: fsub v26.4s, v0.4s, v26.4s
234b17a90: fsub v27.4s, v1.4s, v27.4s
234b17a94: fmul v2.4s, v2.4s, v10.s[3]
234b17a98: fmul v3.4s, v3.4s, v12.s[1]
234b17a9c: ldr q28, [x7]
234b17aa0: ldr q29, [x7, x4]
234b17aa4: add x7, x7, x4, lsl #1
234b17aa8: fmul v6.4s, v6.4s, v10.s[2]
234b17aac: fmul v7.4s, v7.4s, v12.s[0]
234b17ab0: ldr q24, [x8]
234b17ab4: ldr q25, [x8, x4]
234b17ab8: add x8, x8, x4, lsl #1
234b17abc: fsub v22.4s, v22.4s, v4.4s
234b17ac0: fsub v23.4s, v23.4s, v5.4s
234b17ac4: ldr q0, [x11]
234b17ac8: ldr q1, [x11, x4]
234b17acc: add x11, x11, x4, lsl #1
234b17ad0: fmul v4.4s, v28.4s, v10.s[0]
234b17ad4: fmul v5.4s, v29.4s, v11.s[2]
234b17ad8: ldr q30, [x12]
234b17adc: ldr q31, [x12, x4]
234b17ae0: add x12, x12, x4, lsl #1
234b17ae4: fmul v20.4s, v24.4s, v10.s[1]
234b17ae8: fmul v21.4s, v25.4s, v11.s[3]
234b17aec: str q16, [x16]
234b17af0: str q17, [x16, x4]
234b17af4: add x16, x16, x4, lsl #1
234b17af8: fmul v16.4s, v0.4s, v11.s[0]
234b17afc: fmul v17.4s, v1.4s, v12.s[2]
234b17b00: fmul v18.4s, v30.4s, v11.s[1]
234b17b04: fmul v19.4s, v31.4s, v12.s[3]
234b17b08: str q26, [x19]
234b17b0c: str q27, [x19, x4]
234b17b10: add x19, x19, x4, lsl #1
234b17b14: fmul v28.4s, v28.4s, v10.s[1]
234b17b18: fmul v29.4s, v29.4s, v11.s[3]
234b17b1c: fmul v8.4s, v24.4s, v10.s[0]
234b17b20: fmul v9.4s, v25.4s, v11.s[2]
234b17b24: ldr q24, [x5]
234b17b28: ldr q25, [x5, x4]
234b17b2c: add x5, x5, x4, lsl #1
234b17b30: fmul v26.4s, v0.4s, v11.s[1]
234b17b34: fmul v27.4s, v1.4s, v12.s[3]
234b17b38: ldr q0, [x6]
234b17b3c: ldr q1, [x6, x4]
234b17b40: add x6, x6, x4, lsl #1
234b17b44: fmul v30.4s, v30.4s, v11.s[0]
234b17b48: fmul v31.4s, v31.4s, v12.s[2]
234b17b4c: b.gt #0x234b17970
234b17b50: fsub v4.4s, v4.4s, v20.4s
234b17b54: fsub v5.4s, v5.4s, v21.4s
234b17b58: fsub v16.4s, v16.4s, v18.4s
234b17b5c: fsub v17.4s, v17.4s, v19.4s
234b17b60: fadd v18.4s, v24.4s, v22.4s
234b17b64: fadd v19.4s, v25.4s, v23.4s
234b17b68: fadd v20.4s, v4.4s, v16.4s
234b17b6c: fadd v21.4s, v5.4s, v17.4s
234b17b70: fadd v28.4s, v28.4s, v8.4s
234b17b74: fadd v29.4s, v29.4s, v9.4s
234b17b78: fadd v8.4s, v18.4s, v20.4s
234b17b7c: fadd v9.4s, v19.4s, v21.4s
234b17b80: fsub v18.4s, v18.4s, v20.4s
234b17b84: fsub v19.4s, v19.4s, v21.4s
234b17b88: fadd v26.4s, v26.4s, v30.4s
234b17b8c: fadd v27.4s, v27.4s, v31.4s
234b17b90: str q8, [x0]
234b17b94: str q9, [x0, x4]
234b17b98: fsub v22.4s, v24.4s, v22.4s
234b17b9c: fsub v23.4s, v25.4s, v23.4s
234b17ba0: fsub v24.4s, v28.4s, v26.4s
234b17ba4: fsub v25.4s, v29.4s, v27.4s
234b17ba8: str q18, [x13]
234b17bac: str q19, [x13, x4]
234b17bb0: fadd v30.4s, v2.4s, v6.4s
234b17bb4: fadd v31.4s, v3.4s, v7.4s
234b17bb8: fsub v8.4s, v22.4s, v24.4s
234b17bbc: fsub v9.4s, v23.4s, v25.4s
234b17bc0: fadd v26.4s, v28.4s, v26.4s
234b17bc4: fadd v27.4s, v29.4s, v27.4s
234b17bc8: fadd v24.4s, v22.4s, v24.4s
234b17bcc: fadd v25.4s, v23.4s, v25.4s
234b17bd0: str q8, [x15]
234b17bd4: str q9, [x15, x4]
234b17bd8: fadd v8.4s, v0.4s, v30.4s
234b17bdc: fadd v9.4s, v1.4s, v31.4s
234b17be0: fadd v28.4s, v8.4s, v26.4s
234b17be4: fadd v29.4s, v9.4s, v27.4s
234b17be8: str q24, [x17]
234b17bec: str q25, [x17, x4]
234b17bf0: fsub v0.4s, v0.4s, v30.4s
234b17bf4: fsub v1.4s, v1.4s, v31.4s
234b17bf8: fsub v24.4s, v8.4s, v26.4s
234b17bfc: fsub v25.4s, v9.4s, v27.4s
234b17c00: str q28, [x1]
234b17c04: str q29, [x1, x4]
234b17c08: fsub v26.4s, v4.4s, v16.4s
234b17c0c: fsub v27.4s, v5.4s, v17.4s
234b17c10: fadd v16.4s, v0.4s, v26.4s
234b17c14: fadd v17.4s, v1.4s, v27.4s
234b17c18: str q24, [x14]
234b17c1c: str q25, [x14, x4]
234b17c20: fsub v26.4s, v0.4s, v26.4s
234b17c24: fsub v27.4s, v1.4s, v27.4s
234b17c28: str q16, [x16]
234b17c2c: str q17, [x16, x4]
234b17c30: str q26, [x19]
234b17c34: str q27, [x19, x4]
234b17c38: mov x9, x29
234b17c3c: ldp x19, x20, [x9, #-0x10]
234b17c40: ldp x29, x30, [x9], #0x10
234b17c44: ld1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234b17c48: ld1 {v12.4s}, [x9]
234b17c4c: add sp, sp, #0x70
234b17c50: retab 
