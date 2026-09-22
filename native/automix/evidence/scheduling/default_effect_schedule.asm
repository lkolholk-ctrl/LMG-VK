; Binary: _SonicKit_MusicKit
; Address: 0x2721af178
; Symbol: FUN_2721af178
; Signature: undefined FUN_2721af178()
2721af178: pacibsp
2721af17c: stp d9,d8,[sp, #-0x70]!
2721af180: stp x28,x27,[sp, #0x10]
2721af184: stp x26,x25,[sp, #0x20]
2721af188: stp x24,x23,[sp, #0x30]
2721af18c: stp x22,x21,[sp, #0x40]
2721af190: stp x20,x19,[sp, #0x50]
2721af194: stp x29,x30,[sp, #0x60]
2721af198: add x29,sp,#0x60
2721af19c: sub sp,sp,#0x70
2721af1a0: mov x0,#0x0
2721af1a4: bl 0x2743dabd0
2721af1a8: mov x19,x0
2721af1ac: mov x9,x0
2721af1b0: ldr x8,[x9, #-0x8]!
2721af1b4: mov x16,x8
2721af1b8: mov x17,x9
2721af1bc: movk x17,#0x2e3f, LSL #48
2721af1c0: autda x16,x17
2721af1c4: mov x17,x16
2721af1c8: xpacd x17
2721af1cc: cmp x16,x17
2721af1d0: b.eq 0x2721af1d8
2721af1d4: brk #0xc472
2721af1d8: mov x21,x16
2721af1dc: mov x16,x8
2721af1e0: mov x17,x9
2721af1e4: movk x17,#0x2e3f, LSL #48
2721af1e8: autda x16,x17
2721af1ec: ldr x8,[x16, #0x40]
2721af1f0: mov x9,x8
2721af1f4: adrp x17,0x2824d0000
2721af1f8: add x17,x17,#0xf28
2721af1fc: ldr x16,[x17]
2721af200: blraa x16,x17
2721af204: mov x9,sp
2721af208: add x10,x8,#0xf
2721af20c: and x12,x10,#-0x10
2721af210: sub x22,x9,x12
2721af214: mov sp,x22
2721af218: mov x9,x8
2721af21c: adrp x17,0x2824d0000
2721af220: add x17,x17,#0xf28
2721af224: ldr x16,[x17]
2721af228: blraa x16,x17
2721af22c: mov x8,sp
2721af230: sub x23,x8,x12
2721af234: mov sp,x23
2721af238: mov x0,#0x0
2721af23c: bl 0x2720f6f68
2721af240: mov x24,x0
2721af244: adrp x1,0x280c81000
2721af248: add x1,x1,#0x3d8
2721af24c: adrp x16,0x2780e2000
2721af250: ldr x16,[x16, #0x188]
2721af254: mov x17,#0xb4e8
2721af258: pacia x16,x17
2721af25c: mov x2,x16
2721af260: mov x0,#0x0
2721af264: bl 0x2721af988
2721af268: mov x20,x0
2721af26c: adrp x0,0x280c81000
2721af270: add x0,x0,#0x3e0
2721af274: adrp x16,0x2720f6000
2721af278: add x16,x16,#0xf68
2721af27c: mov x17,#0x7099
2721af280: pacia x16,x17
2721af284: mov x1,x16
2721af288: adrp x16,0x2780e3000
2721af28c: ldr x16,[x16, #0xde8]
2721af290: mov x17,#0xc6eb
2721af294: pacda x16,x17
2721af298: mov x2,x16
2721af29c: bl 0x2721af72c
2721af2a0: mov x3,x0
2721af2a4: adrp x0,0x2780e3000
2721af2a8: ldr x0,[x0, #0xc50]
2721af2ac: mov x1,x24
2721af2b0: mov x2,x20
2721af2b4: bl 0x2743dbcc0
2721af2b8: stur x0,[x29, #-0x90]
2721af2bc: bl 0x2743dabc0
2721af2c0: mov x25,x0
2721af2c4: ldr x8,[x0, #0x10]
2721af2c8: stur x8,[x29, #-0xa8]
2721af2cc: cbz x8,0x2721af560
2721af2d0: stp x24,x25,[x29, #-0xc8]
2721af2d4: stur xzr,[x29, #-0x98]
2721af2d8: mov x27,#0x0
2721af2dc: ldrb w8,[x21, #0x50]
2721af2e0: add x9,x8,#0x20
2721af2e4: bic x8,x9,x8
2721af2e8: add x8,x25,x8
2721af2ec: stur x8,[x29, #-0x78]
2721af2f0: add x8,x21,#0x10
2721af2f4: stur x8,[x29, #-0x80]
2721af2f8: add x8,x21,#0x8
2721af2fc: stur x8,[x29, #-0x70]
2721af300: ldur x24,[x29, #-0xa8]
2721af304: stp x22,x21,[x29, #-0xb8]
2721af308: ldr x8,[x25, #0x10]
2721af30c: cmp x27,x8
2721af310: b.cs 0x2721af59c
2721af314: ldr x8,[x21, #0x48]
2721af318: ldp x9,x10,[x29, #-0x80]
2721af31c: madd x1,x8,x27,x10
2721af320: ldr x8,[x21, #0x10]
2721af324: mov x0,x23
2721af328: mov x2,x19
2721af32c: mov x17,x9
2721af330: movk x17,#0xe3ba, LSL #48
2721af334: blraa x8,x17
2721af338: mov x8,x22
2721af33c: bl 0x2743dab90
2721af340: adrp x16,0x2780dc000
2721af344: ldr x16,[x16, #0xf00]
2721af348: mov x17,#0x7099
2721af34c: pacia x16,x17
2721af350: mov x1,x16
2721af354: adrp x16,0x2780dc000
2721af358: ldr x16,[x16, #0xf08]
2721af35c: mov x17,#0xc6eb
2721af360: pacda x16,x17
2721af364: mov x2,x16
2721af368: adrp x0,0x280c81000
2721af36c: add x0,x0,#0x3e8
2721af370: bl 0x2721af72c
2721af374: mov x3,x0
2721af378: mov x0,x23
2721af37c: mov x1,x22
2721af380: mov x20,x19
2721af384: mov x2,x19
2721af388: bl 0x2743dbd90
2721af38c: mov x20,x0
2721af390: ldr x28,[x21, #0x8]
2721af394: mov x0,x22
2721af398: mov x1,x19
2721af39c: ldur x8,[x29, #-0x70]
2721af3a0: mov x17,x8
2721af3a4: movk x17,#0x4f8, LSL #48
2721af3a8: blraa x28,x17
2721af3ac: tbnz w20,#0x0,0x2721af52c
2721af3b0: mov x20,x23
2721af3b4: bl 0x2743dabb0
2721af3b8: mov x20,x1
2721af3bc: bl 0x2743dbe40
2721af3c0: mov x26,x0
2721af3c4: mov x0,x20
2721af3c8: bl 0x2743dcc60
2721af3cc: stur x26,[x29, #-0x88]
2721af3d0: tbnz x26,#0x20,0x2721af52c
2721af3d4: movi d0,#0x0
2721af3d8: mov w0,#0xca00
2721af3dc: movk w0,#0x3b9a, LSL #16
2721af3e0: bl 0x2743dc320
2721af3e4: mov x24,x0
2721af3e8: mov x25,x1
2721af3ec: mov x22,x2
2721af3f0: mov x20,x23
2721af3f4: bl 0x2743dab80
2721af3f8: fmov d8,d0
2721af3fc: ldur x0,[x29, #-0x98]
2721af400: mov x1,#0x0
2721af404: bl 0x2721af774
2721af408: ldur x20,[x29, #-0x90]
2721af40c: mov x0,x20
2721af410: bl 0x2743dcf60
2721af414: mov x21,x0
2721af418: stur x20,[x29, #-0x68]
2721af41c: stp x24,x22,[x29, #-0xa0]
2721af420: mov x0,x24
2721af424: mov x1,x25
2721af428: mov x2,x22
2721af42c: bl 0x272196ae4
2721af430: ldr x8,[x20, #0x10]
2721af434: mvn w9,w1
2721af438: and x9,x9,#0x1
2721af43c: adds x22,x8,x9
2721af440: b.vs 0x2721af5a0
2721af444: mov x24,x0
2721af448: mov x26,x1
2721af44c: mov x0,#0x0
2721af450: bl 0x2721af784
2721af454: mov x2,x0
2721af458: sub x20,x29,#0x68
2721af45c: mov x0,x21
2721af460: mov x1,x22
2721af464: bl 0x2743dc500
2721af468: ldur x20,[x29, #-0x68]
2721af46c: tbz w0,#0x0,0x2721af490
2721af470: ldp x0,x2,[x29, #-0xa0]
2721af474: mov x1,x25
2721af478: bl 0x272196ae4
2721af47c: and w8,w1,#0x1
2721af480: and w9,w26,#0x1
2721af484: cmp w9,w8
2721af488: b.ne 0x2721af5a8
2721af48c: mov x24,x0
2721af490: tbnz w26,#0x0,0x2721af4f4
2721af494: lsr x21,x25,#0x20
2721af498: sub x8,x29,#0x68
2721af49c: bl 0x2721af5b4
2721af4a0: ldur x8,[x29, #-0x68]
2721af4a4: lsr x9,x24,#0x6
2721af4a8: add x9,x20,x9, LSL #0x3
2721af4ac: mov w10,#0x1
2721af4b0: lsl x10,x10,x24
2721af4b4: ldr x11,[x9, #0x40]
2721af4b8: orr x10,x11,x10
2721af4bc: str x10,[x9, #0x40]
2721af4c0: ldr x9,[x20, #0x30]
2721af4c4: mov w10,#0x18
2721af4c8: madd x9,x24,x10,x9
2721af4cc: ldp x11,x10,[x29, #-0xa0]
2721af4d0: str x11,[x9]
2721af4d4: stp w25,w21,[x9, #0x8]
2721af4d8: str x10,[x9, #0x10]
2721af4dc: ldr x9,[x20, #0x38]
2721af4e0: str x8,[x9, x24, LSL #0x3]
2721af4e4: ldr x8,[x20, #0x10]
2721af4e8: adds x8,x8,#0x1
2721af4ec: b.vs 0x2721af5a4
2721af4f0: str x8,[x20, #0x10]
2721af4f4: fcvt s0,d8
2721af4f8: stur x20,[x29, #-0x90]
2721af4fc: ldr x8,[x20, #0x38]
2721af500: add x20,x8,x24, LSL #0x3
2721af504: ldur x0,[x29, #-0x88]
2721af508: bl 0x2721af84c
2721af50c: adrp x16,0x2721af000
2721af510: add x16,x16,#0x5b4
2721af514: mov x17,#0x720f
2721af518: pacia x16,x17
2721af51c: stur x16,[x29, #-0x98]
2721af520: ldp x22,x21,[x29, #-0xb8]
2721af524: ldur x25,[x29, #-0xc0]
2721af528: ldur x24,[x29, #-0xa8]
2721af52c: add x27,x27,#0x1
2721af530: mov x0,x23
2721af534: mov x1,x19
2721af538: ldur x8,[x29, #-0x70]
2721af53c: mov x17,x8
2721af540: movk x17,#0x4f8, LSL #48
2721af544: blraa x28,x17
2721af548: cmp x24,x27
2721af54c: b.ne 0x2721af308
2721af550: mov x0,x25
2721af554: bl 0x2743dcc60
2721af558: ldur x0,[x29, #-0x98]
2721af55c: b 0x2721af56c
2721af560: mov x0,x25
2721af564: bl 0x2743dcc60
2721af568: mov x0,#0x0
2721af56c: mov x1,#0x0
2721af570: bl 0x2721af774
2721af574: ldur x0,[x29, #-0x90]
2721af578: sub sp,x29,#0x60
2721af57c: ldp x29,x30,[sp, #0x60]
2721af580: ldp x20,x19,[sp, #0x50]
2721af584: ldp x22,x21,[sp, #0x40]
2721af588: ldp x24,x23,[sp, #0x30]
2721af58c: ldp x26,x25,[sp, #0x20]
2721af590: ldp x28,x27,[sp, #0x10]
2721af594: ldp d9,d8,[sp], #0x70
2721af598: retab
2721af59c: brk #0x1
2721af5a0: brk #0x1
2721af5a4: brk #0x1
2721af5a8: ldur x0,[x29, #-0xc8]
2721af5ac: bl 0x2743dc7f0
2721af5b0: brk #0x1

