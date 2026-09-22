0x234acac84: bf1440f1 cmp x5, #5, lsl #12
0x234acac88: a2000054 b.hs #0x234acac9c
0x234acac8c: d0071eca eor x16, x30, x30, lsl #1
0x234acac90: 5000f0b6 tbz x16, #0x3e, #0x234acac98
0x234acac94: 208e38d4 brk #0xc471
0x234acac98: f6020014 b #0x234acb870
0x234acac9c: 3f0400f1 cmp x1, #1
0x234acaca0: 61ffff54 b.ne #0x234acac8c
0x234acaca4: 9f0400f1 cmp x4, #1
0x234acaca8: 21ffff54 b.ne #0x234acac8c
0x234acacac: 7f2303d5 pacibsp 
0x234acacb0: fa67bba9 stp x26, x25, [sp, #-0x50]!
0x234acacb4: f85f01a9 stp x24, x23, [sp, #0x10]
0x234acacb8: f65702a9 stp x22, x21, [sp, #0x20]
0x234acacbc: f44f03a9 stp x20, x19, [sp, #0x30]
0x234acacc0: fd7b04a9 stp x29, x30, [sp, #0x40]
0x234acacc4: fd030191 add x29, sp, #0x40
0x234acacc8: f70300aa mov x23, x0
0x234acaccc: 75fc0191 add x21, x3, #0x7f
0x234acacd0: b3e27992 and x19, x21, #0xffffffffffffff80
0x234acacd4: 680203eb subs x8, x19, x3
0x234acacd8: 14fd4293 asr x20, x8, #2
0x234acacdc: f80305aa mov x24, x5
0x234acace0: b60014cb sub x22, x5, x20
0x234acace4: f90303aa mov x25, x3
0x234acace8: fa0302aa mov x26, x2
0x234acacec: 46260094 bl #0x234ad4604
0x234acacf0: e90319aa mov x9, x25
0x234acacf4: 0d00a1d2 mov x13, #0x8000000
0x234acacf8: 0d00f0f2 movk x13, #0x8000, lsl #48
0x234acacfc: c8124092 and x8, x22, #0x1f
0x234acad00: 1f2003d5 nop 
0x234acad04: 1f2003d5 nop 
0x234acad08: 1f2003d5 nop 
