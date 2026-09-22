; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f3ebd0..0x234f3ec50
; Code SHA256: 837e2e20e7a7da74f11777d6ebb1748bd868931adf8f1883c14b706e68a43e05
0x234f3ebd0: 7f2303d5 pacibsp 
0x234f3ebd4: fd7bbfa9 stp x29, x30, [sp, #-0x10]!
0x234f3ebd8: fd030091 mov x29, sp
0x234f3ebdc: 010784d2 mov x1, #0x2038
0x234f3ebe0: e1c3bff2 movk x1, #0xfe1f, lsl #16
0x234f3ebe4: 0108c6f2 movk x1, #0x3040, lsl #32
0x234f3ebe8: e121e0f2 movk x1, #0x10f, lsl #48
0x234f3ebec: 00838052 mov w0, #0x418
0x234f3ebf0: 841b8094 bl #0x236f45a00
0x234f3ebf4: 30fefff0 adrp x16, #0x234f05000
0x234f3ebf8: 10e23191 add x16, x16, #0xc78
0x234f3ebfc: f023c1da paciza x16
0x234f3ec00: 100000f9 str x16, [x0]
0x234f3ec04: d0feff90 adrp x16, #0x234f16000
0x234f3ec08: 10c23591 add x16, x16, #0xd70
0x234f3ec0c: f023c1da paciza x16
0x234f3ec10: 100400f9 str x16, [x0, #8]
0x234f3ec14: d00500d0 adrp x16, #0x234ff8000
0x234f3ec18: 10922e91 add x16, x16, #0xba4
0x234f3ec1c: f023c1da paciza x16
0x234f3ec20: 107c01a9 stp x16, xzr, [x0, #0x10]
0x234f3ec24: 10000090 adrp x16, #0x234f3e000
0x234f3ec28: 10d23191 add x16, x16, #0xc74
0x234f3ec2c: f023c1da paciza x16
0x234f3ec30: 101000f9 str x16, [x0, #0x20]
0x234f3ec34: 10000090 adrp x16, #0x234f3e000
0x234f3ec38: 10423191 add x16, x16, #0xc50
0x234f3ec3c: f023c1da paciza x16
0x234f3ec40: 1f7c03a9 stp xzr, xzr, [x0, #0x30]
0x234f3ec44: 101400f9 str x16, [x0, #0x28]
0x234f3ec48: fd7bc1a8 ldp x29, x30, [sp], #0x10
0x234f3ec4c: ff0f5fd6 retab 
