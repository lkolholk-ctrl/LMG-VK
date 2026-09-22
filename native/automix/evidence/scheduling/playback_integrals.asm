
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000027224fae4 <_OUTLINED_FUNCTION_1>:
272250300: fd400000    	ldr	d0, [x0]
272250304: 6d411023    	ldp	d3, d4, [x1, #0x10]
272250308: 1e602080    	fcmp	d4, d0
27225030c: 1e604c80    	fcsel	d0, d4, d0, mi
272250310: 1e633801    	fsub	d1, d0, d3
272250314: 2f00e400    	movi	d0, #0000000000000000
272250318: 1e602028    	fcmp	d1, #0.0
27225031c: 5400048d    	b.le	0x2722503ac <_OUTLINED_FUNCTION_1+0x8c8>
272250320: d503237f    	pacibsp
272250324: 6dbd2beb    	stp	d11, d10, [sp, #-0x30]!
272250328: 6d0123e9    	stp	d9, d8, [sp, #0x10]
27225032c: a9027bfd    	stp	x29, x30, [sp, #0x20]
272250330: 910083fd    	add	x29, sp, #0x20
272250334: 6d400820    	ldp	d0, d2, [x1]
272250338: 1e633883    	fsub	d3, d4, d3
27225033c: 1e602068    	fcmp	d3, #0.0
272250340: 5400010d    	b.le	0x272250360 <_OUTLINED_FUNCTION_1+0x87c>
272250344: 1e603844    	fsub	d4, d2, d0
272250348: 1e63188a    	fdiv	d10, d4, d3
27225034c: 1e60c143    	fabs	d3, d10
272250350: d0000248    	adrp	x8, 0x27229a000 <_type_layout_string 015_SonicKit_MusicB9_Packages15TransitionStyleV21MappedAutomationValueO+0x260>
272250354: fd457d04    	ldr	d4, [x8, #0xaf8]
272250358: 1e642060    	fcmp	d3, d4
27225035c: 5400010a    	b.ge	0x27225037c <_OUTLINED_FUNCTION_1+0x898>
272250360: 1e622800    	fadd	d0, d0, d2
272250364: 1e6c1002    	fmov	d2, #0.50000000
272250368: 1e620800    	fmul	d0, d0, d2
27225036c: 1e6e1002    	fmov	d2, #1.00000000
272250370: 1e601840    	fdiv	d0, d2, d0
272250374: 1e610800    	fmul	d0, d0, d1
272250378: 14000009    	b	0x27225039c <_OUTLINED_FUNCTION_1+0x8b8>
27225037c: 1e610941    	fmul	d1, d10, d1
272250380: 1e612808    	fadd	d8, d0, d1
272250384: 948635e7    	bl	0x2743ddb20 <_swift_willThrow+0x2743ddb20>
272250388: 1e604009    	fmov	d9, d0
27225038c: 1e604100    	fmov	d0, d8
272250390: 948635e4    	bl	0x2743ddb20 <_swift_willThrow+0x2743ddb20>
272250394: 1e693800    	fsub	d0, d0, d9
272250398: 1e6a1800    	fdiv	d0, d0, d10
27225039c: a9427bfd    	ldp	x29, x30, [sp, #0x20]
2722503a0: 6d4123e9    	ldp	d9, d8, [sp, #0x10]
2722503a4: 6cc32beb    	ldp	d11, d10, [sp], #0x30
2722503a8: d50323ff    	autibsp
2722503ac: d65f03c0    	ret
2722503b0: d503237f    	pacibsp
2722503b4: d104c3ff    	sub	sp, sp, #0x130
2722503b8: 6d0d33ed    	stp	d13, d12, [sp, #0xd0]
2722503bc: 6d0e2beb    	stp	d11, d10, [sp, #0xe0]
2722503c0: 6d0f23e9    	stp	d9, d8, [sp, #0xf0]
2722503c4: a9106ffc    	stp	x28, x27, [sp, #0x100]
2722503c8: a9114ff4    	stp	x20, x19, [sp, #0x110]
2722503cc: a9127bfd    	stp	x29, x30, [sp, #0x120]
2722503d0: 910483fd    	add	x29, sp, #0x120
2722503d4: fd40000a    	ldr	d10, [x0]
2722503d8: 6d402428    	ldp	d8, d9, [x1]
2722503dc: ad400680    	ldp	q0, q1, [x20]
2722503e0: ad0007e0    	stp	q0, q1, [sp]
2722503e4: 3dc00a80    	ldr	q0, [x20, #0x20]
2722503e8: ad400682    	ldp	q2, q1, [x20]
2722503ec: 3d800be0    	str	q0, [sp, #0x20]
2722503f0: 3cc29280    	ldur	q0, [x20, #0x29]
2722503f4: 3c8293e0    	stur	q0, [sp, #0x29]
2722503f8: 6d41302b    	ldp	d11, d12, [x1, #0x10]
2722503fc: 39408033    	ldrb	w19, [x1, #0x20]
272250400: ad0387e2    	stp	q2, q1, [sp, #0x70]
272250404: 3dc00a80    	ldr	q0, [x20, #0x20]
272250408: 3d8027e0    	str	q0, [sp, #0x90]
27225040c: 3cc29280    	ldur	q0, [x20, #0x29]
272250410: 3c8993e0    	stur	q0, [sp, #0x99]
272250414: fd0037eb    	str	d11, [sp, #0x68]
272250418: 6d0427e8    	stp	d8, d9, [sp, #0x40]
27225041c: 6d0533eb    	stp	d11, d12, [sp, #0x50]
272250420: 390183f3    	strb	w19, [sp, #0x60]
272250424: d101a3a8    	sub	x8, x29, #0x68
272250428: 9101a3e0    	add	x0, sp, #0x68
27225042c: 910103e1    	add	x1, sp, #0x40
272250430: 9101c3f4    	add	x20, sp, #0x70
272250434: 97fffdb5    	bl	0x27224fb08 <_OUTLINED_FUNCTION_1+0x24>
272250438: fc5a03ad    	ldur	d13, [x29, #-0x60]
27225043c: fc1983ac    	stur	d12, [x29, #-0x68]
272250440: 6d0727e8    	stp	d8, d9, [sp, #0x70]
272250444: 6d0833eb    	stp	d11, d12, [sp, #0x80]
272250448: 390243f3    	strb	w19, [sp, #0x90]
27225044c: 910103e8    	add	x8, sp, #0x40
272250450: d101a3a0    	sub	x0, x29, #0x68
272250454: 9101c3e1    	add	x1, sp, #0x70
272250458: 910003f4    	mov	x20, sp
27225045c: 97fffdab    	bl	0x27224fb08 <_OUTLINED_FUNCTION_1+0x24>
272250460: fd4027e0    	ldr	d0, [sp, #0x48]
272250464: 1e6a2000    	fcmp	d0, d10
272250468: 1e6a4c00    	fcsel	d0, d0, d10, mi
27225046c: 1e6d380a    	fsub	d10, d0, d13
272250470: 2f00e400    	movi	d0, #0000000000000000
272250474: 1e602148    	fcmp	d10, #0.0
272250478: 5400032d    	b.le	0x2722504dc <_OUTLINED_FUNCTION_1+0x9f8>
27225047c: 1e6b3980    	fsub	d0, d12, d11
272250480: 1e602008    	fcmp	d0, #0.0
272250484: 5400010d    	b.le	0x2722504a4 <_OUTLINED_FUNCTION_1+0x9c0>
272250488: 1e683921    	fsub	d1, d9, d8
27225048c: 1e60182b    	fdiv	d11, d1, d0
272250490: 1e60c160    	fabs	d0, d11
272250494: d0000248    	adrp	x8, 0x27229a000 <_type_layout_string 015_SonicKit_MusicB9_Packages15TransitionStyleV21MappedAutomationValueO+0x260>
272250498: fd457d01    	ldr	d1, [x8, #0xaf8]
27225049c: 1e612000    	fcmp	d0, d1
2722504a0: 5400010a    	b.ge	0x2722504c0 <_OUTLINED_FUNCTION_1+0x9dc>
2722504a4: 1e692900    	fadd	d0, d8, d9
2722504a8: 1e6c1001    	fmov	d1, #0.50000000
2722504ac: 1e610800    	fmul	d0, d0, d1
2722504b0: 1e6e1001    	fmov	d1, #1.00000000
2722504b4: 1e601820    	fdiv	d0, d1, d0
2722504b8: 1e601940    	fdiv	d0, d10, d0
2722504bc: 14000008    	b	0x2722504dc <_OUTLINED_FUNCTION_1+0x9f8>
2722504c0: 1e604100    	fmov	d0, d8
2722504c4: 94863597    	bl	0x2743ddb20 <_swift_willThrow+0x2743ddb20>
2722504c8: 1e6a0961    	fmul	d1, d11, d10
2722504cc: 1e612800    	fadd	d0, d0, d1
2722504d0: 9486358c    	bl	0x2743ddb00 <_swift_willThrow+0x2743ddb00>
2722504d4: 1e683800    	fsub	d0, d0, d8
2722504d8: 1e6b1800    	fdiv	d0, d0, d11
2722504dc: a9527bfd    	ldp	x29, x30, [sp, #0x120]
2722504e0: a9514ff4    	ldp	x20, x19, [sp, #0x110]
2722504e4: a9506ffc    	ldp	x28, x27, [sp, #0x100]
2722504e8: 6d4f23e9    	ldp	d9, d8, [sp, #0xf0]
2722504ec: 6d4e2beb    	ldp	d11, d10, [sp, #0xe0]
2722504f0: 6d4d33ed    	ldp	d13, d12, [sp, #0xd0]
2722504f4: 9104c3ff    	add	sp, sp, #0x130
2722504f8: d65f0fff    	retab
