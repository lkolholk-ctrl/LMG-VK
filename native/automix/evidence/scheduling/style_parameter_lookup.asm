
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000027226b780 <_OUTLINED_FUNCTION_3>:
27226bd5c: d503237f    	pacibsp
27226bd60: d10203ff    	sub	sp, sp, #0x80
27226bd64: a9026ffc    	stp	x28, x27, [sp, #0x20]
27226bd68: a90367fa    	stp	x26, x25, [sp, #0x30]
27226bd6c: a9045ff8    	stp	x24, x23, [sp, #0x40]
27226bd70: a90557f6    	stp	x22, x21, [sp, #0x50]
27226bd74: a9064ff4    	stp	x20, x19, [sp, #0x60]
27226bd78: a9077bfd    	stp	x29, x30, [sp, #0x70]
27226bd7c: 9101c3fd    	add	x29, sp, #0x70
27226bd80: a90087e8    	stp	x8, x1, [sp, #0x8]
27226bd84: aa0003f5    	mov	x21, x0
27226bd88: 940002ca    	bl	0x27226c8b0 <_OUTLINED_FUNCTION_3+0x1130>
27226bd8c: d2800014    	mov	x20, #0x0               ; =0
27226bd90: f9000fe0    	str	x0, [sp, #0x18]
27226bd94: f9400808    	ldr	x8, [x0, #0x10]
27226bd98: 91000516    	add	x22, x8, #0x1
27226bd9c: f10006d6    	subs	x22, x22, #0x1
27226bda0: 54000220    	b.eq	0x27226bde4 <_OUTLINED_FUNCTION_3+0x664>
27226bda4: a94123e3    	ldp	x3, x8, [sp, #0x10]
27226bda8: 8b140108    	add	x8, x8, x20
27226bdac: a942611a    	ldp	x26, x24, [x8, #0x20]
27226bdb0: a943711b    	ldp	x27, x28, [x8, #0x30]
27226bdb4: a9446513    	ldp	x19, x25, [x8, #0x40]
27226bdb8: f9402917    	ldr	x23, [x8, #0x50]
27226bdbc: eb15033f    	cmp	x25, x21
27226bdc0: fa4302e0    	ccmp	x23, x3, #0x0, eq
27226bdc4: 54000200    	b.eq	0x27226be04 <_OUTLINED_FUNCTION_3+0x684>
27226bdc8: 9100e294    	add	x20, x20, #0x38
27226bdcc: 94000be3    	bl	0x27226ed58 <_OUTLINED_FUNCTION_34>
27226bdd0: aa1503e2    	mov	x2, x21
27226bdd4: 52800004    	mov	w4, #0x0                ; =0
27226bdd8: 9485c6da    	bl	0x2743dd940 <_swift_willThrow+0x2743dd940>
27226bddc: 3607fe00    	tbz	w0, #0x0, 0x27226bd9c <_OUTLINED_FUNCTION_3+0x61c>
27226bde0: 1400000a    	b	0x27226be08 <_OUTLINED_FUNCTION_3+0x688>
27226bde4: f9400fe0    	ldr	x0, [sp, #0x18]
27226bde8: 9485c7be    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
27226bdec: f94007e8    	ldr	x8, [sp, #0x8]
27226bdf0: f900191f    	str	xzr, [x8, #0x30]
27226bdf4: 6f00e400    	movi.2d	v0, #0000000000000000
27226bdf8: ad008100    	stp	q0, q0, [x8, #0x10]
27226bdfc: 3d800100    	str	q0, [x8]
27226be00: 1400000d    	b	0x27226be34 <_OUTLINED_FUNCTION_3+0x6b4>
27226be04: aa1503f9    	mov	x25, x21
27226be08: aa1803e0    	mov	x0, x24
27226be0c: 9485c7bd    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
27226be10: aa1703e0    	mov	x0, x23
27226be14: 9485c7bb    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
27226be18: f9400fe0    	ldr	x0, [sp, #0x18]
27226be1c: 9485c7b1    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
27226be20: f94007e8    	ldr	x8, [sp, #0x8]
27226be24: a900611a    	stp	x26, x24, [x8]
27226be28: a901711b    	stp	x27, x28, [x8, #0x10]
27226be2c: a9026513    	stp	x19, x25, [x8, #0x20]
27226be30: f9001917    	str	x23, [x8, #0x30]
27226be34: a9477bfd    	ldp	x29, x30, [sp, #0x70]
27226be38: a9464ff4    	ldp	x20, x19, [sp, #0x60]
27226be3c: a94557f6    	ldp	x22, x21, [sp, #0x50]
27226be40: a9445ff8    	ldp	x24, x23, [sp, #0x40]
27226be44: a94367fa    	ldp	x26, x25, [sp, #0x30]
27226be48: a9426ffc    	ldp	x28, x27, [sp, #0x20]
27226be4c: 910203ff    	add	sp, sp, #0x80
27226be50: d65f0fff    	retab
