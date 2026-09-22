
/srv/research/tmp/extracted_dylibs/AudioToolboxCore:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000018f3c2dd8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE5resetEv>:
18f3c2dd8: bd430800    	ldr	s0, [x0, #0x308]
18f3c2ddc: 394c3008    	ldrb	w8, [x0, #0x30c]
18f3c2de0: 7100011f    	cmp	w8, #0x0
18f3c2de4: 1e2e1001    	fmov	s1, #1.00000000
18f3c2de8: 2f00e402    	movi	d2, #0000000000000000
18f3c2dec: 1e211c41    	fcsel	s1, s2, s1, ne
18f3c2df0: 1e210800    	fmul	s0, s0, s1
18f3c2df4: bd032400    	str	s0, [x0, #0x324]
18f3c2df8: 52802028    	mov	w8, #0x101              ; =257
18f3c2dfc: 79065808    	strh	w8, [x0, #0x32c]
18f3c2e00: d65f03c0    	ret
