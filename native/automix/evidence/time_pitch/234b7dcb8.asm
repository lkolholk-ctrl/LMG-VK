; Original vDSP 0x234b7dcb8..0x234b7dcf4
; SHA256 e728d5c2de29c78c0e994868290426ec8ad0a60c926978cc9f975b2808441140
0x234b7dcb8: 7f2303d5 pacibsp 
0x234b7dcbc: fd7bbfa9 stp x29, x30, [sp, #-0x10]!
0x234b7dcc0: fd030091 mov x29, sp
0x234b7dcc4: b7fd8e94 bl #0x236f3d3a0
0x234b7dcc8: 1f0864f2 tst x0, #0x70000000
0x234b7dccc: 10fbffb0 adrp x16, #0x234ade000
0x234b7dcd0: 10821491 add x16, x16, #0x520
0x234b7dcd4: f023c1da paciza x16
0x234b7dcd8: e80310aa mov x8, x16
0x234b7dcdc: 10fbffb0 adrp x16, #0x234ade000
0x234b7dce0: 10c23391 add x16, x16, #0xcf0
0x234b7dce4: f023c1da paciza x16
0x234b7dce8: 0002889a csel x0, x16, x8, eq
0x234b7dcec: fd7bc1a8 ldp x29, x30, [sp], #0x10
0x234b7dcf0: ff0f5fd6 retab 
