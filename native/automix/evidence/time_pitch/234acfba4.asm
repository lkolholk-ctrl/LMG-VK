; original vDSP magnitude 0x234acfba4..0x234ad00a4
; SHA256 35db8c6d1260054f14f61585b70726388007adf2126064ee1b7ce2a0a077459e
0x234acfba4: e60304aa mov x6, x4
0x234acfba8: e50303aa mov x5, x3
0x234acfbac: e40302aa mov x4, x2
0x234acfbb0: 080840a9 ldp x8, x2, [x0]
0x234acfbb4: e00308aa mov x0, x8
0x234acfbb8: e30301aa mov x3, x1
0x234acfbbc: 01000014 b #0x234acfbc0
0x234acfbc0: 062700b4 cbz x6, #0x234ad00a0
0x234acfbc4: df1000f1 cmp x6, #4
0x234acfbc8: 0b180054 b.lt #0x234acfec8
0x234acfbcc: 3f0003eb cmp x1, x3
0x234acfbd0: c1170054 b.ne #0x234acfec8
0x234acfbd4: 3f0005eb cmp x1, x5
0x234acfbd8: 81170054 b.ne #0x234acfec8
0x234acfbdc: 3f0400b1 cmn x1, #1
0x234acfbe0: 01010054 b.ne #0x234acfc00
0x234acfbe4: c70400d1 sub x7, x6, #1
0x234acfbe8: 000807cb sub x0, x0, x7, lsl #2
0x234acfbec: 420807cb sub x2, x2, x7, lsl #2
0x234acfbf0: 840807cb sub x4, x4, x7, lsl #2
0x234acfbf4: 210080d2 mov x1, #1
0x234acfbf8: 230080d2 mov x3, #1
0x234acfbfc: 250080d2 mov x5, #1
0x234acfc00: 3f0400f1 cmp x1, #1
0x234acfc04: 21160054 b.ne #0x234acfec8
0x234acfc08: 9f0c40f2 tst x4, #0xf
0x234acfc0c: 40020054 b.eq #0x234acfc54
0x234acfc10: 004440bc ldr s0, [x0], #4
0x234acfc14: 504440bc ldr s16, [x2], #4
0x234acfc18: 0008201e fmul s0, s0, s0
0x234acfc1c: 0002101f fmadd s0, s16, s16, s0
0x234acfc20: 10d8a17e frsqrte s16, s0
0x234acfc24: 18d8a05e fcmeq s24, s0, #0.0
0x234acfc28: 101e780e bic v16.8b, v16.8b, v24.8b
0x234acfc2c: 1808301e fmul s24, s0, s16
0x234acfc30: 18feb85e frsqrts s24, s16, s24
0x234acfc34: 100a381e fmul s16, s16, s24
0x234acfc38: c60400f1 subs x6, x6, #1
0x234acfc3c: 1808301e fmul s24, s0, s16
0x234acfc40: 10feb85e frsqrts s16, s16, s24
0x234acfc44: 180b301e fmul s24, s24, s16
0x234acfc48: 984400bc str s24, [x4], #4
0x234acfc4c: 9f0c40f2 tst x4, #0xf
0x234acfc50: 01feff54 b.ne #0x234acfc10
0x234acfc54: c64000f1 subs x6, x6, #0x10
0x234acfc58: a40e0054 b.mi #0x234acfe2c
0x234acfc5c: 0004c43c ldr q0, [x0], #0x40
0x234acfc60: 5004c43c ldr q16, [x2], #0x40
0x234acfc64: 0100dd3c ldur q1, [x0, #-0x30]
0x234acfc68: 0200de3c ldur q2, [x0, #-0x20]
0x234acfc6c: 00dc206e fmul v0.4s, v0.4s, v0.4s
0x234acfc70: 0300df3c ldur q3, [x0, #-0x10]
0x234acfc74: 21dc216e fmul v1.4s, v1.4s, v1.4s
0x234acfc78: 5100dd3c ldur q17, [x2, #-0x30]
0x234acfc7c: 42dc226e fmul v2.4s, v2.4s, v2.4s
0x234acfc80: 5200de3c ldur q18, [x2, #-0x20]
0x234acfc84: 63dc236e fmul v3.4s, v3.4s, v3.4s
0x234acfc88: 5300df3c ldur q19, [x2, #-0x10]
0x234acfc8c: 00ce304e fmla v0.4s, v16.4s, v16.4s
0x234acfc90: 21ce314e fmla v1.4s, v17.4s, v17.4s
0x234acfc94: 42ce324e fmla v2.4s, v18.4s, v18.4s
0x234acfc98: 63ce334e fmla v3.4s, v19.4s, v19.4s
0x234acfc9c: c64000f1 subs x6, x6, #0x10
0x234acfca0: 6b070054 b.lt #0x234acfd8c
0x234acfca4: 10d8a16e frsqrte v16.4s, v0.4s
0x234acfca8: 18d8a04e fcmeq v24.4s, v0.4s, #0.0
0x234acfcac: 31d8a16e frsqrte v17.4s, v1.4s
0x234acfcb0: 39d8a04e fcmeq v25.4s, v1.4s, #0.0
0x234acfcb4: 52d8a16e frsqrte v18.4s, v2.4s
0x234acfcb8: 5ad8a04e fcmeq v26.4s, v2.4s, #0.0
0x234acfcbc: 73d8a16e frsqrte v19.4s, v3.4s
0x234acfcc0: 7bd8a04e fcmeq v27.4s, v3.4s, #0.0
0x234acfcc4: 101e784e bic v16.16b, v16.16b, v24.16b
0x234acfcc8: 311e794e bic v17.16b, v17.16b, v25.16b
0x234acfccc: 521e7a4e bic v18.16b, v18.16b, v26.16b
0x234acfcd0: 731e7b4e bic v19.16b, v19.16b, v27.16b
0x234acfcd4: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfcd8: 39dc316e fmul v25.4s, v1.4s, v17.4s
0x234acfcdc: 5adc326e fmul v26.4s, v2.4s, v18.4s
0x234acfce0: 7bdc336e fmul v27.4s, v3.4s, v19.4s
0x234acfce4: 18feb84e frsqrts v24.4s, v16.4s, v24.4s
0x234acfce8: 39feb94e frsqrts v25.4s, v17.4s, v25.4s
0x234acfcec: 5afeba4e frsqrts v26.4s, v18.4s, v26.4s
0x234acfcf0: 7bfebb4e frsqrts v27.4s, v19.4s, v27.4s
0x234acfcf4: 10de386e fmul v16.4s, v16.4s, v24.4s
0x234acfcf8: 31de396e fmul v17.4s, v17.4s, v25.4s
0x234acfcfc: 52de3a6e fmul v18.4s, v18.4s, v26.4s
0x234acfd00: 73de3b6e fmul v19.4s, v19.4s, v27.4s
0x234acfd04: c64000f1 subs x6, x6, #0x10
0x234acfd08: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfd0c: 0004c43c ldr q0, [x0], #0x40
0x234acfd10: 39dc316e fmul v25.4s, v1.4s, v17.4s
0x234acfd14: 0100dd3c ldur q1, [x0, #-0x30]
0x234acfd18: 5adc326e fmul v26.4s, v2.4s, v18.4s
0x234acfd1c: 0200de3c ldur q2, [x0, #-0x20]
0x234acfd20: 7bdc336e fmul v27.4s, v3.4s, v19.4s
0x234acfd24: 0300df3c ldur q3, [x0, #-0x10]
0x234acfd28: 10feb84e frsqrts v16.4s, v16.4s, v24.4s
0x234acfd2c: 00dc206e fmul v0.4s, v0.4s, v0.4s
0x234acfd30: 31feb94e frsqrts v17.4s, v17.4s, v25.4s
0x234acfd34: 21dc216e fmul v1.4s, v1.4s, v1.4s
0x234acfd38: 52feba4e frsqrts v18.4s, v18.4s, v26.4s
0x234acfd3c: 42dc226e fmul v2.4s, v2.4s, v2.4s
0x234acfd40: 73febb4e frsqrts v19.4s, v19.4s, v27.4s
0x234acfd44: 63dc236e fmul v3.4s, v3.4s, v3.4s
0x234acfd48: 18df306e fmul v24.4s, v24.4s, v16.4s
0x234acfd4c: 5004c43c ldr q16, [x2], #0x40
0x234acfd50: 39df316e fmul v25.4s, v25.4s, v17.4s
0x234acfd54: 5100dd3c ldur q17, [x2, #-0x30]
0x234acfd58: 5adf326e fmul v26.4s, v26.4s, v18.4s
0x234acfd5c: 5200de3c ldur q18, [x2, #-0x20]
0x234acfd60: 7bdf336e fmul v27.4s, v27.4s, v19.4s
0x234acfd64: 5300df3c ldur q19, [x2, #-0x10]
0x234acfd68: 9804843c str q24, [x4], #0x40
0x234acfd6c: 00ce304e fmla v0.4s, v16.4s, v16.4s
0x234acfd70: 99009d3c stur q25, [x4, #-0x30]
0x234acfd74: 21ce314e fmla v1.4s, v17.4s, v17.4s
0x234acfd78: 9a009e3c stur q26, [x4, #-0x20]
0x234acfd7c: 42ce324e fmla v2.4s, v18.4s, v18.4s
0x234acfd80: 9b009f3c stur q27, [x4, #-0x10]
0x234acfd84: 63ce334e fmla v3.4s, v19.4s, v19.4s
0x234acfd88: eaf8ff54 b.ge #0x234acfca4
0x234acfd8c: 10d8a16e frsqrte v16.4s, v0.4s
0x234acfd90: 18d8a04e fcmeq v24.4s, v0.4s, #0.0
0x234acfd94: 31d8a16e frsqrte v17.4s, v1.4s
0x234acfd98: 39d8a04e fcmeq v25.4s, v1.4s, #0.0
0x234acfd9c: 52d8a16e frsqrte v18.4s, v2.4s
0x234acfda0: 5ad8a04e fcmeq v26.4s, v2.4s, #0.0
0x234acfda4: 73d8a16e frsqrte v19.4s, v3.4s
0x234acfda8: 7bd8a04e fcmeq v27.4s, v3.4s, #0.0
0x234acfdac: 101e784e bic v16.16b, v16.16b, v24.16b
0x234acfdb0: 311e794e bic v17.16b, v17.16b, v25.16b
0x234acfdb4: 521e7a4e bic v18.16b, v18.16b, v26.16b
0x234acfdb8: 731e7b4e bic v19.16b, v19.16b, v27.16b
0x234acfdbc: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfdc0: 39dc316e fmul v25.4s, v1.4s, v17.4s
0x234acfdc4: 5adc326e fmul v26.4s, v2.4s, v18.4s
0x234acfdc8: 7bdc336e fmul v27.4s, v3.4s, v19.4s
0x234acfdcc: 18feb84e frsqrts v24.4s, v16.4s, v24.4s
0x234acfdd0: 39feb94e frsqrts v25.4s, v17.4s, v25.4s
0x234acfdd4: 5afeba4e frsqrts v26.4s, v18.4s, v26.4s
0x234acfdd8: 7bfebb4e frsqrts v27.4s, v19.4s, v27.4s
0x234acfddc: 10de386e fmul v16.4s, v16.4s, v24.4s
0x234acfde0: 31de396e fmul v17.4s, v17.4s, v25.4s
0x234acfde4: 52de3a6e fmul v18.4s, v18.4s, v26.4s
0x234acfde8: 73de3b6e fmul v19.4s, v19.4s, v27.4s
0x234acfdec: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfdf0: 39dc316e fmul v25.4s, v1.4s, v17.4s
0x234acfdf4: 5adc326e fmul v26.4s, v2.4s, v18.4s
0x234acfdf8: 7bdc336e fmul v27.4s, v3.4s, v19.4s
0x234acfdfc: 10feb84e frsqrts v16.4s, v16.4s, v24.4s
0x234acfe00: 31feb94e frsqrts v17.4s, v17.4s, v25.4s
0x234acfe04: 52feba4e frsqrts v18.4s, v18.4s, v26.4s
0x234acfe08: 73febb4e frsqrts v19.4s, v19.4s, v27.4s
0x234acfe0c: 18df306e fmul v24.4s, v24.4s, v16.4s
0x234acfe10: 39df316e fmul v25.4s, v25.4s, v17.4s
0x234acfe14: 5adf326e fmul v26.4s, v26.4s, v18.4s
0x234acfe18: 7bdf336e fmul v27.4s, v27.4s, v19.4s
0x234acfe1c: 9804843c str q24, [x4], #0x40
0x234acfe20: 99009d3c stur q25, [x4, #-0x30]
0x234acfe24: 9a009e3c stur q26, [x4, #-0x20]
0x234acfe28: 9b009f3c stur q27, [x4, #-0x10]
0x234acfe2c: c64000b1 adds x6, x6, #0x10
0x234acfe30: 80130054 b.eq #0x234ad00a0
0x234acfe34: c61000f1 subs x6, x6, #4
0x234acfe38: 44040054 b.mi #0x234acfec0
0x234acfe3c: 0004c13c ldr q0, [x0], #0x10
0x234acfe40: 5004c13c ldr q16, [x2], #0x10
0x234acfe44: 00dc206e fmul v0.4s, v0.4s, v0.4s
0x234acfe48: 00ce304e fmla v0.4s, v16.4s, v16.4s
0x234acfe4c: 10d8a16e frsqrte v16.4s, v0.4s
0x234acfe50: 18d8a04e fcmeq v24.4s, v0.4s, #0.0
0x234acfe54: 101e784e bic v16.16b, v16.16b, v24.16b
0x234acfe58: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfe5c: c61000f1 subs x6, x6, #4
0x234acfe60: 4b020054 b.lt #0x234acfea8
0x234acfe64: 4404c13c ldr q4, [x2], #0x10
0x234acfe68: 18feb84e frsqrts v24.4s, v16.4s, v24.4s
0x234acfe6c: 84dc246e fmul v4.4s, v4.4s, v4.4s
0x234acfe70: 10de386e fmul v16.4s, v16.4s, v24.4s
0x234acfe74: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfe78: 0004c13c ldr q0, [x0], #0x10
0x234acfe7c: 10feb84e frsqrts v16.4s, v16.4s, v24.4s
0x234acfe80: 00dc206e fmul v0.4s, v0.4s, v0.4s
0x234acfe84: c61000f1 subs x6, x6, #4
0x234acfe88: 18df306e fmul v24.4s, v24.4s, v16.4s
0x234acfe8c: 00d4244e fadd v0.4s, v0.4s, v4.4s
0x234acfe90: 9804813c str q24, [x4], #0x10
0x234acfe94: 10d8a16e frsqrte v16.4s, v0.4s
0x234acfe98: 04d8a04e fcmeq v4.4s, v0.4s, #0.0
0x234acfe9c: 101e644e bic v16.16b, v16.16b, v4.16b
0x234acfea0: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfea4: 0afeff54 b.ge #0x234acfe64
0x234acfea8: 18feb84e frsqrts v24.4s, v16.4s, v24.4s
0x234acfeac: 10de386e fmul v16.4s, v16.4s, v24.4s
0x234acfeb0: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acfeb4: 10feb84e frsqrts v16.4s, v16.4s, v24.4s
0x234acfeb8: 18df306e fmul v24.4s, v24.4s, v16.4s
0x234acfebc: 9804813c str q24, [x4], #0x10
0x234acfec0: c61000b1 adds x6, x6, #4
0x234acfec4: e00e0054 b.eq #0x234ad00a0
0x234acfec8: 21f47ed3 lsl x1, x1, #2
0x234acfecc: 63f47ed3 lsl x3, x3, #2
0x234acfed0: a5f47ed3 lsl x5, x5, #2
0x234acfed4: c61000f1 subs x6, x6, #4
0x234acfed8: 64090054 b.mi #0x234ad0004
0x234acfedc: 0080400d ld1 {v0.s}[0], [x0]
0x234acfee0: 0000018b add x0, x0, x1
0x234acfee4: 5080400d ld1 {v16.s}[0], [x2]
0x234acfee8: 4200038b add x2, x2, x3
0x234acfeec: 0090400d ld1 {v0.s}[1], [x0]
0x234acfef0: 0000018b add x0, x0, x1
0x234acfef4: 5090400d ld1 {v16.s}[1], [x2]
0x234acfef8: 4200038b add x2, x2, x3
0x234acfefc: 0080404d ld1 {v0.s}[2], [x0]
0x234acff00: 0000018b add x0, x0, x1
0x234acff04: 5080404d ld1 {v16.s}[2], [x2]
0x234acff08: 4200038b add x2, x2, x3
0x234acff0c: 0090404d ld1 {v0.s}[3], [x0]
0x234acff10: 0000018b add x0, x0, x1
0x234acff14: 5090404d ld1 {v16.s}[3], [x2]
0x234acff18: 4200038b add x2, x2, x3
0x234acff1c: 00dc206e fmul v0.4s, v0.4s, v0.4s
0x234acff20: 00ce304e fmla v0.4s, v16.4s, v16.4s
0x234acff24: 10d8a16e frsqrte v16.4s, v0.4s
0x234acff28: 18d8a04e fcmeq v24.4s, v0.4s, #0.0
0x234acff2c: 101e784e bic v16.16b, v16.16b, v24.16b
0x234acff30: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acff34: c61000f1 subs x6, x6, #4
0x234acff38: cb040054 b.lt #0x234acffd0
0x234acff3c: 0480400d ld1 {v4.s}[0], [x0]
0x234acff40: 0000018b add x0, x0, x1
0x234acff44: 18feb84e frsqrts v24.4s, v16.4s, v24.4s
0x234acff48: 0490400d ld1 {v4.s}[1], [x0]
0x234acff4c: 0000018b add x0, x0, x1
0x234acff50: 10de386e fmul v16.4s, v16.4s, v24.4s
0x234acff54: 0480404d ld1 {v4.s}[2], [x0]
0x234acff58: 0000018b add x0, x0, x1
0x234acff5c: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acff60: 0490404d ld1 {v4.s}[3], [x0]
0x234acff64: 0000018b add x0, x0, x1
0x234acff68: 10feb84e frsqrts v16.4s, v16.4s, v24.4s
0x234acff6c: 18df306e fmul v24.4s, v24.4s, v16.4s
0x234acff70: 5080400d ld1 {v16.s}[0], [x2]
0x234acff74: 4200038b add x2, x2, x3
0x234acff78: 80dc246e fmul v0.4s, v4.4s, v4.4s
0x234acff7c: 5090400d ld1 {v16.s}[1], [x2]
0x234acff80: 4200038b add x2, x2, x3
0x234acff84: 5080404d ld1 {v16.s}[2], [x2]
0x234acff88: 4200038b add x2, x2, x3
0x234acff8c: 5090404d ld1 {v16.s}[3], [x2]
0x234acff90: 4200038b add x2, x2, x3
0x234acff94: 9880000d st1 {v24.s}[0], [x4]
0x234acff98: 8400058b add x4, x4, x5
0x234acff9c: 9890000d st1 {v24.s}[1], [x4]
0x234acffa0: 8400058b add x4, x4, x5
0x234acffa4: 00ce304e fmla v0.4s, v16.4s, v16.4s
0x234acffa8: 9880004d st1 {v24.s}[2], [x4]
0x234acffac: 8400058b add x4, x4, x5
0x234acffb0: 9890004d st1 {v24.s}[3], [x4]
0x234acffb4: 8400058b add x4, x4, x5
0x234acffb8: 10d8a16e frsqrte v16.4s, v0.4s
0x234acffbc: 18d8a04e fcmeq v24.4s, v0.4s, #0.0
0x234acffc0: 101e784e bic v16.16b, v16.16b, v24.16b
0x234acffc4: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acffc8: c61000f1 subs x6, x6, #4
0x234acffcc: 8afbff54 b.ge #0x234acff3c
0x234acffd0: 18feb84e frsqrts v24.4s, v16.4s, v24.4s
0x234acffd4: 10de386e fmul v16.4s, v16.4s, v24.4s
0x234acffd8: 18dc306e fmul v24.4s, v0.4s, v16.4s
0x234acffdc: 10feb84e frsqrts v16.4s, v16.4s, v24.4s
0x234acffe0: 18df306e fmul v24.4s, v24.4s, v16.4s
0x234acffe4: 9880000d st1 {v24.s}[0], [x4]
0x234acffe8: 8400058b add x4, x4, x5
0x234acffec: 9890000d st1 {v24.s}[1], [x4]
0x234acfff0: 8400058b add x4, x4, x5
0x234acfff4: 9880004d st1 {v24.s}[2], [x4]
0x234acfff8: 8400058b add x4, x4, x5
0x234acfffc: 9890004d st1 {v24.s}[3], [x4]
0x234ad0000: 8400058b add x4, x4, x5
0x234ad0004: c61000b1 adds x6, x6, #4
0x234ad0008: c0040054 b.eq #0x234ad00a0
0x234ad000c: 000040bd ldr s0, [x0]
0x234ad0010: 0000018b add x0, x0, x1
0x234ad0014: 500040bd ldr s16, [x2]
0x234ad0018: 4200038b add x2, x2, x3
0x234ad001c: 0008201e fmul s0, s0, s0
0x234ad0020: 0002101f fmadd s0, s16, s16, s0
0x234ad0024: 10d8a17e frsqrte s16, s0
0x234ad0028: 18d8a05e fcmeq s24, s0, #0.0
0x234ad002c: 101e784e bic v16.16b, v16.16b, v24.16b
0x234ad0030: 1808301e fmul s24, s0, s16
0x234ad0034: c60400f1 subs x6, x6, #1
0x234ad0038: 8d020054 b.le #0x234ad0088
0x234ad003c: 440040bd ldr s4, [x2]
0x234ad0040: 4200038b add x2, x2, x3
0x234ad0044: 18feb85e frsqrts s24, s16, s24
0x234ad0048: 8408241e fmul s4, s4, s4
0x234ad004c: 100a381e fmul s16, s16, s24
0x234ad0050: 1808301e fmul s24, s0, s16
0x234ad0054: 000040bd ldr s0, [x0]
0x234ad0058: 0000018b add x0, x0, x1
0x234ad005c: 10feb85e frsqrts s16, s16, s24
0x234ad0060: 0010001f fmadd s0, s0, s0, s4
0x234ad0064: 180b301e fmul s24, s24, s16
0x234ad0068: 980000bd str s24, [x4]
0x234ad006c: 8400058b add x4, x4, x5
0x234ad0070: 10d8a17e frsqrte s16, s0
0x234ad0074: 18d8a05e fcmeq s24, s0, #0.0
0x234ad0078: 101e780e bic v16.8b, v16.8b, v24.8b
0x234ad007c: 1808301e fmul s24, s0, s16
0x234ad0080: c60400f1 subs x6, x6, #1
0x234ad0084: ccfdff54 b.gt #0x234ad003c
0x234ad0088: 18feb85e frsqrts s24, s16, s24
0x234ad008c: 100a381e fmul s16, s16, s24
0x234ad0090: 1808301e fmul s24, s0, s16
0x234ad0094: 10feb85e frsqrts s16, s16, s24
0x234ad0098: 180b301e fmul s24, s24, s16
0x234ad009c: 980000bd str s24, [x4]
0x234ad00a0: c0035fd6 ret 
