; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27221eb58
; Symbol: FUN_27221eb58
; Signature: undefined FUN_27221eb58()
27221eb58: pacibsp
27221eb5c: stp x24,x23,[sp, #-0x40]!
27221eb60: stp x22,x21,[sp, #0x10]
27221eb64: stp x20,x19,[sp, #0x20]
27221eb68: stp x29,x30,[sp, #0x30]
27221eb6c: add x29,sp,#0x30
27221eb70: mov x19,x1
27221eb74: mov x20,x0
27221eb78: and w8,w1,#0xff00
27221eb7c: ubfx w22,w0,#0x8,#0x8
27221eb80: cmp w22,w8, LSR #0x8
27221eb84: b.ne 0x27221eb9c
27221eb88: eor w8,w19,w20
27221eb8c: and w8,w8,#0xff
27221eb90: cbnz w8,0x27221eb9c
27221eb94: mov w0,#0x0
27221eb98: b 0x27221ec28
27221eb9c: ubfx w23,w19,#0x8,#0x8
27221eba0: cbz w22,0x27221ebbc
27221eba4: cmp w22,#0x1
27221eba8: b.ne 0x27221ec24
27221ebac: cmp w23,#0x1
27221ebb0: b.eq 0x27221ec08
27221ebb4: cbz w23,0x27221ebc8
27221ebb8: b 0x27221ec24
27221ebbc: cbz w23,0x27221ec08
27221ebc0: cmp w23,#0x1
27221ebc4: b.ne 0x27221ec24
27221ebc8: mov x0,x20
27221ebcc: bl 0x27221ec70
27221ebd0: tbz w1,#0x0,0x27221ebe4
27221ebd4: cbz w22,0x27221ec04
27221ebd8: cmp w23,#0x1
27221ebdc: b.eq 0x27221ec08
27221ebe0: b 0x27221ec24
27221ebe4: mov x21,x0
27221ebe8: mov x0,x19
27221ebec: bl 0x27221ec70
27221ebf0: tbnz w1,#0x0,0x27221ebd4
27221ebf4: cmp x21,x0
27221ebf8: b.ne 0x27221ebd4
27221ebfc: mov w0,#0x1
27221ec00: b 0x27221ec28
27221ec04: cbnz w23,0x27221ec24
27221ec08: mov x0,x20
27221ec0c: bl 0x27221ec70
27221ec10: tbnz w1,#0x0,0x27221ec24
27221ec14: mov x20,x0
27221ec18: mov x0,x19
27221ec1c: bl 0x27221ec70
27221ec20: tbz w1,#0x0,0x27221ec3c
27221ec24: mov w0,#0x3
27221ec28: ldp x29,x30,[sp, #0x30]
27221ec2c: ldp x20,x19,[sp, #0x20]
27221ec30: ldp x22,x21,[sp, #0x10]
27221ec34: ldp x24,x23,[sp], #0x40
27221ec38: retab
27221ec3c: cmp x0,x20
27221ec40: csel x8,x0,x20,gt
27221ec44: csel x9,x0,x20,lt
27221ec48: cbnz x9,0x27221ec54
27221ec4c: cmp x8,#0xb
27221ec50: b.eq 0x27221ec64
27221ec54: adds x9,x9,#0x1
27221ec58: b.vs 0x27221ec6c
27221ec5c: cmp x8,x9
27221ec60: b.ne 0x27221ec24
27221ec64: mov w0,#0x2
27221ec68: b 0x27221ec28
27221ec6c: brk #0x1

