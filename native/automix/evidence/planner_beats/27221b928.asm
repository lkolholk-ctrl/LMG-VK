; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27221b928
; Symbol: FUN_27221b928
; Signature: undefined FUN_27221b928()
27221b928: pacibsp
27221b92c: sub sp,sp,#0x170
27221b930: str x28,[sp, #0x110]
27221b934: stp x27,x26,[sp, #0x120]
27221b938: stp x25,x24,[sp, #0x130]
27221b93c: stp x23,x22,[sp, #0x140]
27221b940: stp x20,x19,[sp, #0x150]
27221b944: stp x29,x30,[sp, #0x160]
27221b948: add x29,sp,#0x160
27221b94c: ldp x9,x8,[x0, #0x18]
27221b950: lsr x8,x8,#0x1
27221b954: str x9,[sp, #0x28]
27221b958: subs x19,x8,x9
27221b95c: b.vs 0x27221bd3c
27221b960: mov x22,x21
27221b964: str x8,[sp, #0x38]
27221b968: ldp x23,x26,[x0]
27221b96c: ldr x8,[x0, #0x10]
27221b970: str x8,[sp, #0x18]
27221b974: ldr x8,[x23, #0x10]
27221b978: cmp x19,x8
27221b97c: str x8,[sp, #0x30]
27221b980: csel x28,x19,x8,lt
27221b984: adrp x8,0x2780e3000
27221b988: ldr x8,[x8, #0xc50]
27221b98c: stur x8,[x29, #-0x48]
27221b990: bic x1,x28,x28, ASR #0x3f
27221b994: sub x20,x29,#0x48
27221b998: mov w0,#0x0
27221b99c: mov w2,#0x0
27221b9a0: bl 0x272263470
27221b9a4: tbnz x19,#0x3f,0x27221bd40
27221b9a8: stp x26,x22,[sp]
27221b9ac: ldur x24,[x29, #-0x48]
27221b9b0: str x23,[sp, #0x20]
27221b9b4: str x28,[sp, #0x10]
27221b9b8: cbz x28,0x27221bb34
27221b9bc: add x25,x23,#0x20
27221b9c0: mov x0,x23
27221b9c4: bl 0x2743ddd00
27221b9c8: mov x0,x26
27221b9cc: bl 0x2743ddf40
27221b9d0: mov w8,#0x28
27221b9d4: ldp x27,x19,[sp, #0x28]
27221b9d8: ldr x9,[sp, #0x18]
27221b9dc: madd x26,x27,x8,x9
27221b9e0: cbz x19,0x27221bd4c
27221b9e4: add x1,sp,#0x68
27221b9e8: mov x0,x25
27221b9ec: bl 0x27221361c
27221b9f0: ldr x8,[sp, #0x38]
27221b9f4: cmp x8,x27
27221b9f8: b.eq 0x27221bd44
27221b9fc: cmp x27,x8
27221ba00: b.ge 0x27221bd24
27221ba04: add x0,sp,#0x68
27221ba08: sub x1,x29,#0xa8
27221ba0c: bl 0x2722136e0
27221ba10: sub x23,x29,#0xa8
27221ba14: add x1,x23,#0x28
27221ba18: mov x0,x26
27221ba1c: bl 0x27221361c
27221ba20: sub x0,x29,#0xa8
27221ba24: add x1,sp,#0x68
27221ba28: bl 0x27221361c
27221ba2c: ldp x22,x20,[x29, #-0x68]
27221ba30: add x0,x23,#0x28
27221ba34: mov x1,x22
27221ba38: bl 0x27220ff94
27221ba3c: ldr x8,[x20, #0x8]
27221ba40: ldr x1,[x8, #0x8]
27221ba44: mov x8,x1
27221ba48: ldr x9,[x8, #0x10]!
27221ba4c: mov x20,x0
27221ba50: mov x0,x22
27221ba54: mov x17,x8
27221ba58: movk x17,#0xbc30, LSL #48
27221ba5c: blraa x9,x17
27221ba60: mov x22,x0
27221ba64: ldp x23,x20,[x29, #-0x90]
27221ba68: sub x0,x29,#0xa8
27221ba6c: mov x1,x23
27221ba70: bl 0x27220ff94
27221ba74: ldr x8,[x20, #0x8]
27221ba78: ldr x1,[x8, #0x8]
27221ba7c: mov x8,x1
27221ba80: ldr x9,[x8, #0x10]!
27221ba84: mov x20,x0
27221ba88: mov x0,x23
27221ba8c: mov x17,x8
27221ba90: movk x17,#0xbc30, LSL #48
27221ba94: blraa x9,x17
27221ba98: subs x8,x22,x0
27221ba9c: b.vs 0x27221bd28
27221baa0: str x8,[sp, #0x90]
27221baa4: sub x0,x29,#0xa8
27221baa8: adrp x1,0x280c99000
27221baac: add x1,x1,#0x578
27221bab0: adrp x2,0x280c99000
27221bab4: add x2,x2,#0x560
27221bab8: adrp x3,0x2722a3000
27221babc: add x3,x3,#0x4d0
27221bac0: bl 0x27221e650
27221bac4: stur x24,[x29, #-0x48]
27221bac8: ldp x22,x8,[x24, #0x10]
27221bacc: add x23,x22,#0x1
27221bad0: cmp x22,x8, LSR #0x1
27221bad4: b.cs 0x27221bb14
27221bad8: str x23,[x24, #0x10]
27221badc: mov w8,#0x30
27221bae0: madd x8,x22,x8,x24
27221bae4: ldur q0,[sp, #0x68]
27221bae8: ldur q1,[sp, #0x78]
27221baec: ldur q2,[sp, #0x88]
27221baf0: stp q1,q2,[x8, #0x30]
27221baf4: add x25,x25,#0x28
27221baf8: str q0,[x8, #0x20]
27221bafc: sub x19,x19,#0x1
27221bb00: add x26,x26,#0x28
27221bb04: add x27,x27,#0x1
27221bb08: sub x28,x28,#0x1
27221bb0c: cbnz x28,0x27221b9e0
27221bb10: b 0x27221bb48
27221bb14: cmp x8,#0x1
27221bb18: cset w0,hi
27221bb1c: sub x20,x29,#0x48
27221bb20: mov x1,x23
27221bb24: mov w2,#0x1
27221bb28: bl 0x272263470
27221bb2c: ldur x24,[x29, #-0x48]
27221bb30: b 0x27221bad8
27221bb34: mov x0,x23
27221bb38: bl 0x2743ddd00
27221bb3c: mov x0,x26
27221bb40: bl 0x2743ddf40
27221bb44: ldr x27,[sp, #0x28]
27221bb48: mov w8,#0x28
27221bb4c: ldp x28,x9,[sp, #0x10]
27221bb50: madd x25,x27,x8,x9
27221bb54: ldr x9,[sp, #0x20]
27221bb58: madd x8,x28,x8,x9
27221bb5c: add x26,x8,#0x20
27221bb60: mov x19,x27
27221bb64: ldr x8,[sp, #0x30]
27221bb68: cmp x8,x28
27221bb6c: b.eq 0x27221bcec
27221bb70: cmp x28,x8
27221bb74: b.cs 0x27221bd2c
27221bb78: add x1,sp,#0x40
27221bb7c: mov x0,x26
27221bb80: bl 0x27221361c
27221bb84: cmn x28,#0x1
27221bb88: b.vs 0x27221bd30
27221bb8c: ldr x9,[sp, #0x38]
27221bb90: cmp x9,x19
27221bb94: b.eq 0x27221bce4
27221bb98: ldr x8,[sp, #0x28]
27221bb9c: cmp x27,x8
27221bba0: ccmp x19,x9,#0x0,ge
27221bba4: b.ge 0x27221bd34
27221bba8: add x0,sp,#0x40
27221bbac: add x1,sp,#0x68
27221bbb0: bl 0x2722136e0
27221bbb4: add x8,sp,#0x68
27221bbb8: add x1,x8,#0x28
27221bbbc: mov x0,x25
27221bbc0: bl 0x27221361c
27221bbc4: sub x0,x29,#0xa8
27221bbc8: add x1,sp,#0x68
27221bbcc: mov w2,#0x50
27221bbd0: bl 0x272290d8c
27221bbd4: sub x0,x29,#0xa8
27221bbd8: add x1,sp,#0x68
27221bbdc: bl 0x27221361c
27221bbe0: ldp x23,x20,[x29, #-0x68]
27221bbe4: sub x8,x29,#0xa8
27221bbe8: add x0,x8,#0x28
27221bbec: mov x1,x23
27221bbf0: bl 0x27220ff94
27221bbf4: ldr x8,[x20, #0x8]
27221bbf8: ldr x1,[x8, #0x8]
27221bbfc: mov x8,x1
27221bc00: ldr x9,[x8, #0x10]!
27221bc04: mov x20,x0
27221bc08: mov x0,x23
27221bc0c: mov x17,x8
27221bc10: movk x17,#0xbc30, LSL #48
27221bc14: blraa x9,x17
27221bc18: mov x23,x0
27221bc1c: ldp x22,x20,[x29, #-0x90]
27221bc20: sub x0,x29,#0xa8
27221bc24: mov x1,x22
27221bc28: bl 0x27220ff94
27221bc2c: ldr x8,[x20, #0x8]
27221bc30: ldr x1,[x8, #0x8]
27221bc34: mov x8,x1
27221bc38: ldr x9,[x8, #0x10]!
27221bc3c: mov x20,x0
27221bc40: mov x0,x22
27221bc44: mov x17,x8
27221bc48: movk x17,#0xbc30, LSL #48
27221bc4c: blraa x9,x17
27221bc50: subs x8,x23,x0
27221bc54: b.vs 0x27221bd38
27221bc58: str x8,[sp, #0x90]
27221bc5c: sub x0,x29,#0xa8
27221bc60: adrp x1,0x280c99000
27221bc64: add x1,x1,#0x578
27221bc68: adrp x2,0x280c99000
27221bc6c: add x2,x2,#0x560
27221bc70: adrp x3,0x2722a3000
27221bc74: add x3,x3,#0x4d0
27221bc78: bl 0x27221e650
27221bc7c: stur x24,[x29, #-0x48]
27221bc80: ldp x22,x8,[x24, #0x10]
27221bc84: add x23,x22,#0x1
27221bc88: cmp x22,x8, LSR #0x1
27221bc8c: b.cs 0x27221bcc4
27221bc90: str x23,[x24, #0x10]
27221bc94: mov w8,#0x30
27221bc98: madd x8,x22,x8,x24
27221bc9c: ldur q0,[sp, #0x68]
27221bca0: ldur q1,[sp, #0x78]
27221bca4: ldur q2,[sp, #0x88]
27221bca8: stp q1,q2,[x8, #0x30]
27221bcac: str q0,[x8, #0x20]
27221bcb0: add x25,x25,#0x28
27221bcb4: add x28,x28,#0x1
27221bcb8: add x26,x26,#0x28
27221bcbc: add x19,x19,#0x1
27221bcc0: b 0x27221bb64
27221bcc4: cmp x8,#0x1
27221bcc8: cset w0,hi
27221bccc: sub x20,x29,#0x48
27221bcd0: mov x1,x23
27221bcd4: mov w2,#0x1
27221bcd8: bl 0x272263470
27221bcdc: ldur x24,[x29, #-0x48]
27221bce0: b 0x27221bc90
27221bce4: add x0,sp,#0x40
27221bce8: bl 0x272210e78
27221bcec: ldr x0,[sp]
27221bcf0: bl 0x2743ddf20
27221bcf4: ldr x0,[sp, #0x20]
27221bcf8: bl 0x2743ddce0
27221bcfc: mov x0,x24
27221bd00: ldr x21,[sp, #0x8]
27221bd04: ldp x29,x30,[sp, #0x160]
27221bd08: ldp x20,x19,[sp, #0x150]
27221bd0c: ldp x23,x22,[sp, #0x140]
27221bd10: ldp x25,x24,[sp, #0x130]
27221bd14: ldp x27,x26,[sp, #0x120]
27221bd18: ldr x28,[sp, #0x110]
27221bd1c: add sp,sp,#0x170
27221bd20: retab
27221bd24: brk #0x1
27221bd28: brk #0x1
27221bd2c: brk #0x1
27221bd30: brk #0x1
27221bd34: brk #0x1
27221bd38: brk #0x1
27221bd3c: brk #0x1
27221bd40: brk #0x1
27221bd44: add x0,sp,#0x68
27221bd48: bl 0x272210e78
27221bd4c: brk #0x1

