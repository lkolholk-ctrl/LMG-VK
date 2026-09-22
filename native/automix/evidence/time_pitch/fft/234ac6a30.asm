234ac6a30: pacibsp 
234ac6a34: sub x9, sp, #0
234ac6a38: sub sp, sp, #0x10
234ac6a3c: stp x29, x30, [x9, #-0x10]!
234ac6a40: mov x29, x9
234ac6a44: sub x30, x3, #2
234ac6a48: add x30, x30, #0
234ac6a4c: lsl x30, x2, x30
234ac6a50: mov x4, x0
234ac6a54: mov x8, x1
234ac6a58: add x5, x4, x30
234ac6a5c: add x9, x8, x30
234ac6a60: mov x12, x5
234ac6a64: mov x13, x9
234ac6a68: add x6, x4, x30, lsl #1
234ac6a6c: add x10, x8, x30, lsl #1
234ac6a70: mov x14, x6
234ac6a74: mov x15, x10
234ac6a78: add x7, x5, x30, lsl #1
234ac6a7c: add x11, x9, x30, lsl #1
234ac6a80: mov x16, x7
234ac6a84: mov x17, x11
234ac6a88: mov x30, #1
234ac6a8c: lsl x3, x30, x3
234ac6a90: lsl x3, x3, #2
234ac6a94: subs x2, x2, #0x20
234ac6a98: b.lt #0x234ac6d40
234ac6a9c: ldr q18, [x4]
234ac6aa0: ldr q19, [x4, x3]
234ac6aa4: add x4, x4, x3, lsl #1
234ac6aa8: subs x2, x2, #0x20
234ac6aac: ldr q22, [x6]
234ac6ab0: ldr q23, [x6, x3]
234ac6ab4: add x6, x6, x3, lsl #1
234ac6ab8: ldr q28, [x5]
234ac6abc: ldr q29, [x5, x3]
234ac6ac0: add x5, x5, x3, lsl #1
234ac6ac4: ldr q2, [x7]
234ac6ac8: ldr q3, [x7, x3]
234ac6acc: add x7, x7, x3, lsl #1
234ac6ad0: fadd v16.4s, v18.4s, v22.4s
234ac6ad4: fadd v17.4s, v19.4s, v23.4s
234ac6ad8: fsub v0.4s, v18.4s, v22.4s
234ac6adc: fsub v1.4s, v19.4s, v23.4s
234ac6ae0: ldr q18, [x8]
234ac6ae4: ldr q19, [x8, x3]
234ac6ae8: add x8, x8, x3, lsl #1
234ac6aec: fadd v24.4s, v28.4s, v2.4s
234ac6af0: fadd v25.4s, v29.4s, v3.4s
234ac6af4: fsub v2.4s, v28.4s, v2.4s
234ac6af8: fsub v3.4s, v29.4s, v3.4s
234ac6afc: ldr q26, [x10]
234ac6b00: ldr q27, [x10, x3]
234ac6b04: add x10, x10, x3, lsl #1
234ac6b08: fadd v22.4s, v16.4s, v24.4s
234ac6b0c: fadd v23.4s, v17.4s, v25.4s
234ac6b10: fsub v20.4s, v16.4s, v24.4s
234ac6b14: fsub v21.4s, v17.4s, v25.4s
234ac6b18: ldr q28, [x9]
234ac6b1c: ldr q29, [x9, x3]
234ac6b20: add x9, x9, x3, lsl #1
234ac6b24: fadd v24.4s, v18.4s, v26.4s
234ac6b28: fadd v25.4s, v19.4s, v27.4s
234ac6b2c: fsub v16.4s, v18.4s, v26.4s
234ac6b30: fsub v17.4s, v19.4s, v27.4s
234ac6b34: ldr q6, [x11]
234ac6b38: ldr q7, [x11, x3]
234ac6b3c: add x11, x11, x3, lsl #1
234ac6b40: fadd v26.4s, v28.4s, v6.4s
234ac6b44: fadd v27.4s, v29.4s, v7.4s
234ac6b48: fsub v6.4s, v28.4s, v6.4s
234ac6b4c: fsub v7.4s, v29.4s, v7.4s
234ac6b50: b.lt #0x234ac6ca8
234ac6b54: nop 
234ac6b58: nop 
234ac6b5c: nop 
234ac6b60: ldr q18, [x4]
234ac6b64: ldr q19, [x4, x3]
234ac6b68: add x4, x4, x3, lsl #1
234ac6b6c: str q22, [x0]
234ac6b70: str q23, [x0, x3]
234ac6b74: add x0, x0, x3, lsl #1
234ac6b78: fadd v4.4s, v24.4s, v26.4s
234ac6b7c: fadd v5.4s, v25.4s, v27.4s
234ac6b80: fsub v24.4s, v24.4s, v26.4s
234ac6b84: fsub v25.4s, v25.4s, v27.4s
234ac6b88: subs x2, x2, #0x20
234ac6b8c: ldr q22, [x6]
234ac6b90: ldr q23, [x6, x3]
234ac6b94: add x6, x6, x3, lsl #1
234ac6b98: str q20, [x12]
234ac6b9c: str q21, [x12, x3]
234ac6ba0: add x12, x12, x3, lsl #1
234ac6ba4: fsub v26.4s, v0.4s, v6.4s
234ac6ba8: fsub v27.4s, v1.4s, v7.4s
234ac6bac: fadd v20.4s, v0.4s, v6.4s
234ac6bb0: fadd v21.4s, v1.4s, v7.4s
234ac6bb4: ldr q28, [x5]
234ac6bb8: ldr q29, [x5, x3]
234ac6bbc: add x5, x5, x3, lsl #1
234ac6bc0: str q4, [x1]
234ac6bc4: str q5, [x1, x3]
234ac6bc8: add x1, x1, x3, lsl #1
234ac6bcc: fadd v6.4s, v16.4s, v2.4s
234ac6bd0: fadd v7.4s, v17.4s, v3.4s
234ac6bd4: fsub v4.4s, v16.4s, v2.4s
234ac6bd8: fsub v5.4s, v17.4s, v3.4s
234ac6bdc: ldr q2, [x7]
234ac6be0: ldr q3, [x7, x3]
234ac6be4: add x7, x7, x3, lsl #1
234ac6be8: str q24, [x13]
234ac6bec: str q25, [x13, x3]
234ac6bf0: add x13, x13, x3, lsl #1
234ac6bf4: fadd v16.4s, v18.4s, v22.4s
234ac6bf8: fadd v17.4s, v19.4s, v23.4s
234ac6bfc: fsub v0.4s, v18.4s, v22.4s
234ac6c00: fsub v1.4s, v19.4s, v23.4s
234ac6c04: ldr q18, [x8]
234ac6c08: ldr q19, [x8, x3]
234ac6c0c: add x8, x8, x3, lsl #1
234ac6c10: str q26, [x14]
234ac6c14: str q27, [x14, x3]
234ac6c18: add x14, x14, x3, lsl #1
234ac6c1c: fadd v24.4s, v28.4s, v2.4s
234ac6c20: fadd v25.4s, v29.4s, v3.4s
234ac6c24: fsub v2.4s, v28.4s, v2.4s
234ac6c28: fsub v3.4s, v29.4s, v3.4s
234ac6c2c: ldr q26, [x10]
234ac6c30: ldr q27, [x10, x3]
234ac6c34: add x10, x10, x3, lsl #1
234ac6c38: str q20, [x16]
234ac6c3c: str q21, [x16, x3]
234ac6c40: add x16, x16, x3, lsl #1
234ac6c44: fadd v22.4s, v16.4s, v24.4s
234ac6c48: fadd v23.4s, v17.4s, v25.4s
234ac6c4c: fsub v20.4s, v16.4s, v24.4s
234ac6c50: fsub v21.4s, v17.4s, v25.4s
234ac6c54: ldr q28, [x9]
234ac6c58: ldr q29, [x9, x3]
234ac6c5c: add x9, x9, x3, lsl #1
234ac6c60: str q6, [x15]
234ac6c64: str q7, [x15, x3]
234ac6c68: add x15, x15, x3, lsl #1
234ac6c6c: fadd v24.4s, v18.4s, v26.4s
234ac6c70: fadd v25.4s, v19.4s, v27.4s
234ac6c74: fsub v16.4s, v18.4s, v26.4s
234ac6c78: fsub v17.4s, v19.4s, v27.4s
234ac6c7c: ldr q6, [x11]
234ac6c80: ldr q7, [x11, x3]
234ac6c84: add x11, x11, x3, lsl #1
234ac6c88: str q4, [x17]
234ac6c8c: str q5, [x17, x3]
234ac6c90: add x17, x17, x3, lsl #1
234ac6c94: fadd v26.4s, v28.4s, v6.4s
234ac6c98: fadd v27.4s, v29.4s, v7.4s
234ac6c9c: fsub v6.4s, v28.4s, v6.4s
234ac6ca0: fsub v7.4s, v29.4s, v7.4s
234ac6ca4: b.ge #0x234ac6b60
234ac6ca8: str q22, [x0]
234ac6cac: str q23, [x0, x3]
234ac6cb0: add x0, x0, x3, lsl #1
234ac6cb4: fadd v4.4s, v24.4s, v26.4s
234ac6cb8: fadd v5.4s, v25.4s, v27.4s
234ac6cbc: adds x2, x2, #0x20
234ac6cc0: fsub v24.4s, v24.4s, v26.4s
234ac6cc4: fsub v25.4s, v25.4s, v27.4s
234ac6cc8: str q20, [x12]
234ac6ccc: str q21, [x12, x3]
234ac6cd0: add x12, x12, x3, lsl #1
234ac6cd4: fsub v26.4s, v0.4s, v6.4s
234ac6cd8: fsub v27.4s, v1.4s, v7.4s
234ac6cdc: fadd v20.4s, v0.4s, v6.4s
234ac6ce0: fadd v21.4s, v1.4s, v7.4s
234ac6ce4: str q4, [x1]
234ac6ce8: str q5, [x1, x3]
234ac6cec: add x1, x1, x3, lsl #1
234ac6cf0: fadd v6.4s, v16.4s, v2.4s
234ac6cf4: fadd v7.4s, v17.4s, v3.4s
234ac6cf8: fsub v4.4s, v16.4s, v2.4s
234ac6cfc: fsub v5.4s, v17.4s, v3.4s
234ac6d00: str q24, [x13]
234ac6d04: str q25, [x13, x3]
234ac6d08: add x13, x13, x3, lsl #1
234ac6d0c: str q26, [x14]
234ac6d10: str q27, [x14, x3]
234ac6d14: add x14, x14, x3, lsl #1
234ac6d18: str q20, [x16]
234ac6d1c: str q21, [x16, x3]
234ac6d20: add x16, x16, x3, lsl #1
234ac6d24: str q6, [x15]
234ac6d28: str q7, [x15, x3]
234ac6d2c: add x15, x15, x3, lsl #1
234ac6d30: str q4, [x17]
234ac6d34: str q5, [x17, x3]
234ac6d38: add x17, x17, x3, lsl #1
234ac6d3c: b.eq #0x234ac6dc0
234ac6d40: ldr q18, [x4]
234ac6d44: ldr q22, [x6]
234ac6d48: ldr q28, [x5]
234ac6d4c: ldr q2, [x7]
234ac6d50: fadd v16.4s, v18.4s, v22.4s
234ac6d54: fsub v0.4s, v18.4s, v22.4s
234ac6d58: ldr q18, [x8]
234ac6d5c: fadd v24.4s, v28.4s, v2.4s
234ac6d60: fsub v2.4s, v28.4s, v2.4s
234ac6d64: ldr q26, [x10]
234ac6d68: fadd v22.4s, v16.4s, v24.4s
234ac6d6c: fsub v20.4s, v16.4s, v24.4s
234ac6d70: ldr q28, [x9]
234ac6d74: fadd v24.4s, v18.4s, v26.4s
234ac6d78: fsub v16.4s, v18.4s, v26.4s
234ac6d7c: ldr q6, [x11]
234ac6d80: fadd v26.4s, v28.4s, v6.4s
234ac6d84: fsub v6.4s, v28.4s, v6.4s
234ac6d88: str q22, [x0]
234ac6d8c: fadd v4.4s, v24.4s, v26.4s
234ac6d90: fsub v24.4s, v24.4s, v26.4s
234ac6d94: str q20, [x12]
234ac6d98: fsub v26.4s, v0.4s, v6.4s
234ac6d9c: fadd v20.4s, v0.4s, v6.4s
234ac6da0: str q4, [x1]
234ac6da4: fadd v6.4s, v16.4s, v2.4s
234ac6da8: fsub v4.4s, v16.4s, v2.4s
234ac6dac: str q24, [x13]
234ac6db0: str q26, [x14]
234ac6db4: str q20, [x16]
234ac6db8: str q6, [x15]
234ac6dbc: str q4, [x17]
234ac6dc0: ldp x29, x30, [x29]
234ac6dc4: add sp, sp, #0x10
234ac6dc8: retab 
234ac6dcc: udf #0
