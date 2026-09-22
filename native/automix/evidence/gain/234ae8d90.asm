0x234ae8d90: ff1440f1 cmp x7, #5, lsl #12
0x234ae8d94: a2000054 b.hs #0x234ae8da8
0x234ae8d98: d0071eca eor x16, x30, x30, lsl #1
0x234ae8d9c: 5000f0b6 tbz x16, #0x3e, #0x234ae8da4
0x234ae8da0: 208e38d4 brk #0xc471
0x234ae8da4: 03f8ff17 b #0x234ae6db0
0x234ae8da8: 3f0400f1 cmp x1, #1
0x234ae8dac: 61ffff54 b.ne #0x234ae8d98
0x234ae8db0: 9f0400f1 cmp x4, #1
0x234ae8db4: 21ffff54 b.ne #0x234ae8d98
0x234ae8db8: df0400f1 cmp x6, #1
0x234ae8dbc: e1feff54 b.ne #0x234ae8d98
0x234ae8dc0: 7f2303d5 pacibsp 
0x234ae8dc4: fc6fbaa9 stp x28, x27, [sp, #-0x60]!
0x234ae8dc8: fa6701a9 stp x26, x25, [sp, #0x10]
0x234ae8dcc: f85f02a9 stp x24, x23, [sp, #0x20]
0x234ae8dd0: f65703a9 stp x22, x21, [sp, #0x30]
0x234ae8dd4: f44f04a9 stp x20, x19, [sp, #0x40]
0x234ae8dd8: fd7b05a9 stp x29, x30, [sp, #0x50]
0x234ae8ddc: fd430191 add x29, sp, #0x50
0x234ae8de0: f80300aa mov x24, x0
0x234ae8de4: f70303aa mov x23, x3
0x234ae8de8: b5fc0191 add x21, x5, #0x7f
0x234ae8dec: b3e27992 and x19, x21, #0xffffffffffffff80
0x234ae8df0: 680205eb subs x8, x19, x5
0x234ae8df4: 14fd4293 asr x20, x8, #2
0x234ae8df8: f90307aa mov x25, x7
0x234ae8dfc: f60014cb sub x22, x7, x20
0x234ae8e00: fa0305aa mov x26, x5
0x234ae8e04: fb0302aa mov x27, x2
0x234ae8e08: ffadff97 bl #0x234ad4604
0x234ae8e0c: e9031aaa mov x9, x26
0x234ae8e10: 2102a0d2 mov x1, #0x110000
0x234ae8e14: 0100f0f2 movk x1, #0x8000, lsl #48
0x234ae8e18: c8124092 and x8, x22, #0x1f
0x234ae8e1c: 1f2003d5 nop 
0x234ae8e20: 1f2003d5 nop 
0x234ae8e24: 1f2003d5 nop 
