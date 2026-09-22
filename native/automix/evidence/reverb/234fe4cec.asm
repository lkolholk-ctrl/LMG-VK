; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe4cec..0x234fe4d2c
; Code SHA256: b705f5469aba301eb58ad900c48981a7654151665c6aa85a3dc19e6d6ab49261
0x234fe4cec: 7f2303d5 pacibsp 
0x234fe4cf0: f44fbea9 stp x20, x19, [sp, #-0x20]!
0x234fe4cf4: fd7b01a9 stp x29, x30, [sp, #0x10]
0x234fe4cf8: fd430091 add x29, sp, #0x10
0x234fe4cfc: f40301aa mov x20, x1
0x234fe4d00: f30300aa mov x19, x0
0x234fe4d04: 9522fd97 bl #0x234f2d758
0x234fe4d08: 880a0051 sub w8, w20, #2
0x234fe4d0c: 1f110071 cmp w8, #4
0x234fe4d10: 88000054 b.hi #0x234fe4d20
0x234fe4d14: 681a42b9 ldr w8, [x19, #0x218]
0x234fe4d18: 08050011 add w8, w8, #1
0x234fe4d1c: 681a02b9 str w8, [x19, #0x218]
0x234fe4d20: fd7b41a9 ldp x29, x30, [sp, #0x10]
0x234fe4d24: f44fc2a8 ldp x20, x19, [sp], #0x20
0x234fe4d28: ff0f5fd6 retab 
