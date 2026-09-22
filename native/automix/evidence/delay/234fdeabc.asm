; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fdeabc..0x234fded20
; Code SHA256: b8c47ae9ccdebe2c0a6db035f9e4e1f11e268b01eb8de36fcf89ffabd48aecd6
0x234fdeabc: 7f2303d5 pacibsp 
0x234fdeac0: ed33b86d stp d13, d12, [sp, #-0x80]!
0x234fdeac4: eb2b016d stp d11, d10, [sp, #0x10]
0x234fdeac8: e923026d stp d9, d8, [sp, #0x20]
0x234fdeacc: fa6703a9 stp x26, x25, [sp, #0x30]
0x234fdead0: f85f04a9 stp x24, x23, [sp, #0x40]
0x234fdead4: f65705a9 stp x22, x21, [sp, #0x50]
0x234fdead8: f44f06a9 stp x20, x19, [sp, #0x60]
0x234fdeadc: fd7b07a9 stp x29, x30, [sp, #0x70]
0x234fdeae0: fdc30191 add x29, sp, #0x70
0x234fdeae4: f30304aa mov x19, x4
0x234fdeae8: f70303aa mov x23, x3
0x234fdeaec: f50302aa mov x21, x2
0x234fdeaf0: f60301aa mov x22, x1
0x234fdeaf4: f40300aa mov x20, x0
0x234fdeaf8: 000440f9 ldr x0, [x0, #8]
0x234fdeafc: 01008052 mov w1, #0
0x234fdeb00: a86afd97 bl #0x234f395a0
0x234fdeb04: 0840201e fmov s8, s0
0x234fdeb08: e80000d0 adrp x8, #0x234ffc000
0x234fdeb0c: 0b6547fd ldr d11, [x8, #0xec8]
0x234fdeb10: 800640f9 ldr x0, [x20, #8]
0x234fdeb14: 21008052 mov w1, #1
0x234fdeb18: a26afd97 bl #0x234f395a0
0x234fdeb1c: 0940201e fmov s9, s0
0x234fdeb20: 800640f9 ldr x0, [x20, #8]
0x234fdeb24: 41008052 mov w1, #2
0x234fdeb28: 9e6afd97 bl #0x234f395a0
0x234fdeb2c: 0a40201e fmov s10, s0
0x234fdeb30: 800640f9 ldr x0, [x20, #8]
0x234fdeb34: 61008052 mov w1, #3
0x234fdeb38: 9a6afd97 bl #0x234f395a0
0x234fdeb3c: 0190241e fmov s1, #10.00000000
0x234fdeb40: 0020211e fcmp s0, s1
0x234fdeb44: 204c201e fcsel s0, s1, s0, mi
0x234fdeb48: e80000f0 adrp x8, #0x234ffd000
0x234fdeb4c: 01c948bd ldr s1, [x8, #0x8c8]
0x234fdeb50: 0020211e fcmp s0, s1
0x234fdeb54: 20cc201e fcsel s0, s1, s0, gt
0x234fdeb58: 00c0221e fcvt d0, s0
0x234fdeb5c: 0c28601e fadd d12, d0, d0
0x234fdeb60: 800640f9 ldr x0, [x20, #8]
0x234fdeb64: 846afd97 bl #0x234f39574
0x234fdeb68: 8019601e fdiv d0, d12, d0
0x234fdeb6c: 0c40621e fcvt s12, d0
0x234fdeb70: 20c1221e fcvt d0, s9
0x234fdeb74: e80000d0 adrp x8, #0x234ffc000
0x234fdeb78: 011d47fd ldr d1, [x8, #0xe38]
0x234fdeb7c: 0020611e fcmp d0, d1
0x234fdeb80: e80000f0 adrp x8, #0x234ffd000
0x234fdeb84: 004148bd ldr s0, [x8, #0x840]
0x234fdeb88: 004c291e fcsel s0, s0, s9, mi
0x234fdeb8c: 0110201e fmov s1, #2.00000000
0x234fdeb90: 0020211e fcmp s0, s1
0x234fdeb94: 20cc201e fcsel s0, s1, s0, gt
0x234fdeb98: 09c0221e fcvt d9, s0
0x234fdeb9c: 800640f9 ldr x0, [x20, #8]
0x234fdeba0: 756afd97 bl #0x234f39574
0x234fdeba4: 0008691e fmul d0, d0, d9
0x234fdeba8: 0800781e fcvtzs w8, d0
0x234fdebac: 1f050071 cmp w8, #1
0x234fdebb0: 08c59f1a csinc w8, w8, wzr, gt
0x234fdebb4: 983e40b9 ldr w24, [x20, #0x3c]
0x234fdebb8: 09070051 sub w9, w24, #1
0x234fdebbc: 1f01096b cmp w8, w9
0x234fdebc0: 0831891a csel w8, w8, w9, lo
0x234fdebc4: 40c1221e fcvt d0, s10
0x234fdebc8: 00086b1e fmul d0, d0, d11
0x234fdebcc: 0040621e fcvt s0, d0
0x234fdebd0: 01c0221e fcvt d1, s0
0x234fdebd4: e90000f0 adrp x9, #0x234ffd000
0x234fdebd8: 220943fd ldr d2, [x9, #0x610]
0x234fdebdc: 2020621e fcmp d1, d2
0x234fdebe0: e90000f0 adrp x9, #0x234ffd000
0x234fdebe4: 21cd48bd ldr s1, [x9, #0x8cc]
0x234fdebe8: 204c201e fcsel s0, s1, s0, mi
0x234fdebec: 01c0221e fcvt d1, s0
0x234fdebf0: e90000d0 adrp x9, #0x234ffc000
0x234fdebf4: 22bd47fd ldr d2, [x9, #0xf78]
0x234fdebf8: 2020621e fcmp d1, d2
0x234fdebfc: e90000f0 adrp x9, #0x234ffd000
0x234fdec00: 21d148bd ldr s1, [x9, #0x8d0]
0x234fdec04: 29cc201e fcsel s9, s1, s0, gt
0x234fdec08: 993640b9 ldr w25, [x20, #0x34]
0x234fdec0c: 2903180b add w9, w25, w24
0x234fdec10: 2801084b sub w8, w9, w8
0x234fdec14: 0909d81a udiv w9, w8, w24
0x234fdec18: 3aa1181b msub w26, w9, w24, w8
0x234fdec1c: 9a3200b9 str w26, [x20, #0x30]
0x234fdec20: 00102e1e fmov s0, #1.00000000
0x234fdec24: 8021201e fcmp s12, s0
0x234fdec28: 00cc2c1e fcsel s0, s0, s12, gt
0x234fdec2c: 00c0221e fcvt d0, s0
0x234fdec30: e80000d0 adrp x8, #0x234ffc000
0x234fdec34: 019547fd ldr d1, [x8, #0xf28]
0x234fdec38: 0008611e fmul d0, d0, d1
0x234fdec3c: d59a7d94 bl #0x236f45790
0x234fdec40: f7050034 cbz w23, #0x234fdecfc
0x234fdec44: 01c1221e fcvt d1, s8
0x234fdec48: 21086b1e fmul d1, d1, d11
0x234fdec4c: 2240621e fcvt s2, d1
0x234fdec50: 03106e1e fmov d3, #1.00000000
0x234fdec54: 6038601e fsub d0, d3, d0
0x234fdec58: 0040621e fcvt s0, d0
0x234fdec5c: 41c0211e fsqrt s1, s2
0x234fdec60: 42c0221e fcvt d2, s2
0x234fdec64: 6238621e fsub d2, d3, d2
0x234fdec68: 42c0611e fsqrt d2, d2
0x234fdec6c: 4240621e fcvt s2, d2
0x234fdec70: 881240f9 ldr x8, [x20, #0x20]
0x234fdec74: e903172a mov w9, w23
0x234fdec78: 03e4002f movi d3, #0000000000000000
0x234fdec7c: c44640bc ldr s4, [x22], #4
0x234fdec80: 05597abc ldr s5, [x8, w26, uxtw #2]
0x234fdec84: 4a070011 add w10, w26, #1
0x234fdec88: 5f01186b cmp w10, w24
0x234fdec8c: fa079a1a csinc w26, wzr, w26, eq
0x234fdec90: 863a40bd ldr s6, [x20, #0x38]
0x234fdec94: c708201e fmul s7, s6, s0
0x234fdec98: a528261e fadd s5, s5, s6
0x234fdec9c: a538271e fsub s5, s5, s7
0x234fdeca0: 853a00bd str s5, [x20, #0x38]
0x234fdeca4: e510091f fmadd s5, s7, s9, s4
0x234fdeca8: 055939bc str s5, [x8, w25, uxtw #2]
0x234fdecac: 2a070011 add w10, w25, #1
0x234fdecb0: 4b09d81a udiv w11, w10, w24
0x234fdecb4: 79a9181b msub w25, w11, w24, w10
0x234fdecb8: 2508271e fmul s5, s1, s7
0x234fdecbc: 4614041f fmadd s6, s2, s4, s5
0x234fdecc0: 4414241f fnmadd s4, s2, s4, s5
0x234fdecc4: c820201e fcmp s6, #0.0
0x234fdecc8: 844c261e fcsel s4, s4, s6, mi
0x234fdeccc: 8020231e fcmp s4, s3
0x234fdecd0: 83cc231e fcsel s3, s4, s3, gt
0x234fdecd4: a64600bc str s6, [x21], #4
0x234fdecd8: 290500f1 subs x9, x9, #1
0x234fdecdc: 01fdff54 b.ne #0x234fdec7c
0x234fdece0: 9a660629 stp w26, w25, [x20, #0x30]
0x234fdece4: 60c0221e fcvt d0, s3
0x234fdece8: e80000f0 adrp x8, #0x234ffd000
0x234fdecec: 010d43fd ldr d1, [x8, #0x618]
0x234fdecf0: 0020611e fcmp d0, d1
0x234fdecf4: 4d000054 b.le #0x234fdecfc
0x234fdecf8: 7f020039 strb wzr, [x19]
0x234fdecfc: fd7b47a9 ldp x29, x30, [sp, #0x70]
0x234fded00: f44f46a9 ldp x20, x19, [sp, #0x60]
0x234fded04: f65745a9 ldp x22, x21, [sp, #0x50]
0x234fded08: f85f44a9 ldp x24, x23, [sp, #0x40]
0x234fded0c: fa6743a9 ldp x26, x25, [sp, #0x30]
0x234fded10: e923426d ldp d9, d8, [sp, #0x20]
0x234fded14: eb2b416d ldp d11, d10, [sp, #0x10]
0x234fded18: ed33c86c ldp d13, d12, [sp], #0x80
0x234fded1c: ff0f5fd6 retab 
