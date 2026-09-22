
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

0000000272251548 <_OUTLINED_FUNCTION_3>:
27225157c: d503237f    	pacibsp
272251580: d102c3ff    	sub	sp, sp, #0xb0
272251584: a9094ff4    	stp	x20, x19, [sp, #0x90]
272251588: a90a7bfd    	stp	x29, x30, [sp, #0xa0]
27225158c: 910283fd    	add	x29, sp, #0xa0
272251590: aa0803f3    	mov	x19, x8
272251594: fd400000    	ldr	d0, [x0]
272251598: 6d400a81    	ldp	d1, d2, [x20]
27225159c: fd400a83    	ldr	d3, [x20, #0x10]
2722515a0: 1e632000    	fcmp	d0, d3
2722515a4: 540002e4    	b.mi	0x272251600 <_OUTLINED_FUNCTION_3+0xb8>
2722515a8: 3940e288    	ldrb	w8, [x20, #0x38]
2722515ac: 7103ed1f    	cmp	w8, #0xfb
2722515b0: 54000288    	b.hi	0x272251600 <_OUTLINED_FUNCTION_3+0xb8>
2722515b4: a941aa89    	ldp	x9, x10, [x20, #0x18]
2722515b8: a942b28b    	ldp	x11, x12, [x20, #0x28]
2722515bc: 6d038be1    	stp	d1, d2, [sp, #0x38]
2722515c0: fd0027e3    	str	d3, [sp, #0x48]
2722515c4: a9052be9    	stp	x9, x10, [sp, #0x50]
2722515c8: a90633eb    	stp	x11, x12, [sp, #0x60]
2722515cc: 3901c3e8    	strb	w8, [sp, #0x70]
2722515d0: fd001be0    	str	d0, [sp, #0x30]
2722515d4: a900abe9    	stp	x9, x10, [sp, #0x8]
2722515d8: a901b3eb    	stp	x11, x12, [sp, #0x18]
2722515dc: 3900a3e8    	strb	w8, [sp, #0x28]
2722515e0: d100a3a8    	sub	x8, x29, #0x28
2722515e4: 9100c3e0    	add	x0, sp, #0x30
2722515e8: 910023e1    	add	x1, sp, #0x8
2722515ec: 9100e3f4    	add	x20, sp, #0x38
2722515f0: 97fff987    	bl	0x27224fc0c <_OUTLINED_FUNCTION_1+0x128>
2722515f4: 6d7d8fa1    	ldp	d1, d3, [x29, #-0x28]
2722515f8: fc5e83a0    	ldur	d0, [x29, #-0x18]
2722515fc: 14000005    	b	0x272251610 <_OUTLINED_FUNCTION_3+0xc8>
272251600: 1e633803    	fsub	d3, d0, d3
272251604: 1e632843    	fadd	d3, d2, d3
272251608: 1e623862    	fsub	d2, d3, d2
27225160c: 1e622821    	fadd	d1, d1, d2
272251610: 6d000e61    	stp	d1, d3, [x19]
272251614: fd000a60    	str	d0, [x19, #0x10]
272251618: a94a7bfd    	ldp	x29, x30, [sp, #0xa0]
27225161c: a9494ff4    	ldp	x20, x19, [sp, #0x90]
272251620: 9102c3ff    	add	sp, sp, #0xb0
272251624: d65f0fff    	retab
