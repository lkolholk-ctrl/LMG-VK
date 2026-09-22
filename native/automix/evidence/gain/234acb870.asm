0x234acb870: 7f2303d5 pacibsp 
0x234acb874: 651e00b4 cbz x5, #0x234acbc40
0x234acb878: fd7bbfa9 stp x29, x30, [sp, #-0x10]!
0x234acb87c: fd030091 mov x29, sp
0x234acb880: 500040bd ldr s16, [x2]
0x234acb884: bf1000f1 cmp x5, #4
0x234acb888: ab1b0054 b.lt #0x234acbbfc
0x234acb88c: 3f0400b1 cmn x1, #1
0x234acb890: 241841fa ccmp x1, #1, #4, ne
0x234acb894: 411b0054 b.ne #0x234acbbfc
0x234acb898: 9f0400b1 cmn x4, #1
0x234acb89c: 841841fa ccmp x4, #1, #4, ne
0x234acb8a0: e11a0054 b.ne #0x234acbbfc
0x234acb8a4: 3f0004eb cmp x1, x4
0x234acb8a8: 21010054 b.ne #0x234acb8cc
0x234acb8ac: 3f0400f1 cmp x1, #1
0x234acb8b0: e0010054 b.eq #0x234acb8ec
0x234acb8b4: a60400d1 sub x6, x5, #1
0x234acb8b8: 210080d2 mov x1, #1
0x234acb8bc: 240080d2 mov x4, #1
0x234acb8c0: 000806cb sub x0, x0, x6, lsl #2
0x234acb8c4: 630806cb sub x3, x3, x6, lsl #2
0x234acb8c8: 09000014 b #0x234acb8ec
0x234acb8cc: 3f0400f1 cmp x1, #1
0x234acb8d0: 600b0054 b.eq #0x234acba3c
0x234acb8d4: a60400d1 sub x6, x5, #1
0x234acb8d8: 210080d2 mov x1, #1
0x234acb8dc: 04008092 mov x4, #-1
0x234acb8e0: 000806cb sub x0, x0, x6, lsl #2
0x234acb8e4: 6308068b add x3, x3, x6, lsl #2
0x234acb8e8: 55000014 b #0x234acba3c
0x234acb8ec: 7f0c40f2 tst x3, #0xf
0x234acb8f0: e0000054 b.eq #0x234acb90c
0x234acb8f4: 004440bc ldr s0, [x0], #4
0x234acb8f8: 1808301e fmul s24, s0, s16
0x234acb8fc: 784400bc str s24, [x3], #4
0x234acb900: a50400f1 subs x5, x5, #1
0x234acb904: a0190054 b.eq #0x234acbc38
0x234acb908: f9ffff17 b #0x234acb8ec
0x234acb90c: a58000f1 subs x5, x5, #0x20
0x234acb910: 1006044e dup v16.4s, v16.s[0]
0x234acb914: a4060054 b.mi #0x234acb9e8
0x234acb918: 071cc03d ldr q7, [x0, #0x70]
0x234acb91c: 0618c03d ldr q6, [x0, #0x60]
0x234acb920: 0514c03d ldr q5, [x0, #0x50]
0x234acb924: 0410c03d ldr q4, [x0, #0x40]
0x234acb928: 030cc03d ldr q3, [x0, #0x30]
0x234acb92c: ffdc306e fmul v31.4s, v7.4s, v16.4s
0x234acb930: 0208c03d ldr q2, [x0, #0x20]
0x234acb934: dedc306e fmul v30.4s, v6.4s, v16.4s
0x234acb938: 0104c03d ldr q1, [x0, #0x10]
0x234acb93c: bddc306e fmul v29.4s, v5.4s, v16.4s
0x234acb940: 0004c83c ldr q0, [x0], #0x80
0x234acb944: 9cdc306e fmul v28.4s, v4.4s, v16.4s
0x234acb948: a58000f1 subs x5, x5, #0x20
0x234acb94c: 64030054 b.mi #0x234acb9b8
0x234acb950: 071cc03d ldr q7, [x0, #0x70]
0x234acb954: 7bdc306e fmul v27.4s, v3.4s, v16.4s
0x234acb958: 0618c03d ldr q6, [x0, #0x60]
0x234acb95c: 5adc306e fmul v26.4s, v2.4s, v16.4s
0x234acb960: 0514c03d ldr q5, [x0, #0x50]
0x234acb964: 39dc306e fmul v25.4s, v1.4s, v16.4s
0x234acb968: 0410c03d ldr q4, [x0, #0x40]
0x234acb96c: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acb970: 030cc03d ldr q3, [x0, #0x30]
0x234acb974: 0208c03d ldr q2, [x0, #0x20]
0x234acb978: 0104c03d ldr q1, [x0, #0x10]
0x234acb97c: 0004c83c ldr q0, [x0], #0x80
0x234acb980: 7f1c803d str q31, [x3, #0x70]
0x234acb984: 7e18803d str q30, [x3, #0x60]
0x234acb988: 7d14803d str q29, [x3, #0x50]
0x234acb98c: 7c10803d str q28, [x3, #0x40]
0x234acb990: 7b0c803d str q27, [x3, #0x30]
0x234acb994: ffdc306e fmul v31.4s, v7.4s, v16.4s
0x234acb998: 7a08803d str q26, [x3, #0x20]
0x234acb99c: dedc306e fmul v30.4s, v6.4s, v16.4s
0x234acb9a0: 7904803d str q25, [x3, #0x10]
0x234acb9a4: bddc306e fmul v29.4s, v5.4s, v16.4s
0x234acb9a8: 7804883c str q24, [x3], #0x80
0x234acb9ac: 9cdc306e fmul v28.4s, v4.4s, v16.4s
0x234acb9b0: a58000f1 subs x5, x5, #0x20
0x234acb9b4: e5fcff54 b.pl #0x234acb950
0x234acb9b8: 7f1c803d str q31, [x3, #0x70]
0x234acb9bc: 7bdc306e fmul v27.4s, v3.4s, v16.4s
0x234acb9c0: 7e18803d str q30, [x3, #0x60]
0x234acb9c4: 5adc306e fmul v26.4s, v2.4s, v16.4s
0x234acb9c8: 7d14803d str q29, [x3, #0x50]
0x234acb9cc: 39dc306e fmul v25.4s, v1.4s, v16.4s
0x234acb9d0: 7c10803d str q28, [x3, #0x40]
0x234acb9d4: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acb9d8: 7b0c803d str q27, [x3, #0x30]
0x234acb9dc: 7a08803d str q26, [x3, #0x20]
0x234acb9e0: 7904803d str q25, [x3, #0x10]
0x234acb9e4: 7804883c str q24, [x3], #0x80
0x234acb9e8: a58000b1 adds x5, x5, #0x20
0x234acb9ec: 60120054 b.eq #0x234acbc38
0x234acb9f0: a51000f1 subs x5, x5, #4
0x234acb9f4: c4000054 b.mi #0x234acba0c
0x234acb9f8: 0004c13c ldr q0, [x0], #0x10
0x234acb9fc: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acba00: a51000f1 subs x5, x5, #4
0x234acba04: 7804813c str q24, [x3], #0x10
0x234acba08: 85ffff54 b.pl #0x234acb9f8
0x234acba0c: a50800b1 adds x5, x5, #2
0x234acba10: a4000054 b.mi #0x234acba24
0x234acba14: 008440fc ldr d0, [x0], #8
0x234acba18: 18dc302e fmul v24.2s, v0.2s, v16.2s
0x234acba1c: a50800f1 subs x5, x5, #2
0x234acba20: 788400fc str d24, [x3], #8
0x234acba24: a50400b1 adds x5, x5, #1
0x234acba28: 84100054 b.mi #0x234acbc38
0x234acba2c: 000040bd ldr s0, [x0]
0x234acba30: 1808301e fmul s24, s0, s16
0x234acba34: 780000bd str s24, [x3]
0x234acba38: 80000014 b #0x234acbc38
0x234acba3c: 06000090 adrp x6, #0x234acb000
0x234acba40: 63100091 add x3, x3, #4
0x234acba44: d114c33d ldr q17, [x6, #0xc50]
0x234acba48: 7f0c40f2 tst x3, #0xf
0x234acba4c: 00010054 b.eq #0x234acba6c
0x234acba50: 004440bc ldr s0, [x0], #4
0x234acba54: 631000d1 sub x3, x3, #4
0x234acba58: 1808301e fmul s24, s0, s16
0x234acba5c: 780000bd str s24, [x3]
0x234acba60: a50400f1 subs x5, x5, #1
0x234acba64: a00e0054 b.eq #0x234acbc38
0x234acba68: f8ffff17 b #0x234acba48
0x234acba6c: a58000f1 subs x5, x5, #0x20
0x234acba70: 1006044e dup v16.4s, v16.s[0]
0x234acba74: e4080054 b.mi #0x234acbb90
0x234acba78: 634000d1 sub x3, x3, #0x10
0x234acba7c: 071cc03d ldr q7, [x0, #0x70]
0x234acba80: 0618c03d ldr q6, [x0, #0x60]
0x234acba84: 0514c03d ldr q5, [x0, #0x50]
0x234acba88: 0410c03d ldr q4, [x0, #0x40]
0x234acba8c: 030cc03d ldr q3, [x0, #0x30]
0x234acba90: ffdc306e fmul v31.4s, v7.4s, v16.4s
0x234acba94: 0208c03d ldr q2, [x0, #0x20]
0x234acba98: dedc306e fmul v30.4s, v6.4s, v16.4s
0x234acba9c: 0104c03d ldr q1, [x0, #0x10]
0x234acbaa0: bddc306e fmul v29.4s, v5.4s, v16.4s
0x234acbaa4: 0004c83c ldr q0, [x0], #0x80
0x234acbaa8: 9cdc306e fmul v28.4s, v4.4s, v16.4s
0x234acbaac: a58000f1 subs x5, x5, #0x20
0x234acbab0: 84040054 b.mi #0x234acbb40
0x234acbab4: 1f2003d5 nop 
0x234acbab8: 071cc03d ldr q7, [x0, #0x70]
0x234acbabc: 7bdc306e fmul v27.4s, v3.4s, v16.4s
0x234acbac0: 0618c03d ldr q6, [x0, #0x60]
0x234acbac4: 5adc306e fmul v26.4s, v2.4s, v16.4s
0x234acbac8: 0514c03d ldr q5, [x0, #0x50]
0x234acbacc: 39dc306e fmul v25.4s, v1.4s, v16.4s
0x234acbad0: 0410c03d ldr q4, [x0, #0x40]
0x234acbad4: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acbad8: 030cc03d ldr q3, [x0, #0x30]
0x234acbadc: ff03114e tbl v31.16b, {v31.16b}, v17.16b
0x234acbae0: 0208c03d ldr q2, [x0, #0x20]
0x234acbae4: de03114e tbl v30.16b, {v30.16b}, v17.16b
0x234acbae8: 0104c03d ldr q1, [x0, #0x10]
0x234acbaec: bd03114e tbl v29.16b, {v29.16b}, v17.16b
0x234acbaf0: 0004c83c ldr q0, [x0], #0x80
0x234acbaf4: 9c03114e tbl v28.16b, {v28.16b}, v17.16b
0x234acbaf8: 7f00993c stur q31, [x3, #-0x70]
0x234acbafc: 7b03114e tbl v27.16b, {v27.16b}, v17.16b
0x234acbb00: 7e009a3c stur q30, [x3, #-0x60]
0x234acbb04: 5a03114e tbl v26.16b, {v26.16b}, v17.16b
0x234acbb08: 7d009b3c stur q29, [x3, #-0x50]
0x234acbb0c: 3903114e tbl v25.16b, {v25.16b}, v17.16b
0x234acbb10: 7c009c3c stur q28, [x3, #-0x40]
0x234acbb14: 1803114e tbl v24.16b, {v24.16b}, v17.16b
0x234acbb18: 7b009d3c stur q27, [x3, #-0x30]
0x234acbb1c: ffdc306e fmul v31.4s, v7.4s, v16.4s
0x234acbb20: 7a009e3c stur q26, [x3, #-0x20]
0x234acbb24: dedc306e fmul v30.4s, v6.4s, v16.4s
0x234acbb28: 79009f3c stur q25, [x3, #-0x10]
0x234acbb2c: bddc306e fmul v29.4s, v5.4s, v16.4s
0x234acbb30: 7804983c str q24, [x3], #0xffffffffffffff80
0x234acbb34: 9cdc306e fmul v28.4s, v4.4s, v16.4s
0x234acbb38: a58000f1 subs x5, x5, #0x20
0x234acbb3c: e5fbff54 b.pl #0x234acbab8
0x234acbb40: 7bdc306e fmul v27.4s, v3.4s, v16.4s
0x234acbb44: 5adc306e fmul v26.4s, v2.4s, v16.4s
0x234acbb48: 39dc306e fmul v25.4s, v1.4s, v16.4s
0x234acbb4c: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acbb50: ff03114e tbl v31.16b, {v31.16b}, v17.16b
0x234acbb54: de03114e tbl v30.16b, {v30.16b}, v17.16b
0x234acbb58: 7f00993c stur q31, [x3, #-0x70]
0x234acbb5c: bd03114e tbl v29.16b, {v29.16b}, v17.16b
0x234acbb60: 7e009a3c stur q30, [x3, #-0x60]
0x234acbb64: 9c03114e tbl v28.16b, {v28.16b}, v17.16b
0x234acbb68: 7d009b3c stur q29, [x3, #-0x50]
0x234acbb6c: 7b03114e tbl v27.16b, {v27.16b}, v17.16b
0x234acbb70: 7c009c3c stur q28, [x3, #-0x40]
0x234acbb74: 5a03114e tbl v26.16b, {v26.16b}, v17.16b
0x234acbb78: 7b009d3c stur q27, [x3, #-0x30]
0x234acbb7c: 3903114e tbl v25.16b, {v25.16b}, v17.16b
0x234acbb80: 7a009e3c stur q26, [x3, #-0x20]
0x234acbb84: 1803114e tbl v24.16b, {v24.16b}, v17.16b
0x234acbb88: 79009f3c stur q25, [x3, #-0x10]
0x234acbb8c: 7804993c str q24, [x3], #0xffffffffffffff90
0x234acbb90: a58000b1 adds x5, x5, #0x20
0x234acbb94: 20050054 b.eq #0x234acbc38
0x234acbb98: a51000f1 subs x5, x5, #4
0x234acbb9c: 24010054 b.mi #0x234acbbc0
0x234acbba0: 634000d1 sub x3, x3, #0x10
0x234acbba4: 0004c13c ldr q0, [x0], #0x10
0x234acbba8: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acbbac: a51000f1 subs x5, x5, #4
0x234acbbb0: 1803114e tbl v24.16b, {v24.16b}, v17.16b
0x234acbbb4: 78049f3c str q24, [x3], #0xfffffffffffffff0
0x234acbbb8: 65ffff54 b.pl #0x234acbba4
0x234acbbbc: 63400091 add x3, x3, #0x10
0x234acbbc0: 631000d1 sub x3, x3, #4
0x234acbbc4: a50800b1 adds x5, x5, #2
0x234acbbc8: e4000054 b.mi #0x234acbbe4
0x234acbbcc: 008440fc ldr d0, [x0], #8
0x234acbbd0: 631000d1 sub x3, x3, #4
0x234acbbd4: 18dc302e fmul v24.2s, v0.2s, v16.2s
0x234acbbd8: a50800f1 subs x5, x5, #2
0x234acbbdc: 180ba00e rev64 v24.2s, v24.2s
0x234acbbe0: 78c41ffc str d24, [x3], #0xfffffffffffffffc
0x234acbbe4: a50400b1 adds x5, x5, #1
0x234acbbe8: 84020054 b.mi #0x234acbc38
0x234acbbec: 000040bd ldr s0, [x0]
0x234acbbf0: 1808301e fmul s24, s0, s16
0x234acbbf4: 780000bd str s24, [x3]
0x234acbbf8: 10000014 b #0x234acbc38
0x234acbbfc: 000040bd ldr s0, [x0]
0x234acbc00: 0008018b add x0, x0, x1, lsl #2
0x234acbc04: 630804cb sub x3, x3, x4, lsl #2
0x234acbc08: a50400f1 subs x5, x5, #1
0x234acbc0c: 0d010054 b.le #0x234acbc2c
0x234acbc10: 1808301e fmul s24, s0, s16
0x234acbc14: 000040bd ldr s0, [x0]
0x234acbc18: 6308048b add x3, x3, x4, lsl #2
0x234acbc1c: 0008018b add x0, x0, x1, lsl #2
0x234acbc20: a50400f1 subs x5, x5, #1
0x234acbc24: 780000bd str s24, [x3]
0x234acbc28: 4cffff54 b.gt #0x234acbc10
0x234acbc2c: 1808301e fmul s24, s0, s16
0x234acbc30: 6308048b add x3, x3, x4, lsl #2
0x234acbc34: 780000bd str s24, [x3]
0x234acbc38: bf030091 mov sp, x29
0x234acbc3c: fd7bc1a8 ldp x29, x30, [sp], #0x10
0x234acbc40: ff0f5fd6 retab 
0x234acbc44: 1f2003d5 nop 
0x234acbc48: 1f2003d5 nop 
0x234acbc4c: 1f2003d5 nop 
