
/srv/research/tmp/extracted_dylibs/AudioToolbox:	file format mach-o arm64

Disassembly of section __TEXT,__text:

00000001b8f676a0
1b8f676a0: 528ead88    	mov	w8, #0x756c             ; =30060
1b8f676a4: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f676a8: 6b08003f    	cmp	w1, w8
1b8f676ac: 5400028c    	b.gt	0x1b8f676fc
1b8f676b0: 528cae08    	mov	w8, #0x6570             ; =25968
1b8f676b4: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f676b8: 6b08003f    	cmp	w1, w8
1b8f676bc: 5400046c    	b.gt	0x1b8f67748
1b8f676c0: 528c6c08    	mov	w8, #0x6360             ; =25440
1b8f676c4: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f676c8: 6b08003f    	cmp	w1, w8
1b8f676cc: 5400084c    	b.gt	0x1b8f677d4
1b8f676d0: 528dcda8    	mov	w8, #0x6e6d             ; =28269
1b8f676d4: 72ad8c88    	movk	w8, #0x6c64, lsl #16
1b8f676d8: 6b08003f    	cmp	w1, w8
1b8f676dc: 54000d80    	b.eq	0x1b8f6788c
1b8f676e0: 528d8dc8    	mov	w8, #0x6c6e             ; =27758
1b8f676e4: 72ad8da8    	movk	w8, #0x6c6d, lsl #16
1b8f676e8: 6b08003f    	cmp	w1, w8
1b8f676ec: 54000c61    	b.ne	0x1b8f67878
1b8f676f0: 91076000    	add	x0, x0, #0x1d8
1b8f676f4: b5000ea2    	cbnz	x2, 0x1b8f678c8
1b8f676f8: d65f03c0    	ret
1b8f676fc: 528e6ca8    	mov	w8, #0x7365             ; =29541
1b8f67700: 72ae6e08    	movk	w8, #0x7370, lsl #16
1b8f67704: 6b08003f    	cmp	w1, w8
1b8f67708: 5400042c    	b.gt	0x1b8f6778c
1b8f6770c: 528eada8    	mov	w8, #0x756d             ; =30061
1b8f67710: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67714: 6b08003f    	cmp	w1, w8
1b8f67718: 54000a60    	b.eq	0x1b8f67864
1b8f6771c: 528e6ce8    	mov	w8, #0x7367             ; =29543
1b8f67720: 72ae0de8    	movk	w8, #0x706f, lsl #16
1b8f67724: 6b08003f    	cmp	w1, w8
1b8f67728: 54000940    	b.eq	0x1b8f67850
1b8f6772c: 528cace8    	mov	w8, #0x6567             ; =25959
1b8f67730: 72ae0e48    	movk	w8, #0x7072, lsl #16
1b8f67734: 6b08003f    	cmp	w1, w8
1b8f67738: 54000a01    	b.ne	0x1b8f67878
1b8f6773c: 91046000    	add	x0, x0, #0x118
1b8f67740: b50008c2    	cbnz	x2, 0x1b8f67858
1b8f67744: 17ffffed    	b	0x1b8f676f8
1b8f67748: 528dce28    	mov	w8, #0x6e71             ; =28273
1b8f6774c: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67750: 6b08003f    	cmp	w1, w8
1b8f67754: 540005ac    	b.gt	0x1b8f67808
1b8f67758: 528cae28    	mov	w8, #0x6571             ; =25969
1b8f6775c: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67760: 6b08003f    	cmp	w1, w8
1b8f67764: 540009a0    	b.eq	0x1b8f67898
1b8f67768: 528d8e48    	mov	w8, #0x6c72             ; =27762
1b8f6776c: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67770: 6b08003f    	cmp	w1, w8
1b8f67774: 54000821    	b.ne	0x1b8f67878
1b8f67778: 9105e000    	add	x0, x0, #0x178
1b8f6777c: b4fffbe2    	cbz	x2, 0x1b8f676f8
1b8f67780: 90001028    	adrp	x8, 0x1b916b000
1b8f67784: fd47a100    	ldr	d0, [x8, #0xf40]
1b8f67788: 14000057    	b	0x1b8f678e4
1b8f6778c: 5291fa08    	mov	w8, #0x8fd0             ; =36816
1b8f67790: 72b173c8    	movk	w8, #0x8b9e, lsl #16
1b8f67794: 0b080028    	add	w8, w1, w8
1b8f67798: 7100091f    	cmp	w8, #0x2
1b8f6779c: 54000503    	b.lo	0x1b8f6783c
1b8f677a0: 528e0e88    	mov	w8, #0x7074             ; =28788
1b8f677a4: 72ae8da8    	movk	w8, #0x746d, lsl #16
1b8f677a8: 6b08003f    	cmp	w1, w8
1b8f677ac: 54000940    	b.eq	0x1b8f678d4
1b8f677b0: 528e6cc8    	mov	w8, #0x7366             ; =29542
1b8f677b4: 72ae6e08    	movk	w8, #0x7370, lsl #16
1b8f677b8: 6b08003f    	cmp	w1, w8
1b8f677bc: 540005e1    	b.ne	0x1b8f67878
1b8f677c0: 910a0000    	add	x0, x0, #0x280
1b8f677c4: b4fff9a2    	cbz	x2, 0x1b8f676f8
1b8f677c8: 90001028    	adrp	x8, 0x1b916b000
1b8f677cc: fd47b500    	ldr	d0, [x8, #0xf68]
1b8f677d0: 14000045    	b	0x1b8f678e4
1b8f677d4: 528c6c28    	mov	w8, #0x6361             ; =25441
1b8f677d8: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f677dc: 6b08003f    	cmp	w1, w8
1b8f677e0: 54000660    	b.eq	0x1b8f678ac
1b8f677e4: 528c8da8    	mov	w8, #0x646d             ; =25709
1b8f677e8: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f677ec: 6b08003f    	cmp	w1, w8
1b8f677f0: 54000441    	b.ne	0x1b8f67878
1b8f677f4: 9102c000    	add	x0, x0, #0xb0
1b8f677f8: b4fff802    	cbz	x2, 0x1b8f676f8
1b8f677fc: 90001028    	adrp	x8, 0x1b916b000
1b8f67800: fd47a900    	ldr	d0, [x8, #0xf50]
1b8f67804: 14000038    	b	0x1b8f678e4
1b8f67808: 528dce48    	mov	w8, #0x6e72             ; =28274
1b8f6780c: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67810: 6b08003f    	cmp	w1, w8
1b8f67814: 54000560    	b.eq	0x1b8f678c0
1b8f67818: 528e6e08    	mov	w8, #0x7370             ; =29552
1b8f6781c: 72ae0be8    	movk	w8, #0x705f, lsl #16
1b8f67820: 6b08003f    	cmp	w1, w8
1b8f67824: 540002a1    	b.ne	0x1b8f67878
1b8f67828: 91038000    	add	x0, x0, #0xe0
1b8f6782c: b4fff662    	cbz	x2, 0x1b8f676f8
1b8f67830: 90001028    	adrp	x8, 0x1b916b000
1b8f67834: fd47a500    	ldr	d0, [x8, #0xf48]
1b8f67838: 1400002b    	b	0x1b8f678e4
1b8f6783c: 910ca000    	add	x0, x0, #0x328
1b8f67840: b4fff5c2    	cbz	x2, 0x1b8f676f8
1b8f67844: 90001028    	adrp	x8, 0x1b916b000
1b8f67848: fd479100    	ldr	d0, [x8, #0xf20]
1b8f6784c: 14000026    	b	0x1b8f678e4
1b8f67850: 9106a000    	add	x0, x0, #0x1a8
1b8f67854: b4fff522    	cbz	x2, 0x1b8f676f8
1b8f67858: 90001028    	adrp	x8, 0x1b916b000
1b8f6785c: fd479d00    	ldr	d0, [x8, #0xf38]
1b8f67860: 14000021    	b	0x1b8f678e4
1b8f67864: 91020000    	add	x0, x0, #0x80
1b8f67868: b4fff482    	cbz	x2, 0x1b8f676f8
1b8f6786c: 90001028    	adrp	x8, 0x1b916b000
1b8f67870: fd47ad00    	ldr	d0, [x8, #0xf58]
1b8f67874: 1400001c    	b	0x1b8f678e4
1b8f67878: d2800000    	mov	x0, #0x0                ; =0
1b8f6787c: b4fff3e2    	cbz	x2, 0x1b8f676f8
1b8f67880: a9007c5f    	stp	xzr, xzr, [x2]
1b8f67884: b900105f    	str	wzr, [x2, #0x10]
1b8f67888: d65f03c0    	ret
1b8f6788c: 91008000    	add	x0, x0, #0x20
1b8f67890: b50001c2    	cbnz	x2, 0x1b8f678c8
1b8f67894: 17ffff99    	b	0x1b8f676f8
1b8f67898: 91014000    	add	x0, x0, #0x50
1b8f6789c: b4fff2e2    	cbz	x2, 0x1b8f676f8
1b8f678a0: 90001028    	adrp	x8, 0x1b916b000
1b8f678a4: fd47b100    	ldr	d0, [x8, #0xf60]
1b8f678a8: 1400000f    	b	0x1b8f678e4
1b8f678ac: 91082000    	add	x0, x0, #0x208
1b8f678b0: b4fff242    	cbz	x2, 0x1b8f676f8
1b8f678b4: 90001028    	adrp	x8, 0x1b916b000
1b8f678b8: fd479900    	ldr	d0, [x8, #0xf30]
1b8f678bc: 1400000a    	b	0x1b8f678e4
1b8f678c0: 91052000    	add	x0, x0, #0x148
1b8f678c4: b4fff1a2    	cbz	x2, 0x1b8f676f8
1b8f678c8: 90001028    	adrp	x8, 0x1b916b000
1b8f678cc: fd475d00    	ldr	d0, [x8, #0xeb8]
1b8f678d0: 14000005    	b	0x1b8f678e4
1b8f678d4: 9115a000    	add	x0, x0, #0x568
1b8f678d8: b4fff102    	cbz	x2, 0x1b8f676f8
1b8f678dc: 90001028    	adrp	x8, 0x1b916b000
1b8f678e0: fd479500    	ldr	d0, [x8, #0xf28]
1b8f678e4: fd000040    	str	d0, [x2]
1b8f678e8: 528e0d88    	mov	w8, #0x706c             ; =28780
1b8f678ec: 72ac2e08    	movk	w8, #0x6170, lsl #16
1b8f678f0: 2901fc5f    	stp	wzr, wzr, [x2, #0xc]
1b8f678f4: b9000848    	str	w8, [x2, #0x8]
1b8f678f8: d65f03c0    	ret
