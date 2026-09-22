; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe37c8..0x234fe3ff4
; Code SHA256: 37d2708a01a933422eed7618a7f1d4d1c4a7d223c571b3859755756a8a078a6a
0x234fe37c8: 7f2303d5 pacibsp 
0x234fe37cc: ffc303d1 sub sp, sp, #0xf0
0x234fe37d0: eb2b076d stp d11, d10, [sp, #0x70]
0x234fe37d4: e923086d stp d9, d8, [sp, #0x80]
0x234fe37d8: fc6f09a9 stp x28, x27, [sp, #0x90]
0x234fe37dc: fa670aa9 stp x26, x25, [sp, #0xa0]
0x234fe37e0: f85f0ba9 stp x24, x23, [sp, #0xb0]
0x234fe37e4: f6570ca9 stp x22, x21, [sp, #0xc0]
0x234fe37e8: f44f0da9 stp x20, x19, [sp, #0xd0]
0x234fe37ec: fd7b0ea9 stp x29, x30, [sp, #0xe0]
0x234fe37f0: fd830391 add x29, sp, #0xe0
0x234fe37f4: f30300aa mov x19, x0
0x234fe37f8: 088821b0 adrp x8, #0x2780e4000
0x234fe37fc: 084545f9 ldr x8, [x8, #0xa88]
0x234fe3800: 080140f9 ldr x8, [x8]
0x234fe3804: e83700f9 str x8, [sp, #0x68]
0x234fe3808: 604e8512 mov w0, #-0x2a74
0x234fe380c: 7f040071 cmp w3, #1
0x234fe3810: e1030054 b.ne #0x234fe388c
0x234fe3814: bf040071 cmp w5, #1
0x234fe3818: a1030054 b.ne #0x234fe388c
0x234fe381c: f40302aa mov x20, x2
0x234fe3820: 9a0040f9 ldr x26, [x4]
0x234fe3824: db0040f9 ldr x27, [x6]
0x234fe3828: 5c0340b9 ldr w28, [x26]
0x234fe382c: 780340b9 ldr w24, [x27]
0x234fe3830: 60420191 add x0, x19, #0x50
0x234fe3834: 01008052 mov w1, #0
0x234fe3838: c0a8fc97 bl #0x234f0db38
0x234fe383c: 81020036 tbz w1, #0, #0x234fe388c
0x234fe3840: 086c40b9 ldr w8, [x0, #0x6c]
0x234fe3844: 9f03086b cmp w28, w8
0x234fe3848: 01020054 b.ne #0x234fe3888
0x234fe384c: 60020291 add x0, x19, #0x80
0x234fe3850: 01008052 mov w1, #0
0x234fe3854: b9a8fc97 bl #0x234f0db38
0x234fe3858: 813c0036 tbz w1, #0, #0x234fe3fe8
0x234fe385c: 086c40b9 ldr w8, [x0, #0x6c]
0x234fe3860: 1f03086b cmp w24, w8
0x234fe3864: 21010054 b.ne #0x234fe3888
0x234fe3868: 550b40f9 ldr x21, [x26, #0x10]
0x234fe386c: 600b40f9 ldr x0, [x27, #0x10]
0x234fe3870: 1f0b0071 cmp w24, #2
0x234fe3874: c1020054 b.ne #0x234fe38cc
0x234fe3878: 9f0b0071 cmp w28, #2
0x234fe387c: 41030054 b.ne #0x234fe38e4
0x234fe3880: 571340f9 ldr x23, [x26, #0x20]
0x234fe3884: 19000014 b #0x234fe38e8
0x234fe3888: 20068012 mov w0, #-0x32
0x234fe388c: e83740f9 ldr x8, [sp, #0x68]
0x234fe3890: 098821b0 adrp x9, #0x2780e4000
0x234fe3894: 294545f9 ldr x9, [x9, #0xa88]
0x234fe3898: 290140f9 ldr x9, [x9]
0x234fe389c: 3f0108eb cmp x9, x8
0x234fe38a0: 613a0054 b.ne #0x234fe3fec
0x234fe38a4: fd7b4ea9 ldp x29, x30, [sp, #0xe0]
0x234fe38a8: f44f4da9 ldp x20, x19, [sp, #0xd0]
0x234fe38ac: f6574ca9 ldp x22, x21, [sp, #0xc0]
0x234fe38b0: f85f4ba9 ldp x24, x23, [sp, #0xb0]
0x234fe38b4: fa674aa9 ldp x26, x25, [sp, #0xa0]
0x234fe38b8: fc6f49a9 ldp x28, x27, [sp, #0x90]
0x234fe38bc: e923486d ldp d9, d8, [sp, #0x80]
0x234fe38c0: eb2b476d ldp d11, d10, [sp, #0x70]
0x234fe38c4: ffc30391 add sp, sp, #0xf0
0x234fe38c8: ff0f5fd6 retab 
0x234fe38cc: 68c24839 ldrb w8, [x19, #0x230]
0x234fe38d0: 1f050071 cmp w8, #1
0x234fe38d4: 21020054 b.ne #0x234fe3918
0x234fe38d8: 82761e53 lsl w2, w20, #2
0x234fe38dc: e10315aa mov x1, x21
0x234fe38e0: ab010014 b #0x234fe3f8c
0x234fe38e4: 170080d2 mov x23, #0
0x234fe38e8: 761340f9 ldr x22, [x27, #0x20]
0x234fe38ec: 68c24839 ldrb w8, [x19, #0x230]
0x234fe38f0: 88010036 tbz w8, #0, #0x234fe3920
0x234fe38f4: 93761e53 lsl w19, w20, #2
0x234fe38f8: e10315aa mov x1, x21
0x234fe38fc: e20313aa mov x2, x19
0x234fe3900: ff620094 bl #0x234ffc4fc
0x234fe3904: 763400b4 cbz x22, #0x234fe3f90
0x234fe3908: e00316aa mov x0, x22
0x234fe390c: d73300b4 cbz x23, #0x234fe3f84
0x234fe3910: e10317aa mov x1, x23
0x234fe3914: 9d010014 b #0x234fe3f88
0x234fe3918: 170080d2 mov x23, #0
0x234fe391c: 160080d2 mov x22, #0
0x234fe3920: f70700f9 str x23, [sp, #8]
0x234fe3924: e00f00f9 str x0, [sp, #0x18]
0x234fe3928: 68c64839 ldrb w8, [x19, #0x231]
0x234fe392c: 1f050071 cmp w8, #1
0x234fe3930: 61000054 b.ne #0x234fe393c
0x234fe3934: 600a41f9 ldr x0, [x19, #0x210]
0x234fe3938: af010094 bl #0x234fe3ff4
0x234fe393c: f60b00f9 str x22, [sp, #0x10]
0x234fe3940: f51300f9 str x21, [sp, #0x20]
0x234fe3944: 681a42b9 ldr w8, [x19, #0x218]
0x234fe3948: 691e42b9 ldr w9, [x19, #0x21c]
0x234fe394c: f70313aa mov x23, x19
0x234fe3950: 17aef5f2 movk x23, #0xad70, lsl #48
0x234fe3954: 1f01096b cmp w8, w9
0x234fe3958: 00150054 b.eq #0x234fe3bf8
0x234fe395c: 681e02b9 str w8, [x19, #0x21c]
0x234fe3960: 15f0a752 mov w21, #0x3f800000
0x234fe3964: f52b00b9 str w21, [sp, #0x28]
0x234fe3968: 700240f9 ldr x16, [x19]
0x234fe396c: f10313aa mov x17, x19
0x234fe3970: 11aef5f2 movk x17, #0xad70, lsl #48
0x234fe3974: 301ac1da autda x16, x17
0x234fe3978: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe397c: e90310aa mov x9, x16
0x234fe3980: e4a30091 add x4, sp, #0x28
0x234fe3984: e00313aa mov x0, x19
0x234fe3988: 41008052 mov w1, #2
0x234fe398c: 02008052 mov w2, #0
0x234fe3990: 03008052 mov w3, #0
0x234fe3994: f10309aa mov x17, x9
0x234fe3998: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe399c: 11093fd7 blraa x8, x17
0x234fe39a0: e92b40bd ldr s9, [sp, #0x28]
0x234fe39a4: f52b00b9 str w21, [sp, #0x28]
0x234fe39a8: 700240f9 ldr x16, [x19]
0x234fe39ac: f01ac1da autda x16, x23
0x234fe39b0: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe39b4: e90310aa mov x9, x16
0x234fe39b8: e4a30091 add x4, sp, #0x28
0x234fe39bc: e00313aa mov x0, x19
0x234fe39c0: 61008052 mov w1, #3
0x234fe39c4: 02008052 mov w2, #0
0x234fe39c8: 03008052 mov w3, #0
0x234fe39cc: f10309aa mov x17, x9
0x234fe39d0: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe39d4: 11093fd7 blraa x8, x17
0x234fe39d8: ea2b40bd ldr s10, [sp, #0x28]
0x234fe39dc: 08f0a752 mov w8, #0x3f800000
0x234fe39e0: e82b00b9 str w8, [sp, #0x28]
0x234fe39e4: 700240f9 ldr x16, [x19]
0x234fe39e8: f01ac1da autda x16, x23
0x234fe39ec: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe39f0: e90310aa mov x9, x16
0x234fe39f4: e4a30091 add x4, sp, #0x28
0x234fe39f8: e00313aa mov x0, x19
0x234fe39fc: c1008052 mov w1, #6
0x234fe3a00: 02008052 mov w2, #0
0x234fe3a04: 03008052 mov w3, #0
0x234fe3a08: f10309aa mov x17, x9
0x234fe3a0c: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3a10: 11093fd7 blraa x8, x17
0x234fe3a14: e82b40bd ldr s8, [sp, #0x28]
0x234fe3a18: 60020291 add x0, x19, #0x80
0x234fe3a1c: 01008052 mov w1, #0
0x234fe3a20: 46a8fc97 bl #0x234f0db38
0x234fe3a24: 212e0036 tbz w1, #0, #0x234fe3fe8
0x234fe3a28: 1501391e fcvtzu w21, s8
0x234fe3a2c: 082840fd ldr d8, [x0, #0x50]
0x234fe3a30: 602642bd ldr s0, [x19, #0x224]
0x234fe3a34: 2021201e fcmp s9, s0
0x234fe3a38: e1000054 b.ne #0x234fe3a54
0x234fe3a3c: 602a42bd ldr s0, [x19, #0x228]
0x234fe3a40: 4021201e fcmp s10, s0
0x234fe3a44: 81000054 b.ne #0x234fe3a54
0x234fe3a48: 682242b9 ldr w8, [x19, #0x220]
0x234fe3a4c: 1f01156b cmp w8, w21
0x234fe3a50: 40010054 b.eq #0x234fe3a78
0x234fe3a54: 600a41f9 ldr x0, [x19, #0x210]
0x234fe3a58: a202631e ucvtf d2, w21
0x234fe3a5c: 0101791e fcvtzu w1, d8
0x234fe3a60: 2041201e fmov s0, s9
0x234fe3a64: 4141201e fmov s1, s10
0x234fe3a68: 80010094 bl #0x234fe4068
0x234fe3a6c: 692602bd str s9, [x19, #0x224]
0x234fe3a70: 6a2a02bd str s10, [x19, #0x228]
0x234fe3a74: 752202b9 str w21, [x19, #0x220]
0x234fe3a78: 790a41f9 ldr x25, [x19, #0x210]
0x234fe3a7c: 15f0a752 mov w21, #0x3f800000
0x234fe3a80: f52b00b9 str w21, [sp, #0x28]
0x234fe3a84: 700240f9 ldr x16, [x19]
0x234fe3a88: f10313aa mov x17, x19
0x234fe3a8c: 11aef5f2 movk x17, #0xad70, lsl #48
0x234fe3a90: 301ac1da autda x16, x17
0x234fe3a94: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe3a98: e90310aa mov x9, x16
0x234fe3a9c: e4a30091 add x4, sp, #0x28
0x234fe3aa0: e00313aa mov x0, x19
0x234fe3aa4: 41008052 mov w1, #2
0x234fe3aa8: 02008052 mov w2, #0
0x234fe3aac: 03008052 mov w3, #0
0x234fe3ab0: f10309aa mov x17, x9
0x234fe3ab4: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3ab8: 11093fd7 blraa x8, x17
0x234fe3abc: e92b40bd ldr s9, [sp, #0x28]
0x234fe3ac0: f52b00b9 str w21, [sp, #0x28]
0x234fe3ac4: 700240f9 ldr x16, [x19]
0x234fe3ac8: f10313aa mov x17, x19
0x234fe3acc: 11aef5f2 movk x17, #0xad70, lsl #48
0x234fe3ad0: 301ac1da autda x16, x17
0x234fe3ad4: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe3ad8: e90310aa mov x9, x16
0x234fe3adc: e4a30091 add x4, sp, #0x28
0x234fe3ae0: e00313aa mov x0, x19
0x234fe3ae4: 61008052 mov w1, #3
0x234fe3ae8: 02008052 mov w2, #0
0x234fe3aec: 03008052 mov w3, #0
0x234fe3af0: f10309aa mov x17, x9
0x234fe3af4: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3af8: 11093fd7 blraa x8, x17
0x234fe3afc: ea2b40bd ldr s10, [sp, #0x28]
0x234fe3b00: f52b00b9 str w21, [sp, #0x28]
0x234fe3b04: 700240f9 ldr x16, [x19]
0x234fe3b08: f10313aa mov x17, x19
0x234fe3b0c: 11aef5f2 movk x17, #0xad70, lsl #48
0x234fe3b10: 301ac1da autda x16, x17
0x234fe3b14: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe3b18: e90310aa mov x9, x16
0x234fe3b1c: e4a30091 add x4, sp, #0x28
0x234fe3b20: e00313aa mov x0, x19
0x234fe3b24: c1008052 mov w1, #6
0x234fe3b28: 02008052 mov w2, #0
0x234fe3b2c: 03008052 mov w3, #0
0x234fe3b30: f10309aa mov x17, x9
0x234fe3b34: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3b38: 11093fd7 blraa x8, x17
0x234fe3b3c: 76420891 add x22, x19, #0x210
0x234fe3b40: e02b40bd ldr s0, [sp, #0x28]
0x234fe3b44: 0100391e fcvtzu w1, s0
0x234fe3b48: e00319aa mov x0, x25
0x234fe3b4c: 2041201e fmov s0, s9
0x234fe3b50: 4141201e fmov s1, s10
0x234fe3b54: 0241601e fmov d2, d8
0x234fe3b58: 44010094 bl #0x234fe4068
0x234fe3b5c: d90240f9 ldr x25, [x22]
0x234fe3b60: f52b00b9 str w21, [sp, #0x28]
0x234fe3b64: 700240f9 ldr x16, [x19]
0x234fe3b68: f10313aa mov x17, x19
0x234fe3b6c: 11aef5f2 movk x17, #0xad70, lsl #48
0x234fe3b70: 301ac1da autda x16, x17
0x234fe3b74: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe3b78: e90310aa mov x9, x16
0x234fe3b7c: e4a30091 add x4, sp, #0x28
0x234fe3b80: e00313aa mov x0, x19
0x234fe3b84: 81008052 mov w1, #4
0x234fe3b88: 02008052 mov w2, #0
0x234fe3b8c: 03008052 mov w3, #0
0x234fe3b90: f10309aa mov x17, x9
0x234fe3b94: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3b98: 11093fd7 blraa x8, x17
0x234fe3b9c: e82b40bd ldr s8, [sp, #0x28]
0x234fe3ba0: 08f0a752 mov w8, #0x3f800000
0x234fe3ba4: e82b00b9 str w8, [sp, #0x28]
0x234fe3ba8: 700240f9 ldr x16, [x19]
0x234fe3bac: f10313aa mov x17, x19
0x234fe3bb0: 11aef5f2 movk x17, #0xad70, lsl #48
0x234fe3bb4: 301ac1da autda x16, x17
0x234fe3bb8: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe3bbc: e90310aa mov x9, x16
0x234fe3bc0: e4a30091 add x4, sp, #0x28
0x234fe3bc4: e00313aa mov x0, x19
0x234fe3bc8: a1008052 mov w1, #5
0x234fe3bcc: 02008052 mov w2, #0
0x234fe3bd0: 03008052 mov w3, #0
0x234fe3bd4: f10309aa mov x17, x9
0x234fe3bd8: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3bdc: 11093fd7 blraa x8, x17
0x234fe3be0: e12b40bd ldr s1, [sp, #0x28]
0x234fe3be4: e00319aa mov x0, x25
0x234fe3be8: 0041201e fmov s0, s8
0x234fe3bec: 77010094 bl #0x234fe41c8
0x234fe3bf0: c00240f9 ldr x0, [x22]
0x234fe3bf4: 00010094 bl #0x234fe3ff4
0x234fe3bf8: ff2b00b9 str wzr, [sp, #0x28]
0x234fe3bfc: 700240f9 ldr x16, [x19]
0x234fe3c00: f01ac1da autda x16, x23
0x234fe3c04: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe3c08: e90310aa mov x9, x16
0x234fe3c0c: e4a30091 add x4, sp, #0x28
0x234fe3c10: e00313aa mov x0, x19
0x234fe3c14: 21008052 mov w1, #1
0x234fe3c18: 02008052 mov w2, #0
0x234fe3c1c: 03008052 mov w3, #0
0x234fe3c20: f10309aa mov x17, x9
0x234fe3c24: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3c28: 11093fd7 blraa x8, x17
0x234fe3c2c: e02b40bd ldr s0, [sp, #0x28]
0x234fe3c30: c80000d0 adrp x8, #0x234ffd000
0x234fe3c34: 01d547bd ldr s1, [x8, #0x7d4]
0x234fe3c38: 0008211e fmul s0, s0, s1
0x234fe3c3c: 31867d94 bl #0x236f45500
0x234fe3c40: 680a41f9 ldr x8, [x19, #0x210]
0x234fe3c44: 001900bd str s0, [x8, #0x18]
0x234fe3c48: 011d40bd ldr s1, [x8, #0x1c]
0x234fe3c4c: 0008211e fmul s0, s0, s1
0x234fe3c50: 002500bd str s0, [x8, #0x24]
0x234fe3c54: 08f0a752 mov w8, #0x3f800000
0x234fe3c58: e82b00b9 str w8, [sp, #0x28]
0x234fe3c5c: 700240f9 ldr x16, [x19]
0x234fe3c60: f01ac1da autda x16, x23
0x234fe3c64: 080e49f8 ldr x8, [x16, #0x90]!
0x234fe3c68: e90310aa mov x9, x16
0x234fe3c6c: e4a30091 add x4, sp, #0x28
0x234fe3c70: e00313aa mov x0, x19
0x234fe3c74: 01008052 mov w1, #0
0x234fe3c78: 02008052 mov w2, #0
0x234fe3c7c: 03008052 mov w3, #0
0x234fe3c80: f10309aa mov x17, x9
0x234fe3c84: 11d5f0f2 movk x17, #0x86a8, lsl #48
0x234fe3c88: 11093fd7 blraa x8, x17
0x234fe3c8c: 68420891 add x8, x19, #0x210
0x234fe3c90: e02b40bd ldr s0, [sp, #0x28]
0x234fe3c94: c90000d0 adrp x9, #0x234ffd000
0x234fe3c98: 212548bd ldr s1, [x9, #0x824]
0x234fe3c9c: 0008211e fmul s0, s0, s1
0x234fe3ca0: 000140f9 ldr x0, [x8]
0x234fe3ca4: 002000bd str s0, [x0, #0x20]
0x234fe3ca8: 9f0f0071 cmp w28, #3
0x234fe3cac: 23150054 b.lo #0x234fe3f50
0x234fe3cb0: 9f03186b cmp w28, w24
0x234fe3cb4: e1140054 b.ne #0x234fe3f50
0x234fe3cb8: 480340b9 ldr w8, [x26]
0x234fe3cbc: 1f310071 cmp w8, #0xc
0x234fe3cc0: 48170054 b.hi #0x234fe3fa8
0x234fe3cc4: 34170034 cbz w20, #0x234fe3fa8
0x234fe3cc8: 090080d2 mov x9, #0
0x234fe3ccc: 0001231e ucvtf s0, w8
0x234fe3cd0: 00c0211e fsqrt s0, s0
0x234fe3cd4: ca0000d0 adrp x10, #0x234ffd000
0x234fe3cd8: 41d548bd ldr s1, [x10, #0x8d4]
0x234fe3cdc: 0008211e fmul s0, s0, s1
0x234fe3ce0: 01102e1e fmov s1, #1.00000000
0x234fe3ce4: 0ae00091 add x10, x0, #0x38
0x234fe3ce8: eb03142a mov w11, w20
0x234fe3cec: eca30091 add x12, sp, #0x28
0x234fe3cf0: 2018201e fdiv s0, s1, s0
0x234fe3cf4: 8d110091 add x13, x12, #4
0x234fe3cf8: 6e430091 add x14, x27, #0x10
0x234fe3cfc: 4f430091 add x15, x26, #0x10
0x234fe3d00: 10800291 add x16, x0, #0xa0
0x234fe3d04: 110080d2 mov x17, #0
0x234fe3d08: e1a30091 add x1, sp, #0x28
0x234fe3d0c: 0200118b add x2, x0, x17
0x234fe3d10: 440c5329 ldp w4, w3, [x2, #0x98]
0x234fe3d14: 8400030a and w4, w4, w3
0x234fe3d18: 453840f9 ldr x5, [x2, #0x70]
0x234fe3d1c: a15864bc ldr s1, [x5, w4, uxtw #2]
0x234fe3d20: 4308512d ldp s3, s2, [x2, #0x88]
0x234fe3d24: 2108221e fmul s1, s1, s2
0x234fe3d28: 429040bd ldr s2, [x2, #0x90]
0x234fe3d2c: 6208221e fmul s2, s3, s2
0x234fe3d30: 2128221e fadd s1, s1, s2
0x234fe3d34: 214400bc str s1, [x1], #4
0x234fe3d38: 419000bd str s1, [x2, #0x90]
0x234fe3d3c: 63040011 add w3, w3, #1
0x234fe3d40: 439c00b9 str w3, [x2, #0x9c]
0x234fe3d44: 31220191 add x17, x17, #0x48
0x234fe3d48: 3f0212f1 cmp x17, #0x480
0x234fe3d4c: 01feff54 b.ne #0x234fe3d0c
0x234fe3d50: e10b452d ldp s1, s2, [sp, #0x28]
0x234fe3d54: 2328221e fadd s3, s1, s2
0x234fe3d58: e417462d ldp s4, s5, [sp, #0x30]
0x234fe3d5c: e61f472d ldp s6, s7, [sp, #0x38]
0x234fe3d60: f047482d ldp s16, s17, [sp, #0x40]
0x234fe3d64: f24f492d ldp s18, s19, [sp, #0x48]
0x234fe3d68: f4574a2d ldp s20, s21, [sp, #0x50]
0x234fe3d6c: f65f4b2d ldp s22, s23, [sp, #0x58]
0x234fe3d70: 2138221e fsub s1, s1, s2
0x234fe3d74: 8228251e fadd s2, s4, s5
0x234fe3d78: 8438251e fsub s4, s4, s5
0x234fe3d7c: c528271e fadd s5, s6, s7
0x234fe3d80: c638271e fsub s6, s6, s7
0x234fe3d84: 072a311e fadd s7, s16, s17
0x234fe3d88: 103a311e fsub s16, s16, s17
0x234fe3d8c: 512a331e fadd s17, s18, s19
0x234fe3d90: 523a331e fsub s18, s18, s19
0x234fe3d94: 932a351e fadd s19, s20, s21
0x234fe3d98: 943a351e fsub s20, s20, s21
0x234fe3d9c: d52a371e fadd s21, s22, s23
0x234fe3da0: f8674c2d ldp s24, s25, [sp, #0x60]
0x234fe3da4: d63a371e fsub s22, s22, s23
0x234fe3da8: 172b391e fadd s23, s24, s25
0x234fe3dac: 183b391e fsub s24, s24, s25
0x234fe3db0: 7928221e fadd s25, s3, s2
0x234fe3db4: 3a28241e fadd s26, s1, s4
0x234fe3db8: 6238221e fsub s2, s3, s2
0x234fe3dbc: 2138241e fsub s1, s1, s4
0x234fe3dc0: a328271e fadd s3, s5, s7
0x234fe3dc4: c428301e fadd s4, s6, s16
0x234fe3dc8: a538271e fsub s5, s5, s7
0x234fe3dcc: c638301e fsub s6, s6, s16
0x234fe3dd0: 272a331e fadd s7, s17, s19
0x234fe3dd4: 502a341e fadd s16, s18, s20
0x234fe3dd8: 313a331e fsub s17, s17, s19
0x234fe3ddc: 523a341e fsub s18, s18, s20
0x234fe3de0: b32a371e fadd s19, s21, s23
0x234fe3de4: d42a381e fadd s20, s22, s24
0x234fe3de8: b53a371e fsub s21, s21, s23
0x234fe3dec: d63a381e fsub s22, s22, s24
0x234fe3df0: 372b231e fadd s23, s25, s3
0x234fe3df4: 582b241e fadd s24, s26, s4
0x234fe3df8: 5b28251e fadd s27, s2, s5
0x234fe3dfc: 3c28261e fadd s28, s1, s6
0x234fe3e00: 233b231e fsub s3, s25, s3
0x234fe3e04: 443b241e fsub s4, s26, s4
0x234fe3e08: 4238251e fsub s2, s2, s5
0x234fe3e0c: 2138261e fsub s1, s1, s6
0x234fe3e10: e528331e fadd s5, s7, s19
0x234fe3e14: 062a341e fadd s6, s16, s20
0x234fe3e18: 392a351e fadd s25, s17, s21
0x234fe3e1c: 5a2a361e fadd s26, s18, s22
0x234fe3e20: e738331e fsub s7, s7, s19
0x234fe3e24: 103a341e fsub s16, s16, s20
0x234fe3e28: 313a351e fsub s17, s17, s21
0x234fe3e2c: f32a251e fadd s19, s23, s5
0x234fe3e30: 142b261e fadd s20, s24, s6
0x234fe3e34: f353052d stp s19, s20, [sp, #0x28]
0x234fe3e38: 732b391e fadd s19, s27, s25
0x234fe3e3c: 942b3a1e fadd s20, s28, s26
0x234fe3e40: f353062d stp s19, s20, [sp, #0x30]
0x234fe3e44: 7328271e fadd s19, s3, s7
0x234fe3e48: 9428301e fadd s20, s4, s16
0x234fe3e4c: f353072d stp s19, s20, [sp, #0x38]
0x234fe3e50: 523a361e fsub s18, s18, s22
0x234fe3e54: 5328311e fadd s19, s2, s17
0x234fe3e58: 3428321e fadd s20, s1, s18
0x234fe3e5c: f353082d stp s19, s20, [sp, #0x40]
0x234fe3e60: e53a251e fsub s5, s23, s5
0x234fe3e64: 063b261e fsub s6, s24, s6
0x234fe3e68: e51b092d stp s5, s6, [sp, #0x48]
0x234fe3e6c: 653b391e fsub s5, s27, s25
0x234fe3e70: 863b3a1e fsub s6, s28, s26
0x234fe3e74: e51b0a2d stp s5, s6, [sp, #0x50]
0x234fe3e78: 6338271e fsub s3, s3, s7
0x234fe3e7c: 8438301e fsub s4, s4, s16
0x234fe3e80: e3130b2d stp s3, s4, [sp, #0x58]
0x234fe3e84: 4238311e fsub s2, s2, s17
0x234fe3e88: 2138321e fsub s1, s1, s18
0x234fe3e8c: e2070c2d stp s2, s1, [sp, #0x60]
0x234fe3e90: 88030034 cbz w8, #0x234fe3f00
0x234fe3e94: f1030faa mov x17, x15
0x234fe3e98: 01e4002f movi d1, #0000000000000000
0x234fe3e9c: e1030eaa mov x1, x14
0x234fe3ea0: e2030daa mov x2, x13
0x234fe3ea4: e30308aa mov x3, x8
0x234fe3ea8: e4030aaa mov x4, x10
0x234fe3eac: 250641f8 ldr x5, [x17], #0x10
0x234fe3eb0: 260441f8 ldr x6, [x1], #0x10
0x234fe3eb4: a27869bc ldr s2, [x5, x9, lsl #2]
0x234fe3eb8: 2128221e fadd s1, s1, s2
0x234fe3ebc: 424440bc ldr s2, [x2], #4
0x234fe3ec0: 0310452d ldp s3, s4, [x0, #0x28]
0x234fe3ec4: 4308231e fmul s3, s2, s3
0x234fe3ec8: 850040bd ldr s5, [x4]
0x234fe3ecc: 8408251e fmul s4, s4, s5
0x234fe3ed0: 824400bc str s2, [x4], #4
0x234fe3ed4: 6228241e fadd s2, s3, s4
0x234fe3ed8: 040c442d ldp s4, s3, [x0, #0x20]
0x234fe3edc: 4208231e fmul s2, s2, s3
0x234fe3ee0: a37869bc ldr s3, [x5, x9, lsl #2]
0x234fe3ee4: 4238231e fsub s2, s2, s3
0x234fe3ee8: 8208221e fmul s2, s4, s2
0x234fe3eec: 6228221e fadd s2, s3, s2
0x234fe3ef0: c27829bc str s2, [x6, x9, lsl #2]
0x234fe3ef4: 630400f1 subs x3, x3, #1
0x234fe3ef8: a1fdff54 b.ne #0x234fe3eac
0x234fe3efc: 02000014 b #0x234fe3f04
0x234fe3f00: 01e4002f movi d1, #0000000000000000
0x234fe3f04: 110080d2 mov x17, #0
0x234fe3f08: 0108211e fmul s1, s0, s1
0x234fe3f0c: e10310aa mov x1, x16
0x234fe3f10: 826971bc ldr s2, [x12, x17]
0x234fe3f14: 2228221e fadd s2, s1, s2
0x234fe3f18: 220040b9 ldr w2, [x1]
0x234fe3f1c: 23805fb8 ldur w3, [x1, #-8]
0x234fe3f20: 6300020a and w3, w3, w2
0x234fe3f24: 24005df8 ldur x4, [x1, #-0x30]
0x234fe3f28: 825823bc str s2, [x4, w3, uxtw #2]
0x234fe3f2c: 42040011 add w2, w2, #1
0x234fe3f30: 228404b8 str w2, [x1], #0x48
0x234fe3f34: 31120091 add x17, x17, #4
0x234fe3f38: 3f0201f1 cmp x17, #0x40
0x234fe3f3c: a1feff54 b.ne #0x234fe3f10
0x234fe3f40: 29050091 add x9, x9, #1
0x234fe3f44: 3f010beb cmp x9, x11
0x234fe3f48: e1edff54 b.ne #0x234fe3d04
0x234fe3f4c: 17000014 b #0x234fe3fa8
0x234fe3f50: e50b40f9 ldr x5, [sp, #0x10]
0x234fe3f54: 050100b4 cbz x5, #0x234fe3f74
0x234fe3f58: e21340f9 ldr x2, [sp, #0x20]
0x234fe3f5c: e30740f9 ldr x3, [sp, #8]
0x234fe3f60: c30100b4 cbz x3, #0x234fe3f98
0x234fe3f64: e10314aa mov x1, x20
0x234fe3f68: e40f40f9 ldr x4, [sp, #0x18]
0x234fe3f6c: ec000094 bl #0x234fe431c
0x234fe3f70: 0e000014 b #0x234fe3fa8
0x234fe3f74: e10314aa mov x1, x20
0x234fe3f78: e38b41a9 ldp x3, x2, [sp, #0x18]
0x234fe3f7c: 2e020094 bl #0x234fe4834
0x234fe3f80: 0a000014 b #0x234fe3fa8
0x234fe3f84: e10315aa mov x1, x21
0x234fe3f88: e20313aa mov x2, x19
0x234fe3f8c: 5c610094 bl #0x234ffc4fc
0x234fe3f90: 00008052 mov w0, #0
0x234fe3f94: 3efeff17 b #0x234fe388c
0x234fe3f98: e10314aa mov x1, x20
0x234fe3f9c: e30f40f9 ldr x3, [sp, #0x18]
0x234fe3fa0: e40305aa mov x4, x5
0x234fe3fa4: 83010094 bl #0x234fe45b0
0x234fe3fa8: 69c24839 ldrb w9, [x19, #0x230]
0x234fe3fac: 68c64839 ldrb w8, [x19, #0x231]
0x234fe3fb0: 3f050071 cmp w9, #1
0x234fe3fb4: 81000054 b.ne #0x234fe3fc4
0x234fe3fb8: 88000037 tbnz w8, #0, #0x234fe3fc8
0x234fe3fbc: 02008052 mov w2, #0
0x234fe3fc0: 03000014 b #0x234fe3fcc
0x234fe3fc4: a8000036 tbz w8, #0, #0x234fe3fd8
0x234fe3fc8: 22008052 mov w2, #1
0x234fe3fcc: e00313aa mov x0, x19
0x234fe3fd0: e10314aa mov x1, x20
0x234fe3fd4: b0020094 bl #0x234fe4a94
0x234fe3fd8: 00008052 mov w0, #0
0x234fe3fdc: 68c24839 ldrb w8, [x19, #0x230]
0x234fe3fe0: 68c60839 strb w8, [x19, #0x231]
0x234fe3fe4: 2afeff17 b #0x234fe388c
0x234fe3fe8: 29feff17 b #0x234fe388c
0x234fe3fec: 59857d94 bl #0x236f45550
0x234fe3ff0: e8807d94 bl #0x236f44390
