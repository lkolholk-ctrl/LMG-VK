; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27221dac0
; Symbol: FUN_27221dac0
; Signature: undefined FUN_27221dac0()
27221dac0: pacibsp
27221dac4: sub sp,sp,#0x1d0
27221dac8: stp d9,d8,[sp, #0x170]
27221dacc: stp x26,x25,[sp, #0x180]
27221dad0: stp x24,x23,[sp, #0x190]
27221dad4: stp x22,x21,[sp, #0x1a0]
27221dad8: stp x20,x19,[sp, #0x1b0]
27221dadc: stp x29,x30,[sp, #0x1c0]
27221dae0: add x29,sp,#0x1c0
27221dae4: mov x20,x1
27221dae8: mov x22,x0
27221daec: mov x19,x8
27221daf0: str x0,[sp, #0x50]
27221daf4: str x1,[sp, #0xc0]
27221daf8: adrp x16,0x27221e000
27221dafc: add x16,x16,#0x1e0
27221db00: mov x17,#0x20d4
27221db04: pacia x16,x17
27221db08: mov x0,x16
27221db0c: add x1,sp,#0x40
27221db10: mov x2,x20
27221db14: mov x21,#0x0
27221db18: bl 0x27221e200
27221db1c: mov x2,x0
27221db20: mov x9,x1
27221db24: add x25,sp,#0xc0
27221db28: str x25,[sp, #0x30]
27221db2c: adrp x16,0x27221e000
27221db30: add x16,x16,#0x34c
27221db34: mov x17,#0xd983
27221db38: pacia x16,x17
27221db3c: mov x0,x16
27221db40: sub x8,x29,#0x78
27221db44: add x1,sp,#0x20
27221db48: and w3,w9,#0x1
27221db4c: sub x4,x29,#0x88
27221db50: bl 0x27221e2c0
27221db54: str x22,[sp, #0x10]
27221db58: adrp x16,0x27221e000
27221db5c: add x16,x16,#0x36c
27221db60: mov x17,#0x20d4
27221db64: pacia x16,x17
27221db68: mov x0,x16
27221db6c: adrp x16,0x272213000
27221db70: add x16,x16,#0x6e0
27221db74: mov x17,#0xde25
27221db78: pacia x16,x17
27221db7c: mov x3,x16
27221db80: sub x8,x29,#0xb0
27221db84: mov x1,sp
27221db88: mov x2,x20
27221db8c: bl 0x27221aca4
27221db90: sub x0,x29,#0x78
27221db94: add x1,sp,#0xc0
27221db98: bl 0x27221e0d8
27221db9c: sub x0,x29,#0xb0
27221dba0: add x1,x25,#0x28
27221dba4: bl 0x27221e0d8
27221dba8: ldr x8,[sp, #0xd8]
27221dbac: cbz x8,0x27221dd50
27221dbb0: ldr x8,[sp, #0x100]
27221dbb4: cbz x8,0x27221dda0
27221dbb8: add x23,sp,#0x98
27221dbbc: add x0,sp,#0xc0
27221dbc0: add x1,sp,#0x98
27221dbc4: bl 0x2722136e0
27221dbc8: add x24,sp,#0x70
27221dbcc: add x0,x25,#0x28
27221dbd0: add x1,sp,#0x70
27221dbd4: bl 0x2722136e0
27221dbd8: ldp x21,x20,[sp, #0xb0]
27221dbdc: add x0,sp,#0x98
27221dbe0: mov x1,x21
27221dbe4: bl 0x27220ff94
27221dbe8: ldr x8,[x20, #0x8]
27221dbec: ldr x1,[x8, #0x8]
27221dbf0: mov x9,x1
27221dbf4: ldr x10,[x9, #0x8]!
27221dbf8: add x8,sp,#0x68
27221dbfc: mov x20,x0
27221dc00: mov x0,x21
27221dc04: mov x17,x9
27221dc08: movk x17,#0x96a8, LSL #48
27221dc0c: blraa x10,x17
27221dc10: ldr d8,[sp, #0x68]
27221dc14: ldp x21,x20,[x22, #0x18]
27221dc18: mov x0,x22
27221dc1c: mov x1,x21
27221dc20: bl 0x27220ff94
27221dc24: ldr x8,[x20, #0x8]
27221dc28: ldr x8,[x8, #0x8]
27221dc2c: ldr x8,[x8, #0x8]
27221dc30: ldr x1,[x8, #0x8]
27221dc34: mov x9,x1
27221dc38: ldr x10,[x9, #0x8]!
27221dc3c: add x8,sp,#0x60
27221dc40: mov x20,x0
27221dc44: mov x0,x21
27221dc48: mov x17,x9
27221dc4c: movk x17,#0x96a8, LSL #48
27221dc50: blraa x10,x17
27221dc54: ldr d0,[sp, #0x60]
27221dc58: fsub d8,d0,d8
27221dc5c: ldp x21,x20,[x22, #0x18]
27221dc60: mov x0,x22
27221dc64: mov x1,x21
27221dc68: bl 0x27220ff94
27221dc6c: ldr x8,[x20, #0x8]
27221dc70: ldr x8,[x8, #0x8]
27221dc74: ldr x8,[x8, #0x8]
27221dc78: ldr x1,[x8, #0x8]
27221dc7c: mov x9,x1
27221dc80: ldr x10,[x9, #0x8]!
27221dc84: add x8,sp,#0x68
27221dc88: mov x20,x0
27221dc8c: mov x0,x21
27221dc90: mov x17,x9
27221dc94: movk x17,#0x96a8, LSL #48
27221dc98: blraa x10,x17
27221dc9c: ldr d9,[sp, #0x68]
27221dca0: ldp x21,x20,[sp, #0x88]
27221dca4: add x0,sp,#0x70
27221dca8: mov x1,x21
27221dcac: bl 0x27220ff94
27221dcb0: ldr x8,[x20, #0x8]
27221dcb4: ldr x1,[x8, #0x8]
27221dcb8: mov x9,x1
27221dcbc: ldr x10,[x9, #0x8]!
27221dcc0: add x8,sp,#0x60
27221dcc4: mov x20,x0
27221dcc8: mov x0,x21
27221dccc: mov x17,x9
27221dcd0: movk x17,#0x96a8, LSL #48
27221dcd4: blraa x10,x17
27221dcd8: adrp x20,0x280c99000
27221dcdc: add x20,x20,#0x548
27221dce0: adrp x21,0x280c99000
27221dce4: add x21,x21,#0x440
27221dce8: adrp x22,0x2722a3000
27221dcec: add x22,x22,#0x49c
27221dcf0: sub x0,x29,#0xb0
27221dcf4: mov x1,x20
27221dcf8: mov x2,x21
27221dcfc: mov x3,x22
27221dd00: bl 0x27221e520
27221dd04: sub x0,x29,#0x78
27221dd08: mov x1,x20
27221dd0c: mov x2,x21
27221dd10: mov x3,x22
27221dd14: bl 0x27221e520
27221dd18: ldr d0,[sp, #0x60]
27221dd1c: fsub d0,d0,d9
27221dd20: adrp x8,0x272298000
27221dd24: ldr d1,[x8, #0x68]
27221dd28: fadd d0,d0,d1
27221dd2c: fcmp d8,d0
27221dd30: csel x0,x23,x24,mi
27221dd34: mov x1,x19
27221dd38: bl 0x27221361c
27221dd3c: add x0,sp,#0x70
27221dd40: bl 0x272210e78
27221dd44: add x0,sp,#0x98
27221dd48: bl 0x272210e78
27221dd4c: b 0x27221ddfc
27221dd50: adrp x20,0x280c99000
27221dd54: add x20,x20,#0x548
27221dd58: adrp x21,0x280c99000
27221dd5c: add x21,x21,#0x440
27221dd60: adrp x22,0x2722a3000
27221dd64: add x22,x22,#0x49c
27221dd68: sub x0,x29,#0xb0
27221dd6c: mov x1,x20
27221dd70: mov x2,x21
27221dd74: mov x3,x22
27221dd78: bl 0x27221e520
27221dd7c: sub x0,x29,#0x78
27221dd80: mov x1,x20
27221dd84: mov x2,x21
27221dd88: mov x3,x22
27221dd8c: bl 0x27221e520
27221dd90: ldr x8,[sp, #0x100]
27221dd94: cbz x8,0x27221ddf0
27221dd98: add x0,x25,#0x28
27221dd9c: b 0x27221dde4
27221dda0: adrp x20,0x280c99000
27221dda4: add x20,x20,#0x548
27221dda8: adrp x21,0x280c99000
27221ddac: add x21,x21,#0x440
27221ddb0: adrp x22,0x2722a3000
27221ddb4: add x22,x22,#0x49c
27221ddb8: sub x0,x29,#0xb0
27221ddbc: mov x1,x20
27221ddc0: mov x2,x21
27221ddc4: mov x3,x22
27221ddc8: bl 0x27221e520
27221ddcc: sub x0,x29,#0x78
27221ddd0: mov x1,x20
27221ddd4: mov x2,x21
27221ddd8: mov x3,x22
27221dddc: bl 0x27221e520
27221dde0: add x0,sp,#0xc0
27221dde4: mov x1,x19
27221dde8: bl 0x2722136e0
27221ddec: b 0x27221ddfc
27221ddf0: str xzr,[x19, #0x20]
27221ddf4: movi v0.2D,#0x0
27221ddf8: stp q0,q0,[x19]
27221ddfc: ldp x29,x30,[sp, #0x1c0]
27221de00: ldp x20,x19,[sp, #0x1b0]
27221de04: ldp x22,x21,[sp, #0x1a0]
27221de08: ldp x24,x23,[sp, #0x190]
27221de0c: ldp x26,x25,[sp, #0x180]
27221de10: ldp d9,d8,[sp, #0x170]
27221de14: add sp,sp,#0x1d0
27221de18: retab

