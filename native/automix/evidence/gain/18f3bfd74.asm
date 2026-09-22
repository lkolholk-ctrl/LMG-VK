
/srv/research/tmp/extracted_dylibs/AudioToolboxCore:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000018f3bfd74 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj>:
18f3bfd74: d503237f    	pacibsp
18f3bfd78: d103c3ff    	sub	sp, sp, #0xf0
18f3bfd7c: 6d0823e9    	stp	d9, d8, [sp, #0x80]
18f3bfd80: a9096ffc    	stp	x28, x27, [sp, #0x90]
18f3bfd84: a90a67fa    	stp	x26, x25, [sp, #0xa0]
18f3bfd88: a90b5ff8    	stp	x24, x23, [sp, #0xb0]
18f3bfd8c: a90c57f6    	stp	x22, x21, [sp, #0xc0]
18f3bfd90: a90d4ff4    	stp	x20, x19, [sp, #0xd0]
18f3bfd94: a90e7bfd    	stp	x29, x30, [sp, #0xe0]
18f3bfd98: 910383fd    	add	x29, sp, #0xe0
18f3bfd9c: aa0003fb    	mov	x27, x0
18f3bfda0: a9442000    	ldp	x0, x8, [x0, #0x40]
18f3bfda4: eb00011f    	cmp	x8, x0
18f3bfda8: 540029a0    	b.eq	0x18f3c02dc <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x568>
18f3bfdac: aa0103f6    	mov	x22, x1
18f3bfdb0: f9400010    	ldr	x16, [x0]
18f3bfdb4: aa0003f1    	mov	x17, x0
18f3bfdb8: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f3bfdbc: dac11a30    	autda	x16, x17
18f3bfdc0: f8428e08    	ldr	x8, [x16, #0x28]!
18f3bfdc4: aa1003e9    	mov	x9, x16
18f3bfdc8: aa0903f1    	mov	x17, x9
18f3bfdcc: f2e0d291    	movk	x17, #0x694, lsl #48
18f3bfdd0: d73f0911    	blraa	x8, x17
18f3bfdd4: aa0003e8    	mov	x8, x0
18f3bfdd8: a945a760    	ldp	x0, x9, [x27, #0x58]
18f3bfddc: eb00013f    	cmp	x9, x0
18f3bfde0: 54002b80    	b.eq	0x18f3c0350 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x5dc>
18f3bfde4: f9401d08    	ldr	x8, [x8, #0x38]
18f3bfde8: f9402918    	ldr	x24, [x8, #0x50]
18f3bfdec: f9400010    	ldr	x16, [x0]
18f3bfdf0: aa0003f1    	mov	x17, x0
18f3bfdf4: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f3bfdf8: dac11a30    	autda	x16, x17
18f3bfdfc: f8428e08    	ldr	x8, [x16, #0x28]!
18f3bfe00: aa1003e9    	mov	x9, x16
18f3bfe04: aa0903f1    	mov	x17, x9
18f3bfe08: f2e0d291    	movk	x17, #0x694, lsl #48
18f3bfe0c: d73f0911    	blraa	x8, x17
18f3bfe10: aa0003e8    	mov	x8, x0
18f3bfe14: a945a760    	ldp	x0, x9, [x27, #0x58]
18f3bfe18: eb00013f    	cmp	x9, x0
18f3bfe1c: 54002d40    	b.eq	0x18f3c03c4 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x650>
18f3bfe20: f9401d08    	ldr	x8, [x8, #0x38]
18f3bfe24: f9402919    	ldr	x25, [x8, #0x50]
18f3bfe28: f9400010    	ldr	x16, [x0]
18f3bfe2c: aa0003f1    	mov	x17, x0
18f3bfe30: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f3bfe34: dac11a30    	autda	x16, x17
18f3bfe38: f8428e08    	ldr	x8, [x16, #0x28]!
18f3bfe3c: aa1003e9    	mov	x9, x16
18f3bfe40: aa0903f1    	mov	x17, x9
18f3bfe44: f2e0d291    	movk	x17, #0x694, lsl #48
18f3bfe48: d73f0911    	blraa	x8, x17
18f3bfe4c: f9403c08    	ldr	x8, [x0, #0x78]
18f3bfe50: b9400909    	ldr	w9, [x8, #0x8]
18f3bfe54: 528cae28    	mov	w8, #0x6571             ; =25969
18f3bfe58: 72acce48    	movk	w8, #0x6672, lsl #16
18f3bfe5c: b90037e9    	str	w9, [sp, #0x34]
18f3bfe60: 6b08013f    	cmp	w9, w8
18f3bfe64: 1a9f17e8    	cset	w8, eq
18f3bfe68: b940031c    	ldr	w28, [x24]
18f3bfe6c: 394cb369    	ldrb	w9, [x27, #0x32c]
18f3bfe70: 7100053f    	cmp	w9, #0x1
18f3bfe74: 54000041    	b.ne	0x18f3bfe7c <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x108>
18f3bfe78: 390cb37f    	strb	wzr, [x27, #0x32c]
18f3bfe7c: 394cb769    	ldrb	w9, [x27, #0x32d]
18f3bfe80: 7100053f    	cmp	w9, #0x1
18f3bfe84: 54000121    	b.ne	0x18f3bfea8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x134>
18f3bfe88: bd430b60    	ldr	s0, [x27, #0x308]
18f3bfe8c: 394c3369    	ldrb	w9, [x27, #0x30c]
18f3bfe90: 7100013f    	cmp	w9, #0x0
18f3bfe94: 1e2e1001    	fmov	s1, #1.00000000
18f3bfe98: 2f00e402    	movi	d2, #0000000000000000
18f3bfe9c: 1e211c41    	fcsel	s1, s2, s1, ne
18f3bfea0: 1e210800    	fmul	s0, s0, s1
18f3bfea4: bd032b60    	str	s0, [x27, #0x328]
18f3bfea8: a9442760    	ldp	x0, x9, [x27, #0x40]
18f3bfeac: eb00013f    	cmp	x9, x0
18f3bfeb0: 54002c40    	b.eq	0x18f3c0438 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x6c4>
18f3bfeb4: d37e7ec9    	ubfiz	x9, x22, #2, #32
18f3bfeb8: 9ac82137    	lsl	x23, x9, x8
18f3bfebc: f9400010    	ldr	x16, [x0]
18f3bfec0: aa0003f1    	mov	x17, x0
18f3bfec4: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f3bfec8: dac11a30    	autda	x16, x17
18f3bfecc: f8428e08    	ldr	x8, [x16, #0x28]!
18f3bfed0: aa1003e9    	mov	x9, x16
18f3bfed4: aa0903f1    	mov	x17, x9
18f3bfed8: f2e0d291    	movk	x17, #0x694, lsl #48
18f3bfedc: d73f0911    	blraa	x8, x17
18f3bfee0: f9401c08    	ldr	x8, [x0, #0x38]
18f3bfee4: 39412108    	ldrb	w8, [x8, #0x48]
18f3bfee8: 372004a8    	tbnz	w8, #0x4, 0x18f3bff7c <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x208>
18f3bfeec: 2a1603f4    	mov	w20, w22
18f3bfef0: bd432760    	ldr	s0, [x27, #0x324]
18f3bfef4: 910ca375    	add	x21, x27, #0x328
18f3bfef8: bd432b61    	ldr	s1, [x27, #0x328]
18f3bfefc: 1e212000    	fcmp	s0, s1
18f3bff00: 54000521    	b.ne	0x18f3bffa4 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x230>
18f3bff04: 340014bc    	cbz	w28, 0x18f3c0198 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x424>
18f3bff08: 91004333    	add	x19, x25, #0x10
18f3bff0c: 91004319    	add	x25, x24, #0x10
18f3bff10: 528cae3a    	mov	w26, #0x6571            ; =25969
18f3bff14: 72acce5a    	movk	w26, #0x6672, lsl #16
18f3bff18: f9400336    	ldr	x22, [x25]
18f3bff1c: f9400278    	ldr	x24, [x19]
18f3bff20: aa1603e0    	mov	x0, x22
18f3bff24: 52800021    	mov	w1, #0x1                ; =1
18f3bff28: aa1503e2    	mov	x2, x21
18f3bff2c: aa1803e3    	mov	x3, x24
18f3bff30: 52800024    	mov	w4, #0x1                ; =1
18f3bff34: aa1403e5    	mov	x5, x20
18f3bff38: 950dd1c2    	bl	0x193734640 <_objc_msgSend$xpcconnection+0x4048b80>
18f3bff3c: b94037e8    	ldr	w8, [sp, #0x34]
18f3bff40: 6b1a011f    	cmp	w8, w26
18f3bff44: 54000101    	b.ne	0x18f3bff64 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x1f0>
18f3bff48: 8b140ac0    	add	x0, x22, x20, lsl #2
18f3bff4c: 8b140b03    	add	x3, x24, x20, lsl #2
18f3bff50: 52800021    	mov	w1, #0x1                ; =1
18f3bff54: aa1503e2    	mov	x2, x21
18f3bff58: 52800024    	mov	w4, #0x1                ; =1
18f3bff5c: aa1403e5    	mov	x5, x20
18f3bff60: 950dd1b8    	bl	0x193734640 <_objc_msgSend$xpcconnection+0x4048b80>
18f3bff64: b81fc277    	stur	w23, [x19, #-0x4]
18f3bff68: 91004273    	add	x19, x19, #0x10
18f3bff6c: 91004339    	add	x25, x25, #0x10
18f3bff70: f100079c    	subs	x28, x28, #0x1
18f3bff74: 54fffd21    	b.ne	0x18f3bff18 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x1a4>
18f3bff78: 14000088    	b	0x18f3c0198 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x424>
18f3bff7c: 340010fc    	cbz	w28, 0x18f3c0198 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x424>
18f3bff80: 91004333    	add	x19, x25, #0x10
18f3bff84: f9400260    	ldr	x0, [x19]
18f3bff88: aa1703e1    	mov	x1, x23
18f3bff8c: 950dcce5    	bl	0x193733320 <_objc_msgSend$xpcconnection+0x4047860>
18f3bff90: b81fc277    	stur	w23, [x19, #-0x4]
18f3bff94: 91004273    	add	x19, x19, #0x10
18f3bff98: f100079c    	subs	x28, x28, #0x1
18f3bff9c: 54ffff41    	b.ne	0x18f3bff84 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x210>
18f3bffa0: 1400007e    	b	0x18f3c0198 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x424>
18f3bffa4: 394cb768    	ldrb	w8, [x27, #0x32d]
18f3bffa8: 360001a8    	tbz	w8, #0x0, 0x18f3bffdc <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x268>
18f3bffac: 1e203828    	fsub	s8, s1, s0
18f3bffb0: fd418b69    	ldr	d9, [x27, #0x310]
18f3bffb4: aa1b03e0    	mov	x0, x27
18f3bffb8: 94080b98    	bl	0x18f5c2e18 <__ZNK8DSPGraph3Box10sampleRateEv>
18f3bffbc: 1e630000    	ucvtf	d0, w0
18f3bffc0: 1e600920    	fmul	d0, d9, d0
18f3bffc4: 1e790008    	fcvtzu	w8, d0
18f3bffc8: 1e230100    	ucvtf	s0, w8
18f3bffcc: 1e201901    	fdiv	s1, s8, s0
18f3bffd0: bd032361    	str	s1, [x27, #0x320]
18f3bffd4: bd432760    	ldr	s0, [x27, #0x324]
18f3bffd8: 14000002    	b	0x18f3bffe0 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x26c>
18f3bffdc: bd432361    	ldr	s1, [x27, #0x320]
18f3bffe0: 390cb77f    	strb	wzr, [x27, #0x32d]
18f3bffe4: 1e2302c2    	ucvtf	s2, w22
18f3bffe8: 1f020028    	fmadd	s8, s1, s2, s0
18f3bffec: 1e202028    	fcmp	s1, #0.0
18f3bfff0: 5400008d    	b.le	0x18f3c0000 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x28c>
18f3bfff4: bd4002a2    	ldr	s2, [x21]
18f3bfff8: 1e222100    	fcmp	s8, s2
18f3bfffc: 540000cc    	b.gt	0x18f3c0014 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x2a0>
18f3c0000: 1e202028    	fcmp	s1, #0.0
18f3c0004: 54000805    	b.pl	0x18f3c0104 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x390>
18f3c0008: bd4002a2    	ldr	s2, [x21]
18f3c000c: 1e222100    	fcmp	s8, s2
18f3c0010: 540007a5    	b.pl	0x18f3c0104 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x390>
18f3c0014: 34000bfc    	cbz	w28, 0x18f3c0190 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x41c>
18f3c0018: 1e203840    	fsub	s0, s2, s0
18f3c001c: 1e211800    	fdiv	s0, s0, s1
18f3c0020: 1e20c000    	fabs	s0, s0
18f3c0024: 1e390017    	fcvtzu	w23, s0
18f3c0028: 4b1702c8    	sub	w8, w22, w23
18f3c002c: a9026fe8    	stp	x8, x27, [sp, #0x20]
18f3c0030: 91004336    	add	x22, x25, #0x10
18f3c0034: 91004313    	add	x19, x24, #0x10
18f3c0038: f9400278    	ldr	x24, [x19]
18f3c003c: f94002d9    	ldr	x25, [x22]
18f3c0040: bd432760    	ldr	s0, [x27, #0x324]
18f3c0044: bd006be0    	str	s0, [sp, #0x68]
18f3c0048: 9101a3e2    	add	x2, sp, #0x68
18f3c004c: 910c8363    	add	x3, x27, #0x320
18f3c0050: aa1803e0    	mov	x0, x24
18f3c0054: 52800021    	mov	w1, #0x1                ; =1
18f3c0058: aa1903e4    	mov	x4, x25
18f3c005c: 52800025    	mov	w5, #0x1                ; =1
18f3c0060: aa1703e6    	mov	x6, x23
18f3c0064: 950dd15f    	bl	0x1937345e0 <_objc_msgSend$xpcconnection+0x4048b20>
18f3c0068: 8b170b1a    	add	x26, x24, x23, lsl #2
18f3c006c: 8b170b3b    	add	x27, x25, x23, lsl #2
18f3c0070: aa1a03e0    	mov	x0, x26
18f3c0074: 52800021    	mov	w1, #0x1                ; =1
18f3c0078: aa1503e2    	mov	x2, x21
18f3c007c: aa1b03e3    	mov	x3, x27
18f3c0080: 52800024    	mov	w4, #0x1                ; =1
18f3c0084: f94013e5    	ldr	x5, [sp, #0x20]
18f3c0088: 950dd16e    	bl	0x193734640 <_objc_msgSend$xpcconnection+0x4048b80>
18f3c008c: b94037e8    	ldr	w8, [sp, #0x34]
18f3c0090: 528cae29    	mov	w9, #0x6571             ; =25969
18f3c0094: 72acce49    	movk	w9, #0x6672, lsl #16
18f3c0098: 6b09011f    	cmp	w8, w9
18f3c009c: 54000261    	b.ne	0x18f3c00e8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x374>
18f3c00a0: f94017e8    	ldr	x8, [sp, #0x28]
18f3c00a4: bd432500    	ldr	s0, [x8, #0x324]
18f3c00a8: bd006be0    	str	s0, [sp, #0x68]
18f3c00ac: 8b140b00    	add	x0, x24, x20, lsl #2
18f3c00b0: 8b140b24    	add	x4, x25, x20, lsl #2
18f3c00b4: 9101a3e2    	add	x2, sp, #0x68
18f3c00b8: 910c8103    	add	x3, x8, #0x320
18f3c00bc: 52800021    	mov	w1, #0x1                ; =1
18f3c00c0: 52800025    	mov	w5, #0x1                ; =1
18f3c00c4: aa1703e6    	mov	x6, x23
18f3c00c8: 950dd146    	bl	0x1937345e0 <_objc_msgSend$xpcconnection+0x4048b20>
18f3c00cc: 8b140b40    	add	x0, x26, x20, lsl #2
18f3c00d0: 8b140b63    	add	x3, x27, x20, lsl #2
18f3c00d4: 52800021    	mov	w1, #0x1                ; =1
18f3c00d8: aa1503e2    	mov	x2, x21
18f3c00dc: 52800024    	mov	w4, #0x1                ; =1
18f3c00e0: f94013e5    	ldr	x5, [sp, #0x20]
18f3c00e4: 950dd157    	bl	0x193734640 <_objc_msgSend$xpcconnection+0x4048b80>
18f3c00e8: 910042d6    	add	x22, x22, #0x10
18f3c00ec: 91004273    	add	x19, x19, #0x10
18f3c00f0: f100079c    	subs	x28, x28, #0x1
18f3c00f4: f94017fb    	ldr	x27, [sp, #0x28]
18f3c00f8: 54fffa01    	b.ne	0x18f3c0038 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x2c4>
18f3c00fc: bd4002a8    	ldr	s8, [x21]
18f3c0100: 14000025    	b	0x18f3c0194 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x420>
18f3c0104: 3400049c    	cbz	w28, 0x18f3c0194 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x420>
18f3c0108: 91004333    	add	x19, x25, #0x10
18f3c010c: 91004317    	add	x23, x24, #0x10
18f3c0110: 528cae38    	mov	w24, #0x6571            ; =25969
18f3c0114: 72acce58    	movk	w24, #0x6672, lsl #16
18f3c0118: f94002f5    	ldr	x21, [x23]
18f3c011c: f9400276    	ldr	x22, [x19]
18f3c0120: bd432760    	ldr	s0, [x27, #0x324]
18f3c0124: bd006be0    	str	s0, [sp, #0x68]
18f3c0128: 9101a3e2    	add	x2, sp, #0x68
18f3c012c: 910c8363    	add	x3, x27, #0x320
18f3c0130: aa1503e0    	mov	x0, x21
18f3c0134: 52800021    	mov	w1, #0x1                ; =1
18f3c0138: aa1603e4    	mov	x4, x22
18f3c013c: 52800025    	mov	w5, #0x1                ; =1
18f3c0140: aa1403e6    	mov	x6, x20
18f3c0144: 950dd127    	bl	0x1937345e0 <_objc_msgSend$xpcconnection+0x4048b20>
18f3c0148: b94037e8    	ldr	w8, [sp, #0x34]
18f3c014c: 6b18011f    	cmp	w8, w24
18f3c0150: 54000161    	b.ne	0x18f3c017c <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x408>
18f3c0154: bd432760    	ldr	s0, [x27, #0x324]
18f3c0158: bd006be0    	str	s0, [sp, #0x68]
18f3c015c: 8b140aa0    	add	x0, x21, x20, lsl #2
18f3c0160: 8b140ac4    	add	x4, x22, x20, lsl #2
18f3c0164: 9101a3e2    	add	x2, sp, #0x68
18f3c0168: 910c8363    	add	x3, x27, #0x320
18f3c016c: 52800021    	mov	w1, #0x1                ; =1
18f3c0170: 52800025    	mov	w5, #0x1                ; =1
18f3c0174: aa1403e6    	mov	x6, x20
18f3c0178: 950dd11a    	bl	0x1937345e0 <_objc_msgSend$xpcconnection+0x4048b20>
18f3c017c: 91004273    	add	x19, x19, #0x10
18f3c0180: 910042f7    	add	x23, x23, #0x10
18f3c0184: f100079c    	subs	x28, x28, #0x1
18f3c0188: 54fffc81    	b.ne	0x18f3c0118 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x3a4>
18f3c018c: 14000002    	b	0x18f3c0194 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x420>
18f3c0190: 1e204048    	fmov	s8, s2
18f3c0194: bd032768    	str	s8, [x27, #0x324]
18f3c0198: 394cb768    	ldrb	w8, [x27, #0x32d]
18f3c019c: 7100051f    	cmp	w8, #0x1
18f3c01a0: 540000c1    	b.ne	0x18f3c01b8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x444>
18f3c01a4: bd432b60    	ldr	s0, [x27, #0x328]
18f3c01a8: 1e202008    	fcmp	s0, #0.0
18f3c01ac: 1a9f17e8    	cset	w8, eq
18f3c01b0: 531c6d13    	lsl	w19, w8, #4
18f3c01b4: 14000002    	b	0x18f3c01bc <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x448>
18f3c01b8: 52800013    	mov	w19, #0x0               ; =0
18f3c01bc: a9442360    	ldp	x0, x8, [x27, #0x40]
18f3c01c0: eb00011f    	cmp	x8, x0
18f3c01c4: 54001740    	b.eq	0x18f3c04ac <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x738>
18f3c01c8: f9400010    	ldr	x16, [x0]
18f3c01cc: aa0003f1    	mov	x17, x0
18f3c01d0: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f3c01d4: dac11a30    	autda	x16, x17
18f3c01d8: f8428e08    	ldr	x8, [x16, #0x28]!
18f3c01dc: aa1003e9    	mov	x9, x16
18f3c01e0: aa0903f1    	mov	x17, x9
18f3c01e4: f2e0d291    	movk	x17, #0x694, lsl #48
18f3c01e8: d73f0911    	blraa	x8, x17
18f3c01ec: aa0003e8    	mov	x8, x0
18f3c01f0: a9442760    	ldp	x0, x9, [x27, #0x40]
18f3c01f4: eb00013f    	cmp	x9, x0
18f3c01f8: 54001940    	b.eq	0x18f3c0520 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x7ac>
18f3c01fc: f9401d08    	ldr	x8, [x8, #0x38]
18f3c0200: b9404916    	ldr	w22, [x8, #0x48]
18f3c0204: f9400010    	ldr	x16, [x0]
18f3c0208: aa0003f1    	mov	x17, x0
18f3c020c: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f3c0210: dac11a30    	autda	x16, x17
18f3c0214: f8428e08    	ldr	x8, [x16, #0x28]!
18f3c0218: aa1003e9    	mov	x9, x16
18f3c021c: aa0903f1    	mov	x17, x9
18f3c0220: f2e0d291    	movk	x17, #0x694, lsl #48
18f3c0224: d73f0911    	blraa	x8, x17
18f3c0228: a945d774    	ldp	x20, x21, [x27, #0x58]
18f3c022c: eb15029f    	cmp	x20, x21
18f3c0230: 54000380    	b.eq	0x18f3c02a0 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x52c>
18f3c0234: 2a1302d3    	orr	w19, w22, w19
18f3c0238: f9401c16    	ldr	x22, [x0, #0x38]
18f3c023c: aa1403e0    	mov	x0, x20
18f3c0240: aa1403f7    	mov	x23, x20
18f3c0244: f84206f0    	ldr	x16, [x23], #0x20
18f3c0248: aa1403f1    	mov	x17, x20
18f3c024c: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f3c0250: dac11a30    	autda	x16, x17
18f3c0254: f8428e08    	ldr	x8, [x16, #0x28]!
18f3c0258: aa1003e9    	mov	x9, x16
18f3c025c: aa0903f1    	mov	x17, x9
18f3c0260: f2e0d291    	movk	x17, #0x694, lsl #48
18f3c0264: d73f0911    	blraa	x8, x17
18f3c0268: f9401c08    	ldr	x8, [x0, #0x38]
18f3c026c: 3cc382c0    	ldur	q0, [x22, #0x38]
18f3c0270: 3cc282c1    	ldur	q1, [x22, #0x28]
18f3c0274: 3cc182c2    	ldur	q2, [x22, #0x18]
18f3c0278: 3cc082c3    	ldur	q3, [x22, #0x8]
18f3c027c: 3c808103    	stur	q3, [x8, #0x8]
18f3c0280: 3c818102    	stur	q2, [x8, #0x18]
18f3c0284: 3c828101    	stur	q1, [x8, #0x28]
18f3c0288: 3c838100    	stur	q0, [x8, #0x38]
18f3c028c: b9004913    	str	w19, [x8, #0x48]
18f3c0290: 91008294    	add	x20, x20, #0x20
18f3c0294: aa1703e0    	mov	x0, x23
18f3c0298: eb1502ff    	cmp	x23, x21
18f3c029c: 54fffd41    	b.ne	0x18f3c0244 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x4d0>
18f3c02a0: bd432760    	ldr	s0, [x27, #0x324]
18f3c02a4: bd432b61    	ldr	s1, [x27, #0x328]
18f3c02a8: 1e212000    	fcmp	s0, s1
18f3c02ac: 54000061    	b.ne	0x18f3c02b8 <__ZN8DSPGraph14GenericGainBoxINS_16LinearGainPolicyEE7processEj+0x544>
18f3c02b0: 52800028    	mov	w8, #0x1                ; =1
18f3c02b4: 390cb768    	strb	w8, [x27, #0x32d]
18f3c02b8: a94e7bfd    	ldp	x29, x30, [sp, #0xe0]
18f3c02bc: a94d4ff4    	ldp	x20, x19, [sp, #0xd0]
18f3c02c0: a94c57f6    	ldp	x22, x21, [sp, #0xc0]
18f3c02c4: a94b5ff8    	ldp	x24, x23, [sp, #0xb0]
18f3c02c8: a94a67fa    	ldp	x26, x25, [sp, #0xa0]
18f3c02cc: a9496ffc    	ldp	x28, x27, [sp, #0x90]
18f3c02d0: 6d4823e9    	ldp	d9, d8, [sp, #0x80]
18f3c02d4: 9103c3ff    	add	sp, sp, #0xf0
18f3c02d8: d65f0fff    	retab
