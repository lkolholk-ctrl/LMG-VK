; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fd4ac0..0x234fd4b78
; Code SHA256: cb565ab1046590dbfb7d8d43ffab4556d041351c0cea03c583f067ca6fcf9697
0x234fd4ac0: 48fe9f52 mov w8, #0xfff2
0x234fd4ac4: 1f00086b cmp w0, w8
0x234fd4ac8: 03030054 b.lo #0x234fd4b28
0x234fd4acc: 00000032 orr w0, w0, #1
0x234fd4ad0: 690100d0 adrp x9, #0x235002000
0x234fd4ad4: 29113c91 add x9, x9, #0xf04
0x234fd4ad8: aa318352 mov w10, #0x198d
0x234fd4adc: e80300aa mov x8, x0
0x234fd4ae0: 2b008052 mov w11, #1
0x234fd4ae4: 2c008052 mov w12, #1
0x234fd4ae8: 2d796b78 ldrh w13, [x9, x11, lsl #1]
0x234fd4aec: ae7d0d1b mul w14, w13, w13
0x234fd4af0: df01086b cmp w14, w8
0x234fd4af4: 48010054 b.hi #0x234fd4b1c
0x234fd4af8: 0e09cd1a udiv w14, w8, w13
0x234fd4afc: cda10d1b msub w13, w14, w13, w8
0x234fd4b00: bf010071 cmp w13, #0
0x234fd4b04: ee079f1a cset w14, ne
0x234fd4b08: cc010c0a and w12, w14, w12
0x234fd4b0c: bf010071 cmp w13, #0
0x234fd4b10: 62114afa ccmp x11, x10, #2, ne
0x234fd4b14: 6b050091 add x11, x11, #1
0x234fd4b18: 83feff54 b.lo #0x234fd4ae8
0x234fd4b1c: ac020037 tbnz w12, #0, #0x234fd4b70
0x234fd4b20: 08090031 adds w8, w8, #2
0x234fd4b24: e1fdff54 b.ne #0x234fd4ae0
0x234fd4b28: 1f0c0071 cmp w0, #3
0x234fd4b2c: 03020054 b.lo #0x234fd4b6c
0x234fd4b30: 09008052 mov w9, #0
0x234fd4b34: aa318352 mov w10, #0x198d
0x234fd4b38: 6b0100d0 adrp x11, #0x235002000
0x234fd4b3c: 6b113c91 add x11, x11, #0xf04
0x234fd4b40: 4801090b add w8, w10, w9
0x234fd4b44: 087d480b add w8, w8, w8, lsr #31
0x234fd4b48: 087d0113 asr w8, w8, #1
0x234fd4b4c: 6cd96878 ldrh w12, [x11, w8, sxtw #1]
0x234fd4b50: 1f000c6b cmp w0, w12
0x234fd4b54: 2995881a csinc w9, w9, w8, ls
0x234fd4b58: 4a81881a csel w10, w10, w8, hi
0x234fd4b5c: 68d96978 ldrh w8, [x11, w9, sxtw #1]
0x234fd4b60: 1f00086b cmp w0, w8
0x234fd4b64: e8feff54 b.hi #0x234fd4b40
0x234fd4b68: 02000014 b #0x234fd4b70
0x234fd4b6c: 48008052 mov w8, #2
0x234fd4b70: e00308aa mov x0, x8
0x234fd4b74: c0035fd6 ret 
