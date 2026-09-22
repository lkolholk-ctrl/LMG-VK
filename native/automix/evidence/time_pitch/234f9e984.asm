; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f9e984..0x234f9ea04
; Code SHA256: c670f3deee62466396cb7ebca7b92aeac302b4a56b9741d93caaeace86d337b6
0x234f9e984: 7f2303d5 pacibsp 
0x234f9e988: fd7bbfa9 stp x29, x30, [sp, #-0x10]!
0x234f9e98c: fd030091 mov x29, sp
0x234f9e990: 010784d2 mov x1, #0x2038
0x234f9e994: e1c3bff2 movk x1, #0xfe1f, lsl #16
0x234f9e998: 0108c6f2 movk x1, #0x3040, lsl #32
0x234f9e99c: e121e0f2 movk x1, #0x10f, lsl #48
0x234f9e9a0: 00838052 mov w0, #0x418
0x234f9e9a4: 179c7e94 bl #0x236f45a00
0x234f9e9a8: 30fbfff0 adrp x16, #0x234f05000
0x234f9e9ac: 10e23191 add x16, x16, #0xc78
0x234f9e9b0: f023c1da paciza x16
0x234f9e9b4: 100000f9 str x16, [x0]
0x234f9e9b8: d0fbff90 adrp x16, #0x234f16000
0x234f9e9bc: 10c23591 add x16, x16, #0xd70
0x234f9e9c0: f023c1da paciza x16
0x234f9e9c4: 100400f9 str x16, [x0, #8]
0x234f9e9c8: d00200d0 adrp x16, #0x234ff8000
0x234f9e9cc: 10922e91 add x16, x16, #0xba4
0x234f9e9d0: f023c1da paciza x16
0x234f9e9d4: 107c01a9 stp x16, xzr, [x0, #0x10]
0x234f9e9d8: 10fdff90 adrp x16, #0x234f3e000
0x234f9e9dc: 10d23191 add x16, x16, #0xc74
0x234f9e9e0: f023c1da paciza x16
0x234f9e9e4: 101000f9 str x16, [x0, #0x20]
0x234f9e9e8: 10fdff90 adrp x16, #0x234f3e000
0x234f9e9ec: 10423191 add x16, x16, #0xc50
0x234f9e9f0: f023c1da paciza x16
0x234f9e9f4: 1f7c03a9 stp xzr, xzr, [x0, #0x30]
0x234f9e9f8: 101400f9 str x16, [x0, #0x28]
0x234f9e9fc: fd7bc1a8 ldp x29, x30, [sp], #0x10
0x234f9ea00: ff0f5fd6 retab 
