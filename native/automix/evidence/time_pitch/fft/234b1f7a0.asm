234b1f7a0: pacibsp 
234b1f7a4: sub x9, sp, #0x80
234b1f7a8: sub sp, sp, #0xe0
234b1f7ac: st1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234b1f7b0: st1 {v12.4s, v13.4s, v14.4s, v15.4s}, [x9], #64
234b1f7b4: stp x29, x30, [x9, #-0x90]!
234b1f7b8: mov x29, x9
234b1f7bc: stp x27, x28, [x9, #-0x10]
234b1f7c0: stp x25, x26, [x9, #-0x20]
234b1f7c4: stp x23, x24, [x9, #-0x30]
234b1f7c8: stp x21, x22, [x9, #-0x40]
234b1f7cc: stp x19, x20, [x9, #-0x50]
234b1f7d0: lsl x12, x2, #2
234b1f7d4: sub x1, x1, x0
234b1f7d8: lsl x4, x12, #2
234b1f7dc: sub x4, x4, x12
234b1f7e0: neg x12, x12
234b1f7e4: add x1, x1, x4
234b1f7e8: add x0, x0, x4
234b1f7ec: clz x2, x2
234b1f7f0: mov x5, #0
234b1f7f4: mov x6, #0
234b1f7f8: sub x2, x2, #5
234b1f7fc: add x17, x0, x5
234b1f800: ld1 {v19.4s}, [x17], x12
234b1f804: ld1 {v18.4s}, [x17], x12
234b1f808: zip1 v22.4s, v18.4s, v19.4s
234b1f80c: zip2 v23.4s, v18.4s, v19.4s
234b1f810: ld1 {v17.4s}, [x17], x12
234b1f814: ld1 {v16.4s}, [x17], x1
234b1f818: zip1 v20.4s, v16.4s, v17.4s
234b1f81c: zip2 v21.4s, v16.4s, v17.4s
234b1f820: ld1 {v27.4s}, [x17], x12
234b1f824: ld1 {v26.4s}, [x17], x12
234b1f828: zip1 v30.4s, v26.4s, v27.4s
234b1f82c: zip2 v31.4s, v26.4s, v27.4s
234b1f830: ld1 {v25.4s}, [x17], x12
234b1f834: ld1 {v24.4s}, [x17]
234b1f838: zip1 v28.4s, v24.4s, v25.4s
234b1f83c: zip2 v29.4s, v24.4s, v25.4s
234b1f840: zip1 v0.4s, v20.4s, v22.4s
234b1f844: zip2 v1.4s, v20.4s, v22.4s
234b1f848: zip1 v2.4s, v21.4s, v23.4s
234b1f84c: zip2 v3.4s, v21.4s, v23.4s
234b1f850: zip1 v4.4s, v28.4s, v30.4s
234b1f854: zip2 v5.4s, v28.4s, v30.4s
234b1f858: zip1 v6.4s, v29.4s, v31.4s
234b1f85c: zip2 v7.4s, v29.4s, v31.4s
234b1f860: ld1 {v9.4s, v10.4s}, [x3], #32
234b1f864: fmul v14.4s, v1.4s, v9.4s
234b1f868: fmul v1.4s, v1.4s, v10.4s
234b1f86c: fmul v8.4s, v5.4s, v10.4s
234b1f870: fmul v5.4s, v5.4s, v9.4s
234b1f874: fsub v11.4s, v14.4s, v8.4s
234b1f878: ld1 {v8.4s, v9.4s}, [x3], #32
234b1f87c: fadd v1.4s, v1.4s, v5.4s
234b1f880: fmul v13.4s, v2.4s, v8.4s
234b1f884: fmul v2.4s, v2.4s, v9.4s
234b1f888: ld1 {v14.4s, v15.4s}, [x3], #32
234b1f88c: fmul v9.4s, v6.4s, v9.4s
234b1f890: fmul v6.4s, v6.4s, v8.4s
234b1f894: fmul v8.4s, v3.4s, v15.4s
234b1f898: fmul v12.4s, v7.4s, v14.4s
234b1f89c: add x9, x0, x6
234b1f8a0: fsub v13.4s, v13.4s, v9.4s
234b1f8a4: fadd v9.4s, v8.4s, v12.4s
234b1f8a8: fsub v8.4s, v0.4s, v13.4s
234b1f8ac: fsub v12.4s, v1.4s, v9.4s
234b1f8b0: fmul v3.4s, v3.4s, v14.4s
234b1f8b4: fadd v14.4s, v8.4s, v12.4s
234b1f8b8: fmul v7.4s, v7.4s, v15.4s
234b1f8bc: fadd v6.4s, v2.4s, v6.4s
234b1f8c0: fsub v7.4s, v3.4s, v7.4s
234b1f8c4: fadd v5.4s, v0.4s, v13.4s
234b1f8c8: nop 
234b1f8cc: nop 
234b1f8d0: add x6, x6, #0x10
234b1f8d4: rbit x5, x6
234b1f8d8: lsr x5, x5, x2
234b1f8dc: cmp x6, x5
234b1f8e0: add x17, x0, x5
234b1f8e4: b.hi #0x234b1fb40
234b1f8e8: mov x4, x6
234b1f8ec: nop 
234b1f8f0: fadd v0.4s, v11.4s, v7.4s
234b1f8f4: ld1 {v19.4s}, [x17], x12
234b1f8f8: ld1 {v18.4s}, [x17], x12
234b1f8fc: zip1 v22.4s, v18.4s, v19.4s
234b1f900: zip2 v23.4s, v18.4s, v19.4s
234b1f904: ld1 {v17.4s}, [x17], x12
234b1f908: ld1 {v16.4s}, [x17], x1
234b1f90c: zip1 v20.4s, v16.4s, v17.4s
234b1f910: zip2 v21.4s, v16.4s, v17.4s
234b1f914: ld1 {v27.4s}, [x17], x12
234b1f918: ld1 {v26.4s}, [x17], x12
234b1f91c: zip1 v30.4s, v26.4s, v27.4s
234b1f920: zip2 v31.4s, v26.4s, v27.4s
234b1f924: ld1 {v25.4s}, [x17], x12
234b1f928: ld1 {v24.4s}, [x17]
234b1f92c: zip1 v28.4s, v24.4s, v25.4s
234b1f930: zip2 v29.4s, v24.4s, v25.4s
234b1f934: fadd v9.4s, v1.4s, v9.4s
234b1f938: fsub v1.4s, v5.4s, v0.4s
234b1f93c: zip1 v2.4s, v21.4s, v23.4s
234b1f940: zip2 v3.4s, v21.4s, v23.4s
234b1f944: st1 {v14.4s}, [x9], x12
234b1f948: fsub v8.4s, v8.4s, v12.4s
234b1f94c: fsub v13.4s, v4.4s, v6.4s
234b1f950: st1 {v1.4s}, [x9], x12
234b1f954: fadd v12.4s, v4.4s, v6.4s
234b1f958: fadd v14.4s, v5.4s, v0.4s
234b1f95c: fsub v4.4s, v11.4s, v7.4s
234b1f960: fsub v11.4s, v12.4s, v9.4s
234b1f964: zip1 v0.4s, v20.4s, v22.4s
234b1f968: zip2 v1.4s, v20.4s, v22.4s
234b1f96c: st1 {v8.4s}, [x9], x12
234b1f970: fsub v8.4s, v13.4s, v4.4s
234b1f974: fadd v13.4s, v13.4s, v4.4s
234b1f978: zip1 v6.4s, v29.4s, v31.4s
234b1f97c: zip2 v7.4s, v29.4s, v31.4s
234b1f980: fadd v12.4s, v12.4s, v9.4s
234b1f984: zip1 v4.4s, v28.4s, v30.4s
234b1f988: zip2 v5.4s, v28.4s, v30.4s
234b1f98c: ld1 {v9.4s, v10.4s}, [x3], #32
234b1f990: st1 {v14.4s}, [x9], x1
234b1f994: fmul v14.4s, v1.4s, v9.4s
234b1f998: fmul v1.4s, v1.4s, v10.4s
234b1f99c: st1 {v8.4s}, [x9], x12
234b1f9a0: fmul v8.4s, v5.4s, v10.4s
234b1f9a4: fmul v5.4s, v5.4s, v9.4s
234b1f9a8: st1 {v11.4s}, [x9], x12
234b1f9ac: fsub v11.4s, v14.4s, v8.4s
234b1f9b0: ld1 {v8.4s, v9.4s}, [x3], #32
234b1f9b4: fadd v1.4s, v1.4s, v5.4s
234b1f9b8: st1 {v13.4s}, [x9], x12
234b1f9bc: fmul v13.4s, v2.4s, v8.4s
234b1f9c0: fmul v2.4s, v2.4s, v9.4s
234b1f9c4: ld1 {v14.4s, v15.4s}, [x3], #32
234b1f9c8: fmul v9.4s, v6.4s, v9.4s
234b1f9cc: fmul v6.4s, v6.4s, v8.4s
234b1f9d0: st1 {v12.4s}, [x9]
234b1f9d4: fmul v8.4s, v3.4s, v15.4s
234b1f9d8: fmul v12.4s, v7.4s, v14.4s
234b1f9dc: add x9, x0, x4
234b1f9e0: fsub v13.4s, v13.4s, v9.4s
234b1f9e4: fadd v9.4s, v8.4s, v12.4s
234b1f9e8: fsub v8.4s, v0.4s, v13.4s
234b1f9ec: fsub v12.4s, v1.4s, v9.4s
234b1f9f0: cmp x5, x4
234b1f9f4: fmul v3.4s, v3.4s, v14.4s
234b1f9f8: fadd v14.4s, v8.4s, v12.4s
234b1f9fc: fmul v7.4s, v7.4s, v15.4s
234b1fa00: fadd v6.4s, v2.4s, v6.4s
234b1fa04: add x17, x0, x4
234b1fa08: fsub v7.4s, v3.4s, v7.4s
234b1fa0c: fadd v5.4s, v0.4s, v13.4s
234b1fa10: b.eq #0x234b1f8d0
234b1fa14: ld1 {v19.4s}, [x17], x12
234b1fa18: ld1 {v18.4s}, [x17], x12
234b1fa1c: zip1 v22.4s, v18.4s, v19.4s
234b1fa20: zip2 v23.4s, v18.4s, v19.4s
234b1fa24: ld1 {v17.4s}, [x17], x12
234b1fa28: ld1 {v16.4s}, [x17], x1
234b1fa2c: zip1 v20.4s, v16.4s, v17.4s
234b1fa30: zip2 v21.4s, v16.4s, v17.4s
234b1fa34: ld1 {v27.4s}, [x17], x12
234b1fa38: ld1 {v26.4s}, [x17], x12
234b1fa3c: zip1 v30.4s, v26.4s, v27.4s
234b1fa40: zip2 v31.4s, v26.4s, v27.4s
234b1fa44: ld1 {v25.4s}, [x17], x12
234b1fa48: ld1 {v24.4s}, [x17]
234b1fa4c: zip1 v28.4s, v24.4s, v25.4s
234b1fa50: zip2 v29.4s, v24.4s, v25.4s
234b1fa54: fadd v0.4s, v11.4s, v7.4s
234b1fa58: fadd v9.4s, v1.4s, v9.4s
234b1fa5c: fsub v1.4s, v5.4s, v0.4s
234b1fa60: zip1 v2.4s, v21.4s, v23.4s
234b1fa64: zip2 v3.4s, v21.4s, v23.4s
234b1fa68: st1 {v14.4s}, [x9], x12
234b1fa6c: fsub v8.4s, v8.4s, v12.4s
234b1fa70: fsub v13.4s, v4.4s, v6.4s
234b1fa74: st1 {v1.4s}, [x9], x12
234b1fa78: fadd v12.4s, v4.4s, v6.4s
234b1fa7c: fadd v14.4s, v5.4s, v0.4s
234b1fa80: fsub v4.4s, v11.4s, v7.4s
234b1fa84: fsub v11.4s, v12.4s, v9.4s
234b1fa88: zip1 v0.4s, v20.4s, v22.4s
234b1fa8c: zip2 v1.4s, v20.4s, v22.4s
234b1fa90: st1 {v8.4s}, [x9], x12
234b1fa94: fsub v8.4s, v13.4s, v4.4s
234b1fa98: fadd v13.4s, v13.4s, v4.4s
234b1fa9c: zip1 v6.4s, v29.4s, v31.4s
234b1faa0: zip2 v7.4s, v29.4s, v31.4s
234b1faa4: fadd v12.4s, v12.4s, v9.4s
234b1faa8: zip1 v4.4s, v28.4s, v30.4s
234b1faac: zip2 v5.4s, v28.4s, v30.4s
234b1fab0: ld1 {v9.4s, v10.4s}, [x3], #32
234b1fab4: st1 {v14.4s}, [x9], x1
234b1fab8: fmul v14.4s, v1.4s, v9.4s
234b1fabc: fmul v1.4s, v1.4s, v10.4s
234b1fac0: st1 {v8.4s}, [x9], x12
234b1fac4: fmul v8.4s, v5.4s, v10.4s
234b1fac8: fmul v5.4s, v5.4s, v9.4s
234b1facc: st1 {v11.4s}, [x9], x12
234b1fad0: fsub v11.4s, v14.4s, v8.4s
234b1fad4: ld1 {v8.4s, v9.4s}, [x3], #32
234b1fad8: fadd v1.4s, v1.4s, v5.4s
234b1fadc: st1 {v13.4s}, [x9], x12
234b1fae0: fmul v13.4s, v2.4s, v8.4s
234b1fae4: fmul v2.4s, v2.4s, v9.4s
234b1fae8: ld1 {v14.4s, v15.4s}, [x3], #32
234b1faec: fmul v9.4s, v6.4s, v9.4s
234b1faf0: fmul v6.4s, v6.4s, v8.4s
234b1faf4: st1 {v12.4s}, [x9]
234b1faf8: fmul v8.4s, v3.4s, v15.4s
234b1fafc: fmul v12.4s, v7.4s, v14.4s
234b1fb00: add x9, x0, x5
234b1fb04: fsub v13.4s, v13.4s, v9.4s
234b1fb08: fadd v9.4s, v8.4s, v12.4s
234b1fb0c: add x5, x5, #0x10
234b1fb10: fsub v8.4s, v0.4s, v13.4s
234b1fb14: fsub v12.4s, v1.4s, v9.4s
234b1fb18: rbit x4, x5
234b1fb1c: fmul v3.4s, v3.4s, v14.4s
234b1fb20: fadd v14.4s, v8.4s, v12.4s
234b1fb24: lsr x4, x4, x2
234b1fb28: fmul v7.4s, v7.4s, v15.4s
234b1fb2c: fadd v6.4s, v2.4s, v6.4s
234b1fb30: add x17, x0, x5
234b1fb34: fsub v7.4s, v3.4s, v7.4s
234b1fb38: fadd v5.4s, v0.4s, v13.4s
234b1fb3c: b #0x234b1f8f0
234b1fb40: st1 {v14.4s}, [x9], x12
234b1fb44: fadd v0.4s, v11.4s, v7.4s
234b1fb48: fadd v9.4s, v1.4s, v9.4s
234b1fb4c: fsub v1.4s, v5.4s, v0.4s
234b1fb50: fsub v8.4s, v8.4s, v12.4s
234b1fb54: fsub v13.4s, v4.4s, v6.4s
234b1fb58: st1 {v1.4s}, [x9], x12
234b1fb5c: fadd v12.4s, v4.4s, v6.4s
234b1fb60: fadd v14.4s, v5.4s, v0.4s
234b1fb64: st1 {v8.4s}, [x9], x12
234b1fb68: fsub v4.4s, v11.4s, v7.4s
234b1fb6c: fsub v11.4s, v12.4s, v9.4s
234b1fb70: st1 {v14.4s}, [x9], x1
234b1fb74: fsub v8.4s, v13.4s, v4.4s
234b1fb78: fadd v13.4s, v13.4s, v4.4s
234b1fb7c: st1 {v8.4s}, [x9], x12
234b1fb80: fadd v12.4s, v12.4s, v9.4s
234b1fb84: st1 {v11.4s}, [x9], x12
234b1fb88: st1 {v13.4s}, [x9], x12
234b1fb8c: st1 {v12.4s}, [x9]
234b1fb90: mov x9, x29
234b1fb94: ldp x19, x20, [x9, #-0x50]
234b1fb98: ldp x21, x22, [x9, #-0x40]
234b1fb9c: ldp x23, x24, [x9, #-0x30]
234b1fba0: ldp x25, x26, [x9, #-0x20]
234b1fba4: ldp x27, x28, [x9, #-0x10]
234b1fba8: ldp x29, x30, [x9], #0x10
234b1fbac: ld1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234b1fbb0: ld1 {v12.4s, v13.4s, v14.4s, v15.4s}, [x9]
234b1fbb4: add sp, sp, #0xe0
234b1fbb8: retab 
