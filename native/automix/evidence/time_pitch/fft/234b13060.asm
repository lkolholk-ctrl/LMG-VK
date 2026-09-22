234b13060: pacibsp 
234b13064: sub x9, sp, #0
234b13068: sub sp, sp, #0x10
234b1306c: stp x29, x30, [x9, #-0x10]!
234b13070: mov x29, x9
234b13074: lsr x12, x4, #0
234b13078: add x8, x2, x12
234b1307c: add x9, x3, x12
234b13080: sub x17, x12, x12, lsl #2
234b13084: add x5, x2, x12, lsl #1
234b13088: add x10, x3, x12, lsl #1
234b1308c: add x6, x8, x12, lsl #1
234b13090: add x11, x9, x12, lsl #1
234b13094: add x17, x17, #0x20
234b13098: subs x4, x4, #0x20
234b1309c: b.lt #0x234b13244
234b130a0: ld1 {v18.4s, v19.4s}, [x2], #32
234b130a4: ld1 {v22.4s, v23.4s}, [x5], #32
234b130a8: ld1 {v28.4s, v29.4s}, [x8], #32
234b130ac: ld1 {v2.4s, v3.4s}, [x6], #32
234b130b0: fadd v16.4s, v18.4s, v22.4s
234b130b4: fadd v17.4s, v19.4s, v23.4s
234b130b8: fsub v0.4s, v18.4s, v22.4s
234b130bc: fsub v1.4s, v19.4s, v23.4s
234b130c0: ld1 {v18.4s, v19.4s}, [x3], #32
234b130c4: fadd v24.4s, v28.4s, v2.4s
234b130c8: fadd v25.4s, v29.4s, v3.4s
234b130cc: fsub v2.4s, v28.4s, v2.4s
234b130d0: fsub v3.4s, v29.4s, v3.4s
234b130d4: ld1 {v26.4s, v27.4s}, [x10], #32
234b130d8: fadd v22.4s, v16.4s, v24.4s
234b130dc: fadd v23.4s, v17.4s, v25.4s
234b130e0: fsub v20.4s, v16.4s, v24.4s
234b130e4: fsub v21.4s, v17.4s, v25.4s
234b130e8: ld1 {v28.4s, v29.4s}, [x9], #32
234b130ec: fadd v24.4s, v18.4s, v26.4s
234b130f0: fadd v25.4s, v19.4s, v27.4s
234b130f4: fsub v16.4s, v18.4s, v26.4s
234b130f8: fsub v17.4s, v19.4s, v27.4s
234b130fc: ld1 {v6.4s, v7.4s}, [x11], #32
234b13100: fadd v26.4s, v28.4s, v6.4s
234b13104: fadd v27.4s, v29.4s, v7.4s
234b13108: fsub v6.4s, v28.4s, v6.4s
234b1310c: fsub v7.4s, v29.4s, v7.4s
234b13110: b.le #0x234b131e8
234b13114: nop 
234b13118: nop 
234b1311c: nop 
234b13120: ld1 {v18.4s, v19.4s}, [x2], #32
234b13124: st1 {v22.4s, v23.4s}, [x0], x12
234b13128: fadd v4.4s, v24.4s, v26.4s
234b1312c: fadd v5.4s, v25.4s, v27.4s
234b13130: fsub v24.4s, v24.4s, v26.4s
234b13134: fsub v25.4s, v25.4s, v27.4s
234b13138: subs x4, x4, #0x20
234b1313c: ld1 {v22.4s, v23.4s}, [x5], #32
234b13140: st1 {v20.4s, v21.4s}, [x0], x12
234b13144: fsub v26.4s, v0.4s, v6.4s
234b13148: fsub v27.4s, v1.4s, v7.4s
234b1314c: fadd v20.4s, v0.4s, v6.4s
234b13150: fadd v21.4s, v1.4s, v7.4s
234b13154: ld1 {v28.4s, v29.4s}, [x8], #32
234b13158: st1 {v4.4s, v5.4s}, [x1], x12
234b1315c: fadd v6.4s, v16.4s, v2.4s
234b13160: fadd v7.4s, v17.4s, v3.4s
234b13164: fsub v4.4s, v16.4s, v2.4s
234b13168: fsub v5.4s, v17.4s, v3.4s
234b1316c: ld1 {v2.4s, v3.4s}, [x6], #32
234b13170: st1 {v24.4s, v25.4s}, [x1], x12
234b13174: fadd v16.4s, v18.4s, v22.4s
234b13178: fadd v17.4s, v19.4s, v23.4s
234b1317c: fsub v0.4s, v18.4s, v22.4s
234b13180: fsub v1.4s, v19.4s, v23.4s
234b13184: ld1 {v18.4s, v19.4s}, [x3], #32
234b13188: st1 {v26.4s, v27.4s}, [x0], x12
234b1318c: fadd v24.4s, v28.4s, v2.4s
234b13190: fadd v25.4s, v29.4s, v3.4s
234b13194: fsub v2.4s, v28.4s, v2.4s
234b13198: fsub v3.4s, v29.4s, v3.4s
234b1319c: ld1 {v26.4s, v27.4s}, [x10], #32
234b131a0: st1 {v20.4s, v21.4s}, [x0], x17
234b131a4: fadd v22.4s, v16.4s, v24.4s
234b131a8: fadd v23.4s, v17.4s, v25.4s
234b131ac: fsub v20.4s, v16.4s, v24.4s
234b131b0: fsub v21.4s, v17.4s, v25.4s
234b131b4: ld1 {v28.4s, v29.4s}, [x9], #32
234b131b8: st1 {v6.4s, v7.4s}, [x1], x12
234b131bc: fadd v24.4s, v18.4s, v26.4s
234b131c0: fadd v25.4s, v19.4s, v27.4s
234b131c4: fsub v16.4s, v18.4s, v26.4s
234b131c8: fsub v17.4s, v19.4s, v27.4s
234b131cc: ld1 {v6.4s, v7.4s}, [x11], #32
234b131d0: st1 {v4.4s, v5.4s}, [x1], x17
234b131d4: fadd v26.4s, v28.4s, v6.4s
234b131d8: fadd v27.4s, v29.4s, v7.4s
234b131dc: fsub v6.4s, v28.4s, v6.4s
234b131e0: fsub v7.4s, v29.4s, v7.4s
234b131e4: b.gt #0x234b13120
234b131e8: st1 {v22.4s, v23.4s}, [x0], x12
234b131ec: fadd v4.4s, v24.4s, v26.4s
234b131f0: fadd v5.4s, v25.4s, v27.4s
234b131f4: fsub v24.4s, v24.4s, v26.4s
234b131f8: fsub v25.4s, v25.4s, v27.4s
234b131fc: st1 {v20.4s, v21.4s}, [x0], x12
234b13200: fsub v26.4s, v0.4s, v6.4s
234b13204: fsub v27.4s, v1.4s, v7.4s
234b13208: fadd v20.4s, v0.4s, v6.4s
234b1320c: fadd v21.4s, v1.4s, v7.4s
234b13210: st1 {v4.4s, v5.4s}, [x1], x12
234b13214: fadd v6.4s, v16.4s, v2.4s
234b13218: fadd v7.4s, v17.4s, v3.4s
234b1321c: fsub v4.4s, v16.4s, v2.4s
234b13220: fsub v5.4s, v17.4s, v3.4s
234b13224: st1 {v24.4s, v25.4s}, [x1], x12
234b13228: st1 {v26.4s, v27.4s}, [x0], x12
234b1322c: st1 {v20.4s, v21.4s}, [x0], x17
234b13230: st1 {v6.4s, v7.4s}, [x1], x12
234b13234: st1 {v4.4s, v5.4s}, [x1], x17
234b13238: ldp x29, x30, [x29]
234b1323c: add sp, sp, #0x10
234b13240: retab 
234b13244: ld1 {v18.4s}, [x2], #16
234b13248: ld1 {v22.4s}, [x5], #16
234b1324c: ld1 {v28.4s}, [x8], #16
234b13250: ld1 {v2.4s}, [x6], #16
234b13254: fadd v16.4s, v18.4s, v22.4s
234b13258: fsub v0.4s, v18.4s, v22.4s
234b1325c: ld1 {v18.4s}, [x3], #16
234b13260: fadd v24.4s, v28.4s, v2.4s
234b13264: fsub v2.4s, v28.4s, v2.4s
234b13268: ld1 {v26.4s}, [x10], #16
234b1326c: fadd v22.4s, v16.4s, v24.4s
234b13270: fsub v20.4s, v16.4s, v24.4s
234b13274: ld1 {v28.4s}, [x9], #16
234b13278: fadd v24.4s, v18.4s, v26.4s
234b1327c: fsub v16.4s, v18.4s, v26.4s
234b13280: ld1 {v6.4s}, [x11], #16
234b13284: fadd v26.4s, v28.4s, v6.4s
234b13288: fsub v6.4s, v28.4s, v6.4s
234b1328c: st1 {v22.4s}, [x0], x12
234b13290: fadd v4.4s, v24.4s, v26.4s
234b13294: fsub v24.4s, v24.4s, v26.4s
234b13298: st1 {v20.4s}, [x0], x12
234b1329c: fsub v26.4s, v0.4s, v6.4s
234b132a0: fadd v20.4s, v0.4s, v6.4s
234b132a4: st1 {v4.4s}, [x1], x12
234b132a8: fadd v6.4s, v16.4s, v2.4s
234b132ac: fsub v4.4s, v16.4s, v2.4s
234b132b0: st1 {v24.4s}, [x1], x12
234b132b4: st1 {v26.4s}, [x0], x12
234b132b8: st1 {v20.4s}, [x0]
234b132bc: st1 {v6.4s}, [x1], x12
234b132c0: st1 {v4.4s}, [x1]
234b132c4: b #0x234b13238
234b132c8: udf #0
234b132cc: udf #0
