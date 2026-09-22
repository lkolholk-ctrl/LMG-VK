
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

0000000272210e78 <___swift_destroy_boxed_opaque_existential_1Tm>:
272211104: d503237f    	pacibsp
272211108: 6db933ed    	stp	d13, d12, [sp, #-0x70]!
27221110c: 6d012beb    	stp	d11, d10, [sp, #0x10]
272211110: 6d0223e9    	stp	d9, d8, [sp, #0x20]
272211114: a9035ff8    	stp	x24, x23, [sp, #0x30]
272211118: a90457f6    	stp	x22, x21, [sp, #0x40]
27221111c: a9054ff4    	stp	x20, x19, [sp, #0x50]
272211120: a9067bfd    	stp	x29, x30, [sp, #0x60]
272211124: 910183fd    	add	x29, sp, #0x60
272211128: aa0803f3    	mov	x19, x8
27221112c: 6d402009    	ldp	d9, d8, [x0]
272211130: 90075448    	adrp	x8, 0x280c99000 <_swift_willThrow+0x280c99000>
272211134: f9420908    	ldr	x8, [x8, #0x410]
272211138: b100051f    	cmn	x8, #0x1
27221113c: 54000601    	b.ne	0x2722111fc <___swift_destroy_boxed_opaque_existential_1Tm+0x384>
272211140: b0075448    	adrp	x8, 0x280c9a000 <_swift_willThrow+0x280c9a000>
272211144: 912a6108    	add	x8, x8, #0xa98
272211148: a9405116    	ldp	x22, x20, [x8]
27221114c: 6d412d0a    	ldp	d10, d11, [x8, #0x10]
272211150: fd40110c    	ldr	d12, [x8, #0x20]
272211154: a942d517    	ldp	x23, x21, [x8, #0x28]
272211158: 90075441    	adrp	x1, 0x280c99000 <_swift_willThrow+0x280c99000>
27221115c: 9110a021    	add	x1, x1, #0x428
272211160: f00b14e2    	adrp	x2, 0x2884b0000 <_TtC27_SonicKit_MusicKit_PackagesP33_621ACBB81100A2B81D102DB8A698644434SonicKit_MusicKit_Packages_Locator+0x6161aa0>
272211164: 912c0042    	add	x2, x2, #0xb00
272211168: d2800000    	mov	x0, #0x0                ; =0
27221116c: 94000084    	bl	0x27221137c <___swift_destroy_boxed_opaque_existential_1Tm+0x504>
272211170: 52801018    	mov	w24, #0x80              ; =128
272211174: 52801001    	mov	w1, #0x80               ; =128
272211178: 528000e2    	mov	w2, #0x7                ; =7
27221117c: 948732c5    	bl	0x2743ddc90 <_swift_willThrow+0x2743ddc90>
272211180: b0000428    	adrp	x8, 0x272296000 <_swift_willThrow+0x272296000>
272211184: 3dc1bd00    	ldr	q0, [x8, #0x6f0]
272211188: 3d800400    	str	q0, [x0, #0x10]
27221118c: 6d02240b    	stp	d11, d9, [x0, #0x20]
272211190: 3900c018    	strb	w24, [x0, #0x30]
272211194: 6d03a40a    	stp	d10, d9, [x0, #0x38]
272211198: 39012018    	strb	w24, [x0, #0x48]
27221119c: 6d05200a    	stp	d10, d8, [x0, #0x50]
2722111a0: 39018018    	strb	w24, [x0, #0x60]
2722111a4: 6d06a00b    	stp	d11, d8, [x0, #0x68]
2722111a8: 3901e018    	strb	w24, [x0, #0x78]
2722111ac: a9005276    	stp	x22, x20, [x19]
2722111b0: 6d012e6a    	stp	d10, d11, [x19, #0x10]
2722111b4: fd00126c    	str	d12, [x19, #0x20]
2722111b8: a902d677    	stp	x23, x21, [x19, #0x28]
2722111bc: f9001e60    	str	x0, [x19, #0x38]
2722111c0: aa1403e0    	mov	x0, x20
2722111c4: 948732cf    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
2722111c8: aa1503e0    	mov	x0, x21
2722111cc: a9467bfd    	ldp	x29, x30, [sp, #0x60]
2722111d0: a9454ff4    	ldp	x20, x19, [sp, #0x50]
2722111d4: a94457f6    	ldp	x22, x21, [sp, #0x40]
2722111d8: a9435ff8    	ldp	x24, x23, [sp, #0x30]
2722111dc: 6d4223e9    	ldp	d9, d8, [sp, #0x20]
2722111e0: 6d412beb    	ldp	d11, d10, [sp, #0x10]
2722111e4: 6cc733ed    	ldp	d13, d12, [sp], #0x70
2722111e8: d50323ff    	autibsp
2722111ec: ca1e07d0    	eor	x16, x30, x30, lsl #1
2722111f0: b6f00050    	tbz	x16, #0x3e, 0x2722111f8 <___swift_destroy_boxed_opaque_existential_1Tm+0x380>
2722111f4: d4388e20    	brk	#0xc471
2722111f8: 148732c2    	b	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
2722111fc: 90075440    	adrp	x0, 0x280c99000 <_swift_willThrow+0x280c99000>
272211200: 91104000    	add	x0, x0, #0x410
272211204: b00002f0    	adrp	x16, 0x27226e000 <_OUTLINED_FUNCTION_3+0x2880>
272211208: 9114d210    	add	x16, x16, #0x534
27221120c: dac123f0    	paciza	x16
272211210: aa1003e1    	mov	x1, x16
272211214: 9487331f    	bl	0x2743dde90 <_swift_willThrow+0x2743dde90>
272211218: 17ffffca    	b	0x272211140 <___swift_destroy_boxed_opaque_existential_1Tm+0x2c8>
