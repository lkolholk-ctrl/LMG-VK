
/srv/research/tmp/extracted_dylibs/AudioToolboxCore:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000018f5c416c <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE12setParameterEjjjfj>:
18f5c416c: d503237f    	pacibsp
18f5c4170: d101c3ff    	sub	sp, sp, #0x70
18f5c4174: a9054ff4    	stp	x20, x19, [sp, #0x50]
18f5c4178: a9067bfd    	stp	x29, x30, [sp, #0x60]
18f5c417c: 910183fd    	add	x29, sp, #0x60
18f5c4180: 2a010048    	orr	w8, w2, w1
18f5c4184: 34000108    	cbz	w8, 0x18f5c41a4 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE12setParameterEjjjfj+0x38>
18f5c4188: 7100043f    	cmp	w1, #0x1
18f5c418c: 54000361    	b.ne	0x18f5c41f8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE12setParameterEjjjfj+0x8c>
18f5c4190: 35000342    	cbnz	w2, 0x18f5c41f8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE12setParameterEjjjfj+0x8c>
18f5c4194: 1e202008    	fcmp	s0, #0.0
18f5c4198: 1a9f07e8    	cset	w8, ne
18f5c419c: 390c3008    	strb	w8, [x0, #0x30c]
18f5c41a0: 14000002    	b	0x18f5c41a8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE12setParameterEjjjfj+0x3c>
18f5c41a4: bd030800    	str	s0, [x0, #0x308]
18f5c41a8: f9400408    	ldr	x8, [x0, #0x8]
18f5c41ac: 394be908    	ldrb	w8, [x8, #0x2fa]
18f5c41b0: 7100051f    	cmp	w8, #0x1
18f5c41b4: 54000081    	b.ne	0x18f5c41c4 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE12setParameterEjjjfj+0x58>
18f5c41b8: 394cb008    	ldrb	w8, [x0, #0x32c]
18f5c41bc: 7100051f    	cmp	w8, #0x1
18f5c41c0: 54000141    	b.ne	0x18f5c41e8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE12setParameterEjjjfj+0x7c>
18f5c41c4: bd430800    	ldr	s0, [x0, #0x308]
18f5c41c8: 394c3008    	ldrb	w8, [x0, #0x30c]
18f5c41cc: 7100011f    	cmp	w8, #0x0
18f5c41d0: 1e2e1001    	fmov	s1, #1.00000000
18f5c41d4: 2f00e402    	movi	d2, #0000000000000000
18f5c41d8: 1e211c41    	fcsel	s1, s2, s1, ne
18f5c41dc: 1e210800    	fmul	s0, s0, s1
18f5c41e0: bd032400    	str	s0, [x0, #0x324]
18f5c41e4: bd032800    	str	s0, [x0, #0x328]
18f5c41e8: a9467bfd    	ldp	x29, x30, [sp, #0x60]
18f5c41ec: a9454ff4    	ldp	x20, x19, [sp, #0x50]
18f5c41f0: 9101c3ff    	add	sp, sp, #0x70
18f5c41f4: d65f0fff    	retab
