; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe4a94..0x234fe4bcc
; Code SHA256: 64a5a850c1dc05a3a6ea9a6d81b5ead92f9416168e72f52a855e25794a60cb9f
0x234fe4a94: 7f2303d5 pacibsp 
0x234fe4a98: e923bb6d stp d9, d8, [sp, #-0x50]!
0x234fe4a9c: f85f01a9 stp x24, x23, [sp, #0x10]
0x234fe4aa0: f65702a9 stp x22, x21, [sp, #0x20]
0x234fe4aa4: f44f03a9 stp x20, x19, [sp, #0x30]
0x234fe4aa8: fd7b04a9 stp x29, x30, [sp, #0x40]
0x234fe4aac: fd030191 add x29, sp, #0x40
0x234fe4ab0: f50302aa mov x21, x2
0x234fe4ab4: f30301aa mov x19, x1
0x234fe4ab8: f40300aa mov x20, x0
0x234fe4abc: 00000291 add x0, x0, #0x80
0x234fe4ac0: 01008052 mov w1, #0
0x234fe4ac4: 1da4fc97 bl #0x234f0db38
0x234fe4ac8: 61070036 tbz w1, #0, #0x234fe4bb4
0x234fe4acc: 176c40b9 ldr w23, [x0, #0x6c]
0x234fe4ad0: 80020291 add x0, x20, #0x80
0x234fe4ad4: 01008052 mov w1, #0
0x234fe4ad8: 18a4fc97 bl #0x234f0db38
0x234fe4adc: c1060036 tbz w1, #0, #0x234fe4bb4
0x234fe4ae0: 089040b9 ldr w8, [x0, #0x90]
0x234fe4ae4: 88000034 cbz w8, #0x234fe4af4
0x234fe4ae8: 084c40f9 ldr x8, [x0, #0x98]
0x234fe4aec: 162140f9 ldr x22, [x8, #0x40]
0x234fe4af0: 02000014 b #0x234fe4af8
0x234fe4af4: 160080d2 mov x22, #0
0x234fe4af8: 6002231e ucvtf s0, w19
0x234fe4afc: 01102e1e fmov s1, #1.00000000
0x234fe4b00: 2018201e fdiv s0, s1, s0
0x234fe4b04: 0240211e fneg s2, s0
0x234fe4b08: bf020071 cmp w21, #0
0x234fe4b0c: 03e4002f movi d3, #0000000000000000
0x234fe4b10: 681c211e fcsel s8, s3, s1, ne
0x234fe4b14: 091c221e fcsel s9, s0, s2, ne
0x234fe4b18: ff0a0071 cmp w23, #2
0x234fe4b1c: c1010054 b.ne #0x234fe4b54
0x234fe4b20: 80020291 add x0, x20, #0x80
0x234fe4b24: 01008052 mov w1, #0
0x234fe4b28: 04a4fc97 bl #0x234f0db38
0x234fe4b2c: 41040036 tbz w1, #0, #0x234fe4bb4
0x234fe4b30: 089040b9 ldr w8, [x0, #0x90]
0x234fe4b34: 28020034 cbz w8, #0x234fe4b78
0x234fe4b38: 084c40f9 ldr x8, [x0, #0x98]
0x234fe4b3c: 09704139 ldrb w9, [x0, #0x5c]
0x234fe4b40: 29022837 tbnz w9, #5, #0x234fe4b84
0x234fe4b44: 082140f9 ldr x8, [x8, #0x40]
0x234fe4b48: 08110091 add x8, x8, #4
0x234fe4b4c: 13020035 cbnz w19, #0x234fe4b8c
0x234fe4b50: 19000014 b #0x234fe4bb4
0x234fe4b54: 13030034 cbz w19, #0x234fe4bb4
0x234fe4b58: e803132a mov w8, w19
0x234fe4b5c: c00240bd ldr s0, [x22]
0x234fe4b60: 0009201e fmul s0, s8, s0
0x234fe4b64: c04600bc str s0, [x22], #4
0x234fe4b68: 2829281e fadd s8, s9, s8
0x234fe4b6c: 080500f1 subs x8, x8, #1
0x234fe4b70: 61ffff54 b.ne #0x234fe4b5c
0x234fe4b74: 10000014 b #0x234fe4bb4
0x234fe4b78: 080080d2 mov x8, #0
0x234fe4b7c: 93000035 cbnz w19, #0x234fe4b8c
0x234fe4b80: 0d000014 b #0x234fe4bb4
0x234fe4b84: 082940f9 ldr x8, [x8, #0x50]
0x234fe4b88: 73010034 cbz w19, #0x234fe4bb4
0x234fe4b8c: e903132a mov w9, w19
0x234fe4b90: c00240bd ldr s0, [x22]
0x234fe4b94: 0009201e fmul s0, s8, s0
0x234fe4b98: c04600bc str s0, [x22], #4
0x234fe4b9c: 000140bd ldr s0, [x8]
0x234fe4ba0: 0009201e fmul s0, s8, s0
0x234fe4ba4: 004500bc str s0, [x8], #4
0x234fe4ba8: 2829281e fadd s8, s9, s8
0x234fe4bac: 290500f1 subs x9, x9, #1
0x234fe4bb0: 01ffff54 b.ne #0x234fe4b90
0x234fe4bb4: fd7b44a9 ldp x29, x30, [sp, #0x40]
0x234fe4bb8: f44f43a9 ldp x20, x19, [sp, #0x30]
0x234fe4bbc: f65742a9 ldp x22, x21, [sp, #0x20]
0x234fe4bc0: f85f41a9 ldp x24, x23, [sp, #0x10]
0x234fe4bc4: e923c56c ldp d9, d8, [sp], #0x50
0x234fe4bc8: ff0f5fd6 retab 
