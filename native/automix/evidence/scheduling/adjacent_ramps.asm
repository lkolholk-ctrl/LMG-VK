
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

00000002722706ac <_OUTLINED_FUNCTION_1>:
27227086c: d503237f    	pacibsp
272270870: d10303ff    	sub	sp, sp, #0xc0
272270874: 6d042beb    	stp	d11, d10, [sp, #0x40]
272270878: 6d0523e9    	stp	d9, d8, [sp, #0x50]
27227087c: f90033fc    	str	x28, [sp, #0x60]
272270880: a9076bfb    	stp	x27, x26, [sp, #0x70]
272270884: a90863f9    	stp	x25, x24, [sp, #0x80]
272270888: a9095bf7    	stp	x23, x22, [sp, #0x90]
27227088c: a90a4ff4    	stp	x20, x19, [sp, #0xa0]
272270890: a90b7bfd    	stp	x29, x30, [sp, #0xb0]
272270894: 9102c3fd    	add	x29, sp, #0xb0
272270898: a941a009    	ldp	x9, x8, [x0, #0x18]
27227089c: d341fd08    	lsr	x8, x8, #1
2722708a0: eb090113    	subs	x19, x8, x9
2722708a4: 540013c6    	b.vs	0x272270b1c <_OUTLINED_FUNCTION_1+0x470>
2722708a8: aa1503f6    	mov	x22, x21
2722708ac: a902a7e8    	stp	x8, x9, [sp, #0x28]
2722708b0: a9406017    	ldp	x23, x24, [x0]
2722708b4: f940081a    	ldr	x26, [x0, #0x10]
2722708b8: f9400ae8    	ldr	x8, [x23, #0x10]
2722708bc: eb08027f    	cmp	x19, x8
2722708c0: f90013e8    	str	x8, [sp, #0x20]
2722708c4: 9a88b279    	csel	x25, x19, x8, lt
2722708c8: f002f388    	adrp	x8, 0x2780e3000 <_swift_willThrow+0x2780e3000>
2722708cc: f9462908    	ldr	x8, [x8, #0xc50]
2722708d0: f81b83a8    	stur	x8, [x29, #-0x48]
2722708d4: 8ab9ff21    	bic	x1, x25, x25, asr #63
2722708d8: d10123b4    	sub	x20, x29, #0x48
2722708dc: 52800000    	mov	w0, #0x0                ; =0
2722708e0: 52800002    	mov	w2, #0x0                ; =0
2722708e4: 97ffca65    	bl	0x272263278 <_OUTLINED_FUNCTION_24+0x2a88>
2722708e8: b7f811d3    	tbnz	x19, #0x3f, 0x272270b20 <_OUTLINED_FUNCTION_1+0x474>
2722708ec: a900dbf8    	stp	x24, x22, [sp, #0x8]
2722708f0: f85b83b4    	ldur	x20, [x29, #-0x48]
2722708f4: aa1903f5    	mov	x21, x25
2722708f8: f9001ff7    	str	x23, [sp, #0x38]
2722708fc: f9000ffa    	str	x26, [sp, #0x18]
272270900: b40006b9    	cbz	x25, 0x2722709d4 <_OUTLINED_FUNCTION_1+0x328>
272270904: aa1703e0    	mov	x0, x23
272270908: 9485b4fe    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
27227090c: aa1803e0    	mov	x0, x24
272270910: 9485b58c    	bl	0x2743ddf40 <_swift_willThrow+0x2743ddf40>
272270914: 9100c2f7    	add	x23, x23, #0x30
272270918: 52800308    	mov	w8, #0x18               ; =24
27227091c: a942abeb    	ldp	x11, x10, [sp, #0x28]
272270920: 9b086948    	madd	x8, x10, x8, x26
272270924: 91002116    	add	x22, x8, #0x8
272270928: 52800509    	mov	w9, #0x28               ; =40
27227092c: aa1503fb    	mov	x27, x21
272270930: f94013ec    	ldr	x12, [sp, #0x20]
272270934: aa0c03fc    	mov	x28, x12
272270938: aa0a03f9    	mov	x25, x10
27227093c: b4000f5c    	cbz	x28, 0x272270b24 <_OUTLINED_FUNCTION_1+0x478>
272270940: eb19017f    	cmp	x11, x25
272270944: 54000f00    	b.eq	0x272270b24 <_OUTLINED_FUNCTION_1+0x478>
272270948: eb0b033f    	cmp	x25, x11
27227094c: 54000dca    	b.ge	0x272270b04 <_OUTLINED_FUNCTION_1+0x458>
272270950: 394002f3    	ldrb	w19, [x23]
272270954: 7103ee7f    	cmp	w19, #0xfb
272270958: 54000e68    	b.hi	0x272270b24 <_OUTLINED_FUNCTION_1+0x478>
27227095c: fc5f82e8    	ldur	d8, [x23, #-0x8]
272270960: fd4002c9    	ldr	d9, [x22]
272270964: 1e682120    	fcmp	d9, d8
272270968: 54000d04    	b.mi	0x272270b08 <_OUTLINED_FUNCTION_1+0x45c>
27227096c: fc5f02ea    	ldur	d10, [x23, #-0x10]
272270970: fc5f82cb    	ldur	d11, [x22, #-0x8]
272270974: 94000513    	bl	0x272271dc0 <_OUTLINED_FUNCTION_4>
272270978: 540001a2    	b.hs	0x2722709ac <_OUTLINED_FUNCTION_1+0x300>
27227097c: f9000a98    	str	x24, [x20, #0x10]
272270980: 9b095348    	madd	x8, x26, x9, x20
272270984: 6d022d0a    	stp	d10, d11, [x8, #0x20]
272270988: 910062f7    	add	x23, x23, #0x18
27227098c: 6d032508    	stp	d8, d9, [x8, #0x30]
272270990: 39010113    	strb	w19, [x8, #0x40]
272270994: d100079c    	sub	x28, x28, #0x1
272270998: 910062d6    	add	x22, x22, #0x18
27227099c: 91000739    	add	x25, x25, #0x1
2722709a0: f100077b    	subs	x27, x27, #0x1
2722709a4: 54fffcc1    	b.ne	0x27227093c <_OUTLINED_FUNCTION_1+0x290>
2722709a8: 14000012    	b	0x2722709f0 <_OUTLINED_FUNCTION_1+0x344>
2722709ac: f100051f    	cmp	x8, #0x1
2722709b0: 1a9f97e0    	cset	w0, hi
2722709b4: 94000508    	bl	0x272271dd4 <_OUTLINED_FUNCTION_5>
2722709b8: 97ffca30    	bl	0x272263278 <_OUTLINED_FUNCTION_24+0x2a88>
2722709bc: 52800509    	mov	w9, #0x28               ; =40
2722709c0: f94003f5    	ldr	x21, [sp]
2722709c4: a9422fec    	ldp	x12, x11, [sp, #0x20]
2722709c8: f9401bea    	ldr	x10, [sp, #0x30]
2722709cc: f85b83b4    	ldur	x20, [x29, #-0x48]
2722709d0: 17ffffeb    	b	0x27227097c <_OUTLINED_FUNCTION_1+0x2d0>
2722709d4: aa1703e0    	mov	x0, x23
2722709d8: 9485b4ca    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
2722709dc: aa1803e0    	mov	x0, x24
2722709e0: 9485b558    	bl	0x2743ddf40 <_swift_willThrow+0x2743ddf40>
2722709e4: a942abeb    	ldp	x11, x10, [sp, #0x28]
2722709e8: aa0a03f9    	mov	x25, x10
2722709ec: f94013ec    	ldr	x12, [sp, #0x20]
2722709f0: d2800017    	mov	x23, #0x0               ; =0
2722709f4: 52800308    	mov	w8, #0x18               ; =24
2722709f8: f9400fe9    	ldr	x9, [sp, #0x18]
2722709fc: 9b082729    	madd	x9, x25, x8, x9
272270a00: 91002133    	add	x19, x9, #0x8
272270a04: cb0b032d    	sub	x13, x25, x11
272270a08: cb15019b    	sub	x27, x12, x21
272270a0c: f9401fe9    	ldr	x9, [sp, #0x38]
272270a10: 9b0826a8    	madd	x8, x21, x8, x9
272270a14: 9100c11c    	add	x28, x8, #0x30
272270a18: 52800509    	mov	w9, #0x28               ; =40
272270a1c: eb17037f    	cmp	x27, x23
272270a20: 54000520    	b.eq	0x272270ac4 <_OUTLINED_FUNCTION_1+0x418>
272270a24: 8b1702a8    	add	x8, x21, x23
272270a28: eb0c011f    	cmp	x8, x12
272270a2c: 54000702    	b.hs	0x272270b0c <_OUTLINED_FUNCTION_1+0x460>
272270a30: b100051f    	cmn	x8, #0x1
272270a34: 540006e6    	b.vs	0x272270b10 <_OUTLINED_FUNCTION_1+0x464>
272270a38: 8b1701a8    	add	x8, x13, x23
272270a3c: b4000448    	cbz	x8, 0x272270ac4 <_OUTLINED_FUNCTION_1+0x418>
272270a40: 8b170328    	add	x8, x25, x23
272270a44: eb0a033f    	cmp	x25, x10
272270a48: fa4ba100    	ccmp	x8, x11, #0x0, ge
272270a4c: 5400064a    	b.ge	0x272270b14 <_OUTLINED_FUNCTION_1+0x468>
272270a50: fc5f8388    	ldur	d8, [x28, #-0x8]
272270a54: fd400269    	ldr	d9, [x19]
272270a58: 1e682120    	fcmp	d9, d8
272270a5c: 540005e4    	b.mi	0x272270b18 <_OUTLINED_FUNCTION_1+0x46c>
272270a60: fc5f038a    	ldur	d10, [x28, #-0x10]
272270a64: 39400396    	ldrb	w22, [x28]
272270a68: fc5f826b    	ldur	d11, [x19, #-0x8]
272270a6c: 940004d5    	bl	0x272271dc0 <_OUTLINED_FUNCTION_4>
272270a70: 54000142    	b.hs	0x272270a98 <_OUTLINED_FUNCTION_1+0x3ec>
272270a74: f9000a98    	str	x24, [x20, #0x10]
272270a78: 9b095348    	madd	x8, x26, x9, x20
272270a7c: 6d022d0a    	stp	d10, d11, [x8, #0x20]
272270a80: 6d032508    	stp	d8, d9, [x8, #0x30]
272270a84: 39010116    	strb	w22, [x8, #0x40]
272270a88: 91006273    	add	x19, x19, #0x18
272270a8c: 910006f7    	add	x23, x23, #0x1
272270a90: 9100639c    	add	x28, x28, #0x18
272270a94: 17ffffe2    	b	0x272270a1c <_OUTLINED_FUNCTION_1+0x370>
272270a98: f100051f    	cmp	x8, #0x1
272270a9c: 1a9f97e0    	cset	w0, hi
272270aa0: 940004cd    	bl	0x272271dd4 <_OUTLINED_FUNCTION_5>
272270aa4: f9000fed    	str	x13, [sp, #0x18]
272270aa8: 97ffc9f4    	bl	0x272263278 <_OUTLINED_FUNCTION_24+0x2a88>
272270aac: 52800509    	mov	w9, #0x28               ; =40
272270ab0: a941b3ed    	ldp	x13, x12, [sp, #0x18]
272270ab4: f94003f5    	ldr	x21, [sp]
272270ab8: a942abeb    	ldp	x11, x10, [sp, #0x28]
272270abc: f85b83b4    	ldur	x20, [x29, #-0x48]
272270ac0: 17ffffed    	b	0x272270a74 <_OUTLINED_FUNCTION_1+0x3c8>
272270ac4: f94007e0    	ldr	x0, [sp, #0x8]
272270ac8: 9485b516    	bl	0x2743ddf20 <_swift_willThrow+0x2743ddf20>
272270acc: f9401fe0    	ldr	x0, [sp, #0x38]
272270ad0: 9485b484    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272270ad4: aa1403e0    	mov	x0, x20
272270ad8: f9400bf5    	ldr	x21, [sp, #0x10]
272270adc: a94b7bfd    	ldp	x29, x30, [sp, #0xb0]
272270ae0: a94a4ff4    	ldp	x20, x19, [sp, #0xa0]
272270ae4: a9495bf7    	ldp	x23, x22, [sp, #0x90]
272270ae8: a94863f9    	ldp	x25, x24, [sp, #0x80]
272270aec: a9476bfb    	ldp	x27, x26, [sp, #0x70]
272270af0: f94033fc    	ldr	x28, [sp, #0x60]
272270af4: 6d4523e9    	ldp	d9, d8, [sp, #0x50]
272270af8: 6d442beb    	ldp	d11, d10, [sp, #0x40]
272270afc: 910303ff    	add	sp, sp, #0xc0
272270b00: d65f0fff    	retab
272270b04: d4200020    	brk	#0x1
272270b08: d4200020    	brk	#0x1
272270b0c: d4200020    	brk	#0x1
272270b10: d4200020    	brk	#0x1
272270b14: d4200020    	brk	#0x1
272270b18: d4200020    	brk	#0x1
272270b1c: d4200020    	brk	#0x1
272270b20: d4200020    	brk	#0x1
272270b24: d4200020    	brk	#0x1
272270b28: d503237f    	pacibsp
272270b2c: a9be4ff4    	stp	x20, x19, [sp, #-0x20]!
