234ace690: pacibsp 
234ace694: sub x9, sp, #0x70
234ace698: sub sp, sp, #0x80
234ace69c: st1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234ace6a0: st1 {v12.4s, v13.4s, v14.4s}, [x9], #48
234ace6a4: stp x29, x30, [x9, #-0x80]!
234ace6a8: mov x29, x9
234ace6ac: adr x12, #0x234acece4
234ace6b0: ld1 {v14.s}[0], [x12]
234ace6b4: lsr x12, x4, #1
234ace6b8: sub x17, x12, x12, lsl #3
234ace6bc: lsl x5, x12, #1
234ace6c0: add x6, x17, x12, lsl #1
234ace6c4: add x17, x17, #0x20
234ace6c8: subs x4, x4, #0x40
234ace6cc: b.lt #0x234aceb68
234ace6d0: ld1 {v8.4s, v9.4s}, [x2], x5
234ace6d4: ld1 {v30.4s, v31.4s}, [x2], x5
234ace6d8: ld1 {v12.4s, v13.4s}, [x2], x5
234ace6dc: ld1 {v10.4s, v11.4s}, [x2], x6
234ace6e0: ld1 {v6.4s, v7.4s}, [x2], x5
234ace6e4: ld1 {v16.4s, v17.4s}, [x2], x5
234ace6e8: ld1 {v0.4s, v1.4s}, [x2], x5
234ace6ec: ld1 {v22.4s, v23.4s}, [x2], x17
234ace6f0: fadd v18.4s, v8.4s, v12.4s
234ace6f4: fadd v19.4s, v9.4s, v13.4s
234ace6f8: fsub v2.4s, v8.4s, v12.4s
234ace6fc: fsub v3.4s, v9.4s, v13.4s
234ace700: fadd v24.4s, v30.4s, v10.4s
234ace704: fadd v25.4s, v31.4s, v11.4s
234ace708: fsub v4.4s, v30.4s, v10.4s
234ace70c: fsub v5.4s, v31.4s, v11.4s
234ace710: fadd v20.4s, v6.4s, v0.4s
234ace714: fadd v21.4s, v7.4s, v1.4s
234ace718: fadd v12.4s, v16.4s, v22.4s
234ace71c: fadd v13.4s, v17.4s, v23.4s
234ace720: fadd v28.4s, v18.4s, v24.4s
234ace724: fadd v29.4s, v19.4s, v25.4s
234ace728: fadd v10.4s, v20.4s, v12.4s
234ace72c: fadd v11.4s, v21.4s, v13.4s
234ace730: ld1 {v26.4s, v27.4s}, [x3], x5
234ace734: fsub v18.4s, v18.4s, v24.4s
234ace738: fsub v19.4s, v19.4s, v25.4s
234ace73c: ld1 {v24.4s, v25.4s}, [x3], x5
234ace740: fadd v8.4s, v28.4s, v10.4s
234ace744: fadd v9.4s, v29.4s, v11.4s
234ace748: ld1 {v30.4s, v31.4s}, [x3], x5
234ace74c: fsub v10.4s, v28.4s, v10.4s
234ace750: fsub v11.4s, v29.4s, v11.4s
234ace754: ld1 {v28.4s, v29.4s}, [x3], x6
234ace758: fsub v20.4s, v20.4s, v12.4s
234ace75c: fsub v21.4s, v21.4s, v13.4s
234ace760: st1 {v8.4s, v9.4s}, [x0], x12
234ace764: fsub v6.4s, v6.4s, v0.4s
234ace768: fsub v7.4s, v7.4s, v1.4s
234ace76c: fsub v16.4s, v16.4s, v22.4s
234ace770: fsub v17.4s, v17.4s, v23.4s
234ace774: st1 {v10.4s, v11.4s}, [x0], x12
234ace778: fadd v0.4s, v26.4s, v30.4s
234ace77c: fadd v1.4s, v27.4s, v31.4s
234ace780: ld1 {v22.4s, v23.4s}, [x3], x5
234ace784: fsub v26.4s, v26.4s, v30.4s
234ace788: fsub v27.4s, v27.4s, v31.4s
234ace78c: ld1 {v8.4s, v9.4s}, [x3], x5
234ace790: fadd v30.4s, v24.4s, v28.4s
234ace794: fadd v31.4s, v25.4s, v29.4s
234ace798: ld1 {v10.4s, v11.4s}, [x3], x5
234ace79c: fsub v24.4s, v24.4s, v28.4s
234ace7a0: fsub v25.4s, v25.4s, v29.4s
234ace7a4: ld1 {v12.4s, v13.4s}, [x3], x17
234ace7a8: fadd v28.4s, v22.4s, v10.4s
234ace7ac: fadd v29.4s, v23.4s, v11.4s
234ace7b0: fsub v22.4s, v22.4s, v10.4s
234ace7b4: fsub v23.4s, v23.4s, v11.4s
234ace7b8: fadd v10.4s, v8.4s, v12.4s
234ace7bc: fadd v11.4s, v9.4s, v13.4s
234ace7c0: fsub v8.4s, v8.4s, v12.4s
234ace7c4: fsub v9.4s, v9.4s, v13.4s
234ace7c8: subs x4, x4, #0x40
234ace7cc: fadd v12.4s, v0.4s, v30.4s
234ace7d0: fadd v13.4s, v1.4s, v31.4s
234ace7d4: fsub v0.4s, v0.4s, v30.4s
234ace7d8: fsub v1.4s, v1.4s, v31.4s
234ace7dc: fadd v30.4s, v28.4s, v10.4s
234ace7e0: fadd v31.4s, v29.4s, v11.4s
234ace7e4: fsub v28.4s, v28.4s, v10.4s
234ace7e8: fsub v29.4s, v29.4s, v11.4s
234ace7ec: fadd v10.4s, v12.4s, v30.4s
234ace7f0: fadd v11.4s, v13.4s, v31.4s
234ace7f4: fsub v12.4s, v12.4s, v30.4s
234ace7f8: fsub v13.4s, v13.4s, v31.4s
234ace7fc: fsub v30.4s, v18.4s, v28.4s
234ace800: fsub v31.4s, v19.4s, v29.4s
234ace804: st1 {v10.4s, v11.4s}, [x1], x12
234ace808: fadd v28.4s, v18.4s, v28.4s
234ace80c: fadd v29.4s, v19.4s, v29.4s
234ace810: fadd v18.4s, v0.4s, v20.4s
234ace814: fadd v19.4s, v1.4s, v21.4s
234ace818: st1 {v12.4s, v13.4s}, [x1], x12
234ace81c: fsub v0.4s, v0.4s, v20.4s
234ace820: fsub v1.4s, v1.4s, v21.4s
234ace824: fsub v20.4s, v2.4s, v24.4s
234ace828: fsub v21.4s, v3.4s, v25.4s
234ace82c: st1 {v30.4s, v31.4s}, [x0], x12
234ace830: fadd v2.4s, v2.4s, v24.4s
234ace834: fadd v3.4s, v3.4s, v25.4s
234ace838: fadd v24.4s, v26.4s, v4.4s
234ace83c: fadd v25.4s, v27.4s, v5.4s
234ace840: st1 {v28.4s, v29.4s}, [x0], x12
234ace844: fsub v4.4s, v26.4s, v4.4s
234ace848: fsub v5.4s, v27.4s, v5.4s
234ace84c: fsub v28.4s, v6.4s, v8.4s
234ace850: fsub v29.4s, v7.4s, v9.4s
234ace854: st1 {v18.4s, v19.4s}, [x1], x12
234ace858: fadd v26.4s, v6.4s, v8.4s
234ace85c: fadd v27.4s, v7.4s, v9.4s
234ace860: fadd v6.4s, v22.4s, v16.4s
234ace864: fadd v7.4s, v23.4s, v17.4s
234ace868: st1 {v0.4s, v1.4s}, [x1], x12
234ace86c: b.lt #0x234aceab8
234ace870: fsub v0.4s, v22.4s, v16.4s
234ace874: fsub v1.4s, v23.4s, v17.4s
234ace878: fsub v16.4s, v28.4s, v6.4s
234ace87c: fsub v17.4s, v29.4s, v7.4s
234ace880: ld1 {v8.4s, v9.4s}, [x2], x5
234ace884: fadd v6.4s, v28.4s, v6.4s
234ace888: fadd v7.4s, v29.4s, v7.4s
234ace88c: ld1 {v30.4s, v31.4s}, [x2], x5
234ace890: fmul v16.4s, v16.4s, v14.s[0]
234ace894: fmul v17.4s, v17.4s, v14.s[0]
234ace898: ld1 {v12.4s, v13.4s}, [x2], x5
234ace89c: fmul v18.4s, v6.4s, v14.s[0]
234ace8a0: fmul v19.4s, v7.4s, v14.s[0]
234ace8a4: ld1 {v10.4s, v11.4s}, [x2], x6
234ace8a8: fadd v22.4s, v20.4s, v16.4s
234ace8ac: fadd v23.4s, v21.4s, v17.4s
234ace8b0: ld1 {v6.4s, v7.4s}, [x2], x5
234ace8b4: fsub v28.4s, v20.4s, v16.4s
234ace8b8: fsub v29.4s, v21.4s, v17.4s
234ace8bc: ld1 {v16.4s, v17.4s}, [x2], x5
234ace8c0: fadd v20.4s, v24.4s, v18.4s
234ace8c4: fadd v21.4s, v25.4s, v19.4s
234ace8c8: fsub v18.4s, v24.4s, v18.4s
234ace8cc: fsub v19.4s, v25.4s, v19.4s
234ace8d0: fadd v24.4s, v26.4s, v0.4s
234ace8d4: fadd v25.4s, v27.4s, v1.4s
234ace8d8: st1 {v22.4s, v23.4s}, [x0], x12
234ace8dc: fsub v0.4s, v26.4s, v0.4s
234ace8e0: fsub v1.4s, v27.4s, v1.4s
234ace8e4: fmul v22.4s, v24.4s, v14.s[0]
234ace8e8: fmul v23.4s, v25.4s, v14.s[0]
234ace8ec: st1 {v28.4s, v29.4s}, [x0], x12
234ace8f0: fmul v26.4s, v0.4s, v14.s[0]
234ace8f4: fmul v27.4s, v1.4s, v14.s[0]
234ace8f8: ld1 {v0.4s, v1.4s}, [x2], x5
234ace8fc: fsub v24.4s, v2.4s, v22.4s
234ace900: fsub v25.4s, v3.4s, v23.4s
234ace904: st1 {v20.4s, v21.4s}, [x1], x12
234ace908: fadd v28.4s, v2.4s, v22.4s
234ace90c: fadd v29.4s, v3.4s, v23.4s
234ace910: ld1 {v22.4s, v23.4s}, [x2], x17
234ace914: fadd v20.4s, v4.4s, v26.4s
234ace918: fadd v21.4s, v5.4s, v27.4s
234ace91c: st1 {v18.4s, v19.4s}, [x1], x12
234ace920: fsub v26.4s, v4.4s, v26.4s
234ace924: fsub v27.4s, v5.4s, v27.4s
234ace928: fadd v18.4s, v8.4s, v12.4s
234ace92c: fadd v19.4s, v9.4s, v13.4s
234ace930: st1 {v24.4s, v25.4s}, [x0], x12
234ace934: fsub v2.4s, v8.4s, v12.4s
234ace938: fsub v3.4s, v9.4s, v13.4s
234ace93c: fadd v24.4s, v30.4s, v10.4s
234ace940: fadd v25.4s, v31.4s, v11.4s
234ace944: st1 {v20.4s, v21.4s}, [x1], x12
234ace948: fsub v4.4s, v30.4s, v10.4s
234ace94c: fsub v5.4s, v31.4s, v11.4s
234ace950: fadd v20.4s, v6.4s, v0.4s
234ace954: fadd v21.4s, v7.4s, v1.4s
234ace958: st1 {v28.4s, v29.4s}, [x0], x17
234ace95c: fadd v12.4s, v16.4s, v22.4s
234ace960: fadd v13.4s, v17.4s, v23.4s
234ace964: fadd v28.4s, v18.4s, v24.4s
234ace968: fadd v29.4s, v19.4s, v25.4s
234ace96c: st1 {v26.4s, v27.4s}, [x1], x17
234ace970: fadd v10.4s, v20.4s, v12.4s
234ace974: fadd v11.4s, v21.4s, v13.4s
234ace978: ld1 {v26.4s, v27.4s}, [x3], x5
234ace97c: fsub v18.4s, v18.4s, v24.4s
234ace980: fsub v19.4s, v19.4s, v25.4s
234ace984: ld1 {v24.4s, v25.4s}, [x3], x5
234ace988: fadd v8.4s, v28.4s, v10.4s
234ace98c: fadd v9.4s, v29.4s, v11.4s
234ace990: ld1 {v30.4s, v31.4s}, [x3], x5
234ace994: fsub v10.4s, v28.4s, v10.4s
234ace998: fsub v11.4s, v29.4s, v11.4s
234ace99c: ld1 {v28.4s, v29.4s}, [x3], x6
234ace9a0: fsub v20.4s, v20.4s, v12.4s
234ace9a4: fsub v21.4s, v21.4s, v13.4s
234ace9a8: st1 {v8.4s, v9.4s}, [x0], x12
234ace9ac: fsub v6.4s, v6.4s, v0.4s
234ace9b0: fsub v7.4s, v7.4s, v1.4s
234ace9b4: fsub v16.4s, v16.4s, v22.4s
234ace9b8: fsub v17.4s, v17.4s, v23.4s
234ace9bc: st1 {v10.4s, v11.4s}, [x0], x12
234ace9c0: fadd v0.4s, v26.4s, v30.4s
234ace9c4: fadd v1.4s, v27.4s, v31.4s
234ace9c8: ld1 {v22.4s, v23.4s}, [x3], x5
234ace9cc: fsub v26.4s, v26.4s, v30.4s
234ace9d0: fsub v27.4s, v27.4s, v31.4s
234ace9d4: ld1 {v8.4s, v9.4s}, [x3], x5
234ace9d8: fadd v30.4s, v24.4s, v28.4s
234ace9dc: fadd v31.4s, v25.4s, v29.4s
234ace9e0: ld1 {v10.4s, v11.4s}, [x3], x5
234ace9e4: fsub v24.4s, v24.4s, v28.4s
234ace9e8: fsub v25.4s, v25.4s, v29.4s
234ace9ec: ld1 {v12.4s, v13.4s}, [x3], x17
234ace9f0: fadd v28.4s, v22.4s, v10.4s
234ace9f4: fadd v29.4s, v23.4s, v11.4s
234ace9f8: fsub v22.4s, v22.4s, v10.4s
234ace9fc: fsub v23.4s, v23.4s, v11.4s
234acea00: fadd v10.4s, v8.4s, v12.4s
234acea04: fadd v11.4s, v9.4s, v13.4s
234acea08: fsub v8.4s, v8.4s, v12.4s
234acea0c: fsub v9.4s, v9.4s, v13.4s
234acea10: subs x4, x4, #0x40
234acea14: fadd v12.4s, v0.4s, v30.4s
234acea18: fadd v13.4s, v1.4s, v31.4s
234acea1c: fsub v0.4s, v0.4s, v30.4s
234acea20: fsub v1.4s, v1.4s, v31.4s
234acea24: fadd v30.4s, v28.4s, v10.4s
234acea28: fadd v31.4s, v29.4s, v11.4s
234acea2c: fsub v28.4s, v28.4s, v10.4s
234acea30: fsub v29.4s, v29.4s, v11.4s
234acea34: fadd v10.4s, v12.4s, v30.4s
234acea38: fadd v11.4s, v13.4s, v31.4s
234acea3c: fsub v12.4s, v12.4s, v30.4s
234acea40: fsub v13.4s, v13.4s, v31.4s
234acea44: fsub v30.4s, v18.4s, v28.4s
234acea48: fsub v31.4s, v19.4s, v29.4s
234acea4c: st1 {v10.4s, v11.4s}, [x1], x12
234acea50: fadd v28.4s, v18.4s, v28.4s
234acea54: fadd v29.4s, v19.4s, v29.4s
234acea58: fadd v18.4s, v0.4s, v20.4s
234acea5c: fadd v19.4s, v1.4s, v21.4s
234acea60: st1 {v12.4s, v13.4s}, [x1], x12
234acea64: fsub v0.4s, v0.4s, v20.4s
234acea68: fsub v1.4s, v1.4s, v21.4s
234acea6c: fsub v20.4s, v2.4s, v24.4s
234acea70: fsub v21.4s, v3.4s, v25.4s
234acea74: st1 {v30.4s, v31.4s}, [x0], x12
234acea78: fadd v2.4s, v2.4s, v24.4s
234acea7c: fadd v3.4s, v3.4s, v25.4s
234acea80: fadd v24.4s, v26.4s, v4.4s
234acea84: fadd v25.4s, v27.4s, v5.4s
234acea88: st1 {v28.4s, v29.4s}, [x0], x12
234acea8c: fsub v4.4s, v26.4s, v4.4s
234acea90: fsub v5.4s, v27.4s, v5.4s
234acea94: fsub v28.4s, v6.4s, v8.4s
234acea98: fsub v29.4s, v7.4s, v9.4s
234acea9c: st1 {v18.4s, v19.4s}, [x1], x12
234aceaa0: fadd v26.4s, v6.4s, v8.4s
234aceaa4: fadd v27.4s, v7.4s, v9.4s
234aceaa8: fadd v6.4s, v22.4s, v16.4s
234aceaac: fadd v7.4s, v23.4s, v17.4s
234aceab0: st1 {v0.4s, v1.4s}, [x1], x12
234aceab4: b.ge #0x234ace870
234aceab8: fsub v0.4s, v22.4s, v16.4s
234aceabc: fsub v1.4s, v23.4s, v17.4s
234aceac0: adds x4, x4, #0x40
234aceac4: fsub v16.4s, v28.4s, v6.4s
234aceac8: fsub v17.4s, v29.4s, v7.4s
234aceacc: fadd v6.4s, v28.4s, v6.4s
234acead0: fadd v7.4s, v29.4s, v7.4s
234acead4: fmul v16.4s, v16.4s, v14.s[0]
234acead8: fmul v17.4s, v17.4s, v14.s[0]
234aceadc: fmul v18.4s, v6.4s, v14.s[0]
234aceae0: fmul v19.4s, v7.4s, v14.s[0]
234aceae4: fadd v22.4s, v20.4s, v16.4s
234aceae8: fadd v23.4s, v21.4s, v17.4s
234aceaec: fsub v28.4s, v20.4s, v16.4s
234aceaf0: fsub v29.4s, v21.4s, v17.4s
234aceaf4: fadd v20.4s, v24.4s, v18.4s
234aceaf8: fadd v21.4s, v25.4s, v19.4s
234aceafc: fsub v18.4s, v24.4s, v18.4s
234aceb00: fsub v19.4s, v25.4s, v19.4s
234aceb04: fadd v24.4s, v26.4s, v0.4s
234aceb08: fadd v25.4s, v27.4s, v1.4s
234aceb0c: st1 {v22.4s, v23.4s}, [x0], x12
234aceb10: fsub v0.4s, v26.4s, v0.4s
234aceb14: fsub v1.4s, v27.4s, v1.4s
234aceb18: fmul v22.4s, v24.4s, v14.s[0]
234aceb1c: fmul v23.4s, v25.4s, v14.s[0]
234aceb20: st1 {v28.4s, v29.4s}, [x0], x12
234aceb24: fmul v26.4s, v0.4s, v14.s[0]
234aceb28: fmul v27.4s, v1.4s, v14.s[0]
234aceb2c: fsub v24.4s, v2.4s, v22.4s
234aceb30: fsub v25.4s, v3.4s, v23.4s
234aceb34: st1 {v20.4s, v21.4s}, [x1], x12
234aceb38: fadd v28.4s, v2.4s, v22.4s
234aceb3c: fadd v29.4s, v3.4s, v23.4s
234aceb40: fadd v20.4s, v4.4s, v26.4s
234aceb44: fadd v21.4s, v5.4s, v27.4s
234aceb48: st1 {v18.4s, v19.4s}, [x1], x12
234aceb4c: fsub v26.4s, v4.4s, v26.4s
234aceb50: fsub v27.4s, v5.4s, v27.4s
234aceb54: st1 {v24.4s, v25.4s}, [x0], x12
234aceb58: st1 {v20.4s, v21.4s}, [x1], x12
234aceb5c: st1 {v28.4s, v29.4s}, [x0], x17
234aceb60: st1 {v26.4s, v27.4s}, [x1], x17
234aceb64: b.eq #0x234aceccc
234aceb68: ld1 {v8.4s}, [x2], x5
234aceb6c: ld1 {v30.4s}, [x2], x5
234aceb70: ld1 {v12.4s}, [x2], x5
234aceb74: ld1 {v10.4s}, [x2], x6
234aceb78: ld1 {v6.4s}, [x2], x5
234aceb7c: ld1 {v16.4s}, [x2], x5
234aceb80: ld1 {v0.4s}, [x2], x5
234aceb84: ld1 {v22.4s}, [x2]
234aceb88: fadd v18.4s, v8.4s, v12.4s
234aceb8c: fsub v2.4s, v8.4s, v12.4s
234aceb90: fadd v24.4s, v30.4s, v10.4s
234aceb94: fsub v4.4s, v30.4s, v10.4s
234aceb98: fadd v20.4s, v6.4s, v0.4s
234aceb9c: fadd v12.4s, v16.4s, v22.4s
234aceba0: fadd v28.4s, v18.4s, v24.4s
234aceba4: fadd v10.4s, v20.4s, v12.4s
234aceba8: ld1 {v26.4s}, [x3], x5
234acebac: fsub v18.4s, v18.4s, v24.4s
234acebb0: ld1 {v24.4s}, [x3], x5
234acebb4: fadd v8.4s, v28.4s, v10.4s
234acebb8: ld1 {v30.4s}, [x3], x5
234acebbc: fsub v10.4s, v28.4s, v10.4s
234acebc0: ld1 {v28.4s}, [x3], x6
234acebc4: fsub v20.4s, v20.4s, v12.4s
234acebc8: st1 {v8.4s}, [x0], x12
234acebcc: fsub v6.4s, v6.4s, v0.4s
234acebd0: fsub v16.4s, v16.4s, v22.4s
234acebd4: st1 {v10.4s}, [x0], x12
234acebd8: fadd v0.4s, v26.4s, v30.4s
234acebdc: ld1 {v22.4s}, [x3], x5
234acebe0: fsub v26.4s, v26.4s, v30.4s
234acebe4: ld1 {v8.4s}, [x3], x5
234acebe8: fadd v30.4s, v24.4s, v28.4s
234acebec: ld1 {v10.4s}, [x3], x5
234acebf0: fsub v24.4s, v24.4s, v28.4s
234acebf4: ld1 {v12.4s}, [x3]
234acebf8: fadd v28.4s, v22.4s, v10.4s
234acebfc: fsub v22.4s, v22.4s, v10.4s
234acec00: fadd v10.4s, v8.4s, v12.4s
234acec04: fsub v8.4s, v8.4s, v12.4s
234acec08: subs x4, x4, #0x40
234acec0c: fadd v12.4s, v0.4s, v30.4s
234acec10: fsub v0.4s, v0.4s, v30.4s
234acec14: fadd v30.4s, v28.4s, v10.4s
234acec18: fsub v28.4s, v28.4s, v10.4s
234acec1c: fadd v10.4s, v12.4s, v30.4s
234acec20: fsub v12.4s, v12.4s, v30.4s
234acec24: fsub v30.4s, v18.4s, v28.4s
234acec28: st1 {v10.4s}, [x1], x12
234acec2c: fadd v28.4s, v18.4s, v28.4s
234acec30: fadd v18.4s, v0.4s, v20.4s
234acec34: st1 {v12.4s}, [x1], x12
234acec38: fsub v0.4s, v0.4s, v20.4s
234acec3c: fsub v20.4s, v2.4s, v24.4s
234acec40: st1 {v30.4s}, [x0], x12
234acec44: fadd v2.4s, v2.4s, v24.4s
234acec48: fadd v24.4s, v26.4s, v4.4s
234acec4c: st1 {v28.4s}, [x0], x12
234acec50: fsub v4.4s, v26.4s, v4.4s
234acec54: fsub v28.4s, v6.4s, v8.4s
234acec58: st1 {v18.4s}, [x1], x12
234acec5c: fadd v26.4s, v6.4s, v8.4s
234acec60: fadd v6.4s, v22.4s, v16.4s
234acec64: st1 {v0.4s}, [x1], x12
234acec68: fsub v0.4s, v22.4s, v16.4s
234acec6c: fsub v16.4s, v28.4s, v6.4s
234acec70: fadd v6.4s, v28.4s, v6.4s
234acec74: fmul v16.4s, v16.4s, v14.s[0]
234acec78: fmul v18.4s, v6.4s, v14.s[0]
234acec7c: fadd v22.4s, v20.4s, v16.4s
234acec80: fsub v28.4s, v20.4s, v16.4s
234acec84: fadd v20.4s, v24.4s, v18.4s
234acec88: fsub v18.4s, v24.4s, v18.4s
234acec8c: fadd v24.4s, v26.4s, v0.4s
234acec90: st1 {v22.4s}, [x0], x12
234acec94: fsub v0.4s, v26.4s, v0.4s
234acec98: fmul v22.4s, v24.4s, v14.s[0]
234acec9c: st1 {v28.4s}, [x0], x12
234aceca0: fmul v26.4s, v0.4s, v14.s[0]
234aceca4: fsub v24.4s, v2.4s, v22.4s
234aceca8: st1 {v20.4s}, [x1], x12
234acecac: fadd v28.4s, v2.4s, v22.4s
234acecb0: fadd v20.4s, v4.4s, v26.4s
234acecb4: st1 {v18.4s}, [x1], x12
234acecb8: fsub v26.4s, v4.4s, v26.4s
234acecbc: st1 {v24.4s}, [x0], x12
234acecc0: st1 {v20.4s}, [x1], x12
234acecc4: st1 {v28.4s}, [x0]
234acecc8: st1 {v26.4s}, [x1]
234aceccc: mov x9, x29
234acecd0: ldp x29, x30, [x9], #0x10
234acecd4: ld1 {v8.4s, v9.4s, v10.4s, v11.4s}, [x9], #64
234acecd8: ld1 {v12.4s, v13.4s, v14.4s}, [x9]
234acecdc: add sp, sp, #0x80
234acece0: retab 
