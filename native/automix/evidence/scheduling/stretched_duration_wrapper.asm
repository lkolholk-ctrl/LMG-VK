
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000027220f378 <__text>:
27220f754: d503237f    	pacibsp
27220f758: d10343ff    	sub	sp, sp, #0xd0
27220f75c: 6d092beb    	stp	d11, d10, [sp, #0x90]
27220f760: 6d0a23e9    	stp	d9, d8, [sp, #0xa0]
27220f764: a90b4ff4    	stp	x20, x19, [sp, #0xb0]
27220f768: a90c7bfd    	stp	x29, x30, [sp, #0xc0]
27220f76c: 910303fd    	add	x29, sp, #0xc0
27220f770: aa0003f3    	mov	x19, x0
27220f774: 1e604028    	fmov	d8, d1
27220f778: 6d402809    	ldp	d9, d10, [x0]
27220f77c: 1e602120    	fcmp	d9, d0
27220f780: 5400008c    	b.gt	0x27220f790 <__text+0x418>
27220f784: 3940e268    	ldrb	w8, [x19, #0x38]
27220f788: 7103f11f    	cmp	w8, #0xfc
27220f78c: 54000083    	b.lo	0x27220f79c <__text+0x424>
27220f790: 1e693800    	fsub	d0, d0, d9
27220f794: 1e60294b    	fadd	d11, d10, d0
27220f798: 14000012    	b	0x27220f7e0 <__text+0x468>
27220f79c: 3cc18261    	ldur	q1, [x19, #0x18]
27220f7a0: 3cc28262    	ldur	q2, [x19, #0x28]
27220f7a4: ad000be1    	stp	q1, q2, [sp]
27220f7a8: ad400a61    	ldp	q1, q2, [x19]
27220f7ac: ad018be1    	stp	q1, q2, [sp, #0x30]
27220f7b0: 3dc00a61    	ldr	q1, [x19, #0x20]
27220f7b4: 3d8017e1    	str	q1, [sp, #0x50]
27220f7b8: 3cc29261    	ldur	q1, [x19, #0x29]
27220f7bc: 3c8593e1    	stur	q1, [sp, #0x59]
27220f7c0: fd0017e0    	str	d0, [sp, #0x28]
27220f7c4: 390083e8    	strb	w8, [sp, #0x20]
27220f7c8: d10123a8    	sub	x8, x29, #0x48
27220f7cc: 9100a3e0    	add	x0, sp, #0x28
27220f7d0: 910003e1    	mov	x1, sp
27220f7d4: 9100c3f4    	add	x20, sp, #0x30
27220f7d8: 940100cc    	bl	0x27224fb08 <_OUTLINED_FUNCTION_1+0x24>
27220f7dc: fc5c03ab    	ldur	d11, [x29, #-0x40]
27220f7e0: 1e682120    	fcmp	d9, d8
27220f7e4: 5400008c    	b.gt	0x27220f7f4 <__text+0x47c>
27220f7e8: 3940e268    	ldrb	w8, [x19, #0x38]
27220f7ec: 7103f11f    	cmp	w8, #0xfc
27220f7f0: 54000083    	b.lo	0x27220f800 <__text+0x488>
27220f7f4: 1e693900    	fsub	d0, d8, d9
27220f7f8: 1e602940    	fadd	d0, d10, d0
27220f7fc: 14000012    	b	0x27220f844 <__text+0x4cc>
27220f800: 3cc18260    	ldur	q0, [x19, #0x18]
27220f804: 3cc28261    	ldur	q1, [x19, #0x28]
27220f808: ad0007e0    	stp	q0, q1, [sp]
27220f80c: ad400660    	ldp	q0, q1, [x19]
27220f810: ad0187e0    	stp	q0, q1, [sp, #0x30]
27220f814: 3dc00a60    	ldr	q0, [x19, #0x20]
27220f818: 3d8017e0    	str	q0, [sp, #0x50]
27220f81c: 3cc29260    	ldur	q0, [x19, #0x29]
27220f820: 3c8593e0    	stur	q0, [sp, #0x59]
27220f824: fd0017e8    	str	d8, [sp, #0x28]
27220f828: 390083e8    	strb	w8, [sp, #0x20]
27220f82c: d10123a8    	sub	x8, x29, #0x48
27220f830: 9100a3e0    	add	x0, sp, #0x28
27220f834: 910003e1    	mov	x1, sp
27220f838: 9100c3f4    	add	x20, sp, #0x30
27220f83c: 940100b3    	bl	0x27224fb08 <_OUTLINED_FUNCTION_1+0x24>
27220f840: fc5c03a0    	ldur	d0, [x29, #-0x40]
27220f844: 1e6b3800    	fsub	d0, d0, d11
27220f848: a94c7bfd    	ldp	x29, x30, [sp, #0xc0]
27220f84c: a94b4ff4    	ldp	x20, x19, [sp, #0xb0]
27220f850: 6d4a23e9    	ldp	d9, d8, [sp, #0xa0]
27220f854: 6d492beb    	ldp	d11, d10, [sp, #0x90]
27220f858: 910343ff    	add	sp, sp, #0xd0
27220f85c: d65f0fff    	retab
