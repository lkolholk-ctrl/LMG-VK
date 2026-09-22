
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

00000002722793a4 <___swift_memcpy73_8>:
27227965c: d503237f    	pacibsp
272279660: d10583ff    	sub	sp, sp, #0x160
272279664: 6d0f23e9    	stp	d9, d8, [sp, #0xf0]
272279668: a9106ffc    	stp	x28, x27, [sp, #0x100]
27227966c: a91167fa    	stp	x26, x25, [sp, #0x110]
272279670: a9125ff8    	stp	x24, x23, [sp, #0x120]
272279674: a91357f6    	stp	x22, x21, [sp, #0x130]
272279678: a9144ff4    	stp	x20, x19, [sp, #0x140]
27227967c: a9157bfd    	stp	x29, x30, [sp, #0x150]
272279680: 910543fd    	add	x29, sp, #0x150
272279684: aa0803f3    	mov	x19, x8
272279688: 3dc00280    	ldr	q0, [x20]
27227968c: 3d800fe0    	str	q0, [sp, #0x30]
272279690: fd400a88    	ldr	d8, [x20, #0x10]
272279694: 3cc18280    	ldur	q0, [x20, #0x18]
272279698: 3c8483e0    	stur	q0, [sp, #0x48]
27227969c: f9401688    	ldr	x8, [x20, #0x28]
2722796a0: f9002fe8    	str	x8, [sp, #0x58]
2722796a4: fd401a89    	ldr	d9, [x20, #0x30]
2722796a8: a943ea98    	ldp	x24, x26, [x20, #0x38]
2722796ac: 39412299    	ldrb	w25, [x20, #0x48]
2722796b0: fd0023e8    	str	d8, [sp, #0x40]
2722796b4: fd0033e9    	str	d9, [sp, #0x60]
2722796b8: a906ebf8    	stp	x24, x26, [sp, #0x68]
2722796bc: 3901e3f9    	strb	w25, [sp, #0x78]
2722796c0: 910223e8    	add	x8, sp, #0x88
2722796c4: 9100c3f4    	add	x20, sp, #0x30
2722796c8: 97fffdcd    	bl	0x272278dfc <_OUTLINED_FUNCTION_3+0x12ec>
2722796cc: f9404be8    	ldr	x8, [sp, #0x90]
2722796d0: b4000388    	cbz	x8, 0x272279740 <___swift_memcpy73_8+0x39c>
2722796d4: f94063f6    	ldr	x22, [sp, #0xc0]
2722796d8: aa1603e0    	mov	x0, x22
2722796dc: 52800041    	mov	w1, #0x2                ; =2
2722796e0: 9485918c    	bl	0x2743ddd10 <_swift_willThrow+0x2743ddd10>
2722796e4: 52800020    	mov	w0, #0x1                ; =1
2722796e8: aa1603e1    	mov	x1, x22
2722796ec: 97ffe1a4    	bl	0x272271d7c <_OUTLINED_FUNCTION_1+0x16d0>
2722796f0: aa0003f7    	mov	x23, x0
2722796f4: a93783b6    	stp	x22, x0, [x29, #-0x88]
2722796f8: a9388ba1    	stp	x1, x2, [x29, #-0x78]
2722796fc: f81983a3    	stur	x3, [x29, #-0x68]
272279700: d10223a0    	sub	x0, x29, #0x88
272279704: d2800015    	mov	x21, #0x0               ; =0
272279708: 97ffdc59    	bl	0x27227086c <_OUTLINED_FUNCTION_1+0x1c0>
27227970c: aa0003f4    	mov	x20, x0
272279710: aa1703e0    	mov	x0, x23
272279714: 94859203    	bl	0x2743ddf20 <_swift_willThrow+0x2743ddf20>
272279718: aa1603e0    	mov	x0, x22
27227971c: 94859171    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272279720: 910223e0    	add	x0, sp, #0x88
272279724: 97fffe54    	bl	0x272279074 <_OUTLINED_FUNCTION_3+0x1564>
272279728: f9400a88    	ldr	x8, [x20, #0x10]
27227972c: b4000128    	cbz	x8, 0x272279750 <___swift_memcpy73_8+0x3ac>
272279730: ad410281    	ldp	q1, q0, [x20, #0x20]
272279734: ad0007e0    	stp	q0, q1, [sp]
272279738: 39410295    	ldrb	w21, [x20, #0x40]
27227973c: 14000008    	b	0x27227975c <___swift_memcpy73_8+0x3b8>
272279740: 6f00e402    	movi.2d	v2, #0000000000000000
272279744: 52801f95    	mov	w21, #0xfc              ; =252
272279748: 6f00e403    	movi.2d	v3, #0000000000000000
27227974c: 14000007    	b	0x272279768 <___swift_memcpy73_8+0x3c4>
272279750: 6f00e400    	movi.2d	v0, #0000000000000000
272279754: ad0003e0    	stp	q0, q0, [sp]
272279758: 52801f95    	mov	w21, #0xfc              ; =252
27227975c: aa1403e0    	mov	x0, x20
272279760: 94859160    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272279764: ad400be3    	ldp	q3, q2, [sp]
272279768: 9e670340    	fmov	d0, x26
27227976c: 9e670301    	fmov	d1, x24
272279770: 1e613800    	fsub	d0, d0, d1
272279774: 1e602920    	fadd	d0, d9, d0
272279778: 7200033f    	tst	w25, #0x1
27227977c: 1e601d20    	fcsel	d0, d9, d0, ne
272279780: 6d000269    	stp	d9, d0, [x19]
272279784: fd000a68    	str	d8, [x19, #0x10]
272279788: 3c818262    	stur	q2, [x19, #0x18]
27227978c: 3c828263    	stur	q3, [x19, #0x28]
272279790: 3900e275    	strb	w21, [x19, #0x38]
272279794: a9557bfd    	ldp	x29, x30, [sp, #0x150]
272279798: a9544ff4    	ldp	x20, x19, [sp, #0x140]
27227979c: a95357f6    	ldp	x22, x21, [sp, #0x130]
2722797a0: a9525ff8    	ldp	x24, x23, [sp, #0x120]
2722797a4: a95167fa    	ldp	x26, x25, [sp, #0x110]
2722797a8: a9506ffc    	ldp	x28, x27, [sp, #0x100]
2722797ac: 6d4f23e9    	ldp	d9, d8, [sp, #0xf0]
2722797b0: 910583ff    	add	sp, sp, #0x160
2722797b4: d65f0fff    	retab
