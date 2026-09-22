
/srv/research/tmp/extracted_dylibs/_SonicKit_MusicKit_Packages:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000027225479c <___swift_allocate_value_buffer>:
2722548b4: d503237f    	pacibsp
2722548b8: f81e0ff3    	str	x19, [sp, #-0x20]!
2722548bc: a9017bfd    	stp	x29, x30, [sp, #0x10]
2722548c0: 910043fd    	add	x29, sp, #0x10
2722548c4: aa1503f3    	mov	x19, x21
2722548c8: f9400009    	ldr	x9, [x0]
2722548cc: 3940200a    	ldrb	w10, [x0, #0x8]
2722548d0: 3700028a    	tbnz	w10, #0x0, 0x272254920 <___swift_allocate_value_buffer+0x184>
2722548d4: 9e670120    	fmov	d0, x9
2722548d8: f000022b    	adrp	x11, 0x27229b000 <_type_layout_string 015_SonicKit_MusicB9_Packages10TransitionV15SteppedScheduleV34SynchronizedPlaybackTimeCalculatorV+0x20>
2722548dc: fd419d61    	ldr	d1, [x11, #0x338]
2722548e0: 1e612000    	fcmp	d0, d1
2722548e4: 1e6e1001    	fmov	d1, #1.00000000
2722548e8: 1e61a402    	fccmp	d0, d1, #0x2, ge
2722548ec: 540001a9    	b.ls	0x272254920 <___swift_allocate_value_buffer+0x184>
2722548f0: 97ff59aa    	bl	0x27222af98 <_OUTLINED_FUNCTION_0+0x1f18>
2722548f4: aa0003e1    	mov	x1, x0
2722548f8: d00b12e0    	adrp	x0, 0x2884b2000 <_TtC27_SonicKit_MusicKit_PackagesP33_621ACBB81100A2B81D102DB8A698644434SonicKit_MusicKit_Packages_Locator+0x6163aa0>
2722548fc: 9121c000    	add	x0, x0, #0x870
272254900: d2800002    	mov	x2, #0x0                ; =0
272254904: 52800003    	mov	w3, #0x0                ; =0
272254908: 948624de    	bl	0x2743ddc80 <_swift_willThrow+0x2743ddc80>
27225490c: aa0003f3    	mov	x19, x0
272254910: 3900003f    	strb	wzr, [x1]
272254914: aa0003f5    	mov	x21, x0
272254918: 94862592    	bl	0x2743ddf60 <_swift_willThrow+0x2743ddf60>
27225491c: 14000003    	b	0x272254928 <___swift_allocate_value_buffer+0x18c>
272254920: f9000109    	str	x9, [x8]
272254924: 3900210a    	strb	w10, [x8, #0x8]
272254928: aa1303f5    	mov	x21, x19
27225492c: a9417bfd    	ldp	x29, x30, [sp, #0x10]
272254930: f84207f3    	ldr	x19, [sp], #0x20
272254934: d65f0fff    	retab
272254938: 39402009    	ldrb	w9, [x0, #0x8]
27225493c: 39402028    	ldrb	w8, [x1, #0x8]
272254940: 36000089    	tbz	w9, #0x0, 0x272254950 <___swift_allocate_value_buffer+0x1b4>
272254944: 37000108    	tbnz	w8, #0x0, 0x272254964 <___swift_allocate_value_buffer+0x1c8>
272254948: 52800000    	mov	w0, #0x0                ; =0
27225494c: d65f03c0    	ret
272254950: fd400020    	ldr	d0, [x1]
272254954: fd400001    	ldr	d1, [x0]
272254958: 1e602020    	fcmp	d1, d0
27225495c: 1a9f0508    	csinc	w8, w8, wzr, eq
272254960: 3707ff48    	tbnz	w8, #0x0, 0x272254948 <___swift_allocate_value_buffer+0x1ac>
272254964: 52800020    	mov	w0, #0x1                ; =1
272254968: d65f03c0    	ret
27225496c: 17fffff3    	b	0x272254938 <___swift_allocate_value_buffer+0x19c>
272254970: 340000c1    	cbz	w1, 0x272254988 <___swift_allocate_value_buffer+0x1ec>
272254974: 39402408    	ldrb	w8, [x0, #0x9]
272254978: 34000088    	cbz	w8, 0x272254988 <___swift_allocate_value_buffer+0x1ec>
27225497c: b9400008    	ldr	w8, [x0]
272254980: 11000500    	add	w0, w8, #0x1
272254984: d65f03c0    	ret
272254988: 52800000    	mov	w0, #0x0                ; =0
27225498c: d65f03c0    	ret
272254990: 340000e1    	cbz	w1, 0x2722549ac <___swift_allocate_value_buffer+0x210>
272254994: 51000428    	sub	w8, w1, #0x1
272254998: 3900201f    	strb	wzr, [x0, #0x8]
27225499c: f9000008    	str	x8, [x0]
2722549a0: 340000c2    	cbz	w2, 0x2722549b8 <___swift_allocate_value_buffer+0x21c>
2722549a4: 52800028    	mov	w8, #0x1                ; =1
2722549a8: 14000003    	b	0x2722549b4 <___swift_allocate_value_buffer+0x218>
2722549ac: 34000062    	cbz	w2, 0x2722549b8 <___swift_allocate_value_buffer+0x21c>
2722549b0: 52800008    	mov	w8, #0x0                ; =0
2722549b4: 39002408    	strb	w8, [x0, #0x9]
2722549b8: d65f03c0    	ret
2722549bc: d00b12c0    	adrp	x0, 0x2884ae000 <_TtC27_SonicKit_MusicKit_PackagesP33_621ACBB81100A2B81D102DB8A698644434SonicKit_MusicKit_Packages_Locator+0x615faa0>
2722549c0: 912b2000    	add	x0, x0, #0xac8
2722549c4: d2800001    	mov	x1, #0x0                ; =0
2722549c8: d65f03c0    	ret
2722549cc: f900011f    	str	xzr, [x8]
2722549d0: 52800029    	mov	w9, #0x1                ; =1
2722549d4: 39002109    	strb	w9, [x8, #0x8]
2722549d8: d65f03c0    	ret
