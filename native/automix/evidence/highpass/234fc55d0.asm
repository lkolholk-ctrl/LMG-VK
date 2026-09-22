; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fc55d0..0x234fc5654
; Code SHA256: 1caf5c99f3172deb4a64393b38dd80a3500fe6c1d1d1809861daa8a6d8e4d05f
0x234fc55d0: 7f2303d5 pacibsp 
0x234fc55d4: f44fbea9 stp x20, x19, [sp, #-0x20]!
0x234fc55d8: fd7b01a9 stp x29, x30, [sp, #0x10]
0x234fc55dc: fd430091 add x29, sp, #0x10
0x234fc55e0: f30300aa mov x19, x0
0x234fc55e4: f40308aa mov x20, x8
0x234fc55e8: 21c784d2 mov x1, #0x2639
0x234fc55ec: 6184b3f2 movk x1, #0x9c23, lsl #16
0x234fc55f0: 0188c3f2 movk x1, #0x1c40, lsl #32
0x234fc55f4: 4121e0f2 movk x1, #0x10a, lsl #48
0x234fc55f8: 000e8052 mov w0, #0x70
0x234fc55fc: 20da0094 bl #0x234ffbe7c
0x234fc5600: 1f1000b9 str wzr, [x0, #0x10]
0x234fc5604: 50d827f0 adrp x16, #0x284ad0000
0x234fc5608: 10421b91 add x16, x16, #0x6d0
0x234fc560c: 10420091 add x16, x16, #0x10
0x234fc5610: f10300aa mov x17, x0
0x234fc5614: 9179e7f2 movk x17, #0x3bcc, lsl #48
0x234fc5618: 300ac1da pacda x16, x17
0x234fc561c: 104c00a9 stp x16, x19, [x0]
0x234fc5620: 1ffc01a9 stp xzr, xzr, [x0, #0x18]
0x234fc5624: 1f7c03a9 stp xzr, xzr, [x0, #0x30]
0x234fc5628: 08fee7d2 mov x8, #0x3ff0000000000000
0x234fc562c: 081400f9 str x8, [x0, #0x28]
0x234fc5630: 00e4006f movi v0.2d, #0000000000000000
0x234fc5634: 000002ad stp q0, q0, [x0, #0x40]
0x234fc5638: 133000f9 str x19, [x0, #0x60]
0x234fc563c: 08008012 mov w8, #-1
0x234fc5640: 086800b9 str w8, [x0, #0x68]
0x234fc5644: 800200f9 str x0, [x20]
0x234fc5648: fd7b41a9 ldp x29, x30, [sp, #0x10]
0x234fc564c: f44fc2a8 ldp x20, x19, [sp], #0x20
0x234fc5650: ff0f5fd6 retab 
