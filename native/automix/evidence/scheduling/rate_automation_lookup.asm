
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

0000000272277b10 <_OUTLINED_FUNCTION_3>:
272278dfc: d503237f    	pacibsp
272278e00: d104c3ff    	sub	sp, sp, #0x130
272278e04: 6d0a33ed    	stp	d13, d12, [sp, #0xa0]
272278e08: 6d0b2beb    	stp	d11, d10, [sp, #0xb0]
272278e0c: 6d0c23e9    	stp	d9, d8, [sp, #0xc0]
272278e10: a90d6ffc    	stp	x28, x27, [sp, #0xd0]
272278e14: a90e67fa    	stp	x26, x25, [sp, #0xe0]
272278e18: a90f5ff8    	stp	x24, x23, [sp, #0xf0]
272278e1c: a91057f6    	stp	x22, x21, [sp, #0x100]
272278e20: a9114ff4    	stp	x20, x19, [sp, #0x110]
272278e24: a9127bfd    	stp	x29, x30, [sp, #0x120]
272278e28: 910483fd    	add	x29, sp, #0x120
272278e2c: f90003e8    	str	x8, [sp]
272278e30: d280001b    	mov	x27, #0x0               ; =0
272278e34: f940169c    	ldr	x28, [x20, #0x28]
272278e38: f9400b88    	ldr	x8, [x28, #0x10]
272278e3c: f90007e8    	str	x8, [sp, #0x8]
272278e40: 91008393    	add	x19, x28, #0x20
272278e44: d0075114    	adrp	x20, 0x280c9a000 <_swift_willThrow+0x280c9a000>
272278e48: 9128a294    	add	x20, x20, #0xa28
272278e4c: f94007e8    	ldr	x8, [sp, #0x8]
272278e50: eb1b011f    	cmp	x8, x27
272278e54: 54000940    	b.eq	0x272278f7c <_OUTLINED_FUNCTION_3+0x146c>
272278e58: f9400b88    	ldr	x8, [x28, #0x10]
272278e5c: eb08037f    	cmp	x27, x8
272278e60: 54000c02    	b.hs	0x272278fe0 <_OUTLINED_FUNCTION_3+0x14d0>
272278e64: ad410261    	ldp	q1, q0, [x19, #0x20]
272278e68: ad400a63    	ldp	q3, q2, [x19]
272278e6c: ad028be3    	stp	q3, q2, [sp, #0x50]
272278e70: ad0383e1    	stp	q1, q0, [sp, #0x70]
272278e74: a94557f8    	ldp	x24, x21, [sp, #0x50]
272278e78: 6d4627ea    	ldp	d10, d9, [sp, #0x60]
272278e7c: fd403be8    	ldr	d8, [sp, #0x70]
272278e80: a947dbf7    	ldp	x23, x22, [sp, #0x78]
272278e84: 910143e0    	add	x0, sp, #0x50
272278e88: 910043e1    	add	x1, sp, #0x10
272278e8c: 97fe60e4    	bl	0x27221121c <___swift_destroy_boxed_opaque_existential_1Tm+0x3a4>
272278e90: b0075108    	adrp	x8, 0x280c99000 <_swift_willThrow+0x280c99000>
272278e94: f9420119    	ldr	x25, [x8, #0x400]
272278e98: aa1503e0    	mov	x0, x21
272278e9c: 94859399    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
272278ea0: aa1603e0    	mov	x0, x22
272278ea4: 94859397    	bl	0x2743ddd00 <_swift_willThrow+0x2743ddd00>
272278ea8: b100073f    	cmn	x25, #0x1
272278eac: 54000581    	b.ne	0x272278f5c <_OUTLINED_FUNCTION_3+0x144c>
272278eb0: a9400e82    	ldp	x2, x3, [x20]
272278eb4: 6d41328d    	ldp	d13, d12, [x20, #0x10]
272278eb8: fd40128b    	ldr	d11, [x20, #0x20]
272278ebc: a942ea99    	ldp	x25, x26, [x20, #0x28]
272278ec0: eb02031f    	cmp	x24, x2
272278ec4: fa4302a0    	ccmp	x21, x3, #0x0, eq
272278ec8: 540000c0    	b.eq	0x272278ee0 <_OUTLINED_FUNCTION_3+0x13d0>
272278ecc: aa1803e0    	mov	x0, x24
272278ed0: aa1503e1    	mov	x1, x21
272278ed4: 52800004    	mov	w4, #0x0                ; =0
272278ed8: 9485929a    	bl	0x2743dd940 <_swift_willThrow+0x2743dd940>
272278edc: 360002e0    	tbz	w0, #0x0, 0x272278f38 <_OUTLINED_FUNCTION_3+0x1428>
272278ee0: 1e6d2140    	fcmp	d10, d13
272278ee4: 540002a1    	b.ne	0x272278f38 <_OUTLINED_FUNCTION_3+0x1428>
272278ee8: 1e6c2120    	fcmp	d9, d12
272278eec: 54000261    	b.ne	0x272278f38 <_OUTLINED_FUNCTION_3+0x1428>
272278ef0: 1e6b2100    	fcmp	d8, d11
272278ef4: 54000221    	b.ne	0x272278f38 <_OUTLINED_FUNCTION_3+0x1428>
272278ef8: eb1902ff    	cmp	x23, x25
272278efc: fa5a02c0    	ccmp	x22, x26, #0x0, eq
272278f00: 54000480    	b.eq	0x272278f90 <_OUTLINED_FUNCTION_3+0x1480>
272278f04: aa1703e0    	mov	x0, x23
272278f08: aa1603e1    	mov	x1, x22
272278f0c: aa1903e2    	mov	x2, x25
272278f10: aa1a03e3    	mov	x3, x26
272278f14: 52800004    	mov	w4, #0x0                ; =0
272278f18: 9485928a    	bl	0x2743dd940 <_swift_willThrow+0x2743dd940>
272278f1c: aa0003f7    	mov	x23, x0
272278f20: aa1603e0    	mov	x0, x22
272278f24: 9485936f    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272278f28: aa1503e0    	mov	x0, x21
272278f2c: 9485936d    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272278f30: 360000d7    	tbz	w23, #0x0, 0x272278f48 <_OUTLINED_FUNCTION_3+0x1438>
272278f34: 1400001b    	b	0x272278fa0 <_OUTLINED_FUNCTION_3+0x1490>
272278f38: aa1603e0    	mov	x0, x22
272278f3c: 94859369    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272278f40: aa1503e0    	mov	x0, x21
272278f44: 94859367    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272278f48: 910143e0    	add	x0, sp, #0x50
272278f4c: 97fe60cb    	bl	0x272211278 <___swift_destroy_boxed_opaque_existential_1Tm+0x400>
272278f50: 91010273    	add	x19, x19, #0x40
272278f54: 9100077b    	add	x27, x27, #0x1
272278f58: 17ffffbd    	b	0x272278e4c <_OUTLINED_FUNCTION_3+0x133c>
272278f5c: d0ffffb0    	adrp	x16, 0x27226e000 <_OUTLINED_FUNCTION_3+0x2880>
272278f60: 91104210    	add	x16, x16, #0x410
272278f64: dac123f0    	paciza	x16
272278f68: aa1003e1    	mov	x1, x16
272278f6c: b0075100    	adrp	x0, 0x280c99000 <_swift_willThrow+0x280c99000>
272278f70: 91100000    	add	x0, x0, #0x400
272278f74: 948593c7    	bl	0x2743dde90 <_swift_willThrow+0x2743dde90>
272278f78: 17ffffce    	b	0x272278eb0 <_OUTLINED_FUNCTION_3+0x13a0>
272278f7c: 6f00e400    	movi.2d	v0, #0000000000000000
272278f80: f94003e8    	ldr	x8, [sp]
272278f84: ad010100    	stp	q0, q0, [x8, #0x20]
272278f88: ad000100    	stp	q0, q0, [x8]
272278f8c: 1400000a    	b	0x272278fb4 <_OUTLINED_FUNCTION_3+0x14a4>
272278f90: aa1603e0    	mov	x0, x22
272278f94: 94859353    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272278f98: aa1503e0    	mov	x0, x21
272278f9c: 94859351    	bl	0x2743ddce0 <_swift_willThrow+0x2743ddce0>
272278fa0: ad4287e0    	ldp	q0, q1, [sp, #0x50]
272278fa4: f94003e8    	ldr	x8, [sp]
272278fa8: ad000500    	stp	q0, q1, [x8]
272278fac: ad4387e0    	ldp	q0, q1, [sp, #0x70]
272278fb0: ad010500    	stp	q0, q1, [x8, #0x20]
272278fb4: a9527bfd    	ldp	x29, x30, [sp, #0x120]
272278fb8: a9514ff4    	ldp	x20, x19, [sp, #0x110]
272278fbc: a95057f6    	ldp	x22, x21, [sp, #0x100]
272278fc0: a94f5ff8    	ldp	x24, x23, [sp, #0xf0]
272278fc4: a94e67fa    	ldp	x26, x25, [sp, #0xe0]
272278fc8: a94d6ffc    	ldp	x28, x27, [sp, #0xd0]
272278fcc: 6d4c23e9    	ldp	d9, d8, [sp, #0xc0]
272278fd0: 6d4b2beb    	ldp	d11, d10, [sp, #0xb0]
272278fd4: 6d4a33ed    	ldp	d13, d12, [sp, #0xa0]
272278fd8: 9104c3ff    	add	sp, sp, #0x130
272278fdc: d65f0fff    	retab
272278fe0: d4200020    	brk	#0x1
