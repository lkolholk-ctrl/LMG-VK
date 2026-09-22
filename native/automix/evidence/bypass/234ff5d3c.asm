; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234ff5d3c..0x234ff5e44
; Code SHA256: c32b730def3303604a26915b56215ae3f5d182807cd505186fcc6e65ef82a2d7
0x234ff5d3c: 62000034 cbz w2, #0x234ff5d48
0x234ff5d40: c04f8512 mov w0, #-0x2a7f
0x234ff5d44: c0035fd6 ret 
0x234ff5d48: 7f2303d5 pacibsp 
0x234ff5d4c: f44fbea9 stp x20, x19, [sp, #-0x20]!
0x234ff5d50: fd7b01a9 stp x29, x30, [sp, #0x10]
0x234ff5d54: fd430091 add x29, sp, #0x10
0x234ff5d58: f30300aa mov x19, x0
0x234ff5d5c: 3f740071 cmp w1, #0x1d
0x234ff5d60: 80050054 b.eq #0x234ff5e10
0x234ff5d64: 3f540071 cmp w1, #0x15
0x234ff5d68: 01060054 b.ne #0x234ff5e28
0x234ff5d6c: bf100071 cmp w5, #4
0x234ff5d70: 03060054 b.lo #0x234ff5e30
0x234ff5d74: 940040b9 ldr w20, [x4]
0x234ff5d78: 9f020071 cmp w20, #0
0x234ff5d7c: e9079f1a cset w9, ne
0x234ff5d80: 68a24839 ldrb w8, [x19, #0x228]
0x234ff5d84: 3f01086b cmp w9, w8
0x234ff5d88: 00040054 b.eq #0x234ff5e08
0x234ff5d8c: 34020035 cbnz w20, #0x234ff5dd0
0x234ff5d90: 08020034 cbz w8, #0x234ff5dd0
0x234ff5d94: 68464039 ldrb w8, [x19, #0x11]
0x234ff5d98: 1f050071 cmp w8, #1
0x234ff5d9c: a1010054 b.ne #0x234ff5dd0
0x234ff5da0: 700240f9 ldr x16, [x19]
0x234ff5da4: f10313aa mov x17, x19
0x234ff5da8: 11aef5f2 movk x17, #0xad70, lsl #48
0x234ff5dac: 301ac1da autda x16, x17
0x234ff5db0: 088e44f8 ldr x8, [x16, #0x48]!
0x234ff5db4: e90310aa mov x9, x16
0x234ff5db8: e00313aa mov x0, x19
0x234ff5dbc: 01008052 mov w1, #0
0x234ff5dc0: 02008052 mov w2, #0
0x234ff5dc4: f10309aa mov x17, x9
0x234ff5dc8: 7140fbf2 movk x17, #0xda03, lsl #48
0x234ff5dcc: 11093fd7 blraa x8, x17
0x234ff5dd0: 9f020071 cmp w20, #0
0x234ff5dd4: e1079f1a cset w1, ne
0x234ff5dd8: 700240f9 ldr x16, [x19]
0x234ff5ddc: f10313aa mov x17, x19
0x234ff5de0: 11aef5f2 movk x17, #0xad70, lsl #48
0x234ff5de4: 301ac1da autda x16, x17
0x234ff5de8: 114980d2 mov x17, #0x248
0x234ff5dec: 1002118b add x16, x16, x17
0x234ff5df0: 080240f9 ldr x8, [x16]
0x234ff5df4: e90310aa mov x9, x16
0x234ff5df8: e00313aa mov x0, x19
0x234ff5dfc: f10309aa mov x17, x9
0x234ff5e00: 1135e4f2 movk x17, #0x21a8, lsl #48
0x234ff5e04: 11093fd7 blraa x8, x17
0x234ff5e08: 00008052 mov w0, #0
0x234ff5e0c: 0a000014 b #0x234ff5e34
0x234ff5e10: 00008052 mov w0, #0
0x234ff5e14: 880040b9 ldr w8, [x4]
0x234ff5e18: 1f010071 cmp w8, #0
0x234ff5e1c: e8079f1a cset w8, ne
0x234ff5e20: 68aa0839 strb w8, [x19, #0x22a]
0x234ff5e24: 04000014 b #0x234ff5e34
0x234ff5e28: c04f8512 mov w0, #-0x2a7f
0x234ff5e2c: 02000014 b #0x234ff5e34
0x234ff5e30: 404c8512 mov w0, #-0x2a63
0x234ff5e34: fd7b41a9 ldp x29, x30, [sp, #0x10]
0x234ff5e38: f44fc2a8 ldp x20, x19, [sp], #0x20
0x234ff5e3c: ff2303d5 autibsp 
0x234ff5e40: c0035fd6 ret 
