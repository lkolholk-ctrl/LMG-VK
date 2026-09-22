; Original vDSP 0x234adecf0..0x234adf1e0
; SHA256 0dc3ee5cd1d00e5d689d9d3dbdbf4b48a42f3ee4ea9eb9c66b4ae47c9c33becc
0x234adecf0: e80340f9 ldr x8, [sp]
0x234adecf4: e80f9f3c str q8, [sp, #-0x10]!
0x234adecf8: 281300b4 cbz x8, #0x234adef5c
0x234adecfc: 090000b0 adrp x9, #0x234adf000
0x234aded00: 28f9c33d ldr q8, [x9, #0xfe0]
0x234aded04: 1f2100f1 cmp x8, #8
0x234aded08: 2b110054 b.lt #0x234adef2c
0x234aded0c: 3f0400f1 cmp x1, #1
0x234aded10: a1070054 b.ne #0x234adee04
0x234aded14: 7f0400f1 cmp x3, #1
0x234aded18: e1020054 b.ne #0x234aded74
0x234aded1c: bf0400f1 cmp x5, #1
0x234aded20: e1000054 b.ne #0x234aded3c
0x234aded24: ff0400f1 cmp x7, #1
0x234aded28: 41000054 b.ne #0x234aded30
0x234aded2c: 91000014 b #0x234adef70
0x234aded30: ff0400b1 cmn x7, #1
0x234aded34: c10f0054 b.ne #0x234adef2c
0x234aded38: 2a010014 b #0x234adf1e0
0x234aded3c: bf0400b1 cmn x5, #1
0x234aded40: 610f0054 b.ne #0x234adef2c
0x234aded44: ff0400f1 cmp x7, #1
0x234aded48: 41000054 b.ne #0x234aded50
0x234aded4c: d5010014 b #0x234adf4a0
0x234aded50: ff0400b1 cmn x7, #1
0x234aded54: c10e0054 b.ne #0x234adef2c
0x234aded58: 09f57ed3 lsl x9, x8, #2
0x234aded5c: 291100d1 sub x9, x9, #4
0x234aded60: 2000099b madd x0, x1, x9, x0
0x234aded64: 6208099b madd x2, x3, x9, x2
0x234aded68: a410099b madd x4, x5, x9, x4
0x234aded6c: e618099b madd x6, x7, x9, x6
0x234aded70: 2c030014 b #0x234adfa20
0x234aded74: 7f0400b1 cmn x3, #1
0x234aded78: a10d0054 b.ne #0x234adef2c
0x234aded7c: bf0400f1 cmp x5, #1
0x234aded80: 01020054 b.ne #0x234adedc0
0x234aded84: ff0400f1 cmp x7, #1
0x234aded88: a1000054 b.ne #0x234aded9c
0x234aded8c: e90300aa mov x9, x0
0x234aded90: e00302aa mov x0, x2
0x234aded94: e20309aa mov x2, x9
0x234aded98: 72020014 b #0x234adf760
0x234aded9c: ff0400b1 cmn x7, #1
0x234adeda0: 610c0054 b.ne #0x234adef2c
0x234adeda4: 09f57ed3 lsl x9, x8, #2
0x234adeda8: 291100d1 sub x9, x9, #4
0x234adedac: 2000099b madd x0, x1, x9, x0
0x234adedb0: 6208099b madd x2, x3, x9, x2
0x234adedb4: a410099b madd x4, x5, x9, x4
0x234adedb8: e618099b madd x6, x7, x9, x6
0x234adedbc: d1030014 b #0x234adfd00
0x234adedc0: bf0400b1 cmn x5, #1
0x234adedc4: 410b0054 b.ne #0x234adef2c
0x234adedc8: ff0400f1 cmp x7, #1
0x234adedcc: a1000054 b.ne #0x234adede0
0x234adedd0: e90300aa mov x9, x0
0x234adedd4: e00302aa mov x0, x2
0x234adedd8: e20309aa mov x2, x9
0x234adeddc: c9030014 b #0x234adfd00
0x234adede0: ff0400b1 cmn x7, #1
0x234adede4: 410a0054 b.ne #0x234adef2c
0x234adede8: 09f57ed3 lsl x9, x8, #2
0x234adedec: 291100d1 sub x9, x9, #4
0x234adedf0: 2000099b madd x0, x1, x9, x0
0x234adedf4: 6208099b madd x2, x3, x9, x2
0x234adedf8: a410099b madd x4, x5, x9, x4
0x234adedfc: e618099b madd x6, x7, x9, x6
0x234adee00: 58020014 b #0x234adf760
0x234adee04: 3f0400b1 cmn x1, #1
0x234adee08: 21090054 b.ne #0x234adef2c
0x234adee0c: 7f0400f1 cmp x3, #1
0x234adee10: 61040054 b.ne #0x234adee9c
0x234adee14: bf0400f1 cmp x5, #1
0x234adee18: 01020054 b.ne #0x234adee58
0x234adee1c: ff0400f1 cmp x7, #1
0x234adee20: 41000054 b.ne #0x234adee28
0x234adee24: 4f020014 b #0x234adf760
0x234adee28: ff0400b1 cmn x7, #1
0x234adee2c: 01080054 b.ne #0x234adef2c
0x234adee30: 09f57ed3 lsl x9, x8, #2
0x234adee34: 291100d1 sub x9, x9, #4
0x234adee38: 2000099b madd x0, x1, x9, x0
0x234adee3c: 6208099b madd x2, x3, x9, x2
0x234adee40: a410099b madd x4, x5, x9, x4
0x234adee44: e618099b madd x6, x7, x9, x6
0x234adee48: e90300aa mov x9, x0
0x234adee4c: e00302aa mov x0, x2
0x234adee50: e20309aa mov x2, x9
0x234adee54: ab030014 b #0x234adfd00
0x234adee58: bf0400b1 cmn x5, #1
0x234adee5c: 81060054 b.ne #0x234adef2c
0x234adee60: ff0400f1 cmp x7, #1
0x234adee64: 41000054 b.ne #0x234adee6c
0x234adee68: a6030014 b #0x234adfd00
0x234adee6c: ff0400b1 cmn x7, #1
0x234adee70: e1050054 b.ne #0x234adef2c
0x234adee74: 09f57ed3 lsl x9, x8, #2
0x234adee78: 291100d1 sub x9, x9, #4
0x234adee7c: 2000099b madd x0, x1, x9, x0
0x234adee80: 6208099b madd x2, x3, x9, x2
0x234adee84: a410099b madd x4, x5, x9, x4
0x234adee88: e618099b madd x6, x7, x9, x6
0x234adee8c: e90300aa mov x9, x0
0x234adee90: e00302aa mov x0, x2
0x234adee94: e20309aa mov x2, x9
0x234adee98: 32020014 b #0x234adf760
0x234adee9c: 7f0400b1 cmn x3, #1
0x234adeea0: 61040054 b.ne #0x234adef2c
0x234adeea4: bf0400f1 cmp x5, #1
0x234adeea8: a1010054 b.ne #0x234adeedc
0x234adeeac: ff0400f1 cmp x7, #1
0x234adeeb0: 41000054 b.ne #0x234adeeb8
0x234adeeb4: db020014 b #0x234adfa20
0x234adeeb8: ff0400b1 cmn x7, #1
0x234adeebc: 81030054 b.ne #0x234adef2c
0x234adeec0: 09f57ed3 lsl x9, x8, #2
0x234adeec4: 291100d1 sub x9, x9, #4
0x234adeec8: 2000099b madd x0, x1, x9, x0
0x234adeecc: 6208099b madd x2, x3, x9, x2
0x234adeed0: a410099b madd x4, x5, x9, x4
0x234adeed4: e618099b madd x6, x7, x9, x6
0x234adeed8: 72010014 b #0x234adf4a0
0x234adeedc: bf0400b1 cmn x5, #1
0x234adeee0: 61020054 b.ne #0x234adef2c
0x234adeee4: ff0400f1 cmp x7, #1
0x234adeee8: 01010054 b.ne #0x234adef08
0x234adeeec: 09f57ed3 lsl x9, x8, #2
0x234adeef0: 291100d1 sub x9, x9, #4
0x234adeef4: 2000099b madd x0, x1, x9, x0
0x234adeef8: 6208099b madd x2, x3, x9, x2
0x234adeefc: a410099b madd x4, x5, x9, x4
0x234adef00: e618099b madd x6, x7, x9, x6
0x234adef04: b7000014 b #0x234adf1e0
0x234adef08: ff0400b1 cmn x7, #1
0x234adef0c: 01010054 b.ne #0x234adef2c
0x234adef10: 09f57ed3 lsl x9, x8, #2
0x234adef14: 291100d1 sub x9, x9, #4
0x234adef18: 2000099b madd x0, x1, x9, x0
0x234adef1c: 6208099b madd x2, x3, x9, x2
0x234adef20: a410099b madd x4, x5, x9, x4
0x234adef24: e618099b madd x6, x7, x9, x6
0x234adef28: 12000014 b #0x234adef70
0x234adef2c: 080500f1 subs x8, x8, #1
0x234adef30: 6b010054 b.lt #0x234adef5c
0x234adef34: 020040bd ldr s2, [x0]
0x234adef38: 400040bd ldr s0, [x2]
0x234adef3c: 830040bd ldr s3, [x4]
0x234adef40: 0008018b add x0, x0, x1, lsl #2
0x234adef44: 4208038b add x2, x2, x3, lsl #2
0x234adef48: 8408058b add x4, x4, x5, lsl #2
0x234adef4c: 410c001f fmadd s1, s2, s0, s3
0x234adef50: c10000bd str s1, [x6]
0x234adef54: c608078b add x6, x6, x7, lsl #2
0x234adef58: f5ffff17 b #0x234adef2c
0x234adef5c: e807c13c ldr q8, [sp], #0x10
0x234adef60: c0035fd6 ret 
0x234adef64: 1f2003d5 nop 
0x234adef68: 1f2003d5 nop 
0x234adef6c: 1f2003d5 nop 
0x234adef70: df0c40f2 tst x6, #0xf
0x234adef74: 20010054 b.eq #0x234adef98
0x234adef78: 024440bc ldr s2, [x0], #4
0x234adef7c: 404440bc ldr s0, [x2], #4
0x234adef80: 834440bc ldr s3, [x4], #4
0x234adef84: 410c001f fmadd s1, s2, s0, s3
0x234adef88: 080500d1 sub x8, x8, #1
0x234adef8c: c14400bc str s1, [x6], #4
0x234adef90: df0c40f2 tst x6, #0xf
0x234adef94: 21ffff54 b.ne #0x234adef78
0x234adef98: 0a8100f1 subs x10, x8, #0x20
0x234adef9c: cb0c0054 b.lt #0x234adf134
0x234adefa0: 0004c83c ldr q0, [x0], #0x80
0x234adefa4: 5004c83c ldr q16, [x2], #0x80
0x234adefa8: 0100d93c ldur q1, [x0, #-0x70]
0x234adefac: 5100d93c ldur q17, [x2, #-0x70]
0x234adefb0: 0200da3c ldur q2, [x0, #-0x60]
0x234adefb4: 5200da3c ldur q18, [x2, #-0x60]
0x234adefb8: 0300db3c ldur q3, [x0, #-0x50]
0x234adefbc: 5300db3c ldur q19, [x2, #-0x50]
0x234adefc0: 0400dc3c ldur q4, [x0, #-0x40]
0x234adefc4: 5400dc3c ldur q20, [x2, #-0x40]
0x234adefc8: 0500dd3c ldur q5, [x0, #-0x30]
0x234adefcc: 5500dd3c ldur q21, [x2, #-0x30]
0x234adefd0: 0600de3c ldur q6, [x0, #-0x20]
0x234adefd4: 5600de3c ldur q22, [x2, #-0x20]
0x234adefd8: 0700df3c ldur q7, [x0, #-0x10]
0x234adefdc: 5700df3c ldur q23, [x2, #-0x10]
0x234adefe0: 10dc306e fmul v16.4s, v0.4s, v16.4s
0x234adefe4: 9804c83c ldr q24, [x4], #0x80
0x234adefe8: 31dc316e fmul v17.4s, v1.4s, v17.4s
0x234adefec: 9900d93c ldur q25, [x4, #-0x70]
0x234adeff0: 52dc326e fmul v18.4s, v2.4s, v18.4s
0x234adeff4: 9a00da3c ldur q26, [x4, #-0x60]
0x234adeff8: 73dc336e fmul v19.4s, v3.4s, v19.4s
0x234adeffc: 9b00db3c ldur q27, [x4, #-0x50]
0x234adf000: 94dc346e fmul v20.4s, v4.4s, v20.4s
0x234adf004: 9c00dc3c ldur q28, [x4, #-0x40]
0x234adf008: b5dc356e fmul v21.4s, v5.4s, v21.4s
0x234adf00c: 9d00dd3c ldur q29, [x4, #-0x30]
0x234adf010: d6dc366e fmul v22.4s, v6.4s, v22.4s
0x234adf014: 9e00de3c ldur q30, [x4, #-0x20]
0x234adf018: f7dc376e fmul v23.4s, v7.4s, v23.4s
0x234adf01c: 9f00df3c ldur q31, [x4, #-0x10]
0x234adf020: 080101f1 subs x8, x8, #0x40
0x234adf024: 6b060054 b.lt #0x234adf0f0
0x234adf028: 18d7304e fadd v24.4s, v24.4s, v16.4s
0x234adf02c: 0004c83c ldr q0, [x0], #0x80
0x234adf030: 39d7314e fadd v25.4s, v25.4s, v17.4s
0x234adf034: 5004c83c ldr q16, [x2], #0x80
0x234adf038: 5ad7324e fadd v26.4s, v26.4s, v18.4s
0x234adf03c: 0100d93c ldur q1, [x0, #-0x70]
0x234adf040: 7bd7334e fadd v27.4s, v27.4s, v19.4s
0x234adf044: 5100d93c ldur q17, [x2, #-0x70]
0x234adf048: 9cd7344e fadd v28.4s, v28.4s, v20.4s
0x234adf04c: 0200da3c ldur q2, [x0, #-0x60]
0x234adf050: bdd7354e fadd v29.4s, v29.4s, v21.4s
0x234adf054: 5200da3c ldur q18, [x2, #-0x60]
0x234adf058: ded7364e fadd v30.4s, v30.4s, v22.4s
0x234adf05c: 0300db3c ldur q3, [x0, #-0x50]
0x234adf060: ffd7374e fadd v31.4s, v31.4s, v23.4s
0x234adf064: 5300db3c ldur q19, [x2, #-0x50]
0x234adf068: 0400dc3c ldur q4, [x0, #-0x40]
0x234adf06c: 5400dc3c ldur q20, [x2, #-0x40]
0x234adf070: 0500dd3c ldur q5, [x0, #-0x30]
0x234adf074: 5500dd3c ldur q21, [x2, #-0x30]
0x234adf078: 0600de3c ldur q6, [x0, #-0x20]
0x234adf07c: 5600de3c ldur q22, [x2, #-0x20]
0x234adf080: 0700df3c ldur q7, [x0, #-0x10]
0x234adf084: 5700df3c ldur q23, [x2, #-0x10]
0x234adf088: 10dc306e fmul v16.4s, v0.4s, v16.4s
0x234adf08c: d804883c str q24, [x6], #0x80
0x234adf090: 31dc316e fmul v17.4s, v1.4s, v17.4s
0x234adf094: d900993c stur q25, [x6, #-0x70]
0x234adf098: 52dc326e fmul v18.4s, v2.4s, v18.4s
0x234adf09c: da009a3c stur q26, [x6, #-0x60]
0x234adf0a0: 73dc336e fmul v19.4s, v3.4s, v19.4s
0x234adf0a4: db009b3c stur q27, [x6, #-0x50]
0x234adf0a8: 94dc346e fmul v20.4s, v4.4s, v20.4s
0x234adf0ac: dc009c3c stur q28, [x6, #-0x40]
0x234adf0b0: b5dc356e fmul v21.4s, v5.4s, v21.4s
0x234adf0b4: dd009d3c stur q29, [x6, #-0x30]
0x234adf0b8: d6dc366e fmul v22.4s, v6.4s, v22.4s
0x234adf0bc: de009e3c stur q30, [x6, #-0x20]
0x234adf0c0: f7dc376e fmul v23.4s, v7.4s, v23.4s
0x234adf0c4: df009f3c stur q31, [x6, #-0x10]
0x234adf0c8: 9804c83c ldr q24, [x4], #0x80
0x234adf0cc: 9900d93c ldur q25, [x4, #-0x70]
0x234adf0d0: 9a00da3c ldur q26, [x4, #-0x60]
0x234adf0d4: 9b00db3c ldur q27, [x4, #-0x50]
0x234adf0d8: 9c00dc3c ldur q28, [x4, #-0x40]
0x234adf0dc: 9d00dd3c ldur q29, [x4, #-0x30]
0x234adf0e0: 9e00de3c ldur q30, [x4, #-0x20]
0x234adf0e4: 9f00df3c ldur q31, [x4, #-0x10]
0x234adf0e8: 088100f1 subs x8, x8, #0x20
0x234adf0ec: eaf9ff54 b.ge #0x234adf028
0x234adf0f0: 18d7304e fadd v24.4s, v24.4s, v16.4s
0x234adf0f4: 39d7314e fadd v25.4s, v25.4s, v17.4s
0x234adf0f8: 5ad7324e fadd v26.4s, v26.4s, v18.4s
0x234adf0fc: 7bd7334e fadd v27.4s, v27.4s, v19.4s
0x234adf100: 9cd7344e fadd v28.4s, v28.4s, v20.4s
0x234adf104: d804883c str q24, [x6], #0x80
0x234adf108: bdd7354e fadd v29.4s, v29.4s, v21.4s
0x234adf10c: d900993c stur q25, [x6, #-0x70]
0x234adf110: ded7364e fadd v30.4s, v30.4s, v22.4s
0x234adf114: da009a3c stur q26, [x6, #-0x60]
0x234adf118: ffd7374e fadd v31.4s, v31.4s, v23.4s
0x234adf11c: db009b3c stur q27, [x6, #-0x50]
0x234adf120: dc009c3c stur q28, [x6, #-0x40]
0x234adf124: dd009d3c stur q29, [x6, #-0x30]
0x234adf128: de009e3c stur q30, [x6, #-0x20]
0x234adf12c: df009f3c stur q31, [x6, #-0x10]
0x234adf130: 088100b1 adds x8, x8, #0x20
0x234adf134: 0a4100f1 subs x10, x8, #0x10
0x234adf138: cb020054 b.lt #0x234adf190
0x234adf13c: 0004c43c ldr q0, [x0], #0x40
0x234adf140: 5004c43c ldr q16, [x2], #0x40
0x234adf144: 9804c43c ldr q24, [x4], #0x40
0x234adf148: 0100dd3c ldur q1, [x0, #-0x30]
0x234adf14c: 5100dd3c ldur q17, [x2, #-0x30]
0x234adf150: 9900dd3c ldur q25, [x4, #-0x30]
0x234adf154: 0200de3c ldur q2, [x0, #-0x20]
0x234adf158: 5200de3c ldur q18, [x2, #-0x20]
0x234adf15c: 9a00de3c ldur q26, [x4, #-0x20]
0x234adf160: 0300df3c ldur q3, [x0, #-0x10]
0x234adf164: 5300df3c ldur q19, [x2, #-0x10]
0x234adf168: 9b00df3c ldur q27, [x4, #-0x10]
0x234adf16c: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234adf170: 39cc314e fmla v25.4s, v1.4s, v17.4s
0x234adf174: 5acc324e fmla v26.4s, v2.4s, v18.4s
0x234adf178: 7bcc334e fmla v27.4s, v3.4s, v19.4s
0x234adf17c: d804843c str q24, [x6], #0x40
0x234adf180: d9009d3c stur q25, [x6, #-0x30]
0x234adf184: da009e3c stur q26, [x6, #-0x20]
0x234adf188: db009f3c stur q27, [x6, #-0x10]
0x234adf18c: 084100f1 subs x8, x8, #0x10
0x234adf190: 081100f1 subs x8, x8, #4
0x234adf194: eb000054 b.lt #0x234adf1b0
0x234adf198: 0004c13c ldr q0, [x0], #0x10
0x234adf19c: 5004c13c ldr q16, [x2], #0x10
0x234adf1a0: 9804c13c ldr q24, [x4], #0x10
0x234adf1a4: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234adf1a8: d804813c str q24, [x6], #0x10
0x234adf1ac: f9ffff17 b #0x234adf190
0x234adf1b0: 080540f2 ands x8, x8, #3
0x234adf1b4: 40edff54 b.eq #0x234adef5c
0x234adf1b8: 024440bc ldr s2, [x0], #4
0x234adf1bc: 404440bc ldr s0, [x2], #4
0x234adf1c0: 834440bc ldr s3, [x4], #4
0x234adf1c4: 410c001f fmadd s1, s2, s0, s3
0x234adf1c8: c14400bc str s1, [x6], #4
0x234adf1cc: 080500f1 subs x8, x8, #1
0x234adf1d0: 4cffff54 b.gt #0x234adf1b8
0x234adf1d4: 62ffff17 b #0x234adef5c
0x234adf1d8: 1f2003d5 nop 
0x234adf1dc: 1f2003d5 nop 
