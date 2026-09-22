; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe41c8..0x234fe431c
; Code SHA256: d9cc6317f886a57c20a06b8481a8f4e477d3f2b9963fac6a03cb273b69e5c7d6
0x234fe41c8: 7f2303d5 pacibsp 
0x234fe41cc: ff0302d1 sub sp, sp, #0x80
0x234fe41d0: ef3b016d stp d15, d14, [sp, #0x10]
0x234fe41d4: ed33026d stp d13, d12, [sp, #0x20]
0x234fe41d8: eb2b036d stp d11, d10, [sp, #0x30]
0x234fe41dc: e923046d stp d9, d8, [sp, #0x40]
0x234fe41e0: f65705a9 stp x22, x21, [sp, #0x50]
0x234fe41e4: f44f06a9 stp x20, x19, [sp, #0x60]
0x234fe41e8: fd7b07a9 stp x29, x30, [sp, #0x70]
0x234fe41ec: fdc30191 add x29, sp, #0x70
0x234fe41f0: f30300aa mov x19, x0
0x234fe41f4: 2020201e fcmp s1, s0
0x234fe41f8: 02cc211e fcsel s2, s0, s1, gt
0x234fe41fc: 0bc0221e fcvt d11, s0
0x234fe4200: 41c0221e fcvt d1, s2
0x234fe4204: 0b84006d stp d11, d1, [x0, #8]
0x234fe4208: e203012d stp s2, s0, [sp, #8]
0x234fe420c: 0018221e fdiv s0, s0, s2
0x234fe4210: 00c0221e fcvt d0, s0
0x234fe4214: 0008601e fmul d0, d0, d0
0x234fe4218: 01106e1e fmov d1, #1.00000000
0x234fe421c: 2e38601e fsub d14, d1, d0
0x234fe4220: 14c00291 add x20, x0, #0xb0
0x234fe4224: 0de4002f movi d13, #0000000000000000
0x234fe4228: 15028052 mov w21, #0x10
0x234fe422c: c8000090 adrp x8, #0x234ffc000
0x234fe4230: 0a1d47fd ldr d10, [x8, #0xe38]
0x234fe4234: c80000b0 adrp x8, #0x234ffd000
0x234fe4238: 087143fd ldr d8, [x8, #0x6e0]
0x234fe423c: c8000090 adrp x8, #0x234ffc000
0x234fe4240: 0cb945fd ldr d12, [x8, #0xb70]
0x234fe4244: 0f106a1e fmov d15, #0.25000000
0x234fe4248: 80825ffc ldur d0, [x20, #-8]
0x234fe424c: 0110711e fmov d1, #-3.00000000
0x234fe4250: 0008611e fmul d0, d0, d1
0x234fe4254: 09186b1e fdiv d9, d0, d11
0x234fe4258: 2041601e fmov d0, d9
0x234fe425c: a5847d94 bl #0x236f454f0
0x234fe4260: 00686a1e fmaxnm d0, d0, d10
0x234fe4264: 2109681e fmul d1, d9, d8
0x234fe4268: c109611e fmul d1, d14, d1
0x234fe426c: 20206c1e fcmp d1, d12
0x234fe4270: 81cd611e fcsel d1, d12, d1, gt
0x234fe4274: 02106e1e fmov d2, #1.00000000
0x234fe4278: 4238611e fsub d2, d2, d1
0x234fe427c: 0008621e fmul d0, d0, d2
0x234fe4280: 02086f1e fmul d2, d0, d15
0x234fe4284: 0040621e fcvt s0, d0
0x234fe4288: 800200bd str s0, [x20]
0x234fe428c: 2040621e fcvt s0, d1
0x234fe4290: 4140621e fcvt s1, d2
0x234fe4294: 80063b2d stp s0, s1, [x20, #-0x28]
0x234fe4298: ad29211e fadd s13, s13, s1
0x234fe429c: 94220191 add x20, x20, #0x48
0x234fe42a0: b50600f1 subs x21, x21, #1
0x234fe42a4: 21fdff54 b.ne #0x234fe4248
0x234fe42a8: 00102e1e fmov s0, #1.00000000
0x234fe42ac: 00182d1e fdiv s0, s0, s13
0x234fe42b0: 601e00bd str s0, [x19, #0x1c]
0x234fe42b4: 611a40bd ldr s1, [x19, #0x18]
0x234fe42b8: 0008211e fmul s0, s0, s1
0x234fe42bc: e207412d ldp s2, s1, [sp, #8]
0x234fe42c0: 4118211e fdiv s1, s2, s1
0x234fe42c4: 21c0221e fcvt d1, s1
0x234fe42c8: 02106e1e fmov d2, #1.00000000
0x234fe42cc: 4338611e fsub d3, d2, d1
0x234fe42d0: 2128621e fadd d1, d1, d2
0x234fe42d4: 6118611e fdiv d1, d3, d1
0x234fe42d8: 2140621e fcvt s1, d1
0x234fe42dc: 23c0221e fcvt d3, s1
0x234fe42e0: 4338631e fsub d3, d2, d3
0x234fe42e4: 4218631e fdiv d2, d2, d3
0x234fe42e8: 4240621e fcvt s2, d2
0x234fe42ec: 608a042d stp s0, s2, [x19, #0x24]
0x234fe42f0: 2088221e fnmul s0, s1, s2
0x234fe42f4: 602e00bd str s0, [x19, #0x2c]
0x234fe42f8: fd7b47a9 ldp x29, x30, [sp, #0x70]
0x234fe42fc: f44f46a9 ldp x20, x19, [sp, #0x60]
0x234fe4300: f65745a9 ldp x22, x21, [sp, #0x50]
0x234fe4304: e923446d ldp d9, d8, [sp, #0x40]
0x234fe4308: eb2b436d ldp d11, d10, [sp, #0x30]
0x234fe430c: ed33426d ldp d13, d12, [sp, #0x20]
0x234fe4310: ef3b416d ldp d15, d14, [sp, #0x10]
0x234fe4314: ff030291 add sp, sp, #0x80
0x234fe4318: ff0f5fd6 retab 
