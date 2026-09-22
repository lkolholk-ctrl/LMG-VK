
/srv/research/tmp/extracted_dylibs/AudioToolbox:	file format mach-o arm64

Disassembly of section __TEXT,__text:

00000001b8f678fc
1b8f678fc: d503237f    	pacibsp
1b8f67900: d10483ff    	sub	sp, sp, #0x120
1b8f67904: 6d0b23e9    	stp	d9, d8, [sp, #0xb0]
1b8f67908: a90c6ffc    	stp	x28, x27, [sp, #0xc0]
1b8f6790c: a90d67fa    	stp	x26, x25, [sp, #0xd0]
1b8f67910: a90e5ff8    	stp	x24, x23, [sp, #0xe0]
1b8f67914: a90f57f6    	stp	x22, x21, [sp, #0xf0]
1b8f67918: a9104ff4    	stp	x20, x19, [sp, #0x100]
1b8f6791c: a9117bfd    	stp	x29, x30, [sp, #0x110]
1b8f67920: 910443fd    	add	x29, sp, #0x110
1b8f67924: aa0403f8    	mov	x24, x4
1b8f67928: aa0303f3    	mov	x19, x3
1b8f6792c: aa0203f6    	mov	x22, x2
1b8f67930: aa0103f4    	mov	x20, x1
1b8f67934: aa0003f5    	mov	x21, x0
1b8f67938: 9016e448    	adrp	x8, 0x1e6bef000
1b8f6793c: f943ad08    	ldr	x8, [x8, #0x758]
1b8f67940: f9400108    	ldr	x8, [x8]
1b8f67944: f81903a8    	stur	x8, [x29, #-0x70]
1b8f67948: 529f7779    	mov	w25, #0xfbbb            ; =64443
1b8f6794c: 72bfffd9    	movk	w25, #0xfffe, lsl #16
1b8f67950: 910073e2    	add	x2, sp, #0x1c
1b8f67954: 97ffff53    	bl	0x1b8f676a0
1b8f67958: b4005da0    	cbz	x0, 0x1b8f6850c
1b8f6795c: aa0003f7    	mov	x23, x0
1b8f67960: b4000093    	cbz	x19, 0x1b8f67970
1b8f67964: f900127f    	str	xzr, [x19, #0x20]
1b8f67968: 6f00e400    	movi.2d	v0, #0000000000000000
1b8f6796c: ad000260    	stp	q0, q0, [x19]
1b8f67970: f94006e0    	ldr	x0, [x23, #0x8]
1b8f67974: b4000380    	cbz	x0, 0x1b8f679e4
1b8f67978: 37005c96    	tbnz	w22, #0x0, 0x1b8f68508
1b8f6797c: 528e6e08    	mov	w8, #0x7370             ; =29552
1b8f67980: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67984: 6b08029f    	cmp	w20, w8
1b8f67988: 540005a1    	b.ne	0x1b8f67a3c
1b8f6798c: f943a2b8    	ldr	x24, [x21, #0x740]
1b8f67990: b4000098    	cbz	x24, 0x1b8f679a0
1b8f67994: f9400308    	ldr	x8, [x24]
1b8f67998: eb00011f    	cmp	x8, x0
1b8f6799c: 54000f80    	b.eq	0x1b8f67b8c
1b8f679a0: 94336de8    	bl	0x1b9c43140
1b8f679a4: f90006ff    	str	xzr, [x23, #0x8]
1b8f679a8: f943baa0    	ldr	x0, [x21, #0x770]
1b8f679ac: f903babf    	str	xzr, [x21, #0x770]
1b8f679b0: b4000140    	cbz	x0, 0x1b8f679d8
1b8f679b4: f9400010    	ldr	x16, [x0]
1b8f679b8: aa0003f1    	mov	x17, x0
1b8f679bc: f2edb471    	movk	x17, #0x6da3, lsl #48
1b8f679c0: dac11a30    	autda	x16, x17
1b8f679c4: f8408e08    	ldr	x8, [x16, #0x8]!
1b8f679c8: aa1003e9    	mov	x9, x16
1b8f679cc: aa0903f1    	mov	x17, x9
1b8f679d0: f2f5bed1    	movk	x17, #0xadf6, lsl #48
1b8f679d4: d73f0911    	blraa	x8, x17
1b8f679d8: f9440ea8    	ldr	x8, [x21, #0x818]
1b8f679dc: f90412a8    	str	x8, [x21, #0x820]
1b8f679e0: 140002ba    	b	0x1b8f684c8
1b8f679e4: 36005936    	tbz	w22, #0x0, 0x1b8f68508
1b8f679e8: 528e6e1b    	mov	w27, #0x7370            ; =29552
1b8f679ec: 72ae0bfb    	movk	w27, #0x705f, lsl #16
1b8f679f0: 5137ff7c    	sub	w28, w27, #0xdff
1b8f679f4: 6b1c029f    	cmp	w20, w28
1b8f679f8: 540004e1    	b.ne	0x1b8f67a94
1b8f679fc: f9400aa8    	ldr	x8, [x21, #0x10]
1b8f67a00: fd402508    	ldr	d8, [x8, #0x48]
1b8f67a04: 90001028    	adrp	x8, 0x1b916b000
1b8f67a08: fd469500    	ldr	d0, [x8, #0xd28]
1b8f67a0c: 1e602100    	fcmp	d8, d0
1b8f67a10: 54000940    	b.eq	0x1b8f67b38
1b8f67a14: 90001028    	adrp	x8, 0x1b916b000
1b8f67a18: fd47b900    	ldr	d0, [x8, #0xf70]
1b8f67a1c: 1e602100    	fcmp	d8, d0
1b8f67a20: 540008c0    	b.eq	0x1b8f67b38
1b8f67a24: d01970c8    	adrp	x8, 0x1ebd81000
1b8f67a28: f943e908    	ldr	x8, [x8, #0x7d0]
1b8f67a2c: b4001068    	cbz	x8, 0x1b8f67c38
1b8f67a30: f9400113    	ldr	x19, [x8]
1b8f67a34: b5001073    	cbnz	x19, 0x1b8f67c40
1b8f67a38: 140002b5    	b	0x1b8f6850c
1b8f67a3c: 94336dc1    	bl	0x1b9c43140
1b8f67a40: f90006ff    	str	xzr, [x23, #0x8]
1b8f67a44: 528d8e28    	mov	w8, #0x6c71             ; =27761
1b8f67a48: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67a4c: 6b08029f    	cmp	w20, w8
1b8f67a50: 5400054c    	b.gt	0x1b8f67af8
1b8f67a54: 528dcda8    	mov	w8, #0x6e6d             ; =28269
1b8f67a58: 72ad8c88    	movk	w8, #0x6c64, lsl #16
1b8f67a5c: 6b08029f    	cmp	w20, w8
1b8f67a60: 54000a80    	b.eq	0x1b8f67bb0
1b8f67a64: 528d8dc8    	mov	w8, #0x6c6e             ; =27758
1b8f67a68: 72ad8da8    	movk	w8, #0x6c6d, lsl #16
1b8f67a6c: 6b08029f    	cmp	w20, w8
1b8f67a70: 54000b00    	b.eq	0x1b8f67bd0
1b8f67a74: 528c8da8    	mov	w8, #0x646d             ; =25709
1b8f67a78: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67a7c: 6b08029f    	cmp	w20, w8
1b8f67a80: 54005241    	b.ne	0x1b8f684c8
1b8f67a84: f943caa0    	ldr	x0, [x21, #0x790]
1b8f67a88: f903cabf    	str	xzr, [x21, #0x790]
1b8f67a8c: b5000b00    	cbnz	x0, 0x1b8f67bec
1b8f67a90: 1400028e    	b	0x1b8f684c8
1b8f67a94: 528e0e88    	mov	w8, #0x7074             ; =28788
1b8f67a98: 72ae8da8    	movk	w8, #0x746d, lsl #16
1b8f67a9c: 6b08029f    	cmp	w20, w8
1b8f67aa0: 54000721    	b.ne	0x1b8f67b84
1b8f67aa4: 391672bf    	strb	wzr, [x21, #0x59c]
1b8f67aa8: b9459aa8    	ldr	w8, [x21, #0x598]
1b8f67aac: 528cac49    	mov	w9, #0x6562             ; =25954
1b8f67ab0: 72ae6e09    	movk	w9, #0x7370, lsl #16
1b8f67ab4: 6b09011f    	cmp	w8, w9
1b8f67ab8: 54000aed    	b.le	0x1b8f67c14
1b8f67abc: 528cac69    	mov	w9, #0x6563             ; =25955
1b8f67ac0: 72ae6e09    	movk	w9, #0x7370, lsl #16
1b8f67ac4: 6b09011f    	cmp	w8, w9
1b8f67ac8: 54000f00    	b.eq	0x1b8f67ca8
1b8f67acc: 528c8de9    	mov	w9, #0x646f             ; =25711
1b8f67ad0: 72ae8d29    	movk	w9, #0x7469, lsl #16
1b8f67ad4: 6b09011f    	cmp	w8, w9
1b8f67ad8: 54001400    	b.eq	0x1b8f67d58
1b8f67adc: 528e0c89    	mov	w9, #0x7064             ; =28772
1b8f67ae0: 72aece69    	movk	w9, #0x7673, lsl #16
1b8f67ae4: 6b09011f    	cmp	w8, w9
1b8f67ae8: 54005121    	b.ne	0x1b8f6850c
1b8f67aec: 528e4d28    	mov	w8, #0x7269             ; =29289
1b8f67af0: 72aecc28    	movk	w8, #0x7661, lsl #16
1b8f67af4: 1400009c    	b	0x1b8f67d64
1b8f67af8: 528d8e48    	mov	w8, #0x6c72             ; =27762
1b8f67afc: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67b00: 6b08029f    	cmp	w20, w8
1b8f67b04: 540005e0    	b.eq	0x1b8f67bc0
1b8f67b08: 528dce48    	mov	w8, #0x6e72             ; =28274
1b8f67b0c: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67b10: 6b08029f    	cmp	w20, w8
1b8f67b14: 54000660    	b.eq	0x1b8f67be0
1b8f67b18: 528eada8    	mov	w8, #0x756d             ; =30061
1b8f67b1c: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67b20: 6b08029f    	cmp	w20, w8
1b8f67b24: 54004d21    	b.ne	0x1b8f684c8
1b8f67b28: f943c6a0    	ldr	x0, [x21, #0x788]
1b8f67b2c: f903c6bf    	str	xzr, [x21, #0x788]
1b8f67b30: b50005e0    	cbnz	x0, 0x1b8f67bec
1b8f67b34: 14000265    	b	0x1b8f684c8
1b8f67b38: f94166a0    	ldr	x0, [x21, #0x2c8]
1b8f67b3c: f9400010    	ldr	x16, [x0]
1b8f67b40: aa0003f1    	mov	x17, x0
1b8f67b44: f2eb62b1    	movk	x17, #0x5b15, lsl #48
1b8f67b48: dac11a30    	autda	x16, x17
1b8f67b4c: f8428e08    	ldr	x8, [x16, #0x28]!
1b8f67b50: aa1003e9    	mov	x9, x16
1b8f67b54: aa0903f1    	mov	x17, x9
1b8f67b58: f2ed3991    	movk	x17, #0x69cc, lsl #48
1b8f67b5c: d73f0911    	blraa	x8, x17
1b8f67b60: b9401c08    	ldr	w8, [x0, #0x1c]
1b8f67b64: 71000d1f    	cmp	w8, #0x3
1b8f67b68: 540000e3    	b.lo	0x1b8f67b84
1b8f67b6c: d01970c8    	adrp	x8, 0x1ebd81000
1b8f67b70: f943e908    	ldr	x8, [x8, #0x7d0]
1b8f67b74: b4000a48    	cbz	x8, 0x1b8f67cbc
1b8f67b78: f9400113    	ldr	x19, [x8]
1b8f67b7c: b5000a53    	cbnz	x19, 0x1b8f67cc4
1b8f67b80: 14000263    	b	0x1b8f6850c
1b8f67b84: 5280001a    	mov	w26, #0x0               ; =0
1b8f67b88: 140000d1    	b	0x1b8f67ecc
1b8f67b8c: 901970c9    	adrp	x9, 0x1ebd7f000
1b8f67b90: f9461d29    	ldr	x9, [x9, #0xc38]
1b8f67b94: b4000909    	cbz	x9, 0x1b8f67cb4
1b8f67b98: aa0803e0    	mov	x0, x8
1b8f67b9c: d63f093f    	blraaz	x9
1b8f67ba0: f94006e0    	ldr	x0, [x23, #0x8]
1b8f67ba4: f900031f    	str	xzr, [x24]
1b8f67ba8: b5ffefc0    	cbnz	x0, 0x1b8f679a0
1b8f67bac: 17ffff7f    	b	0x1b8f679a8
1b8f67bb0: f943b6a0    	ldr	x0, [x21, #0x768]
1b8f67bb4: f903b6bf    	str	xzr, [x21, #0x768]
1b8f67bb8: b50001a0    	cbnz	x0, 0x1b8f67bec
1b8f67bbc: 14000243    	b	0x1b8f684c8
1b8f67bc0: f943c2a0    	ldr	x0, [x21, #0x780]
1b8f67bc4: f903c2bf    	str	xzr, [x21, #0x780]
1b8f67bc8: b5000120    	cbnz	x0, 0x1b8f67bec
1b8f67bcc: 1400023f    	b	0x1b8f684c8
1b8f67bd0: f943cea0    	ldr	x0, [x21, #0x798]
1b8f67bd4: f903cebf    	str	xzr, [x21, #0x798]
1b8f67bd8: b50000a0    	cbnz	x0, 0x1b8f67bec
1b8f67bdc: 1400023b    	b	0x1b8f684c8
1b8f67be0: f943bea0    	ldr	x0, [x21, #0x778]
1b8f67be4: f903bebf    	str	xzr, [x21, #0x778]
1b8f67be8: b4004700    	cbz	x0, 0x1b8f684c8
1b8f67bec: f9400010    	ldr	x16, [x0]
1b8f67bf0: aa0003f1    	mov	x17, x0
1b8f67bf4: f2edb471    	movk	x17, #0x6da3, lsl #48
1b8f67bf8: dac11a30    	autda	x16, x17
1b8f67bfc: f8408e08    	ldr	x8, [x16, #0x8]!
1b8f67c00: aa1003e9    	mov	x9, x16
1b8f67c04: aa0903f1    	mov	x17, x9
1b8f67c08: f2f5bed1    	movk	x17, #0xadf6, lsl #48
1b8f67c0c: d73f0911    	blraa	x8, x17
1b8f67c10: 1400022e    	b	0x1b8f684c8
1b8f67c14: 340004a8    	cbz	w8, 0x1b8f67ca8
1b8f67c18: 528f4d89    	mov	w9, #0x7a6c             ; =31340
1b8f67c1c: 72ad8e29    	movk	w9, #0x6c71, lsl #16
1b8f67c20: 6b09011f    	cmp	w8, w9
1b8f67c24: 54004741    	b.ne	0x1b8f6850c
1b8f67c28: 528e8da8    	mov	w8, #0x746d             ; =29805
1b8f67c2c: 72ad2e08    	movk	w8, #0x6970, lsl #16
1b8f67c30: b90023e8    	str	w8, [sp, #0x20]
1b8f67c34: 1400004f    	b	0x1b8f67d70
1b8f67c38: 9016e453    	adrp	x19, 0x1e6bef000
1b8f67c3c: f9459673    	ldr	x19, [x19, #0xb28]
1b8f67c40: aa1303e0    	mov	x0, x19
1b8f67c44: 52800001    	mov	w1, #0x0                ; =0
1b8f67c48: 94337a02    	bl	0x1b9c46450
1b8f67c4c: 34004600    	cbz	w0, 0x1b8f6850c
1b8f67c50: 90001028    	adrp	x8, 0x1b916b000
1b8f67c54: fd463500    	ldr	d0, [x8, #0xc68]
1b8f67c58: bd0063e0    	str	s0, [sp, #0x60]
1b8f67c5c: 900011a8    	adrp	x8, 0x1b919b000
1b8f67c60: 91263908    	add	x8, x8, #0x98e
1b8f67c64: f80643e8    	stur	x8, [sp, #0x64]
1b8f67c68: 52808008    	mov	w8, #0x400              ; =1024
1b8f67c6c: 7900dbe8    	strh	w8, [sp, #0x6c]
1b8f67c70: 52808c68    	mov	w8, #0x463              ; =1123
1b8f67c74: b806e3e8    	stur	w8, [sp, #0x6e]
1b8f67c78: 52810008    	mov	w8, #0x800              ; =2048
1b8f67c7c: 7900e7e8    	strh	w8, [sp, #0x72]
1b8f67c80: fc0743e8    	stur	d8, [sp, #0x74]
1b8f67c84: 90fffaa0    	adrp	x0, 0x1b8ebb000
1b8f67c88: 91000000    	add	x0, x0, #0x0
1b8f67c8c: d00012c3    	adrp	x3, 0x1b91c1000
1b8f67c90: 9114f063    	add	x3, x3, #0x53c
1b8f67c94: 910183e4    	add	x4, sp, #0x60
1b8f67c98: aa1303e1    	mov	x1, x19
1b8f67c9c: 52800002    	mov	w2, #0x0                ; =0
1b8f67ca0: 52800385    	mov	w5, #0x1c               ; =28
1b8f67ca4: 1400002b    	b	0x1b8f67d50
1b8f67ca8: 528e8e08    	mov	w8, #0x7470             ; =29808
1b8f67cac: 72adcea8    	movk	w8, #0x6e75, lsl #16
1b8f67cb0: 1400002d    	b	0x1b8f67d64
1b8f67cb4: f900031f    	str	xzr, [x24]
1b8f67cb8: 17ffff3a    	b	0x1b8f679a0
1b8f67cbc: 9016e453    	adrp	x19, 0x1e6bef000
1b8f67cc0: f9459673    	ldr	x19, [x19, #0xb28]
1b8f67cc4: aa1303e0    	mov	x0, x19
1b8f67cc8: 52800001    	mov	w1, #0x0                ; =0
1b8f67ccc: 943379e1    	bl	0x1b9c46450
1b8f67cd0: 340041e0    	cbz	w0, 0x1b8f6850c
1b8f67cd4: f94166a0    	ldr	x0, [x21, #0x2c8]
1b8f67cd8: f9400010    	ldr	x16, [x0]
1b8f67cdc: aa0003f1    	mov	x17, x0
1b8f67ce0: f2eb62b1    	movk	x17, #0x5b15, lsl #48
1b8f67ce4: dac11a30    	autda	x16, x17
1b8f67ce8: f8428e08    	ldr	x8, [x16, #0x28]!
1b8f67cec: aa1003e9    	mov	x9, x16
1b8f67cf0: aa0903f1    	mov	x17, x9
1b8f67cf4: f2ed3991    	movk	x17, #0x69cc, lsl #48
1b8f67cf8: d73f0911    	blraa	x8, x17
1b8f67cfc: b9401c08    	ldr	w8, [x0, #0x1c]
1b8f67d00: 90001029    	adrp	x9, 0x1b916b000
1b8f67d04: fd463520    	ldr	d0, [x9, #0xc68]
1b8f67d08: bd0063e0    	str	s0, [sp, #0x60]
1b8f67d0c: 900011a9    	adrp	x9, 0x1b919b000
1b8f67d10: 91263929    	add	x9, x9, #0x98e
1b8f67d14: f80643e9    	stur	x9, [sp, #0x64]
1b8f67d18: 52808009    	mov	w9, #0x400              ; =1024
1b8f67d1c: 7900dbe9    	strh	w9, [sp, #0x6c]
1b8f67d20: 52808d0a    	mov	w10, #0x468             ; =1128
1b8f67d24: b806e3ea    	stur	w10, [sp, #0x6e]
1b8f67d28: 7900e7e9    	strh	w9, [sp, #0x72]
1b8f67d2c: b90077e8    	str	w8, [sp, #0x74]
1b8f67d30: 90fffaa0    	adrp	x0, 0x1b8ebb000
1b8f67d34: 91000000    	add	x0, x0, #0x0
1b8f67d38: d00012c3    	adrp	x3, 0x1b91c1000
1b8f67d3c: 91158863    	add	x3, x3, #0x562
1b8f67d40: 910183e4    	add	x4, sp, #0x60
1b8f67d44: aa1303e1    	mov	x1, x19
1b8f67d48: 52800002    	mov	w2, #0x0                ; =0
1b8f67d4c: 52800305    	mov	w5, #0x18               ; =24
1b8f67d50: 943376cc    	bl	0x1b9c45880
1b8f67d54: 140001ee    	b	0x1b8f6850c
1b8f67d58: 528e8da8    	mov	w8, #0x746d             ; =29805
1b8f67d5c: 72ad2e08    	movk	w8, #0x6970, lsl #16
1b8f67d60: 321f0108    	orr	w8, w8, #0x2
1b8f67d64: b90023e8    	str	w8, [sp, #0x20]
1b8f67d68: 52800028    	mov	w8, #0x1                ; =1
1b8f67d6c: 391672a8    	strb	w8, [x21, #0x59c]
1b8f67d70: 9433723c    	bl	0x1b9c44660
1b8f67d74: 34000aa0    	cbz	w0, 0x1b8f67ec8
1b8f67d78: 390003ff    	strb	wzr, [sp]
1b8f67d7c: b01d5500    	adrp	x0, 0x1f3a08000
1b8f67d80: 912bc000    	add	x0, x0, #0xaf0
1b8f67d84: 901d5501    	adrp	x1, 0x1f3a07000
1b8f67d88: 910bc021    	add	x1, x1, #0x2f0
1b8f67d8c: 910003e2    	mov	x2, sp
1b8f67d90: 943370e4    	bl	0x1b9c44120
1b8f67d94: 394003e8    	ldrb	w8, [sp]
1b8f67d98: 7100051f    	cmp	w8, #0x1
1b8f67d9c: 54000961    	b.ne	0x1b8f67ec8
1b8f67da0: aa0003fa    	mov	x26, x0
1b8f67da4: 528e8de8    	mov	w8, #0x746f             ; =29807
1b8f67da8: 72ad2e08    	movk	w8, #0x6970, lsl #16
1b8f67dac: eb08001f    	cmp	x0, x8
1b8f67db0: 54000120    	b.eq	0x1b8f67dd4
1b8f67db4: 528e4d28    	mov	w8, #0x7269             ; =29289
1b8f67db8: 72aecc28    	movk	w8, #0x7661, lsl #16
1b8f67dbc: eb08035f    	cmp	x26, x8
1b8f67dc0: 540000a0    	b.eq	0x1b8f67dd4
1b8f67dc4: 528e8e08    	mov	w8, #0x7470             ; =29808
1b8f67dc8: 72adcea8    	movk	w8, #0x6e75, lsl #16
1b8f67dcc: eb08035f    	cmp	x26, x8
1b8f67dd0: 540007c1    	b.ne	0x1b8f67ec8
1b8f67dd4: b90023fa    	str	w26, [sp, #0x20]
1b8f67dd8: d01970c8    	adrp	x8, 0x1ebd81000
1b8f67ddc: f943e908    	ldr	x8, [x8, #0x7d0]
1b8f67de0: b4000088    	cbz	x8, 0x1b8f67df0
1b8f67de4: f9400119    	ldr	x25, [x8]
1b8f67de8: b5000099    	cbnz	x25, 0x1b8f67df8
1b8f67dec: 14000037    	b	0x1b8f67ec8
1b8f67df0: 9016e459    	adrp	x25, 0x1e6bef000
1b8f67df4: f9459739    	ldr	x25, [x25, #0xb28]
1b8f67df8: aa1903e0    	mov	x0, x25
1b8f67dfc: 52800001    	mov	w1, #0x0                ; =0
1b8f67e00: 94337994    	bl	0x1b9c46450
1b8f67e04: 34000620    	cbz	w0, 0x1b8f67ec8
1b8f67e08: f9400aa8    	ldr	x8, [x21, #0x10]
1b8f67e0c: 5ac00b49    	rev	w9, w26
1b8f67e10: 1e270120    	fmov	s0, w9
1b8f67e14: 0e003800    	zip1.8b	v0, v0, v0
1b8f67e18: 2f10a401    	ushll.4s	v1, v0, #0x0
1b8f67e1c: 4f385421    	shl.4s	v1, v1, #0x18
1b8f67e20: 6f0007e2    	mvni.4s	v2, #0x1f
1b8f67e24: 4f281422    	ssra.4s	v2, v1, #0x18
1b8f67e28: 4f0207e1    	movi.4s	v1, #0x5f
1b8f67e2c: 6ea23421    	cmhi.4s	v1, v1, v2
1b8f67e30: 0e612821    	xtn.4h	v1, v1
1b8f67e34: 0f0185c2    	movi.4h	v2, #0x2e
1b8f67e38: 2ee11c40    	bif.8b	v0, v2, v1
1b8f67e3c: 52800089    	mov	w9, #0x4                ; =4
1b8f67e40: 39011fe9    	strb	w9, [sp, #0x47]
1b8f67e44: 0e001800    	uzp1.8b	v0, v0, v0
1b8f67e48: bd0033e0    	str	s0, [sp, #0x30]
1b8f67e4c: 3900d3ff    	strb	wzr, [sp, #0x34]
1b8f67e50: 90001029    	adrp	x9, 0x1b916b000
1b8f67e54: fd463920    	ldr	d0, [x9, #0xc70]
1b8f67e58: bd0063e0    	str	s0, [sp, #0x60]
1b8f67e5c: 900011a9    	adrp	x9, 0x1b919b000
1b8f67e60: 91263929    	add	x9, x9, #0x98e
1b8f67e64: f80643e9    	stur	x9, [sp, #0x64]
1b8f67e68: 52808009    	mov	w9, #0x400              ; =1024
1b8f67e6c: 7900dbe9    	strh	w9, [sp, #0x6c]
1b8f67e70: 52809589    	mov	w9, #0x4ac              ; =1196
1b8f67e74: b806e3e9    	stur	w9, [sp, #0x6e]
1b8f67e78: 52810009    	mov	w9, #0x800              ; =2048
1b8f67e7c: 7900e7e9    	strh	w9, [sp, #0x72]
1b8f67e80: f80743e8    	stur	x8, [sp, #0x74]
1b8f67e84: 52810408    	mov	w8, #0x820              ; =2080
1b8f67e88: 7900fbe8    	strh	w8, [sp, #0x7c]
1b8f67e8c: 9100c3e8    	add	x8, sp, #0x30
1b8f67e90: f807e3e8    	stur	x8, [sp, #0x7e]
1b8f67e94: 90fffaa0    	adrp	x0, 0x1b8ebb000
1b8f67e98: 91000000    	add	x0, x0, #0x0
1b8f67e9c: d00012c3    	adrp	x3, 0x1b91c1000
1b8f67ea0: 91163463    	add	x3, x3, #0x58d
1b8f67ea4: 910183e4    	add	x4, sp, #0x60
1b8f67ea8: aa1903e1    	mov	x1, x25
1b8f67eac: 52800002    	mov	w2, #0x0                ; =0
1b8f67eb0: 528004c5    	mov	w5, #0x26               ; =38
1b8f67eb4: 94337673    	bl	0x1b9c45880
1b8f67eb8: 39c11fe8    	ldrsb	w8, [sp, #0x47]
1b8f67ebc: 36f80068    	tbz	w8, #0x1f, 0x1b8f67ec8
1b8f67ec0: f9401be0    	ldr	x0, [sp, #0x30]
1b8f67ec4: 94080177    	bl	0x1b91684a0
1b8f67ec8: 5280003a    	mov	w26, #0x1               ; =1
1b8f67ecc: 910183e0    	add	x0, sp, #0x60
1b8f67ed0: 910073e1    	add	x1, sp, #0x1c
1b8f67ed4: 9406791f    	bl	0x1b9106350
1b8f67ed8: f94002f0    	ldr	x16, [x23]
1b8f67edc: aa1703f1    	mov	x17, x23
1b8f67ee0: f2fe9731    	movk	x17, #0xf4b9, lsl #48
1b8f67ee4: dac11a30    	autda	x16, x17
1b8f67ee8: f8448e08    	ldr	x8, [x16, #0x48]!
1b8f67eec: aa1003e9    	mov	x9, x16
1b8f67ef0: 910183e1    	add	x1, sp, #0x60
1b8f67ef4: aa1703e0    	mov	x0, x23
1b8f67ef8: aa0903f1    	mov	x17, x9
1b8f67efc: f2f547b1    	movk	x17, #0xaa3d, lsl #48
1b8f67f00: d73f0911    	blraa	x8, x17
1b8f67f04: aa0003f9    	mov	x25, x0
1b8f67f08: d01970c8    	adrp	x8, 0x1ebd81000
1b8f67f0c: f943e908    	ldr	x8, [x8, #0x7d0]
1b8f67f10: 340000a0    	cbz	w0, 0x1b8f67f24
1b8f67f14: b4000108    	cbz	x8, 0x1b8f67f34
1b8f67f18: f9400113    	ldr	x19, [x8]
1b8f67f1c: b5000113    	cbnz	x19, 0x1b8f67f3c
1b8f67f20: 14000020    	b	0x1b8f67fa0
1b8f67f24: b4000448    	cbz	x8, 0x1b8f67fac
1b8f67f28: f9400119    	ldr	x25, [x8]
1b8f67f2c: b5000459    	cbnz	x25, 0x1b8f67fb4
1b8f67f30: 1400003a    	b	0x1b8f68018
1b8f67f34: 9016e453    	adrp	x19, 0x1e6bef000
1b8f67f38: f9459673    	ldr	x19, [x19, #0xb28]
1b8f67f3c: aa1303e0    	mov	x0, x19
1b8f67f40: 52800201    	mov	w1, #0x10               ; =16
1b8f67f44: 94337943    	bl	0x1b9c46450
1b8f67f48: 340002c0    	cbz	w0, 0x1b8f67fa0
1b8f67f4c: 90001028    	adrp	x8, 0x1b916b000
1b8f67f50: fd463500    	ldr	d0, [x8, #0xc68]
1b8f67f54: bd0033e0    	str	s0, [sp, #0x30]
1b8f67f58: 900011a8    	adrp	x8, 0x1b919b000
1b8f67f5c: 91263908    	add	x8, x8, #0x98e
1b8f67f60: f80343e8    	stur	x8, [sp, #0x34]
1b8f67f64: 52808008    	mov	w8, #0x400              ; =1024
1b8f67f68: 79007be8    	strh	w8, [sp, #0x3c]
1b8f67f6c: 52809769    	mov	w9, #0x4bb              ; =1211
1b8f67f70: b803e3e9    	stur	w9, [sp, #0x3e]
1b8f67f74: 790087e8    	strh	w8, [sp, #0x42]
1b8f67f78: b90047f4    	str	w20, [sp, #0x44]
1b8f67f7c: 90fffaa0    	adrp	x0, 0x1b8ebb000
1b8f67f80: 91000000    	add	x0, x0, #0x0
1b8f67f84: d00012c3    	adrp	x3, 0x1b91c1000
1b8f67f88: 91175063    	add	x3, x3, #0x5d4
1b8f67f8c: 9100c3e4    	add	x4, sp, #0x30
1b8f67f90: aa1303e1    	mov	x1, x19
1b8f67f94: 52800202    	mov	w2, #0x10               ; =16
1b8f67f98: 52800305    	mov	w5, #0x18               ; =24
1b8f67f9c: 94337639    	bl	0x1b9c45880
1b8f67fa0: 910183e0    	add	x0, sp, #0x60
1b8f67fa4: 94067908    	bl	0x1b91063c4
1b8f67fa8: 14000159    	b	0x1b8f6850c
1b8f67fac: 9016e459    	adrp	x25, 0x1e6bef000
1b8f67fb0: f9459739    	ldr	x25, [x25, #0xb28]
1b8f67fb4: aa1903e0    	mov	x0, x25
1b8f67fb8: 52800041    	mov	w1, #0x2                ; =2
1b8f67fbc: 94337925    	bl	0x1b9c46450
1b8f67fc0: 340002c0    	cbz	w0, 0x1b8f68018
1b8f67fc4: 90001028    	adrp	x8, 0x1b916b000
1b8f67fc8: fd463500    	ldr	d0, [x8, #0xc68]
1b8f67fcc: bd0033e0    	str	s0, [sp, #0x30]
1b8f67fd0: 900011a8    	adrp	x8, 0x1b919b000
1b8f67fd4: 91263908    	add	x8, x8, #0x98e
1b8f67fd8: f80343e8    	stur	x8, [sp, #0x34]
1b8f67fdc: 52808008    	mov	w8, #0x400              ; =1024
1b8f67fe0: 79007be8    	strh	w8, [sp, #0x3c]
1b8f67fe4: 528097c9    	mov	w9, #0x4be              ; =1214
1b8f67fe8: b803e3e9    	stur	w9, [sp, #0x3e]
1b8f67fec: 790087e8    	strh	w8, [sp, #0x42]
1b8f67ff0: b90047f4    	str	w20, [sp, #0x44]
1b8f67ff4: 90fffaa0    	adrp	x0, 0x1b8ebb000
1b8f67ff8: 91000000    	add	x0, x0, #0x0
1b8f67ffc: d00012c3    	adrp	x3, 0x1b91c1000
1b8f68000: 91188863    	add	x3, x3, #0x622
1b8f68004: 9100c3e4    	add	x4, sp, #0x30
1b8f68008: aa1903e1    	mov	x1, x25
1b8f6800c: 52800042    	mov	w2, #0x2                ; =2
1b8f68010: 52800305    	mov	w5, #0x18               ; =24
1b8f68014: 9433761b    	bl	0x1b9c45880
1b8f68018: 34001bda    	cbz	w26, 0x1b8f68390
1b8f6801c: b01970da    	adrp	x26, 0x1ebd81000
1b8f68020: f943eb48    	ldr	x8, [x26, #0x7d0]
1b8f68024: b4000088    	cbz	x8, 0x1b8f68034
1b8f68028: f9400119    	ldr	x25, [x8]
1b8f6802c: b5000099    	cbnz	x25, 0x1b8f6803c
1b8f68030: 14000038    	b	0x1b8f68110
1b8f68034: f016e439    	adrp	x25, 0x1e6bef000
1b8f68038: f9459739    	ldr	x25, [x25, #0xb28]
1b8f6803c: aa1903e0    	mov	x0, x25
1b8f68040: 52800001    	mov	w1, #0x0                ; =0
1b8f68044: 94337903    	bl	0x1b9c46450
1b8f68048: 34000640    	cbz	w0, 0x1b8f68110
1b8f6804c: f9400aa8    	ldr	x8, [x21, #0x10]
1b8f68050: b94023e9    	ldr	w9, [sp, #0x20]
1b8f68054: 5ac00929    	rev	w9, w9
1b8f68058: 1e270120    	fmov	s0, w9
1b8f6805c: 0e003800    	zip1.8b	v0, v0, v0
1b8f68060: 2f10a401    	ushll.4s	v1, v0, #0x0
1b8f68064: 4f385421    	shl.4s	v1, v1, #0x18
1b8f68068: 6f0007e2    	mvni.4s	v2, #0x1f
1b8f6806c: 4f281422    	ssra.4s	v2, v1, #0x18
1b8f68070: 4f0207e1    	movi.4s	v1, #0x5f
1b8f68074: 6ea23421    	cmhi.4s	v1, v1, v2
1b8f68078: 0e612821    	xtn.4h	v1, v1
1b8f6807c: 0f0185c2    	movi.4h	v2, #0x2e
1b8f68080: 2ee11c40    	bif.8b	v0, v2, v1
1b8f68084: 52800089    	mov	w9, #0x4                ; =4
1b8f68088: 39005fe9    	strb	w9, [sp, #0x17]
1b8f6808c: 0e001800    	uzp1.8b	v0, v0, v0
1b8f68090: bd0003e0    	str	s0, [sp]
1b8f68094: 390013ff    	strb	wzr, [sp, #0x4]
1b8f68098: f0001009    	adrp	x9, 0x1b916b000
1b8f6809c: fd463920    	ldr	d0, [x9, #0xc70]
1b8f680a0: bd0033e0    	str	s0, [sp, #0x30]
1b8f680a4: f0001189    	adrp	x9, 0x1b919b000
1b8f680a8: 91263929    	add	x9, x9, #0x98e
1b8f680ac: f80343e9    	stur	x9, [sp, #0x34]
1b8f680b0: 52808009    	mov	w9, #0x400              ; =1024
1b8f680b4: 79007be9    	strh	w9, [sp, #0x3c]
1b8f680b8: 528098c9    	mov	w9, #0x4c6              ; =1222
1b8f680bc: b803e3e9    	stur	w9, [sp, #0x3e]
1b8f680c0: 52810009    	mov	w9, #0x800              ; =2048
1b8f680c4: 790087e9    	strh	w9, [sp, #0x42]
1b8f680c8: f80443e8    	stur	x8, [sp, #0x44]
1b8f680cc: 52810408    	mov	w8, #0x820              ; =2080
1b8f680d0: 79009be8    	strh	w8, [sp, #0x4c]
1b8f680d4: 910003e8    	mov	x8, sp
1b8f680d8: f804e3e8    	stur	x8, [sp, #0x4e]
1b8f680dc: f0fffa80    	adrp	x0, 0x1b8ebb000
1b8f680e0: 91000000    	add	x0, x0, #0x0
1b8f680e4: b00012c3    	adrp	x3, 0x1b91c1000
1b8f680e8: 91191c63    	add	x3, x3, #0x647
1b8f680ec: 9100c3e4    	add	x4, sp, #0x30
1b8f680f0: aa1903e1    	mov	x1, x25
1b8f680f4: 52800002    	mov	w2, #0x0                ; =0
1b8f680f8: 528004c5    	mov	w5, #0x26               ; =38
1b8f680fc: 943375e1    	bl	0x1b9c45880
1b8f68100: 39c05fe8    	ldrsb	w8, [sp, #0x17]
1b8f68104: 36f80068    	tbz	w8, #0x1f, 0x1b8f68110
1b8f68108: f94003e0    	ldr	x0, [sp]
1b8f6810c: 940800e5    	bl	0x1b91684a0
1b8f68110: 90000010    	adrp	x16, 0x1b8f68000
1b8f68114: 91260210    	add	x16, x16, #0x980
1b8f68118: dac123f0    	paciza	x16
1b8f6811c: a90057f0    	stp	x16, x21, [sp]
1b8f68120: f942baa0    	ldr	x0, [x21, #0x570]
1b8f68124: 910003e4    	mov	x4, sp
1b8f68128: 52800621    	mov	w1, #0x31               ; =49
1b8f6812c: 52800002    	mov	w2, #0x0                ; =0
1b8f68130: 52800003    	mov	w3, #0x0                ; =0
1b8f68134: 52800205    	mov	w5, #0x10               ; =16
1b8f68138: 94336cfa    	bl	0x1b9c43520
1b8f6813c: aa0003f9    	mov	x25, x0
1b8f68140: 340000c0    	cbz	w0, 0x1b8f68158
1b8f68144: f943eb48    	ldr	x8, [x26, #0x7d0]
1b8f68148: b40006c8    	cbz	x8, 0x1b8f68220
1b8f6814c: f9400113    	ldr	x19, [x8]
1b8f68150: b50006d3    	cbnz	x19, 0x1b8f68228
1b8f68154: 1400004e    	b	0x1b8f6828c
1b8f68158: b94023e8    	ldr	w8, [sp, #0x20]
1b8f6815c: 528e8e09    	mov	w9, #0x7470             ; =29808
1b8f68160: 72adcea9    	movk	w9, #0x6e75, lsl #16
1b8f68164: 6b09011f    	cmp	w8, w9
1b8f68168: 54000101    	b.ne	0x1b8f68188
1b8f6816c: f942baa0    	ldr	x0, [x21, #0x570]
1b8f68170: 1e221000    	fmov	s0, #4.00000000
1b8f68174: 52800081    	mov	w1, #0x4                ; =4
1b8f68178: 52800002    	mov	w2, #0x0                ; =0
1b8f6817c: 52800003    	mov	w3, #0x0                ; =0
1b8f68180: 52800004    	mov	w4, #0x0                ; =0
1b8f68184: 94336ce3    	bl	0x1b9c43510
1b8f68188: f94166a0    	ldr	x0, [x21, #0x2c8]
1b8f6818c: f9400010    	ldr	x16, [x0]
1b8f68190: aa0003f1    	mov	x17, x0
1b8f68194: f2eb62b1    	movk	x17, #0x5b15, lsl #48
1b8f68198: dac11a30    	autda	x16, x17
1b8f6819c: f8418e08    	ldr	x8, [x16, #0x18]!
1b8f681a0: aa1003e9    	mov	x9, x16
1b8f681a4: aa0903f1    	mov	x17, x9
1b8f681a8: f2e0f971    	movk	x17, #0x7cb, lsl #48
1b8f681ac: d73f0911    	blraa	x8, x17
1b8f681b0: f9400010    	ldr	x16, [x0]
1b8f681b4: aa0003f1    	mov	x17, x0
1b8f681b8: f2f74d71    	movk	x17, #0xba6b, lsl #48
1b8f681bc: dac11a30    	autda	x16, x17
1b8f681c0: d2803d11    	mov	x17, #0x1e8             ; =488
1b8f681c4: 8b110210    	add	x16, x16, x17
1b8f681c8: f9400208    	ldr	x8, [x16]
1b8f681cc: aa1003e9    	mov	x9, x16
1b8f681d0: aa0903f1    	mov	x17, x9
1b8f681d4: f2ec69b1    	movk	x17, #0x634d, lsl #48
1b8f681d8: d73f0911    	blraa	x8, x17
1b8f681dc: 34000da0    	cbz	w0, 0x1b8f68390
1b8f681e0: 52800028    	mov	w8, #0x1                ; =1
1b8f681e4: b90033e8    	str	w8, [sp, #0x30]
1b8f681e8: f942baa0    	ldr	x0, [x21, #0x570]
1b8f681ec: 9100c3e4    	add	x4, sp, #0x30
1b8f681f0: 528004a1    	mov	w1, #0x25               ; =37
1b8f681f4: 52800002    	mov	w2, #0x0                ; =0
1b8f681f8: 52800003    	mov	w3, #0x0                ; =0
1b8f681fc: 52800085    	mov	w5, #0x4                ; =4
1b8f68200: 94336cc8    	bl	0x1b9c43520
1b8f68204: aa0003f9    	mov	x25, x0
1b8f68208: f943eb48    	ldr	x8, [x26, #0x7d0]
1b8f6820c: 34000460    	cbz	w0, 0x1b8f68298
1b8f68210: b40004c8    	cbz	x8, 0x1b8f682a8
1b8f68214: f940011a    	ldr	x26, [x8]
1b8f68218: b50004da    	cbnz	x26, 0x1b8f682b0
1b8f6821c: 1400005d    	b	0x1b8f68390
1b8f68220: f016e433    	adrp	x19, 0x1e6bef000
1b8f68224: f9459673    	ldr	x19, [x19, #0xb28]
1b8f68228: aa1303e0    	mov	x0, x19
1b8f6822c: 52800201    	mov	w1, #0x10               ; =16
1b8f68230: 94337888    	bl	0x1b9c46450
1b8f68234: 340002c0    	cbz	w0, 0x1b8f6828c
1b8f68238: f0001008    	adrp	x8, 0x1b916b000
1b8f6823c: fd463500    	ldr	d0, [x8, #0xc68]
1b8f68240: bd0033e0    	str	s0, [sp, #0x30]
1b8f68244: f0001188    	adrp	x8, 0x1b919b000
1b8f68248: 91263908    	add	x8, x8, #0x98e
1b8f6824c: f80343e8    	stur	x8, [sp, #0x34]
1b8f68250: 52808008    	mov	w8, #0x400              ; =1024
1b8f68254: 79007be8    	strh	w8, [sp, #0x3c]
1b8f68258: 52809969    	mov	w9, #0x4cb              ; =1227
1b8f6825c: b803e3e9    	stur	w9, [sp, #0x3e]
1b8f68260: 790087e8    	strh	w8, [sp, #0x42]
1b8f68264: b90047f9    	str	w25, [sp, #0x44]
1b8f68268: f0fffa80    	adrp	x0, 0x1b8ebb000
1b8f6826c: 91000000    	add	x0, x0, #0x0
1b8f68270: b00012c3    	adrp	x3, 0x1b91c1000
1b8f68274: 911a3c63    	add	x3, x3, #0x68f
1b8f68278: 9100c3e4    	add	x4, sp, #0x30
1b8f6827c: aa1303e1    	mov	x1, x19
1b8f68280: 52800202    	mov	w2, #0x10               ; =16
1b8f68284: 52800305    	mov	w5, #0x18               ; =24
1b8f68288: 9433757e    	bl	0x1b9c45880
1b8f6828c: 910183e0    	add	x0, sp, #0x60
1b8f68290: 9406784d    	bl	0x1b91063c4
1b8f68294: 1400009e    	b	0x1b8f6850c
1b8f68298: b4000448    	cbz	x8, 0x1b8f68320
1b8f6829c: f9400119    	ldr	x25, [x8]
1b8f682a0: b5000459    	cbnz	x25, 0x1b8f68328
1b8f682a4: 1400003b    	b	0x1b8f68390
1b8f682a8: f016e43a    	adrp	x26, 0x1e6bef000
1b8f682ac: f945975a    	ldr	x26, [x26, #0xb28]
1b8f682b0: aa1a03e0    	mov	x0, x26
1b8f682b4: 52800041    	mov	w1, #0x2                ; =2
1b8f682b8: 94337866    	bl	0x1b9c46450
1b8f682bc: 340006a0    	cbz	w0, 0x1b8f68390
1b8f682c0: b94023e8    	ldr	w8, [sp, #0x20]
1b8f682c4: f0001009    	adrp	x9, 0x1b916b000
1b8f682c8: fd463920    	ldr	d0, [x9, #0xc70]
1b8f682cc: bd0033e0    	str	s0, [sp, #0x30]
1b8f682d0: f0001189    	adrp	x9, 0x1b919b000
1b8f682d4: 91263929    	add	x9, x9, #0x98e
1b8f682d8: f80343e9    	stur	x9, [sp, #0x34]
1b8f682dc: 52808009    	mov	w9, #0x400              ; =1024
1b8f682e0: 79007be9    	strh	w9, [sp, #0x3c]
1b8f682e4: 52809b0a    	mov	w10, #0x4d8             ; =1240
1b8f682e8: b803e3ea    	stur	w10, [sp, #0x3e]
1b8f682ec: 790087e9    	strh	w9, [sp, #0x42]
1b8f682f0: b90047e8    	str	w8, [sp, #0x44]
1b8f682f4: 790093e9    	strh	w9, [sp, #0x48]
1b8f682f8: b804a3f9    	stur	w25, [sp, #0x4a]
1b8f682fc: f0fffa80    	adrp	x0, 0x1b8ebb000
1b8f68300: 91000000    	add	x0, x0, #0x0
1b8f68304: b00012c3    	adrp	x3, 0x1b91c1000
1b8f68308: 911bd463    	add	x3, x3, #0x6f5
1b8f6830c: 9100c3e4    	add	x4, sp, #0x30
1b8f68310: aa1a03e1    	mov	x1, x26
1b8f68314: 52800042    	mov	w2, #0x2                ; =2
1b8f68318: 528003c5    	mov	w5, #0x1e               ; =30
1b8f6831c: 1400001c    	b	0x1b8f6838c
1b8f68320: f016e439    	adrp	x25, 0x1e6bef000
1b8f68324: f9459739    	ldr	x25, [x25, #0xb28]
1b8f68328: aa1903e0    	mov	x0, x25
1b8f6832c: 52800041    	mov	w1, #0x2                ; =2
1b8f68330: 94337848    	bl	0x1b9c46450
1b8f68334: 340002e0    	cbz	w0, 0x1b8f68390
1b8f68338: b94023e8    	ldr	w8, [sp, #0x20]
1b8f6833c: f0001009    	adrp	x9, 0x1b916b000
1b8f68340: fd463520    	ldr	d0, [x9, #0xc68]
1b8f68344: bd0033e0    	str	s0, [sp, #0x30]
1b8f68348: f0001189    	adrp	x9, 0x1b919b000
1b8f6834c: 91263929    	add	x9, x9, #0x98e
1b8f68350: f80343e9    	stur	x9, [sp, #0x34]
1b8f68354: 52808009    	mov	w9, #0x400              ; =1024
1b8f68358: 79007be9    	strh	w9, [sp, #0x3c]
1b8f6835c: 52809b8a    	mov	w10, #0x4dc             ; =1244
1b8f68360: b803e3ea    	stur	w10, [sp, #0x3e]
1b8f68364: 790087e9    	strh	w9, [sp, #0x42]
1b8f68368: b90047e8    	str	w8, [sp, #0x44]
1b8f6836c: f0fffa80    	adrp	x0, 0x1b8ebb000
1b8f68370: 91000000    	add	x0, x0, #0x0
1b8f68374: b00012c3    	adrp	x3, 0x1b91c1000
1b8f68378: 911d8063    	add	x3, x3, #0x760
1b8f6837c: 9100c3e4    	add	x4, sp, #0x30
1b8f68380: aa1903e1    	mov	x1, x25
1b8f68384: 52800042    	mov	w2, #0x2                ; =2
1b8f68388: 52800305    	mov	w5, #0x18               ; =24
1b8f6838c: 9433753d    	bl	0x1b9c45880
1b8f68390: 6b1c029f    	cmp	w20, w28
1b8f68394: 54000081    	b.ne	0x1b8f683a4
1b8f68398: aa1503e0    	mov	x0, x21
1b8f6839c: 940000b3    	bl	0x1b8f68668
1b8f683a0: 14000048    	b	0x1b8f684c0
1b8f683a4: 52970308    	mov	w8, #0xb818             ; =47128
1b8f683a8: 72a74608    	movk	w8, #0x3a30, lsl #16
1b8f683ac: 6b54051f    	cmp	w8, w20, lsr #1
1b8f683b0: 54000501    	b.ne	0x1b8f68450
1b8f683b4: 528e0608    	mov	w8, #0x7030             ; =28720
1b8f683b8: 72ae8c28    	movk	w8, #0x7461, lsl #16
1b8f683bc: 6b08029f    	cmp	w20, w8
1b8f683c0: 1a9f17e8    	cset	w8, eq
1b8f683c4: b9002ef4    	str	w20, [x23, #0x2c]
1b8f683c8: 390c82a8    	strb	w8, [x21, #0x320]
1b8f683cc: 52820008    	mov	w8, #0x1000             ; =4096
1b8f683d0: b90003e8    	str	w8, [sp]
1b8f683d4: f94166a0    	ldr	x0, [x21, #0x2c8]
1b8f683d8: 94000122    	bl	0x1b8f68860
1b8f683dc: f9400aa8    	ldr	x8, [x21, #0x10]
1b8f683e0: fd402501    	ldr	d1, [x8, #0x48]
1b8f683e4: 1e612000    	fcmp	d0, d1
1b8f683e8: 54000160    	b.eq	0x1b8f68414
1b8f683ec: 1e601820    	fdiv	d0, d1, d0
1b8f683f0: d2e81608    	mov	x8, #0x40b0000000000000 ; =4661225614328463360
1b8f683f4: 9e670101    	fmov	d1, x8
1b8f683f8: 1e661002    	fmov	d2, #16.00000000
1b8f683fc: 1f410800    	fmadd	d0, d0, d1, d2
1b8f68400: 1e790008    	fcvtzu	w8, d0
1b8f68404: 7140051f    	cmp	w8, #0x1, lsl #12       ; =0x1000
1b8f68408: 52820009    	mov	w9, #0x1000             ; =4096
1b8f6840c: 1a898108    	csel	w8, w8, w9, hi
1b8f68410: b90003e8    	str	w8, [sp]
1b8f68414: f94006e0    	ldr	x0, [x23, #0x8]
1b8f68418: 910003e4    	mov	x4, sp
1b8f6841c: 52819f01    	mov	w1, #0xcf8              ; =3320
1b8f68420: 52800002    	mov	w2, #0x0                ; =0
1b8f68424: 52800003    	mov	w3, #0x0                ; =0
1b8f68428: 52800085    	mov	w5, #0x4                ; =4
1b8f6842c: 94336c3d    	bl	0x1b9c43520
1b8f68430: aa0003f8    	mov	x24, x0
1b8f68434: 34000460    	cbz	w0, 0x1b8f684c0
1b8f68438: b01970c8    	adrp	x8, 0x1ebd81000
1b8f6843c: f943e908    	ldr	x8, [x8, #0x7d0]
1b8f68440: b5000888    	cbnz	x8, 0x1b8f68550
1b8f68444: f016e433    	adrp	x19, 0x1e6bef000
1b8f68448: f9459673    	ldr	x19, [x19, #0xb28]
1b8f6844c: 14000043    	b	0x1b8f68558
1b8f68450: 6b1b029f    	cmp	w20, w27
1b8f68454: 54000361    	b.ne	0x1b8f684c0
1b8f68458: 911d86a8    	add	x8, x21, #0x761
1b8f6845c: 7900011f    	strh	wzr, [x8]
1b8f68460: b4000318    	cbz	x24, 0x1b8f684c0
1b8f68464: f9400319    	ldr	x25, [x24]
1b8f68468: f9401728    	ldr	x8, [x25, #0x28]
1b8f6846c: b4000128    	cbz	x8, 0x1b8f68490
1b8f68470: b9403321    	ldr	w1, [x25, #0x30]
1b8f68474: 912062a0    	add	x0, x21, #0x818
1b8f68478: 94000112    	bl	0x1b8f688c0
1b8f6847c: f9401721    	ldr	x1, [x25, #0x28]
1b8f68480: b9403323    	ldr	w3, [x25, #0x30]
1b8f68484: 912062a0    	add	x0, x21, #0x818
1b8f68488: 8b030022    	add	x2, x1, x3
1b8f6848c: 97fdbc7d    	bl	0x1b8ed7680
1b8f68490: f9400708    	ldr	x8, [x24, #0x8]
1b8f68494: f9400100    	ldr	x0, [x8]
1b8f68498: 94336baa    	bl	0x1b9c43340
1b8f6849c: 36000060    	tbz	w0, #0x0, 0x1b8f684a8
1b8f684a0: 52800020    	mov	w0, #0x1                ; =1
1b8f684a4: 14000003    	b	0x1b8f684b0
1b8f684a8: aa1903e0    	mov	x0, x25
1b8f684ac: 9405d7b1    	bl	0x1b90de370
1b8f684b0: 391d86a0    	strb	w0, [x21, #0x761]
1b8f684b4: aa1903e0    	mov	x0, x25
1b8f684b8: 9405d7c9    	bl	0x1b90de3dc
1b8f684bc: 391d8aa0    	strb	w0, [x21, #0x762]
1b8f684c0: 910183e0    	add	x0, sp, #0x60
1b8f684c4: 940677c0    	bl	0x1b91063c4
1b8f684c8: aa1503e0    	mov	x0, x21
1b8f684cc: aa1403e1    	mov	x1, x20
1b8f684d0: 97ffaf6f    	bl	0x1b8f5428c
1b8f684d4: aa0003f9    	mov	x25, x0
1b8f684d8: 350001a0    	cbnz	w0, 0x1b8f6850c
1b8f684dc: b4000193    	cbz	x19, 0x1b8f6850c
1b8f684e0: 34000176    	cbz	w22, 0x1b8f6850c
1b8f684e4: f94006e0    	ldr	x0, [x23, #0x8]
1b8f684e8: 52800508    	mov	w8, #0x28               ; =40
1b8f684ec: b90063e8    	str	w8, [sp, #0x60]
1b8f684f0: 910183e5    	add	x5, sp, #0x60
1b8f684f4: 52800101    	mov	w1, #0x8                ; =8
1b8f684f8: 52800042    	mov	w2, #0x2                ; =2
1b8f684fc: 52800003    	mov	w3, #0x0                ; =0
1b8f68500: aa1303e4    	mov	x4, x19
1b8f68504: 94336bdf    	bl	0x1b9c43480
1b8f68508: 52800019    	mov	w25, #0x0               ; =0
1b8f6850c: f85903a8    	ldur	x8, [x29, #-0x70]
1b8f68510: f016e429    	adrp	x9, 0x1e6bef000
1b8f68514: f943ad29    	ldr	x9, [x9, #0x758]
1b8f68518: f9400129    	ldr	x9, [x9]
1b8f6851c: eb08013f    	cmp	x9, x8
1b8f68520: 54000161    	b.ne	0x1b8f6854c
1b8f68524: aa1903e0    	mov	x0, x25
