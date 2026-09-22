; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27221ec70
; Symbol: FUN_27221ec70
; Signature: undefined FUN_27221ec70()
27221ec70: mov x8,x0
27221ec74: ubfx w9,w0,#0x8,#0x8
27221ec78: cbz w9,0x27221ec90
27221ec7c: cmp w9,#0x2
27221ec80: b.ne 0x27221eccc
27221ec84: mov x0,#0x0
27221ec88: mov w1,#0x1
27221ec8c: ret
27221ec90: mov x0,#0x0
27221ec94: and w8,w8,#0xff
27221ec98: adrp x9,0x2884aa000
27221ec9c: add x9,x9,#0x481
27221eca0: ldurb w10,[x9, #-0x1]
27221eca4: ldrb w11,[x9]
27221eca8: tst w11,#0xff
27221ecac: ccmp w10,w8,#0x0,eq
27221ecb0: cset w1,ne
27221ecb4: b.eq 0x27221ed0c
27221ecb8: add x9,x9,#0x2
27221ecbc: add x0,x0,#0x1
27221ecc0: cmp x0,#0xc
27221ecc4: b.ne 0x27221eca0
27221ecc8: b 0x27221ed08
27221eccc: mov x0,#0x0
27221ecd0: and w8,w8,#0xff
27221ecd4: adrp x9,0x2884aa000
27221ecd8: add x9,x9,#0x4b9
27221ecdc: ldurb w10,[x9, #-0x1]
27221ece0: ldrb w11,[x9]
27221ece4: and w11,w11,#0xff
27221ece8: cmp w10,w8
27221ecec: ccmp w11,#0x1,#0x0,eq
27221ecf0: cset w1,ne
27221ecf4: b.eq 0x27221ed0c
27221ecf8: add x9,x9,#0x2
27221ecfc: add x0,x0,#0x1
27221ed00: cmp x0,#0xc
27221ed04: b.ne 0x27221ecdc
27221ed08: mov x0,#0x0
27221ed0c: ret

