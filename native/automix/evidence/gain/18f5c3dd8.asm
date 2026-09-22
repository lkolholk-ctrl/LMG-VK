
/srv/research/tmp/extracted_dylibs/AudioToolboxCore:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000018f5c3dd8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE11setPropertyEjjjjPKv>:
18f5c3dd8: 7130683f    	cmp	w1, #0xc1a
18f5c3ddc: 540000c1    	b.ne	0x18f5c3df4 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE11setPropertyEjjjjPKv+0x1c>
18f5c3de0: 7100209f    	cmp	w4, #0x8
18f5c3de4: 540000a1    	b.ne	0x18f5c3df8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE11setPropertyEjjjjPKv+0x20>
18f5c3de8: 34000102    	cbz	w2, 0x18f5c3e08 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE11setPropertyEjjjjPKv+0x30>
18f5c3dec: 12854e28    	mov	w8, #-0x2a72            ; =-10866
18f5c3df0: 14000004    	b	0x18f5c3e00 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE11setPropertyEjjjjPKv+0x28>
18f5c3df4: 17ffd17e    	b	0x18f5b83ec <__ZN8DSPGraph3Box11setPropertyEjjjjPKv>
18f5c3df8: 528d2f48    	mov	w8, #0x697a             ; =27002
18f5c3dfc: 72a42e68    	movk	w8, #0x2173, lsl #16
18f5c3e00: aa0803e0    	mov	x0, x8
18f5c3e04: d65f03c0    	ret
18f5c3e08: fd4000a1    	ldr	d1, [x5]
18f5c3e0c: fd018801    	str	d1, [x0, #0x310]
18f5c3e10: fd418c00    	ldr	d0, [x0, #0x318]
18f5c3e14: 52800008    	mov	w8, #0x0                ; =0
18f5c3e18: 1e602020    	fcmp	d1, d0
18f5c3e1c: 54ffff25    	b.pl	0x18f5c3e00 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE11setPropertyEjjjjPKv+0x28>
18f5c3e20: fd018800    	str	d0, [x0, #0x310]
18f5c3e24: 17fffff7    	b	0x18f5c3e00 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE11setPropertyEjjjjPKv+0x28>
