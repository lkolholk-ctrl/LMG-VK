
/srv/research/tmp/extracted_dylibs/AudioToolboxCore:	file format mach-o arm64

Disassembly of section __TEXT,__text:

000000018f5c3d6c <__ZN8DSPGraph7GainBoxC2Ev>:
18f5c3d6c: d503237f    	pacibsp
18f5c3d70: a9bf7bfd    	stp	x29, x30, [sp, #-0x10]!
18f5c3d74: 910003fd    	mov	x29, sp
18f5c3d78: 52800021    	mov	w1, #0x1                ; =1
18f5c3d7c: 52800022    	mov	w2, #0x1                ; =1
18f5c3d80: 97f78d1c    	bl	0x18f3a71f0 <__ZN8DSPGraph3BoxC2Ejj>
18f5c3d84: 52a7f008    	mov	w8, #0x3f800000         ; =1065353216
18f5c3d88: b9030808    	str	w8, [x0, #0x308]
18f5c3d8c: 390c301f    	strb	wzr, [x0, #0x30c]
18f5c3d90: f0000468    	adrp	x8, 0x18f652000 <-[CarbonComponentScannerXPCClient init]+0xda74>
18f5c3d94: 3dc2d900    	ldr	q0, [x8, #0xb60]
18f5c3d98: 3d80c400    	str	q0, [x0, #0x310]
18f5c3d9c: d0000468    	adrp	x8, 0x18f651000 <__OBJC_$_INSTANCE_METHODS_ExportedMessageChannel+0x8>
18f5c3da0: fd465d00    	ldr	d0, [x8, #0xcb8]
18f5c3da4: fd019000    	str	d0, [x0, #0x320]
18f5c3da8: b903281f    	str	wzr, [x0, #0x328]
18f5c3dac: 52802028    	mov	w8, #0x101              ; =257
18f5c3db0: 79065808    	strh	w8, [x0, #0x32c]
18f5c3db4: 90307f50    	adrp	x16, 0x1f05ab000 <__ZTV35MP4BoxParser_SampleToGroupRunLength+0x28>
18f5c3db8: 91104210    	add	x16, x16, #0x410
18f5c3dbc: 91004210    	add	x16, x16, #0x10
18f5c3dc0: aa0003f1    	mov	x17, x0
18f5c3dc4: f2e3f251    	movk	x17, #0x1f92, lsl #48
18f5c3dc8: dac10a30    	pacda	x16, x17
18f5c3dcc: f9000010    	str	x16, [x0]
18f5c3dd0: a8c17bfd    	ldp	x29, x30, [sp], #0x10
18f5c3dd4: d65f0fff    	retab
