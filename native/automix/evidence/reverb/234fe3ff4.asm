; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe3ff4..0x234fe4068
; Code SHA256: 902611477d4f3e91fb2b88ab9861eef1dd7fb4600bf5053343bc339021adecbf
0x234fe3ff4: 7f2303d5 pacibsp 
0x234fe3ff8: f657bda9 stp x22, x21, [sp, #-0x30]!
0x234fe3ffc: f44f01a9 stp x20, x19, [sp, #0x10]
0x234fe4000: fd7b02a9 stp x29, x30, [sp, #0x20]
0x234fe4004: fd830091 add x29, sp, #0x20
0x234fe4008: f30300aa mov x19, x0
0x234fe400c: 14900291 add x20, x0, #0xa4
0x234fe4010: 15028052 mov w21, #0x10
0x234fe4014: 88025fb8 ldur w8, [x20, #-0x10]
0x234fe4018: 88000034 cbz w8, #0x234fe4028
0x234fe401c: 80c25cf8 ldur x0, [x20, #-0x34]
0x234fe4020: 01f57ed3 lsl x1, x8, #2
0x234fe4024: 93857d94 bl #0x236f45670
0x234fe4028: 880240b9 ldr w8, [x20]
0x234fe402c: e803084b neg w8, w8
0x234fe4030: 89425fb8 ldur w9, [x20, #-0xc]
0x234fe4034: 2801080a and w8, w9, w8
0x234fe4038: 887e3f29 stp w8, wzr, [x20, #-8]
0x234fe403c: 9fc21eb8 stur wzr, [x20, #-0x14]
0x234fe4040: 94220191 add x20, x20, #0x48
0x234fe4044: b50600f1 subs x21, x21, #1
0x234fe4048: 61feff54 b.ne #0x234fe4014
0x234fe404c: 00e4006f movi v0.2d, #0000000000000000
0x234fe4050: 608202ad stp q0, q0, [x19, #0x50]
0x234fe4054: 608201ad stp q0, q0, [x19, #0x30]
0x234fe4058: fd7b42a9 ldp x29, x30, [sp, #0x20]
0x234fe405c: f44f41a9 ldp x20, x19, [sp, #0x10]
0x234fe4060: f657c3a8 ldp x22, x21, [sp], #0x30
0x234fe4064: ff0f5fd6 retab 
