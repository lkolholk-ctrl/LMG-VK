; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27221cb94
; Symbol: FUN_27221cb94
; Signature: undefined FUN_27221cb94()
27221cb94: pacibsp
27221cb98: sub sp,sp,#0xb0
27221cb9c: stp x22,x21,[sp, #0x80]
27221cba0: stp x20,x19,[sp, #0x90]
27221cba4: stp x29,x30,[sp, #0xa0]
27221cba8: add x29,sp,#0xa0
27221cbac: mov x19,x20
27221cbb0: sub x8,x29,#0x48
27221cbb4: bl 0x27221c864
27221cbb8: sub x0,x29,#0x48
27221cbbc: add x1,sp,#0x30
27221cbc0: bl 0x27221e0d8
27221cbc4: ldr x8,[sp, #0x48]
27221cbc8: cbz x8,0x27221cc34
27221cbcc: add x0,sp,#0x30
27221cbd0: add x1,sp,#0x8
27221cbd4: bl 0x2722136e0
27221cbd8: ldp x21,x22,[sp, #0x20]
27221cbdc: add x0,sp,#0x8
27221cbe0: mov x1,x21
27221cbe4: bl 0x27220ff94
27221cbe8: mov x8,x22
27221cbec: bl 0x27220ffd8
27221cbf0: mov x0,x21
27221cbf4: mov x1,x22
27221cbf8: mov x17,x8
27221cbfc: movk x17,#0xbc30, LSL #48
27221cc00: blraa x9,x17
27221cc04: mov x20,x0
27221cc08: adrp x1,0x280c99000
27221cc0c: add x1,x1,#0x548
27221cc10: adrp x2,0x280c99000
27221cc14: add x2,x2,#0x440
27221cc18: adrp x3,0x2722a3000
27221cc1c: add x3,x3,#0x49c
27221cc20: sub x0,x29,#0x48
27221cc24: bl 0x27221e520
27221cc28: add x0,sp,#0x8
27221cc2c: bl 0x272210e78
27221cc30: b 0x27221cc58
27221cc34: adrp x1,0x280c99000
27221cc38: add x1,x1,#0x548
27221cc3c: adrp x2,0x280c99000
27221cc40: add x2,x2,#0x440
27221cc44: adrp x3,0x2722a3000
27221cc48: add x3,x3,#0x49c
27221cc4c: sub x0,x29,#0x48
27221cc50: bl 0x27221e520
27221cc54: mov x20,#0x0
27221cc58: mov x0,x20
27221cc5c: mov x20,x19
27221cc60: bl 0x27221d4b0
27221cc64: mov x21,x0
27221cc68: bl 0x27221d5f4
27221cc6c: mov x22,x0
27221cc70: mov x0,x21
27221cc74: bl 0x2743ddce0
27221cc78: mov x0,x22
27221cc7c: bl 0x27221d858
27221cc80: mov x19,x0
27221cc84: mov x0,x22
27221cc88: bl 0x2743ddce0
27221cc8c: mov x0,x19
27221cc90: ldp x29,x30,[sp, #0xa0]
27221cc94: ldp x20,x19,[sp, #0x90]
27221cc98: ldp x22,x21,[sp, #0x80]
27221cc9c: add sp,sp,#0xb0
27221cca0: retab

