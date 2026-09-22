; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fc5654..0x234fc56cc
; Code SHA256: ad380740aee1771358e592e0d9992ac31b8f118c042e55164ec155adc00f3e27
0x234fc5654: 7f2303d5 pacibsp 
0x234fc5658: f657bda9 stp x22, x21, [sp, #-0x30]!
0x234fc565c: f44f01a9 stp x20, x19, [sp, #0x10]
0x234fc5660: fd7b02a9 stp x29, x30, [sp, #0x20]
0x234fc5664: fd830091 add x29, sp, #0x20
0x234fc5668: f30303aa mov x19, x3
0x234fc566c: f40302aa mov x20, x2
0x234fc5670: f50301aa mov x21, x1
0x234fc5674: f60300aa mov x22, x0
0x234fc5678: 096840b9 ldr w9, [x0, #0x68]
0x234fc567c: 083040f9 ldr x8, [x0, #0x60]
0x234fc5680: 085142b9 ldr w8, [x8, #0x250]
0x234fc5684: 3f01086b cmp w9, w8
0x234fc5688: a0000054 b.eq #0x234fc569c
0x234fc568c: c86a00b9 str w8, [x22, #0x68]
0x234fc5690: c1620091 add x1, x22, #0x18
0x234fc5694: e00316aa mov x0, x22
0x234fc5698: 0d000094 bl #0x234fc56cc
0x234fc569c: c0620091 add x0, x22, #0x18
0x234fc56a0: e10315aa mov x1, x21
0x234fc56a4: e20314aa mov x2, x20
0x234fc56a8: e30313aa mov x3, x19
0x234fc56ac: fd7b42a9 ldp x29, x30, [sp, #0x20]
0x234fc56b0: f44f41a9 ldp x20, x19, [sp, #0x10]
0x234fc56b4: f657c3a8 ldp x22, x21, [sp], #0x30
0x234fc56b8: ff2303d5 autibsp 
0x234fc56bc: d0071eca eor x16, x30, x30, lsl #1
0x234fc56c0: 5000f0b6 tbz x16, #0x3e, #0x234fc56c8
0x234fc56c4: 208e38d4 brk #0xc471
0x234fc56c8: 2510ff17 b #0x234f8975c
