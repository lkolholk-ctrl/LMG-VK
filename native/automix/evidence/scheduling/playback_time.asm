
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000027224fae4 <_OUTLINED_FUNCTION_1>:
27224fb08: d503237f    	pacibsp
27224fb0c: d10343ff    	sub	sp, sp, #0xd0
27224fb10: 6d073bef    	stp	d15, d14, [sp, #0x70]
27224fb14: 6d0833ed    	stp	d13, d12, [sp, #0x80]
27224fb18: 6d092beb    	stp	d11, d10, [sp, #0x90]
27224fb1c: 6d0a23e9    	stp	d9, d8, [sp, #0xa0]
27224fb20: a90b4ff4    	stp	x20, x19, [sp, #0xb0]
27224fb24: a90c7bfd    	stp	x29, x30, [sp, #0xc0]
27224fb28: 910303fd    	add	x29, sp, #0xc0
27224fb2c: aa0803f3    	mov	x19, x8
27224fb30: fd400008    	ldr	d8, [x0]
27224fb34: 6d402c20    	ldp	d0, d11, [x1]
27224fb38: 6d413421    	ldp	d1, d13, [x1, #0x10]
27224fb3c: 39408028    	ldrb	w8, [x1, #0x20]
27224fb40: 6d402682    	ldp	d2, d9, [x20]
27224fb44: fd400a8a    	ldr	d10, [x20, #0x10]
27224fb48: 1e682020    	fcmp	d1, d8
27224fb4c: 1e684c23    	fcsel	d3, d1, d8, mi
27224fb50: 1e623863    	fsub	d3, d3, d2
27224fb54: 2f00e40c    	movi	d12, #0000000000000000
27224fb58: 1e602068    	fcmp	d3, #0.0
27224fb5c: 2f00e40e    	movi	d14, #0000000000000000
27224fb60: 5400008d    	b.le	0x27224fb70 <_OUTLINED_FUNCTION_1+0x8c>
27224fb64: 1e6e1004    	fmov	d4, #1.00000000
27224fb68: 1e601884    	fdiv	d4, d4, d0
27224fb6c: 1e63088e    	fmul	d14, d4, d3
27224fb70: 6d0327e2    	stp	d2, d9, [sp, #0x30]
27224fb74: fd0023ea    	str	d10, [sp, #0x40]
27224fb78: 3cc18282    	ldur	q2, [x20, #0x18]
27224fb7c: 3c8483e2    	stur	q2, [sp, #0x48]
27224fb80: 3cc28282    	ldur	q2, [x20, #0x28]
27224fb84: 3c8583e2    	stur	q2, [sp, #0x58]
27224fb88: 38438289    	ldurb	w9, [x20, #0x38]
27224fb8c: 3901a3e9    	strb	w9, [sp, #0x68]
27224fb90: fd0017e8    	str	d8, [sp, #0x28]
27224fb94: 6d002fe0    	stp	d0, d11, [sp]
27224fb98: 6d0137e1    	stp	d1, d13, [sp, #0x10]
27224fb9c: 390083e8    	strb	w8, [sp, #0x20]
27224fba0: 9100a3e0    	add	x0, sp, #0x28
27224fba4: 910003e1    	mov	x1, sp
27224fba8: 9100c3f4    	add	x20, sp, #0x30
27224fbac: 940001d5    	bl	0x272250300 <_OUTLINED_FUNCTION_1+0x81c>
27224fbb0: 1e6821a0    	fcmp	d13, d8
27224fbb4: 1e6d4d01    	fcsel	d1, d8, d13, mi
27224fbb8: 1e6d3821    	fsub	d1, d1, d13
27224fbbc: 1e602028    	fcmp	d1, #0.0
27224fbc0: 5400008d    	b.le	0x27224fbd0 <_OUTLINED_FUNCTION_1+0xec>
27224fbc4: 1e6e1002    	fmov	d2, #1.00000000
27224fbc8: 1e6b1842    	fdiv	d2, d2, d11
27224fbcc: 1e61084c    	fmul	d12, d2, d1
27224fbd0: 1e6029c0    	fadd	d0, d14, d0
27224fbd4: 1e6c2800    	fadd	d0, d0, d12
27224fbd8: 1e602920    	fadd	d0, d9, d0
27224fbdc: 1e693801    	fsub	d1, d0, d9
27224fbe0: 1e612941    	fadd	d1, d10, d1
27224fbe4: 6d000268    	stp	d8, d0, [x19]
27224fbe8: fd000a61    	str	d1, [x19, #0x10]
27224fbec: a94c7bfd    	ldp	x29, x30, [sp, #0xc0]
27224fbf0: a94b4ff4    	ldp	x20, x19, [sp, #0xb0]
27224fbf4: 6d4a23e9    	ldp	d9, d8, [sp, #0xa0]
27224fbf8: 6d492beb    	ldp	d11, d10, [sp, #0x90]
27224fbfc: 6d4833ed    	ldp	d13, d12, [sp, #0x80]
27224fc00: 6d473bef    	ldp	d15, d14, [sp, #0x70]
27224fc04: 910343ff    	add	sp, sp, #0xd0
27224fc08: d65f0fff    	retab
27224fc0c: d503237f    	pacibsp
27224fc10: d10483ff    	sub	sp, sp, #0x120
27224fc14: 6d0a3bef    	stp	d15, d14, [sp, #0xa0]
27224fc18: 6d0b33ed    	stp	d13, d12, [sp, #0xb0]
27224fc1c: 6d0c2beb    	stp	d11, d10, [sp, #0xc0]
27224fc20: 6d0d23e9    	stp	d9, d8, [sp, #0xd0]
27224fc24: a90e6ffc    	stp	x28, x27, [sp, #0xe0]
27224fc28: a90f57f6    	stp	x22, x21, [sp, #0xf0]
27224fc2c: a9104ff4    	stp	x20, x19, [sp, #0x100]
27224fc30: a9117bfd    	stp	x29, x30, [sp, #0x110]
27224fc34: 910443fd    	add	x29, sp, #0x110
27224fc38: aa1403f5    	mov	x21, x20
27224fc3c: aa0803f3    	mov	x19, x8
27224fc40: fd400000    	ldr	d0, [x0]
27224fc44: 6d402c2d    	ldp	d13, d11, [x1]
27224fc48: 6d413c2e    	ldp	d14, d15, [x1, #0x10]
27224fc4c: 39408036    	ldrb	w22, [x1, #0x20]
27224fc50: 6d40268a    	ldp	d10, d9, [x20]
27224fc54: fd400a88    	ldr	d8, [x20, #0x10]
27224fc58: 6d0137e0    	stp	d0, d13, [sp, #0x10]
27224fc5c: 1e683800    	fsub	d0, d0, d8
27224fc60: 1e60292c    	fadd	d12, d9, d0
27224fc64: 6d04a7ea    	stp	d10, d9, [sp, #0x48]
27224fc68: fd002fe8    	str	d8, [sp, #0x58]
27224fc6c: 3cc18281    	ldur	q1, [x20, #0x18]
27224fc70: 3cc28280    	ldur	q0, [x20, #0x28]
27224fc74: ad0303e1    	stp	q1, q0, [sp, #0x60]
27224fc78: 3940e288    	ldrb	w8, [x20, #0x38]
27224fc7c: 390203e8    	strb	w8, [sp, #0x80]
27224fc80: fd0023ee    	str	d14, [sp, #0x40]
27224fc84: 6d023beb    	stp	d11, d14, [sp, #0x20]
27224fc88: fd001bef    	str	d15, [sp, #0x30]
27224fc8c: 94000365    	bl	0x272250a20 <_OUTLINED_FUNCTION_3>
27224fc90: fd404be0    	ldr	d0, [sp, #0x90]
27224fc94: 1e6c2000    	fcmp	d0, d12
27224fc98: 1e6c4c00    	fcsel	d0, d0, d12, mi
27224fc9c: 1e693800    	fsub	d0, d0, d9
27224fca0: 1e602008    	fcmp	d0, #0.0
27224fca4: 2f00e401    	movi	d1, #0000000000000000
27224fca8: 5400008d    	b.le	0x27224fcb8 <_OUTLINED_FUNCTION_1+0x1d4>
27224fcac: 1e6e1001    	fmov	d1, #1.00000000
27224fcb0: 1e6d1821    	fdiv	d1, d1, d13
27224fcb4: 1e611801    	fdiv	d1, d0, d1
27224fcb8: fd0007e1    	str	d1, [sp, #0x8]
27224fcbc: 94000344    	bl	0x2722509cc <_OUTLINED_FUNCTION_0>
27224fcc0: fd0047ec    	str	d12, [sp, #0x88]
27224fcc4: 6d01afed    	stp	d13, d11, [sp, #0x18]
27224fcc8: 6d02bfee    	stp	d14, d15, [sp, #0x28]
27224fccc: 3900e3f6    	strb	w22, [sp, #0x38]
27224fcd0: 910223e0    	add	x0, sp, #0x88
27224fcd4: 910063e1    	add	x1, sp, #0x18
27224fcd8: 910123f4    	add	x20, sp, #0x48
27224fcdc: 940001b5    	bl	0x2722503b0 <_OUTLINED_FUNCTION_1+0x8cc>
27224fce0: fd0003e0    	str	d0, [sp]
27224fce4: 9400033a    	bl	0x2722509cc <_OUTLINED_FUNCTION_0>
27224fce8: fd0023ef    	str	d15, [sp, #0x40]
27224fcec: 6d01afed    	stp	d13, d11, [sp, #0x18]
27224fcf0: 6d02bfee    	stp	d14, d15, [sp, #0x28]
27224fcf4: 9400034b    	bl	0x272250a20 <_OUTLINED_FUNCTION_3>
27224fcf8: fd404be0    	ldr	d0, [sp, #0x90]
27224fcfc: 1e6c2000    	fcmp	d0, d12
27224fd00: 1e604d81    	fcsel	d1, d12, d0, mi
27224fd04: 1e603820    	fsub	d0, d1, d0
27224fd08: 1e602008    	fcmp	d0, #0.0
27224fd0c: 2f00e401    	movi	d1, #0000000000000000
27224fd10: 5400008d    	b.le	0x27224fd20 <_OUTLINED_FUNCTION_1+0x23c>
27224fd14: 1e6e1001    	fmov	d1, #1.00000000
27224fd18: 1e6b1821    	fdiv	d1, d1, d11
27224fd1c: 1e611801    	fdiv	d1, d0, d1
27224fd20: 6d4003e2    	ldp	d2, d0, [sp]
27224fd24: 1e622800    	fadd	d0, d0, d2
27224fd28: 1e612800    	fadd	d0, d0, d1
27224fd2c: 1e602940    	fadd	d0, d10, d0
27224fd30: 6d003260    	stp	d0, d12, [x19]
27224fd34: fd400be0    	ldr	d0, [sp, #0x10]
27224fd38: fd000a60    	str	d0, [x19, #0x10]
27224fd3c: a9517bfd    	ldp	x29, x30, [sp, #0x110]
27224fd40: a9504ff4    	ldp	x20, x19, [sp, #0x100]
27224fd44: a94f57f6    	ldp	x22, x21, [sp, #0xf0]
27224fd48: a94e6ffc    	ldp	x28, x27, [sp, #0xe0]
27224fd4c: 6d4d23e9    	ldp	d9, d8, [sp, #0xd0]
27224fd50: 6d4c2beb    	ldp	d11, d10, [sp, #0xc0]
27224fd54: 6d4b33ed    	ldp	d13, d12, [sp, #0xb0]
27224fd58: 6d4a3bef    	ldp	d15, d14, [sp, #0xa0]
27224fd5c: 910483ff    	add	sp, sp, #0x120
27224fd60: d65f0fff    	retab
