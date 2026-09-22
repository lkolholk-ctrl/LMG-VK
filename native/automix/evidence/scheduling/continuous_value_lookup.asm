
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

00000002722706ac <_OUTLINED_FUNCTION_1>:
272271704: d503237f    	pacibsp
272271708: d10303ff    	sub	sp, sp, #0xc0
27227170c: 6d0633ed    	stp	d13, d12, [sp, #0x60]
272271710: 6d072beb    	stp	d11, d10, [sp, #0x70]
272271714: 6d0823e9    	stp	d9, d8, [sp, #0x80]
272271718: a90957f6    	stp	x22, x21, [sp, #0x90]
27227171c: a90a4ff4    	stp	x20, x19, [sp, #0xa0]
272271720: a90b7bfd    	stp	x29, x30, [sp, #0xb0]
272271724: 9102c3fd    	add	x29, sp, #0xb0
272271728: f9401e96    	ldr	x22, [x20, #0x38]
27227172c: f9400ac8    	ldr	x8, [x22, #0x10]
272271730: b40006e8    	cbz	x8, 0x27227180c <_OUTLINED_FUNCTION_1+0x1160>
272271734: fd400009    	ldr	d9, [x0]
272271738: aa1603e9    	mov	x9, x22
27227173c: 6dc20128    	ldp	d8, d0, [x9, #0x20]!
272271740: 1e602120    	fcmp	d9, d0
272271744: 540006c4    	b.mi	0x27227181c <_OUTLINED_FUNCTION_1+0x1170>
272271748: 5280030a    	mov	w10, #0x18              ; =24
27227174c: 9b0a2508    	madd	x8, x8, x10, x9
272271750: fc5e8108    	ldur	d8, [x8, #-0x18]
272271754: aa1603e0    	mov	x0, x22
272271758: 52800041    	mov	w1, #0x2                ; =2
27227175c: 9485b16d    	bl	0x2743ddd10 <_swift_willThrow+0x2743ddd10>
272271760: 52800020    	mov	w0, #0x1                ; =1
272271764: aa1603e1    	mov	x1, x22
272271768: 97fffbfa    	bl	0x272270750 <_OUTLINED_FUNCTION_1+0xa4>
27227176c: aa0003f4    	mov	x20, x0
272271770: a90383f6    	stp	x22, x0, [sp, #0x38]
272271774: a9048be1    	stp	x1, x2, [sp, #0x48]
272271778: f9002fe3    	str	x3, [sp, #0x58]
27227177c: 9100e3e0    	add	x0, sp, #0x38
272271780: d2800015    	mov	x21, #0x0               ; =0
272271784: 97fffc3a    	bl	0x27227086c <_OUTLINED_FUNCTION_1+0x1c0>
272271788: aa0003f3    	mov	x19, x0
27227178c: aa1403e0    	mov	x0, x20
272271790: 9485b1e4    	bl	0x2743ddf20 <_swift_willThrow+0x2743ddf20>
272271794: aa1603e0    	mov	x0, x22
272271798: 9485b152    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
27227179c: f9400a68    	ldr	x8, [x19, #0x10]
2722717a0: 52800509    	mov	w9, #0x28               ; =40
2722717a4: 9b094d09    	madd	x9, x8, x9, x19
2722717a8: 91008129    	add	x9, x9, #0x20
2722717ac: 91000508    	add	x8, x8, #0x1
2722717b0: f1000508    	subs	x8, x8, #0x1
2722717b4: 54000300    	b.eq	0x272271814 <_OUTLINED_FUNCTION_1+0x1168>
2722717b8: aa0903ea    	mov	x10, x9
2722717bc: d100a129    	sub	x9, x9, #0x28
2722717c0: fc5e814a    	ldur	d10, [x10, #-0x18]
2722717c4: 1e6a2120    	fcmp	d9, d10
2722717c8: 54ffff44    	b.mi	0x2722717b0 <_OUTLINED_FUNCTION_1+0x1104>
2722717cc: fc5f014b    	ldur	d11, [x10, #-0x10]
2722717d0: 1e692160    	fcmp	d11, d9
2722717d4: 54fffee4    	b.mi	0x2722717b0 <_OUTLINED_FUNCTION_1+0x1104>
2722717d8: 6d403128    	ldp	d8, d12, [x9]
2722717dc: 39408134    	ldrb	w20, [x9, #0x20]
2722717e0: aa1303e0    	mov	x0, x19
2722717e4: 9485b13f    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
2722717e8: 6d00b3e8    	stp	d8, d12, [sp, #0x8]
2722717ec: 6d01afea    	stp	d10, d11, [sp, #0x18]
2722717f0: 3900a3f4    	strb	w20, [sp, #0x28]
2722717f4: fd0003e9    	str	d9, [sp]
2722717f8: 910003e0    	mov	x0, sp
2722717fc: 910023f4    	add	x20, sp, #0x8
272271800: 940011e1    	bl	0x272275f84 <_OUTLINED_FUNCTION_1+0x20>
272271804: 1e604008    	fmov	d8, d0
272271808: 14000005    	b	0x27227181c <_OUTLINED_FUNCTION_1+0x1170>
27227180c: fd401288    	ldr	d8, [x20, #0x20]
272271810: 14000003    	b	0x27227181c <_OUTLINED_FUNCTION_1+0x1170>
272271814: aa1303e0    	mov	x0, x19
272271818: 9485b132    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
27227181c: 1e604100    	fmov	d0, d8
272271820: a94b7bfd    	ldp	x29, x30, [sp, #0xb0]
272271824: a94a4ff4    	ldp	x20, x19, [sp, #0xa0]
272271828: a94957f6    	ldp	x22, x21, [sp, #0x90]
27227182c: 6d4823e9    	ldp	d9, d8, [sp, #0x80]
272271830: 6d472beb    	ldp	d11, d10, [sp, #0x70]
272271834: 6d4633ed    	ldp	d13, d12, [sp, #0x60]
272271838: 910303ff    	add	sp, sp, #0xc0
27227183c: d65f0fff    	retab
