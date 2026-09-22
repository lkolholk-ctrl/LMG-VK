234b224ac: pacibsp 
234b224b0: sub sp, sp, #0x90
234b224b4: stp d11, d10, [sp, #0x10]
234b224b8: stp d9, d8, [sp, #0x20]
234b224bc: stp x28, x27, [sp, #0x30]
234b224c0: stp x26, x25, [sp, #0x40]
234b224c4: stp x24, x23, [sp, #0x50]
234b224c8: stp x22, x21, [sp, #0x60]
234b224cc: stp x20, x19, [sp, #0x70]
234b224d0: stp x29, x30, [sp, #0x80]
234b224d4: add x29, sp, #0x80
234b224d8: mov x19, x7
234b224dc: mov x20, x1
234b224e0: ldr w26, [x29, #0x10]
234b224e4: cmp x3, #1
234b224e8: b.ne #0x234b226f4
234b224ec: cmp x6, #1
234b224f0: b.ne #0x234b226f4
234b224f4: tst w26, #0x80000000
234b224f8: csel x23, x5, x4, eq
234b224fc: csel x24, x4, x5, eq
234b22500: csel x21, x2, x20, eq
234b22504: csel x20, x20, x2, eq
234b22508: sub x8, x19, #1
234b2250c: tst x19, x8
234b22510: b.ne #0x234b22844
234b22514: ldr x25, [x0]
234b22518: cbnz x25, #0x234b2254c
234b2251c: mov w22, #1
234b22520: mov x2, #0x3681
234b22524: movk x2, #0xc967, lsl #16
234b22528: movk x2, #0x40, lsl #32
234b2252c: movk x2, #0x10a, lsl #48
234b22530: mov x26, x0
234b22534: mov w0, #1
234b22538: mov w1, #0x248
234b2253c: bl #0x236f3d490
234b22540: mov x25, x0
234b22544: str x0, [x26]
234b22548: cbz x0, #0x234b22848
234b2254c: ldp x26, x8, [x25]
234b22550: cmp x8, x19
234b22554: b.hs #0x234b22614
234b22558: lsr x22, x19, #4
234b2255c: add x8, x22, x22, lsl #1
234b22560: lsl x1, x8, #3
234b22564: mov x2, #0xfac1
234b22568: movk x2, #0x504f, lsl #16
234b2256c: movk x2, #0x40, lsl #32
234b22570: movk x2, #0x100, lsl #48
234b22574: mov x0, x26
234b22578: bl #0x236f3d4c0
234b2257c: cbz x0, #0x234b22844
234b22580: str x0, [sp, #8]
234b22584: ldr x8, [x25, #8]
234b22588: lsr x8, x8, #4
234b2258c: subs x26, x22, x8
234b22590: b.ls #0x234b2260c
234b22594: lsl x27, x8, #2
234b22598: mov w9, #0x18
234b2259c: ldr x10, [sp, #8]
234b225a0: madd x8, x8, x9, x10
234b225a4: add x28, x8, #0xc
234b225a8: adrp x8, #0x234bd1000
234b225ac: ldr d9, [x8, #0xa38]
234b225b0: fmov d10, #3.00000000
234b225b4: rbit x8, x27
234b225b8: ucvtf d0, x8, #0x40
234b225bc: fmul d8, d0, d9
234b225c0: fmov d0, d8
234b225c4: bl #0x236f3d370
234b225c8: fcvt s1, d1
234b225cc: fcvt s0, d0
234b225d0: stp s1, s0, [x28, #-0xc]
234b225d4: fadd d0, d8, d8
234b225d8: bl #0x236f3d370
234b225dc: fcvt s1, d1
234b225e0: fcvt s0, d0
234b225e4: stp s1, s0, [x28, #-4]
234b225e8: fmul d0, d8, d10
234b225ec: bl #0x236f3d370
234b225f0: fcvt s1, d1
234b225f4: fcvt s0, d0
234b225f8: stp s1, s0, [x28, #4]
234b225fc: add x27, x27, #4
234b22600: add x28, x28, #0x18
234b22604: subs x26, x26, #1
234b22608: b.ne #0x234b225b4
234b2260c: ldr x26, [sp, #8]
234b22610: stp x26, x19, [x25]
234b22614: clz x8, x19
234b22618: add x22, x25, x8, lsl #3
234b2261c: add x0, x22, #0x10
234b22620: mov x1, x19
234b22624: bl #0x234b21c8c
234b22628: cbnz w0, #0x234b22844
234b2262c: ldur x22, [x22, #0x10]
234b22630: mov x0, x20
234b22634: mov x1, x21
234b22638: mov x2, x24
234b2263c: mov x3, x23
234b22640: mov x4, x19
234b22644: tst x19, #0x5555555555555555
234b22648: b.eq #0x234b22974
234b2264c: bl #0x234b13060
234b22650: mov w23, #4
234b22654: mov w8, #2
234b22658: lsr x19, x19, x8
234b2265c: cmp x19, #0x11
234b22660: stp x22, x26, [sp]
234b22664: b.lo #0x234b22990
234b22668: mov x24, x19
234b2266c: mov x0, x20
234b22670: mov x1, x21
234b22674: mov x2, x24
234b22678: mov w3, #2
234b2267c: bl #0x234ac6a30
234b22680: lsr x8, x24, #2
234b22684: cmp x24, #0x43
234b22688: mov x24, x8
234b2268c: b.hi #0x234b2266c
234b22690: add x28, x26, #0x18
234b22694: b #0x234b226ac
234b22698: lsr x8, x19, #2
234b2269c: lsl x23, x23, #2
234b226a0: cmp x19, #0x43
234b226a4: mov x19, x8
234b226a8: b.ls #0x234b22994
234b226ac: cbz x23, #0x234b22698
234b226b0: sub x25, x23, #1
234b226b4: lsl x22, x19, #2
234b226b8: add x24, x21, x22
234b226bc: add x26, x20, x22
234b226c0: mov x27, x28
234b226c4: mov x0, x26
234b226c8: mov x1, x24
234b226cc: mov x2, x19
234b226d0: mov x3, x27
234b226d4: mov w4, #2
234b226d8: bl #0x234b16f20
234b226dc: add x27, x27, #0x18
234b226e0: add x24, x24, x22
234b226e4: add x26, x26, x22
234b226e8: subs x25, x25, #1
234b226ec: b.ne #0x234b226c4
234b226f0: b #0x234b22698
234b226f4: cmp x3, #2
234b226f8: b.ne #0x234b22874
234b226fc: cmp x6, #2
234b22700: b.ne #0x234b22874
234b22704: add x8, x20, #4
234b22708: cmp x8, x2
234b2270c: b.ne #0x234b22874
234b22710: add x8, x4, #4
234b22714: cmp x8, x5
234b22718: b.ne #0x234b22874
234b2271c: sub x8, x19, #1
234b22720: tst x19, x8
234b22724: b.ne #0x234b22844
234b22728: ldr x22, [x0]
234b2272c: cbnz x22, #0x234b22768
234b22730: mov w21, #1
234b22734: mov x2, #0x3681
234b22738: movk x2, #0xc967, lsl #16
234b2273c: movk x2, #0x40, lsl #32
234b22740: movk x2, #0x10a, lsl #48
234b22744: mov x23, x0
234b22748: mov w0, #1
234b2274c: mov w1, #0x248
234b22750: mov x22, x4
234b22754: bl #0x236f3d490
234b22758: mov x4, x22
234b2275c: mov x22, x0
234b22760: str x0, [x23]
234b22764: cbz x0, #0x234b229d0
234b22768: mov x23, x4
234b2276c: ldp x21, x8, [x22]
234b22770: cmp x8, x19
234b22774: b.hs #0x234b2282c
234b22778: lsr x24, x19, #4
234b2277c: add x8, x24, x24, lsl #1
234b22780: lsl x1, x8, #3
234b22784: mov x2, #0xfac1
234b22788: movk x2, #0x504f, lsl #16
234b2278c: movk x2, #0x40, lsl #32
234b22790: movk x2, #0x100, lsl #48
234b22794: mov x0, x21
234b22798: bl #0x236f3d4c0
234b2279c: cbz x0, #0x234b22844
234b227a0: mov x21, x0
234b227a4: ldr x8, [x22, #8]
234b227a8: lsr x8, x8, #4
234b227ac: subs x24, x24, x8
234b227b0: b.ls #0x234b22828
234b227b4: lsl x25, x8, #2
234b227b8: mov w9, #0x18
234b227bc: madd x8, x8, x9, x21
234b227c0: add x27, x8, #0xc
234b227c4: adrp x8, #0x234bd1000
234b227c8: ldr d9, [x8, #0xa38]
234b227cc: fmov d10, #3.00000000
234b227d0: rbit x8, x25
234b227d4: ucvtf d0, x8, #0x40
234b227d8: fmul d8, d0, d9
234b227dc: fmov d0, d8
234b227e0: bl #0x236f3d370
234b227e4: fcvt s1, d1
234b227e8: fcvt s0, d0
234b227ec: stp s1, s0, [x27, #-0xc]
234b227f0: fadd d0, d8, d8
234b227f4: bl #0x236f3d370
234b227f8: fcvt s1, d1
234b227fc: fcvt s0, d0
234b22800: stp s1, s0, [x27, #-4]
234b22804: fmul d0, d8, d10
234b22808: bl #0x236f3d370
234b2280c: fcvt s1, d1
234b22810: fcvt s0, d0
234b22814: stp s1, s0, [x27, #4]
234b22818: add x25, x25, #4
234b2281c: add x27, x27, #0x18
234b22820: subs x24, x24, #1
234b22824: b.ne #0x234b227d0
234b22828: stp x21, x19, [x22]
234b2282c: clz x8, x19
234b22830: add x22, x22, x8, lsl #3
234b22834: add x0, x22, #0x10
234b22838: mov x1, x19
234b2283c: bl #0x234b21c8c
234b22840: cbz w0, #0x234b228b8
234b22844: mov w22, #1
234b22848: mov x0, x22
234b2284c: ldp x29, x30, [sp, #0x80]
234b22850: ldp x20, x19, [sp, #0x70]
234b22854: ldp x22, x21, [sp, #0x60]
234b22858: ldp x24, x23, [sp, #0x50]
234b2285c: ldp x26, x25, [sp, #0x40]
234b22860: ldp x28, x27, [sp, #0x30]
234b22864: ldp d9, d8, [sp, #0x20]
234b22868: ldp d11, d10, [sp, #0x10]
234b2286c: add sp, sp, #0x90
234b22870: retab 
234b22874: str w26, [x29, #0x10]
234b22878: mov x1, x20
234b2287c: mov x7, x19
234b22880: ldp x29, x30, [sp, #0x80]
234b22884: ldp x20, x19, [sp, #0x70]
234b22888: ldp x22, x21, [sp, #0x60]
234b2288c: ldp x24, x23, [sp, #0x50]
234b22890: ldp x26, x25, [sp, #0x40]
234b22894: ldp x28, x27, [sp, #0x30]
234b22898: ldp d9, d8, [sp, #0x20]
234b2289c: ldp d11, d10, [sp, #0x10]
234b228a0: add sp, sp, #0x90
234b228a4: autibsp 
234b228a8: eor x16, x30, x30, lsl #1
234b228ac: tbz x16, #0x3e, #0x234b228b4
234b228b0: brk #0xc471
234b228b4: b #0x234b236c0
234b228b8: ldur x8, [x22, #0x10]
234b228bc: str x8, [sp, #8]
234b228c0: mov x1, x23
234b228c4: mov x0, x20
234b228c8: mov x2, x19
234b228cc: tst x19, #0x5555555555555555
234b228d0: b.eq #0x234b229d8
234b228d4: tbnz w26, #0x1f, #0x234b229e4
234b228d8: bl #0x234b88040
234b228dc: mov w23, #4
234b228e0: mov w8, #2
234b228e4: lsr x19, x19, x8
234b228e8: cmp x19, #0x11
234b228ec: b.lo #0x234b22a18
234b228f0: mov x24, x19
234b228f4: add x1, x20, #0x10
234b228f8: mov x0, x20
234b228fc: mov x2, x24
234b22900: mov w3, #3
234b22904: bl #0x234ac6a30
234b22908: lsr x8, x24, #2
234b2290c: cmp x24, #0x43
234b22910: mov x24, x8
234b22914: b.hi #0x234b228f4
234b22918: add x27, x21, #0x18
234b2291c: b #0x234b22934
234b22920: lsr x8, x19, #2
234b22924: lsl x23, x23, #2
234b22928: cmp x19, #0x43
234b2292c: mov x19, x8
234b22930: b.ls #0x234b22a1c
234b22934: cbz x23, #0x234b22920
234b22938: sub x28, x23, #1
234b2293c: lsl x22, x19, #3
234b22940: add x24, x20, x22
234b22944: mov x25, x27
234b22948: add x1, x24, #0x10
234b2294c: mov x0, x24
234b22950: mov x2, x19
234b22954: mov x3, x25
234b22958: mov w4, #3
234b2295c: bl #0x234b16f20
234b22960: add x25, x25, #0x18
234b22964: add x24, x24, x22
234b22968: subs x28, x28, #1
234b2296c: b.ne #0x234b22948
234b22970: b #0x234b22920
234b22974: bl #0x234ace690
234b22978: mov w23, #8
234b2297c: mov w8, #3
234b22980: lsr x19, x19, x8
234b22984: cmp x19, #0x11
234b22988: stp x22, x26, [sp]
234b2298c: b.hs #0x234b22668
234b22990: mov x8, x19
234b22994: cmp x8, #5
234b22998: b.lo #0x234b229b8
234b2299c: lsl x23, x23, #2
234b229a0: mov x0, x20
234b229a4: mov x1, x21
234b229a8: mov x2, x23
234b229ac: ldr x3, [sp, #8]
234b229b0: mov w4, #2
234b229b4: bl #0x234b17830
234b229b8: ldr x3, [sp]
234b229bc: mov x0, x20
234b229c0: mov x1, x21
234b229c4: mov x2, x23
234b229c8: bl #0x234b1f7a0
234b229cc: b #0x234b22a60
234b229d0: mov x22, x21
234b229d4: b #0x234b22848
234b229d8: tbnz w26, #0x1f, #0x234b22a00
234b229dc: bl #0x234b884e0
234b229e0: b #0x234b22a04
234b229e4: bl #0x234b35ed0
234b229e8: mov w23, #4
234b229ec: mov w8, #2
234b229f0: lsr x19, x19, x8
234b229f4: cmp x19, #0x11
234b229f8: b.hs #0x234b228f0
234b229fc: b #0x234b22a18
234b22a00: bl #0x234b76240
234b22a04: mov w23, #8
234b22a08: mov w8, #3
234b22a0c: lsr x19, x19, x8
234b22a10: cmp x19, #0x11
234b22a14: b.hs #0x234b228f0
234b22a18: mov x8, x19
234b22a1c: cmp x8, #5
234b22a20: b.lo #0x234b22a40
234b22a24: lsl x23, x23, #2
234b22a28: add x1, x20, #0x10
234b22a2c: mov x0, x20
234b22a30: mov x2, x23
234b22a34: mov x3, x21
234b22a38: mov w4, #3
234b22a3c: bl #0x234b17830
234b22a40: mov x0, x20
234b22a44: mov x1, x23
234b22a48: tbnz w26, #0x1f, #0x234b22a58
234b22a4c: ldr x2, [sp, #8]
234b22a50: bl #0x234bb4b10
234b22a54: b #0x234b22a60
234b22a58: ldr x2, [sp, #8]
234b22a5c: bl #0x234b90f70
234b22a60: mov w22, #0
234b22a64: b #0x234b22848
