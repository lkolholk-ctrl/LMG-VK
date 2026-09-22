
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

0000000272210114 <_OUTLINED_FUNCTION_0>:
272210660: d503237f    	pacibsp
272210664: 6db63bef    	stp	d15, d14, [sp, #-0xa0]!
272210668: 6d0133ed    	stp	d13, d12, [sp, #0x10]
27221066c: 6d022beb    	stp	d11, d10, [sp, #0x20]
272210670: 6d0323e9    	stp	d9, d8, [sp, #0x30]
272210674: f90023fc    	str	x28, [sp, #0x40]
272210678: a9056bfb    	stp	x27, x26, [sp, #0x50]
27221067c: a90663f9    	stp	x25, x24, [sp, #0x60]
272210680: a9075bf7    	stp	x23, x22, [sp, #0x70]
272210684: a9084ff4    	stp	x20, x19, [sp, #0x80]
272210688: a9097bfd    	stp	x29, x30, [sp, #0x90]
27221068c: 910243fd    	add	x29, sp, #0x90
272210690: d107c3ff    	sub	sp, sp, #0x1f0
272210694: f90047f5    	str	x21, [sp, #0x88]
272210698: aa0203fc    	mov	x28, x2
27221069c: 6d0407e0    	stp	d0, d1, [sp, #0x40]
2722106a0: d280000a    	mov	x10, #0x0               ; =0
2722106a4: 6d40102e    	ldp	d14, d4, [x1]
2722106a8: f9400809    	ldr	x9, [x0, #0x10]
2722106ac: 91008008    	add	x8, x0, #0x20
2722106b0: a901a7e8    	stp	x8, x9, [sp, #0x18]
2722106b4: f002f689    	adrp	x9, 0x2780e3000 <_swift_willThrow+0x2780e3000>
2722106b8: f9462929    	ldr	x9, [x9, #0xc50]
2722106bc: 1e6e3880    	fsub	d0, d4, d14
2722106c0: fd000be0    	str	d0, [sp, #0x10]
2722106c4: d0000428    	adrp	x8, 0x272296000 <_swift_willThrow+0x272296000>
2722106c8: 3dc1b500    	ldr	q0, [x8, #0x6d0]
2722106cc: 3d8017e0    	str	q0, [sp, #0x50]
2722106d0: fd001be4    	str	d4, [sp, #0x30]
2722106d4: f90033e2    	str	x2, [sp, #0x60]
2722106d8: fd0007ee    	str	d14, [sp, #0x8]
2722106dc: f94013e8    	ldr	x8, [sp, #0x20]
2722106e0: eb08015f    	cmp	x10, x8
2722106e4: f9001fe9    	str	x9, [sp, #0x38]
2722106e8: 54002c00    	b.eq	0x272210c68 <_OUTLINED_FUNCTION_0+0xb54>
2722106ec: f9400fe8    	ldr	x8, [sp, #0x18]
2722106f0: 5280050b    	mov	w11, #0x28              ; =40
2722106f4: 9b0b2148    	madd	x8, x10, x11, x8
2722106f8: f9401117    	ldr	x23, [x8, #0x20]
2722106fc: 6d410101    	ldp	d1, d0, [x8, #0x10]
272210700: 6d400903    	ldp	d3, d2, [x8]
272210704: fd400be5    	ldr	d5, [sp, #0x10]
272210708: 1e6308a3    	fmul	d3, d5, d3
27221070c: 1e632842    	fadd	d2, d2, d3
272210710: 1e6229c2    	fadd	d2, d14, d2
272210714: 1e622080    	fcmp	d4, d2
272210718: 1e624c83    	fcsel	d3, d4, d2, mi
27221071c: 1e6e2040    	fcmp	d2, d14
272210720: 1e634dc2    	fcsel	d2, d14, d3, mi
272210724: 1e6108a1    	fmul	d1, d5, d1
272210728: 1e612800    	fadd	d0, d0, d1
27221072c: 1e6029c0    	fadd	d0, d14, d0
272210730: 1e602080    	fcmp	d4, d0
272210734: 1e604c81    	fcsel	d1, d4, d0, mi
272210738: 1e6e2000    	fcmp	d0, d14
27221073c: 1e614dc0    	fcsel	d0, d14, d1, mi
272210740: 1e623801    	fsub	d1, d0, d2
272210744: 1e6c1003    	fmov	d3, #0.50000000
272210748: 1e630821    	fmul	d1, d1, d3
27221074c: 1e612841    	fadd	d1, d2, d1
272210750: 1e622000    	fcmp	d0, d2
272210754: 1e624c2e    	fcsel	d14, d1, d2, mi
272210758: 1e604c2d    	fcsel	d13, d1, d0, mi
27221075c: f9400af6    	ldr	x22, [x23, #0x10]
272210760: f90017ea    	str	x10, [sp, #0x28]
272210764: b4001d16    	cbz	x22, 0x272210b04 <_OUTLINED_FUNCTION_0+0x9f0>
272210768: f002f688    	adrp	x8, 0x2780e3000 <_swift_willThrow+0x2780e3000>
27221076c: f9462908    	ldr	x8, [x8, #0xc50]
272210770: f81583a8    	stur	x8, [x29, #-0xa8]
272210774: aa1703e0    	mov	x0, x23
272210778: 94873562    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
27221077c: d102a3b4    	sub	x20, x29, #0xa8
272210780: 52800000    	mov	w0, #0x0                ; =0
272210784: aa1603e1    	mov	x1, x22
272210788: 52800002    	mov	w2, #0x0                ; =0
27221078c: 94014ab3    	bl	0x272263258 <_OUTLINED_FUNCTION_24+0x2a68>
272210790: d2800013    	mov	x19, #0x0               ; =0
272210794: f85583b4    	ldur	x20, [x29, #-0xa8]
272210798: 1e6e39a0    	fsub	d0, d13, d14
27221079c: 6d0737e0    	stp	d0, d13, [sp, #0x70]
2722107a0: d10006c8    	sub	x8, x22, #0x1
2722107a4: f90037e8    	str	x8, [sp, #0x68]
2722107a8: 52800409    	mov	w9, #0x20               ; =32
2722107ac: f90043f7    	str	x23, [sp, #0x80]
2722107b0: f9400ae8    	ldr	x8, [x23, #0x10]
2722107b4: eb08027f    	cmp	x19, x8
2722107b8: 54003562    	b.hs	0x272210e64 <_OUTLINED_FUNCTION_0+0xd50>
2722107bc: 910503e0    	add	x0, sp, #0x140
2722107c0: f9004be9    	str	x9, [sp, #0x90]
2722107c4: 8b0902e1    	add	x1, x23, x9
2722107c8: 52801242    	mov	w2, #0x92               ; =146
2722107cc: 94020170    	bl	0x272290d8c <_swift_willThrow+0x272290d8c>
2722107d0: 6d5883e1    	ldp	d1, d0, [sp, #0x188]
2722107d4: 6d0983e1    	stp	d1, d0, [sp, #0x98]
2722107d8: 6d57a3ed    	ldp	d13, d8, [sp, #0x178]
2722107dc: a9546ff6    	ldp	x22, x27, [sp, #0x140]
2722107e0: 6d5533e0    	ldp	d0, d12, [sp, #0x150]
2722107e4: fc1b83a0    	stur	d0, [x29, #-0x48]
2722107e8: fd40b3ef    	ldr	d15, [sp, #0x160]
2722107ec: a956e3f9    	ldp	x25, x24, [sp, #0x168]
2722107f0: 910503e0    	add	x0, sp, #0x140
2722107f4: 9102a3e1    	add	x1, sp, #0xa8
2722107f8: 940002b5    	bl	0x2722112cc <___swift_destroy_boxed_opaque_existential_1Tm+0x454>
2722107fc: b0075448    	adrp	x8, 0x280c99000 <_swift_willThrow+0x280c99000>
272210800: f9420115    	ldr	x21, [x8, #0x400]
272210804: aa1b03e0    	mov	x0, x27
272210808: 9487353e    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
27221080c: aa1803e0    	mov	x0, x24
272210810: 9487353c    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
272210814: b10006bf    	cmn	x21, #0x1
272210818: 54001561    	b.ne	0x272210ac4 <_OUTLINED_FUNCTION_0+0x9b0>
27221081c: d0075448    	adrp	x8, 0x280c9a000 <_swift_willThrow+0x280c9a000>
272210820: 9128a108    	add	x8, x8, #0xa28
272210824: a9400500    	ldp	x0, x1, [x8]
272210828: 6d412d09    	ldp	d9, d11, [x8, #0x10]
27221082c: fd40110a    	ldr	d10, [x8, #0x20]
272210830: a942dd1a    	ldp	x26, x23, [x8, #0x28]
272210834: eb16001f    	cmp	x0, x22
272210838: fa5b0020    	ccmp	x1, x27, #0x0, eq
27221083c: 540000c0    	b.eq	0x272210854 <_OUTLINED_FUNCTION_0+0x740>
272210840: aa1603e2    	mov	x2, x22
272210844: aa1b03e3    	mov	x3, x27
272210848: 52800004    	mov	w4, #0x0                ; =0
27221084c: 9487343d    	bl	0x2743dd940 <_swift_willThrow+0x2743dd940>
272210850: 36000960    	tbz	w0, #0x0, 0x27221097c <_OUTLINED_FUNCTION_0+0x868>
272210854: fc5b83a0    	ldur	d0, [x29, #-0x48]
272210858: 1e602120    	fcmp	d9, d0
27221085c: 1e6c0560    	fccmp	d11, d12, #0x0, eq
272210860: 1e6f0540    	fccmp	d10, d15, #0x0, eq
272210864: 540008c1    	b.ne	0x27221097c <_OUTLINED_FUNCTION_0+0x868>
272210868: eb19035f    	cmp	x26, x25
27221086c: fa5802e0    	ccmp	x23, x24, #0x0, eq
272210870: 54000100    	b.eq	0x272210890 <_OUTLINED_FUNCTION_0+0x77c>
272210874: aa1a03e0    	mov	x0, x26
272210878: aa1703e1    	mov	x1, x23
27221087c: aa1903e2    	mov	x2, x25
272210880: aa1803e3    	mov	x3, x24
272210884: 52800004    	mov	w4, #0x0                ; =0
272210888: 9487342e    	bl	0x2743dd940 <_swift_willThrow+0x2743dd940>
27221088c: 36000780    	tbz	w0, #0x0, 0x27221097c <_OUTLINED_FUNCTION_0+0x868>
272210890: aa1803e0    	mov	x0, x24
272210894: 94873513    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210898: aa1b03e0    	mov	x0, x27
27221089c: 94873511    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
2722108a0: d0075448    	adrp	x8, 0x280c9a000 <_swift_willThrow+0x280c9a000>
2722108a4: 9128a108    	add	x8, x8, #0xa28
2722108a8: a9406d16    	ldp	x22, x27, [x8]
2722108ac: 6d413100    	ldp	d0, d12, [x8, #0x10]
2722108b0: fc1b83a0    	stur	d0, [x29, #-0x48]
2722108b4: fd40110f    	ldr	d15, [x8, #0x20]
2722108b8: a942e119    	ldp	x25, x24, [x8, #0x28]
2722108bc: fd403be2    	ldr	d2, [sp, #0x70]
2722108c0: 1e6d0840    	fmul	d0, d2, d13
2722108c4: 1e682800    	fadd	d0, d0, d8
2722108c8: 1e6029c0    	fadd	d0, d14, d0
2722108cc: fd403fed    	ldr	d13, [sp, #0x78]
2722108d0: 1e6021a0    	fcmp	d13, d0
2722108d4: 1e604da1    	fcsel	d1, d13, d0, mi
2722108d8: 1e6e2000    	fcmp	d0, d14
2722108dc: 1e614dc0    	fcsel	d0, d14, d1, mi
2722108e0: fd404fe1    	ldr	d1, [sp, #0x98]
2722108e4: 1e610841    	fmul	d1, d2, d1
2722108e8: fd4053e2    	ldr	d2, [sp, #0xa0]
2722108ec: 1e622821    	fadd	d1, d1, d2
2722108f0: 1e6129c1    	fadd	d1, d14, d1
2722108f4: 1e6121a0    	fcmp	d13, d1
2722108f8: 1e614da2    	fcsel	d2, d13, d1, mi
2722108fc: 1e6e2020    	fcmp	d1, d14
272210900: 1e624dc1    	fcsel	d1, d14, d2, mi
272210904: 1e603822    	fsub	d2, d1, d0
272210908: 1e6c1003    	fmov	d3, #0.50000000
27221090c: 1e630842    	fmul	d2, d2, d3
272210910: 1e622802    	fadd	d2, d0, d2
272210914: 1e602020    	fcmp	d1, d0
272210918: 1e604c48    	fcsel	d8, d2, d0, mi
27221091c: 1e614c49    	fcsel	d9, d2, d1, mi
272210920: d2800000    	mov	x0, #0x0                ; =0
272210924: b0075441    	adrp	x1, 0x280c99000 <_swift_willThrow+0x280c99000>
272210928: 9110a021    	add	x1, x1, #0x428
27221092c: 900b1502    	adrp	x2, 0x2884b0000 <_TtC27_SonicKit_MusicKit_PackagesP33_621ACBB81100A2B81D102DB8A698644434SonicKit_MusicKit_Packages_Locator+0x6161aa0>
272210930: 912c0042    	add	x2, x2, #0xb00
272210934: 94000292    	bl	0x27221137c <___swift_destroy_boxed_opaque_existential_1Tm+0x504>
272210938: 52800a01    	mov	w1, #0x50               ; =80
27221093c: 528000e2    	mov	w2, #0x7                ; =7
272210940: 948734d4    	bl	0x2743ddc90 <_swift_willThrow+0x2743ddc90>
272210944: aa0003fa    	mov	x26, x0
272210948: 3dc017e0    	ldr	q0, [sp, #0x50]
27221094c: 3d800400    	str	q0, [x0, #0x10]
272210950: 6d4403e1    	ldp	d1, d0, [sp, #0x40]
272210954: 6d022001    	stp	d1, d8, [x0, #0x20]
272210958: 52801008    	mov	w8, #0x80               ; =128
27221095c: 3900c008    	strb	w8, [x0, #0x30]
272210960: 6d03a400    	stp	d0, d9, [x0, #0x38]
272210964: 39012008    	strb	w8, [x0, #0x48]
272210968: aa1b03e0    	mov	x0, x27
27221096c: 948734e5    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
272210970: aa1803e0    	mov	x0, x24
272210974: 948734e3    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
272210978: 1400003b    	b	0x272210a64 <_OUTLINED_FUNCTION_0+0x950>
27221097c: a9598be1    	ldp	x1, x2, [sp, #0x198]
272210980: f940d7e3    	ldr	x3, [sp, #0x1a8]
272210984: 3946c3e4    	ldrb	w4, [sp, #0x1b0]
272210988: aa1c03e0    	mov	x0, x28
27221098c: f94047f5    	ldr	x21, [sp, #0x88]
272210990: 9400eb4a    	bl	0x27224b6b8 <_OUTLINED_FUNCTION_8+0x1058>
272210994: b5001355    	cbnz	x21, 0x272210bfc <_OUTLINED_FUNCTION_0+0xae8>
272210998: 1e60400a    	fmov	d10, d0
27221099c: a95b8be1    	ldp	x1, x2, [sp, #0x1b8]
2722109a0: f940e7e3    	ldr	x3, [sp, #0x1c8]
2722109a4: 394743e4    	ldrb	w4, [sp, #0x1d0]
2722109a8: aa1c03e0    	mov	x0, x28
2722109ac: 9400eb43    	bl	0x27224b6b8 <_OUTLINED_FUNCTION_8+0x1058>
2722109b0: b5001275    	cbnz	x21, 0x272210bfc <_OUTLINED_FUNCTION_0+0xae8>
2722109b4: 1e60400b    	fmov	d11, d0
2722109b8: f90047f5    	str	x21, [sp, #0x88]
2722109bc: fd403be2    	ldr	d2, [sp, #0x70]
2722109c0: 1e6d0840    	fmul	d0, d2, d13
2722109c4: 1e682800    	fadd	d0, d0, d8
2722109c8: 1e6029c0    	fadd	d0, d14, d0
2722109cc: fd403fed    	ldr	d13, [sp, #0x78]
2722109d0: 1e6021a0    	fcmp	d13, d0
2722109d4: 1e604da1    	fcsel	d1, d13, d0, mi
2722109d8: 1e6e2000    	fcmp	d0, d14
2722109dc: 1e614dc0    	fcsel	d0, d14, d1, mi
2722109e0: fd404fe1    	ldr	d1, [sp, #0x98]
2722109e4: 1e610841    	fmul	d1, d2, d1
2722109e8: fd4053e2    	ldr	d2, [sp, #0xa0]
2722109ec: 1e622821    	fadd	d1, d1, d2
2722109f0: 1e6129c1    	fadd	d1, d14, d1
2722109f4: 1e6121a0    	fcmp	d13, d1
2722109f8: 1e614da2    	fcsel	d2, d13, d1, mi
2722109fc: 1e6e2020    	fcmp	d1, d14
272210a00: 1e624dc1    	fcsel	d1, d14, d2, mi
272210a04: 1e603822    	fsub	d2, d1, d0
272210a08: 1e6c1003    	fmov	d3, #0.50000000
272210a0c: 1e630842    	fmul	d2, d2, d3
272210a10: 1e622802    	fadd	d2, d0, d2
272210a14: 1e602020    	fcmp	d1, d0
272210a18: 1e604c48    	fcsel	d8, d2, d0, mi
272210a1c: 1e614c49    	fcsel	d9, d2, d1, mi
272210a20: 394747f7    	ldrb	w23, [sp, #0x1d1]
272210a24: d2800000    	mov	x0, #0x0                ; =0
272210a28: b0075441    	adrp	x1, 0x280c99000 <_swift_willThrow+0x280c99000>
272210a2c: 9110a021    	add	x1, x1, #0x428
272210a30: 900b1502    	adrp	x2, 0x2884b0000 <_TtC27_SonicKit_MusicKit_PackagesP33_621ACBB81100A2B81D102DB8A698644434SonicKit_MusicKit_Packages_Locator+0x6161aa0>
272210a34: 912c0042    	add	x2, x2, #0xb00
272210a38: 94000251    	bl	0x27221137c <___swift_destroy_boxed_opaque_existential_1Tm+0x504>
272210a3c: 52800a01    	mov	w1, #0x50               ; =80
272210a40: 528000e2    	mov	w2, #0x7                ; =7
272210a44: 94873493    	bl	0x2743ddc90 <_swift_willThrow+0x2743ddc90>
272210a48: aa0003fa    	mov	x26, x0
272210a4c: 3dc017e0    	ldr	q0, [sp, #0x50]
272210a50: 3d800400    	str	q0, [x0, #0x10]
272210a54: 6d02200a    	stp	d10, d8, [x0, #0x20]
272210a58: 3900c017    	strb	w23, [x0, #0x30]
272210a5c: 6d03a40b    	stp	d11, d9, [x0, #0x38]
272210a60: 39012017    	strb	w23, [x0, #0x48]
272210a64: 910503e0    	add	x0, sp, #0x140
272210a68: 94000230    	bl	0x272211328 <___swift_destroy_boxed_opaque_existential_1Tm+0x4b0>
272210a6c: f81583b4    	stur	x20, [x29, #-0xa8]
272210a70: a941229c    	ldp	x28, x8, [x20, #0x10]
272210a74: 91000797    	add	x23, x28, #0x1
272210a78: eb48079f    	cmp	x28, x8, lsr #1
272210a7c: 54000342    	b.hs	0x272210ae4 <_OUTLINED_FUNCTION_0+0x9d0>
272210a80: f9000a97    	str	x23, [x20, #0x10]
272210a84: 8b1c1a88    	add	x8, x20, x28, lsl #6
272210a88: a9026d16    	stp	x22, x27, [x8, #0x20]
272210a8c: fc5b83a0    	ldur	d0, [x29, #-0x48]
272210a90: 6d033100    	stp	d0, d12, [x8, #0x30]
272210a94: fd00210f    	str	d15, [x8, #0x40]
272210a98: a904e119    	stp	x25, x24, [x8, #0x48]
272210a9c: f9002d1a    	str	x26, [x8, #0x58]
272210aa0: f94037e8    	ldr	x8, [sp, #0x68]
272210aa4: eb13011f    	cmp	x8, x19
272210aa8: 54000340    	b.eq	0x272210b10 <_OUTLINED_FUNCTION_0+0x9fc>
272210aac: f9404be9    	ldr	x9, [sp, #0x90]
272210ab0: 91026129    	add	x9, x9, #0x98
272210ab4: 91000673    	add	x19, x19, #0x1
272210ab8: f94033fc    	ldr	x28, [sp, #0x60]
272210abc: f94043f7    	ldr	x23, [sp, #0x80]
272210ac0: 17ffff3c    	b	0x2722107b0 <_OUTLINED_FUNCTION_0+0x69c>
272210ac4: d00002f0    	adrp	x16, 0x27226e000 <_OUTLINED_FUNCTION_3+0x2880>
272210ac8: 91104210    	add	x16, x16, #0x410
272210acc: dac123f0    	paciza	x16
272210ad0: aa1003e1    	mov	x1, x16
272210ad4: b0075440    	adrp	x0, 0x280c99000 <_swift_willThrow+0x280c99000>
272210ad8: 91100000    	add	x0, x0, #0x400
272210adc: 948734ed    	bl	0x2743dde90 <_swift_willThrow+0x2743dde90>
272210ae0: 17ffff4f    	b	0x27221081c <_OUTLINED_FUNCTION_0+0x708>
272210ae4: f100051f    	cmp	x8, #0x1
272210ae8: 1a9f97e0    	cset	w0, hi
272210aec: d102a3b4    	sub	x20, x29, #0xa8
272210af0: aa1703e1    	mov	x1, x23
272210af4: 52800022    	mov	w2, #0x1                ; =1
272210af8: 940149d8    	bl	0x272263258 <_OUTLINED_FUNCTION_24+0x2a68>
272210afc: f85583b4    	ldur	x20, [x29, #-0xa8]
272210b00: 17ffffe0    	b	0x272210a80 <_OUTLINED_FUNCTION_0+0x96c>
272210b04: f002f694    	adrp	x20, 0x2780e3000 <_swift_willThrow+0x2780e3000>
272210b08: f9462a94    	ldr	x20, [x20, #0xc50]
272210b0c: 14000005    	b	0x272210b20 <_OUTLINED_FUNCTION_0+0xa0c>
272210b10: f94043e0    	ldr	x0, [sp, #0x80]
272210b14: 94873473    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210b18: f9401fe9    	ldr	x9, [sp, #0x38]
272210b1c: f94033fc    	ldr	x28, [sp, #0x60]
272210b20: f9400a96    	ldr	x22, [x20, #0x10]
272210b24: f9400933    	ldr	x19, [x9, #0x10]
272210b28: ab160277    	adds	x23, x19, x22
272210b2c: fd4007ee    	ldr	d14, [sp, #0x8]
272210b30: 540019e6    	b.vs	0x272210e6c <_OUTLINED_FUNCTION_0+0xd58>
272210b34: aa0903e0    	mov	x0, x9
272210b38: 948734ce    	bl	0x2743dde70 <_swift_willThrow+0x2743dde70>
272210b3c: f9401fe3    	ldr	x3, [sp, #0x38]
272210b40: 34000080    	cbz	w0, 0x272210b50 <_OUTLINED_FUNCTION_0+0xa3c>
272210b44: f9400c68    	ldr	x8, [x3, #0x18]
272210b48: eb4806ff    	cmp	x23, x8, lsr #1
272210b4c: 540000cd    	b.le	0x272210b64 <_OUTLINED_FUNCTION_0+0xa50>
272210b50: eb17027f    	cmp	x19, x23
272210b54: 9a97c261    	csel	x1, x19, x23, gt
272210b58: 52800022    	mov	w2, #0x1                ; =1
272210b5c: 940109ae    	bl	0x272253214 <_OUTLINED_FUNCTION_1+0x954>
272210b60: aa0003e3    	mov	x3, x0
272210b64: f94017ea    	ldr	x10, [sp, #0x28]
272210b68: 9100054a    	add	x10, x10, #0x1
272210b6c: f9400a88    	ldr	x8, [x20, #0x10]
272210b70: b4000348    	cbz	x8, 0x272210bd8 <_OUTLINED_FUNCTION_0+0xac4>
272210b74: a9412468    	ldp	x8, x9, [x3, #0x10]
272210b78: d341fd29    	lsr	x9, x9, #1
272210b7c: cb080129    	sub	x9, x9, x8
272210b80: eb16013f    	cmp	x9, x22
272210b84: 5400176b    	b.lt	0x272210e70 <_OUTLINED_FUNCTION_0+0xd5c>
272210b88: 8b081868    	add	x8, x3, x8, lsl #6
272210b8c: 91008100    	add	x0, x8, #0x20
272210b90: 91008281    	add	x1, x20, #0x20
272210b94: aa1603e2    	mov	x2, x22
272210b98: aa0a03f7    	mov	x23, x10
272210b9c: aa0303f3    	mov	x19, x3
272210ba0: 900b1503    	adrp	x3, 0x2884b0000 <_TtC27_SonicKit_MusicKit_PackagesP33_621ACBB81100A2B81D102DB8A698644434SonicKit_MusicKit_Packages_Locator+0x6161aa0>
272210ba4: 9117c063    	add	x3, x3, #0x5f0
272210ba8: 94873442    	bl	0x2743ddcb0 <_swift_willThrow+0x2743ddcb0>
272210bac: aa1403e0    	mov	x0, x20
272210bb0: 9487344c    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210bb4: aa1303e9    	mov	x9, x19
272210bb8: aa1703ea    	mov	x10, x23
272210bbc: fd401be4    	ldr	d4, [sp, #0x30]
272210bc0: b4ffd8f6    	cbz	x22, 0x2722106dc <_OUTLINED_FUNCTION_0+0x5c8>
272210bc4: f9400928    	ldr	x8, [x9, #0x10]
272210bc8: ab160108    	adds	x8, x8, x22
272210bcc: 54001546    	b.vs	0x272210e74 <_OUTLINED_FUNCTION_0+0xd60>
272210bd0: f9000928    	str	x8, [x9, #0x10]
272210bd4: 17fffec2    	b	0x2722106dc <_OUTLINED_FUNCTION_0+0x5c8>
272210bd8: aa1403e0    	mov	x0, x20
272210bdc: aa0a03f4    	mov	x20, x10
272210be0: aa0303f3    	mov	x19, x3
272210be4: 9487343f    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210be8: aa1303e9    	mov	x9, x19
272210bec: aa1403ea    	mov	x10, x20
272210bf0: fd401be4    	ldr	d4, [sp, #0x30]
272210bf4: b4ffd756    	cbz	x22, 0x2722106dc <_OUTLINED_FUNCTION_0+0x5c8>
272210bf8: d4200020    	brk	#0x1
272210bfc: aa1503f3    	mov	x19, x21
272210c00: aa1803e0    	mov	x0, x24
272210c04: 94873437    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210c08: aa1b03e0    	mov	x0, x27
272210c0c: 94873435    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210c10: 910503e0    	add	x0, sp, #0x140
272210c14: 940001c5    	bl	0x272211328 <___swift_destroy_boxed_opaque_existential_1Tm+0x4b0>
272210c18: aa1403e0    	mov	x0, x20
272210c1c: 948734a1    	bl	0x2743ddea0 <_swift_willThrow+0x2743ddea0>
272210c20: f9401fe0    	ldr	x0, [sp, #0x38]
272210c24: 9487342f    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210c28: f94043e0    	ldr	x0, [sp, #0x80]
272210c2c: 9487342d    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210c30: aa1303f5    	mov	x21, x19
272210c34: aa0903e0    	mov	x0, x9
272210c38: 9107c3ff    	add	sp, sp, #0x1f0
272210c3c: a9497bfd    	ldp	x29, x30, [sp, #0x90]
272210c40: a9484ff4    	ldp	x20, x19, [sp, #0x80]
272210c44: a9475bf7    	ldp	x23, x22, [sp, #0x70]
272210c48: a94663f9    	ldp	x25, x24, [sp, #0x60]
272210c4c: a9456bfb    	ldp	x27, x26, [sp, #0x50]
272210c50: f94023fc    	ldr	x28, [sp, #0x40]
272210c54: 6d4323e9    	ldp	d9, d8, [sp, #0x30]
272210c58: 6d422beb    	ldp	d11, d10, [sp, #0x20]
272210c5c: 6d4133ed    	ldp	d13, d12, [sp, #0x10]
272210c60: 6cca3bef    	ldp	d15, d14, [sp], #0xa0
272210c64: d65f0fff    	retab
272210c68: d2800013    	mov	x19, #0x0               ; =0
272210c6c: f9400928    	ldr	x8, [x9, #0x10]
272210c70: f81b83a8    	stur	x8, [x29, #-0x48]
272210c74: 52800414    	mov	w20, #0x20              ; =32
272210c78: d007545c    	adrp	x28, 0x280c9a000 <_swift_willThrow+0x280c9a000>
272210c7c: 912a639c    	add	x28, x28, #0xa98
272210c80: f85b83a8    	ldur	x8, [x29, #-0x48]
272210c84: eb13011f    	cmp	x8, x19
272210c88: 540009e0    	b.eq	0x272210dc4 <_OUTLINED_FUNCTION_0+0xcb0>
272210c8c: f9400928    	ldr	x8, [x9, #0x10]
272210c90: eb08027f    	cmp	x19, x8
272210c94: 54000ea2    	b.hs	0x272210e68 <_OUTLINED_FUNCTION_0+0xd54>
272210c98: 8b140128    	add	x8, x9, x20
272210c9c: ad410101    	ldp	q1, q0, [x8, #0x20]
272210ca0: ad400903    	ldp	q3, q2, [x8]
272210ca4: 910503e8    	add	x8, sp, #0x140
272210ca8: ad000903    	stp	q3, q2, [x8]
272210cac: ad010101    	stp	q1, q0, [x8, #0x20]
272210cb0: a9545bf9    	ldp	x25, x22, [sp, #0x140]
272210cb4: 6d552be9    	ldp	d9, d10, [sp, #0x150]
272210cb8: fd40b3e8    	ldr	d8, [sp, #0x160]
272210cbc: a956dff8    	ldp	x24, x23, [sp, #0x168]
272210cc0: 910503e0    	add	x0, sp, #0x140
272210cc4: 9102a3e1    	add	x1, sp, #0xa8
272210cc8: 94000155    	bl	0x27221121c <___swift_destroy_boxed_opaque_existential_1Tm+0x3a4>
272210ccc: b0075448    	adrp	x8, 0x280c99000 <_swift_willThrow+0x280c99000>
272210cd0: f9420915    	ldr	x21, [x8, #0x410]
272210cd4: aa1603e0    	mov	x0, x22
272210cd8: 9487340a    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
272210cdc: aa1703e0    	mov	x0, x23
272210ce0: 94873408    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
272210ce4: b10006bf    	cmn	x21, #0x1
272210ce8: 540005e1    	b.ne	0x272210da4 <_OUTLINED_FUNCTION_0+0xc90>
272210cec: a9400f82    	ldp	x2, x3, [x28]
272210cf0: 6d41378c    	ldp	d12, d13, [x28, #0x10]
272210cf4: fd40138b    	ldr	d11, [x28, #0x20]
272210cf8: a942ef9a    	ldp	x26, x27, [x28, #0x28]
272210cfc: eb02033f    	cmp	x25, x2
272210d00: fa4302c0    	ccmp	x22, x3, #0x0, eq
272210d04: 540000c0    	b.eq	0x272210d1c <_OUTLINED_FUNCTION_0+0xc08>
272210d08: aa1903e0    	mov	x0, x25
272210d0c: aa1603e1    	mov	x1, x22
272210d10: 52800004    	mov	w4, #0x0                ; =0
272210d14: 9487330b    	bl	0x2743dd940 <_swift_willThrow+0x2743dd940>
272210d18: 36000300    	tbz	w0, #0x0, 0x272210d78 <_OUTLINED_FUNCTION_0+0xc64>
272210d1c: 1e6c2120    	fcmp	d9, d12
272210d20: 1e6d0540    	fccmp	d10, d13, #0x0, eq
272210d24: 540002a1    	b.ne	0x272210d78 <_OUTLINED_FUNCTION_0+0xc64>
272210d28: 1e6b2100    	fcmp	d8, d11
272210d2c: 54000261    	b.ne	0x272210d78 <_OUTLINED_FUNCTION_0+0xc64>
272210d30: eb1a031f    	cmp	x24, x26
272210d34: fa5b02e0    	ccmp	x23, x27, #0x0, eq
272210d38: 54000840    	b.eq	0x272210e40 <_OUTLINED_FUNCTION_0+0xd2c>
272210d3c: aa1803e0    	mov	x0, x24
272210d40: aa1703e1    	mov	x1, x23
272210d44: aa1a03e2    	mov	x2, x26
272210d48: aa1b03e3    	mov	x3, x27
272210d4c: 52800004    	mov	w4, #0x0                ; =0
272210d50: 948732fc    	bl	0x2743dd940 <_swift_willThrow+0x2743dd940>
272210d54: aa0003f8    	mov	x24, x0
272210d58: 910503e0    	add	x0, sp, #0x140
272210d5c: 94000147    	bl	0x272211278 <___swift_destroy_boxed_opaque_existential_1Tm+0x400>
272210d60: aa1703e0    	mov	x0, x23
272210d64: 948733df    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210d68: aa1603e0    	mov	x0, x22
272210d6c: 948733dd    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210d70: 36000118    	tbz	w24, #0x0, 0x272210d90 <_OUTLINED_FUNCTION_0+0xc7c>
272210d74: 14000039    	b	0x272210e58 <_OUTLINED_FUNCTION_0+0xd44>
272210d78: 910503e0    	add	x0, sp, #0x140
272210d7c: 9400013f    	bl	0x272211278 <___swift_destroy_boxed_opaque_existential_1Tm+0x400>
272210d80: aa1703e0    	mov	x0, x23
272210d84: 948733d7    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210d88: aa1603e0    	mov	x0, x22
272210d8c: 948733d5    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210d90: 91010294    	add	x20, x20, #0x40
272210d94: 91000673    	add	x19, x19, #0x1
272210d98: f9401fe9    	ldr	x9, [sp, #0x38]
272210d9c: fd401be4    	ldr	d4, [sp, #0x30]
272210da0: 17ffffb8    	b	0x272210c80 <_OUTLINED_FUNCTION_0+0xb6c>
272210da4: d00002f0    	adrp	x16, 0x27226e000 <_OUTLINED_FUNCTION_3+0x2880>
272210da8: 9114d210    	add	x16, x16, #0x534
272210dac: dac123f0    	paciza	x16
272210db0: aa1003e1    	mov	x1, x16
272210db4: b0075440    	adrp	x0, 0x280c99000 <_swift_willThrow+0x280c99000>
272210db8: 91104000    	add	x0, x0, #0x410
272210dbc: 94873435    	bl	0x2743dde90 <_swift_willThrow+0x2743dde90>
272210dc0: 17ffffcb    	b	0x272210cec <_OUTLINED_FUNCTION_0+0xbd8>
272210dc4: 1e6e2080    	fcmp	d4, d14
272210dc8: 5400038d    	b.le	0x272210e38 <_OUTLINED_FUNCTION_0+0xd24>
272210dcc: 6d0a93ee    	stp	d14, d4, [sp, #0xa8]
272210dd0: 910503e8    	add	x8, sp, #0x140
272210dd4: 9102a3e0    	add	x0, sp, #0xa8
272210dd8: aa0903f3    	mov	x19, x9
272210ddc: 940000ca    	bl	0x272211104 <___swift_destroy_boxed_opaque_existential_1Tm+0x28c>
272210de0: b0075441    	adrp	x1, 0x280c99000 <_swift_willThrow+0x280c99000>
272210de4: 9110c021    	add	x1, x1, #0x430
272210de8: 900b1502    	adrp	x2, 0x2884b0000 <_TtC27_SonicKit_MusicKit_PackagesP33_621ACBB81100A2B81D102DB8A698644434SonicKit_MusicKit_Packages_Locator+0x6161aa0>
272210dec: 9117c042    	add	x2, x2, #0x5f0
272210df0: d2800000    	mov	x0, #0x0                ; =0
272210df4: 94000162    	bl	0x27221137c <___swift_destroy_boxed_opaque_existential_1Tm+0x504>
272210df8: 52800c01    	mov	w1, #0x60               ; =96
272210dfc: 528000e2    	mov	w2, #0x7                ; =7
272210e00: 948733a4    	bl	0x2743ddc90 <_swift_willThrow+0x2743ddc90>
272210e04: d0000428    	adrp	x8, 0x272296000 <_swift_willThrow+0x272296000>
272210e08: 3dc1b900    	ldr	q0, [x8, #0x6e0]
272210e0c: 910503e8    	add	x8, sp, #0x140
272210e10: ad400901    	ldp	q1, q2, [x8]
272210e14: ad008400    	stp	q0, q1, [x0, #0x10]
272210e18: ad410500    	ldp	q0, q1, [x8, #0x20]
272210e1c: ad018002    	stp	q2, q0, [x0, #0x30]
272210e20: 3d801401    	str	q1, [x0, #0x50]
272210e24: f90057e0    	str	x0, [sp, #0xa8]
272210e28: 9102a3f4    	add	x20, sp, #0xa8
272210e2c: aa1303e0    	mov	x0, x19
272210e30: 9401a968    	bl	0x27227b3d0 <___swift_memcpy137_8+0x698>
272210e34: f94057e9    	ldr	x9, [sp, #0xa8]
272210e38: f94047f5    	ldr	x21, [sp, #0x88]
272210e3c: 17ffff7e    	b	0x272210c34 <_OUTLINED_FUNCTION_0+0xb20>
272210e40: 910503e0    	add	x0, sp, #0x140
272210e44: 9400010d    	bl	0x272211278 <___swift_destroy_boxed_opaque_existential_1Tm+0x400>
272210e48: aa1703e0    	mov	x0, x23
272210e4c: 948733a5    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210e50: aa1603e0    	mov	x0, x22
272210e54: 948733a3    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272210e58: f94047f5    	ldr	x21, [sp, #0x88]
272210e5c: f9401fe9    	ldr	x9, [sp, #0x38]
272210e60: 17ffff75    	b	0x272210c34 <_OUTLINED_FUNCTION_0+0xb20>
272210e64: d4200020    	brk	#0x1
272210e68: d4200020    	brk	#0x1
272210e6c: d4200020    	brk	#0x1
272210e70: d4200020    	brk	#0x1
272210e74: d4200020    	brk	#0x1
