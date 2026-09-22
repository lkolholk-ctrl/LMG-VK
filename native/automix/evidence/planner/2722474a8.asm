; Binary: _SonicKit_MusicKit_Packages
; Address: 0x2722474a8
; Symbol: FUN_2722474a8
; Signature: undefined FUN_2722474a8()
2722474a8: pacibsp
2722474ac: stp x23,x22,[sp, #-0x30]!
2722474b0: stp x20,x19,[sp, #0x10]
2722474b4: stp x29,x30,[sp, #0x20]
2722474b8: add x29,sp,#0x20
2722474bc: mov x19,x21
2722474c0: mov x20,x1
2722474c4: ldp x22,x23,[x0, #0x18]
2722474c8: mov x1,x22
2722474cc: bl 0x27220ff94
2722474d0: mov x1,x0
2722474d4: mov x8,x23
2722474d8: ldr x9,[x8, #0x20]!
2722474dc: mov x0,x20
2722474e0: mov x20,x1
2722474e4: mov x1,x22
2722474e8: mov x2,x23
2722474ec: mov x17,x8
2722474f0: movk x17,#0x7b1e, LSL #48
2722474f4: blraa x9,x17
2722474f8: and w0,w0,#0x1
2722474fc: mov x21,x19
272247500: ldp x29,x30,[sp, #0x20]
272247504: ldp x20,x19,[sp, #0x10]
272247508: ldp x23,x22,[sp], #0x30
27224750c: retab

