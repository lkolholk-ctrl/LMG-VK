; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27221c9b8
; Symbol: FUN_27221c9b8
; Signature: undefined FUN_27221c9b8()
27221c9b8: pacibsp
27221c9bc: sub sp,sp,#0x140
27221c9c0: stp x28,x27,[sp, #0xf0]
27221c9c4: stp x24,x23,[sp, #0x100]
27221c9c8: stp x22,x21,[sp, #0x110]
27221c9cc: stp x20,x19,[sp, #0x120]
27221c9d0: stp x29,x30,[sp, #0x130]
27221c9d4: add x29,sp,#0x130
27221c9d8: mov x19,x8
27221c9dc: ldr x20,[x20, #0x20]
27221c9e0: mov x0,x20
27221c9e4: mov w1,#0x2
27221c9e8: bl 0x2743ddd10
27221c9ec: mov w0,#0x1
27221c9f0: mov x1,x20
27221c9f4: bl 0x272271d7c
27221c9f8: mov x22,x0
27221c9fc: stp x20,x0,[x29, #-0x68]
27221ca00: stp x1,x2,[x29, #-0x58]
27221ca04: stur x3,[x29, #-0x48]
27221ca08: sub x0,x29,#0x68
27221ca0c: mov x21,#0x0
27221ca10: bl 0x27221b928
27221ca14: mov x21,x0
27221ca18: mov x0,x22
27221ca1c: bl 0x2743ddf20
27221ca20: mov x0,x20
27221ca24: bl 0x2743ddce0
27221ca28: mov x23,#0x0
27221ca2c: ldr x24,[x21, #0x10]
27221ca30: add x22,x21,#0x20
27221ca34: cmp x24,x23
27221ca38: b.eq 0x27221ca8c
27221ca3c: ldr x8,[x21, #0x10]
27221ca40: cmp x23,x8
27221ca44: b.cs 0x27221cb90
27221ca48: adrp x16,0x27221e000
27221ca4c: add x16,x16,#0x38c
27221ca50: mov x17,#0xac70
27221ca54: pacia x16,x17
27221ca58: mov x2,x16
27221ca5c: add x1,sp,#0x38
27221ca60: mov x0,x22
27221ca64: bl 0x27221e404
27221ca68: ldrb w8,[sp, #0x60]
27221ca6c: tst w8,#0x3
27221ca70: b.eq 0x27221caa4
27221ca74: add x23,x23,#0x1
27221ca78: bl 0x27221e8f4
27221ca7c: add x0,sp,#0x38
27221ca80: bl 0x27221e4bc
27221ca84: add x22,x22,#0x30
27221ca88: b 0x27221ca34
27221ca8c: mov x0,x21
27221ca90: bl 0x2743ddce0
27221ca94: movi v0.2D,#0x0
27221ca98: stp q0,q0,[sp, #0x80]
27221ca9c: str q0,[sp, #0x70]
27221caa0: b 0x27221cac0
27221caa4: mov x0,x21
27221caa8: bl 0x2743ddce0
27221caac: ldur q0,[sp, #0x38]
27221cab0: ldur q1,[sp, #0x48]
27221cab4: stp q0,q1,[sp, #0x70]
27221cab8: ldur q0,[sp, #0x58]
27221cabc: str q0,[sp, #0x90]
27221cac0: adrp x16,0x27221e000
27221cac4: add x16,x16,#0x464
27221cac8: mov x17,#0xac70
27221cacc: pacia x16,x17
27221cad0: mov x2,x16
27221cad4: add x0,sp,#0x70
27221cad8: add x1,sp,#0x38
27221cadc: bl 0x27221e404
27221cae0: ldr x8,[sp, #0x50]
27221cae4: cbz x8,0x27221cb30
27221cae8: ldur q0,[sp, #0x38]
27221caec: ldur q1,[sp, #0x48]
27221caf0: stp q0,q1,[sp]
27221caf4: ldur q0,[sp, #0x58]
27221caf8: str q0,[sp, #0x20]
27221cafc: mov x0,sp
27221cb00: sub x1,x29,#0x90
27221cb04: bl 0x27221361c
27221cb08: bl 0x27221e8f4
27221cb0c: mov x0,sp
27221cb10: bl 0x27221e4bc
27221cb14: bl 0x27221e918
27221cb18: ldur x8,[x29, #-0x78]
27221cb1c: cbz x8,0x27221cb40
27221cb20: sub x0,x29,#0x90
27221cb24: mov x1,x19
27221cb28: bl 0x2722136e0
27221cb2c: b 0x27221cb74
27221cb30: bl 0x27221e918
27221cb34: movi v0.2D,#0x0
27221cb38: stp q0,q0,[x29, #-0x90]
27221cb3c: stur xzr,[x29, #-0x70]
27221cb40: mov x8,x19
27221cb44: mov x0,x20
27221cb48: bl 0x27221ae44
27221cb4c: ldur x8,[x29, #-0x78]
27221cb50: cbz x8,0x27221cb74
27221cb54: adrp x1,0x280c99000
27221cb58: add x1,x1,#0x558
27221cb5c: adrp x2,0x280c99000
27221cb60: add x2,x2,#0x560
27221cb64: adrp x3,0x2722a3000
27221cb68: add x3,x3,#0x4d0
27221cb6c: sub x0,x29,#0x90
27221cb70: bl 0x27221e520
27221cb74: ldp x29,x30,[sp, #0x130]
27221cb78: ldp x20,x19,[sp, #0x120]
27221cb7c: ldp x22,x21,[sp, #0x110]
27221cb80: ldp x24,x23,[sp, #0x100]
27221cb84: ldp x28,x27,[sp, #0xf0]
27221cb88: add sp,sp,#0x140
27221cb8c: retab
27221cb90: brk #0x1

