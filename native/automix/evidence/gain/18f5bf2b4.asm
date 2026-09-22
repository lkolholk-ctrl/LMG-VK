
/srv/research/tmp/extracted_dylibs/AudioToolboxCore:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000018f5bf2b4 <__ZN8DSPGraph6MixBox7processEj>:
18f5bf2b4: d503237f    	pacibsp
18f5bf2b8: d10383ff    	sub	sp, sp, #0xe0
18f5bf2bc: 6d0723e9    	stp	d9, d8, [sp, #0x70]
18f5bf2c0: a9086ffc    	stp	x28, x27, [sp, #0x80]
18f5bf2c4: a90967fa    	stp	x26, x25, [sp, #0x90]
18f5bf2c8: a90a5ff8    	stp	x24, x23, [sp, #0xa0]
18f5bf2cc: a90b57f6    	stp	x22, x21, [sp, #0xb0]
18f5bf2d0: a90c4ff4    	stp	x20, x19, [sp, #0xc0]
18f5bf2d4: a90d7bfd    	stp	x29, x30, [sp, #0xd0]
18f5bf2d8: 910343fd    	add	x29, sp, #0xd0
18f5bf2dc: aa0003f3    	mov	x19, x0
18f5bf2e0: a945a000    	ldp	x0, x8, [x0, #0x58]
18f5bf2e4: eb00011f    	cmp	x8, x0
18f5bf2e8: 54002240    	b.eq	0x18f5bf730 <__ZN8DSPGraph6MixBox7processEj+0x47c>
18f5bf2ec: aa0103f5    	mov	x21, x1
18f5bf2f0: f9400010    	ldr	x16, [x0]
18f5bf2f4: aa0003f1    	mov	x17, x0
18f5bf2f8: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f5bf2fc: dac11a30    	autda	x16, x17
18f5bf300: f8428e08    	ldr	x8, [x16, #0x28]!
18f5bf304: aa1003e9    	mov	x9, x16
18f5bf308: aa0903f1    	mov	x17, x9
18f5bf30c: f2e0d291    	movk	x17, #0x694, lsl #48
18f5bf310: d73f0911    	blraa	x8, x17
18f5bf314: f9401c08    	ldr	x8, [x0, #0x38]
18f5bf318: f9402918    	ldr	x24, [x8, #0x50]
18f5bf31c: b9400317    	ldr	w23, [x24]
18f5bf320: 531e76b4    	lsl	w20, w21, #2
18f5bf324: a9442260    	ldp	x0, x8, [x19, #0x40]
18f5bf328: cb000109    	sub	x9, x8, x0
18f5bf32c: f27b7d3f    	tst	x9, #0x1fffffffe0
18f5bf330: 54001100    	b.eq	0x18f5bf550 <__ZN8DSPGraph6MixBox7processEj+0x29c>
18f5bf334: d2800016    	mov	x22, #0x0               ; =0
18f5bf338: 9345fd29    	asr	x9, x9, #5
18f5bf33c: 1e2302a8    	ucvtf	s8, w21
18f5bf340: 91004319    	add	x25, x24, #0x10
18f5bf344: 5280003a    	mov	w26, #0x1               ; =1
18f5bf348: 2a1503f5    	mov	w21, w21
18f5bf34c: eb16013f    	cmp	x9, x22
18f5bf350: 540017c9    	b.ls	0x18f5bf648 <__ZN8DSPGraph6MixBox7processEj+0x394>
18f5bf354: 8b161400    	add	x0, x0, x22, lsl #5
18f5bf358: f9400010    	ldr	x16, [x0]
18f5bf35c: aa0003f1    	mov	x17, x0
18f5bf360: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f5bf364: dac11a30    	autda	x16, x17
18f5bf368: f8428e08    	ldr	x8, [x16, #0x28]!
18f5bf36c: aa1003e9    	mov	x9, x16
18f5bf370: aa0903f1    	mov	x17, x9
18f5bf374: f2e0d291    	movk	x17, #0x694, lsl #48
18f5bf378: d73f0911    	blraa	x8, x17
18f5bf37c: f9401c08    	ldr	x8, [x0, #0x38]
18f5bf380: f9418669    	ldr	x9, [x19, #0x308]
18f5bf384: bc767920    	ldr	s0, [x9, x22, lsl #2]
18f5bf388: bd0027e0    	str	s0, [sp, #0x24]
18f5bf38c: f9419269    	ldr	x9, [x19, #0x320]
18f5bf390: bc767920    	ldr	s0, [x9, x22, lsl #2]
18f5bf394: f940291b    	ldr	x27, [x8, #0x50]
18f5bf398: bd0023e0    	str	s0, [sp, #0x20]
18f5bf39c: a9442668    	ldp	x8, x9, [x19, #0x40]
18f5bf3a0: cb080129    	sub	x9, x9, x8
18f5bf3a4: eb8916df    	cmp	x22, x9, asr #5
18f5bf3a8: 540018a2    	b.hs	0x18f5bf6bc <__ZN8DSPGraph6MixBox7processEj+0x408>
18f5bf3ac: 8b161500    	add	x0, x8, x22, lsl #5
18f5bf3b0: f9400010    	ldr	x16, [x0]
18f5bf3b4: aa0003f1    	mov	x17, x0
18f5bf3b8: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f5bf3bc: dac11a30    	autda	x16, x17
18f5bf3c0: f8428e08    	ldr	x8, [x16, #0x28]!
18f5bf3c4: aa1003e9    	mov	x9, x16
18f5bf3c8: aa0903f1    	mov	x17, x9
18f5bf3cc: f2e0d291    	movk	x17, #0x694, lsl #48
18f5bf3d0: d73f0911    	blraa	x8, x17
18f5bf3d4: f9401c08    	ldr	x8, [x0, #0x38]
18f5bf3d8: 39412108    	ldrb	w8, [x8, #0x48]
18f5bf3dc: 37200a68    	tbnz	w8, #0x4, 0x18f5bf528 <__ZN8DSPGraph6MixBox7processEj+0x274>
18f5bf3e0: bd4027e0    	ldr	s0, [sp, #0x24]
18f5bf3e4: 1e202008    	fcmp	s0, #0.0
18f5bf3e8: 54000a00    	b.eq	0x18f5bf528 <__ZN8DSPGraph6MixBox7processEj+0x274>
18f5bf3ec: bd4023e1    	ldr	s1, [sp, #0x20]
18f5bf3f0: 1e212000    	fcmp	s0, s1
18f5bf3f4: 3600025a    	tbz	w26, #0x0, 0x18f5bf43c <__ZN8DSPGraph6MixBox7processEj+0x188>
18f5bf3f8: 54000481    	b.ne	0x18f5bf488 <__ZN8DSPGraph6MixBox7processEj+0x1d4>
18f5bf3fc: 34000957    	cbz	w23, 0x18f5bf524 <__ZN8DSPGraph6MixBox7processEj+0x270>
18f5bf400: 9100437a    	add	x26, x27, #0x10
18f5bf404: aa1903fb    	mov	x27, x25
18f5bf408: aa1703fc    	mov	x28, x23
18f5bf40c: f8410740    	ldr	x0, [x26], #0x10
18f5bf410: f9400363    	ldr	x3, [x27]
18f5bf414: b81fc374    	stur	w20, [x27, #-0x4]
18f5bf418: 910093e2    	add	x2, sp, #0x24
18f5bf41c: 52800021    	mov	w1, #0x1                ; =1
18f5bf420: 52800024    	mov	w4, #0x1                ; =1
18f5bf424: aa1503e5    	mov	x5, x21
18f5bf428: 9505d486    	bl	0x193734640 <_objc_msgSend$xpcconnection+0x4048b80>
18f5bf42c: 9100437b    	add	x27, x27, #0x10
18f5bf430: f100079c    	subs	x28, x28, #0x1
18f5bf434: 54fffec1    	b.ne	0x18f5bf40c <__ZN8DSPGraph6MixBox7processEj+0x158>
18f5bf438: 1400003b    	b	0x18f5bf524 <__ZN8DSPGraph6MixBox7processEj+0x270>
18f5bf43c: 540004e1    	b.ne	0x18f5bf4d8 <__ZN8DSPGraph6MixBox7processEj+0x224>
18f5bf440: 34000737    	cbz	w23, 0x18f5bf524 <__ZN8DSPGraph6MixBox7processEj+0x270>
18f5bf444: 9100437a    	add	x26, x27, #0x10
18f5bf448: aa1903fb    	mov	x27, x25
18f5bf44c: aa1703fc    	mov	x28, x23
18f5bf450: f8410740    	ldr	x0, [x26], #0x10
18f5bf454: f9400363    	ldr	x3, [x27]
18f5bf458: b81fc374    	stur	w20, [x27, #-0x4]
18f5bf45c: 910093e2    	add	x2, sp, #0x24
18f5bf460: 52800021    	mov	w1, #0x1                ; =1
18f5bf464: 52800024    	mov	w4, #0x1                ; =1
18f5bf468: aa0303e5    	mov	x5, x3
18f5bf46c: 52800026    	mov	w6, #0x1                ; =1
18f5bf470: aa1503e7    	mov	x7, x21
18f5bf474: 9505d46b    	bl	0x193734620 <_objc_msgSend$xpcconnection+0x4048b60>
18f5bf478: 9100437b    	add	x27, x27, #0x10
18f5bf47c: f100079c    	subs	x28, x28, #0x1
18f5bf480: 54fffe81    	b.ne	0x18f5bf450 <__ZN8DSPGraph6MixBox7processEj+0x19c>
18f5bf484: 14000028    	b	0x18f5bf524 <__ZN8DSPGraph6MixBox7processEj+0x270>
18f5bf488: 1e213800    	fsub	s0, s0, s1
18f5bf48c: 1e281800    	fdiv	s0, s0, s8
18f5bf490: bd005be0    	str	s0, [sp, #0x58]
18f5bf494: 34000497    	cbz	w23, 0x18f5bf524 <__ZN8DSPGraph6MixBox7processEj+0x270>
18f5bf498: 9100437a    	add	x26, x27, #0x10
18f5bf49c: aa1903fb    	mov	x27, x25
18f5bf4a0: aa1703fc    	mov	x28, x23
18f5bf4a4: f8410740    	ldr	x0, [x26], #0x10
18f5bf4a8: f9400364    	ldr	x4, [x27]
18f5bf4ac: b81fc374    	stur	w20, [x27, #-0x4]
18f5bf4b0: 910083e2    	add	x2, sp, #0x20
18f5bf4b4: 910163e3    	add	x3, sp, #0x58
18f5bf4b8: 52800021    	mov	w1, #0x1                ; =1
18f5bf4bc: 52800025    	mov	w5, #0x1                ; =1
18f5bf4c0: aa1503e6    	mov	x6, x21
18f5bf4c4: 9505d447    	bl	0x1937345e0 <_objc_msgSend$xpcconnection+0x4048b20>
18f5bf4c8: 9100437b    	add	x27, x27, #0x10
18f5bf4cc: f100079c    	subs	x28, x28, #0x1
18f5bf4d0: 54fffea1    	b.ne	0x18f5bf4a4 <__ZN8DSPGraph6MixBox7processEj+0x1f0>
18f5bf4d4: 14000014    	b	0x18f5bf524 <__ZN8DSPGraph6MixBox7processEj+0x270>
18f5bf4d8: 1e213800    	fsub	s0, s0, s1
18f5bf4dc: 1e281800    	fdiv	s0, s0, s8
18f5bf4e0: bd005be0    	str	s0, [sp, #0x58]
18f5bf4e4: 34000217    	cbz	w23, 0x18f5bf524 <__ZN8DSPGraph6MixBox7processEj+0x270>
18f5bf4e8: 9100437a    	add	x26, x27, #0x10
18f5bf4ec: aa1903fb    	mov	x27, x25
18f5bf4f0: aa1703fc    	mov	x28, x23
18f5bf4f4: f8410740    	ldr	x0, [x26], #0x10
18f5bf4f8: f9400364    	ldr	x4, [x27]
18f5bf4fc: b81fc374    	stur	w20, [x27, #-0x4]
18f5bf500: 910083e2    	add	x2, sp, #0x20
18f5bf504: 910163e3    	add	x3, sp, #0x58
18f5bf508: 52800021    	mov	w1, #0x1                ; =1
18f5bf50c: 52800025    	mov	w5, #0x1                ; =1
18f5bf510: aa1503e6    	mov	x6, x21
18f5bf514: 9505d437    	bl	0x1937345f0 <_objc_msgSend$xpcconnection+0x4048b30>
18f5bf518: 9100437b    	add	x27, x27, #0x10
18f5bf51c: f100079c    	subs	x28, x28, #0x1
18f5bf520: 54fffea1    	b.ne	0x18f5bf4f4 <__ZN8DSPGraph6MixBox7processEj+0x240>
18f5bf524: 5280001a    	mov	w26, #0x0               ; =0
18f5bf528: bd4027e0    	ldr	s0, [sp, #0x24]
18f5bf52c: f9419268    	ldr	x8, [x19, #0x320]
18f5bf530: bc367900    	str	s0, [x8, x22, lsl #2]
18f5bf534: 910006d6    	add	x22, x22, #0x1
18f5bf538: a9442260    	ldp	x0, x8, [x19, #0x40]
18f5bf53c: cb000109    	sub	x9, x8, x0
18f5bf540: 9345fd29    	asr	x9, x9, #5
18f5bf544: eb2942df    	cmp	x22, w9, uxtw
18f5bf548: 54fff023    	b.lo	0x18f5bf34c <__ZN8DSPGraph6MixBox7processEj+0x98>
18f5bf54c: 360001ba    	tbz	w26, #0x0, 0x18f5bf580 <__ZN8DSPGraph6MixBox7processEj+0x2cc>
