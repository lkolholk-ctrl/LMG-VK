; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234f9d9cc..0x234f9de48
; Code SHA256: e656c0b844f2a08dd8d52e452e5b4182cb1fd2dccee225467d38d7eb361e4138
0x234f9d9cc: 7f2303d5 pacibsp 
0x234f9d9d0: ff0302d1 sub sp, sp, #0x80
0x234f9d9d4: eb2b016d stp d11, d10, [sp, #0x10]
0x234f9d9d8: e923026d stp d9, d8, [sp, #0x20]
0x234f9d9dc: fa6703a9 stp x26, x25, [sp, #0x30]
0x234f9d9e0: f85f04a9 stp x24, x23, [sp, #0x40]
0x234f9d9e4: f65705a9 stp x22, x21, [sp, #0x50]
0x234f9d9e8: f44f06a9 stp x20, x19, [sp, #0x60]
0x234f9d9ec: fd7b07a9 stp x29, x30, [sp, #0x70]
0x234f9d9f0: fdc30191 add x29, sp, #0x70
0x234f9d9f4: f70303aa mov x23, x3
0x234f9d9f8: f40302aa mov x20, x2
0x234f9d9fc: f50301aa mov x21, x1
0x234f9da00: f30300aa mov x19, x0
0x234f9da04: 400040fd ldr d0, [x2]
0x234f9da08: 01e841fd ldr d1, [x0, #0x3d0]
0x234f9da0c: 0020611e fcmp d0, d1
0x234f9da10: e0040054 b.eq #0x234f9daac
0x234f9da14: 0338611e fsub d3, d0, d1
0x234f9da18: 760e41f9 ldr x22, [x19, #0x218]
0x234f9da1c: c16e44fd ldr d1, [x22, #0x8d8]
0x234f9da20: 2128631e fadd d1, d1, d3
0x234f9da24: c16e04fd str d1, [x22, #0x8d8]
0x234f9da28: c26644fd ldr d2, [x22, #0x8c8]
0x234f9da2c: 4228631e fadd d2, d2, d3
0x234f9da30: c26604fd str d2, [x22, #0x8c8]
0x234f9da34: c84645f9 ldr x8, [x22, #0xa88]
0x234f9da38: 093540f9 ldr x9, [x8, #0x68]
0x234f9da3c: 290140f9 ldr x9, [x9]
0x234f9da40: 490200b4 cbz x9, #0x234f9da88
0x234f9da44: 083140f9 ldr x8, [x8, #0x60]
0x234f9da48: 080540f9 ldr x8, [x8, #8]
0x234f9da4c: 000140f9 ldr x0, [x8]
0x234f9da50: 2040601e fmov d0, d1
0x234f9da54: cc9ffe97 bl #0x234f45984
0x234f9da58: c06a04fd str d0, [x22, #0x8d0]
0x234f9da5c: c84645f9 ldr x8, [x22, #0xa88]
0x234f9da60: c06644fd ldr d0, [x22, #0x8c8]
0x234f9da64: 083140f9 ldr x8, [x8, #0x60]
0x234f9da68: 080540f9 ldr x8, [x8, #8]
0x234f9da6c: 000140f9 ldr x0, [x8]
0x234f9da70: c59ffe97 bl #0x234f45984
0x234f9da74: 0140601e fmov d1, d0
0x234f9da78: c06204fd str d0, [x22, #0x8c0]
0x234f9da7c: c26644fd ldr d2, [x22, #0x8c8]
0x234f9da80: 800240fd ldr d0, [x20]
0x234f9da84: 08000014 b #0x234f9daa4
0x234f9da88: c15644fd ldr d1, [x22, #0x8a8]
0x234f9da8c: c46a44fd ldr d4, [x22, #0x8d0]
0x234f9da90: 6410411f fmadd d4, d3, d1, d4
0x234f9da94: c46a04fd str d4, [x22, #0x8d0]
0x234f9da98: c46244fd ldr d4, [x22, #0x8c0]
0x234f9da9c: 6110411f fmadd d1, d3, d1, d4
0x234f9daa0: c16204fd str d1, [x22, #0x8c0]
0x234f9daa4: c19e04fd str d1, [x22, #0x938]
0x234f9daa8: c2a604fd str d2, [x22, #0x948]
0x234f9daac: e802631e ucvtf d8, w23
0x234f9dab0: 0128681e fadd d1, d0, d8
0x234f9dab4: 61ea01fd str d1, [x19, #0x3d0]
0x234f9dab8: a80240b9 ldr w8, [x21]
0x234f9dabc: 682202b9 str w8, [x19, #0x220]
0x234f9dac0: 68e641f9 ldr x8, [x19, #0x3c8]
0x234f9dac4: 080140f9 ldr x8, [x8]
0x234f9dac8: 880000b4 cbz x8, #0x234f9dad8
0x234f9dacc: 7fa20e39 strb wzr, [x19, #0x3a8]
0x234f9dad0: 60020f91 add x0, x19, #0x3c0
0x234f9dad4: 939c7e94 bl #0x236f44d20
0x234f9dad8: e00313aa mov x0, x19
0x234f9dadc: aafeff97 bl #0x234f9d584
0x234f9dae0: 60020291 add x0, x19, #0x80
0x234f9dae4: 01008052 mov w1, #0
0x234f9dae8: 14c0fd97 bl #0x234f0db38
0x234f9daec: f60300aa mov x22, x0
0x234f9daf0: 41190036 tbz w1, #0, #0x234f9de18
0x234f9daf4: c8a24239 ldrb w8, [x22, #0xa8]
0x234f9daf8: 1f050071 cmp w8, #1
0x234f9dafc: 010f0054 b.ne #0x234f9dcdc
0x234f9db00: c0420291 add x0, x22, #0x90
0x234f9db04: c1420191 add x1, x22, #0x50
0x234f9db08: e20317aa mov x2, x23
0x234f9db0c: 2ac0fd97 bl #0x234f0dbb4
0x234f9db10: 81190036 tbz w1, #0, #0x234f9de40
0x234f9db14: c10245fc ldur d1, [x22, #0x50]
0x234f9db18: 68f24c39 ldrb w8, [x19, #0x33c]
0x234f9db1c: 02106e1e fmov d2, #1.00000000
0x234f9db20: 88000037 tbnz w8, #0, #0x234f9db30
0x234f9db24: 680e41f9 ldr x8, [x19, #0x218]
0x234f9db28: 005544fd ldr d0, [x8, #0x8a8]
0x234f9db2c: 4218601e fdiv d2, d2, d0
0x234f9db30: 60a20891 add x0, x19, #0x228
0x234f9db34: 61a20891 add x1, x19, #0x228
0x234f9db38: e20314aa mov x2, x20
0x234f9db3c: 0041601e fmov d0, d8
0x234f9db40: a4c2fd97 bl #0x234f0e5d0
0x234f9db44: c80249b8 ldur w8, [x22, #0x90]
0x234f9db48: c8100034 cbz w8, #0x234f9dd60
0x234f9db4c: d84e40f9 ldr x24, [x22, #0x98]
0x234f9db50: 750e41f9 ldr x21, [x19, #0x218]
0x234f9db54: 890240fd ldr d9, [x20]
0x234f9db58: a80a6a39 ldrb w8, [x21, #0xa82]
0x234f9db5c: 1f050071 cmp w8, #1
0x234f9db60: 81010054 b.ne #0x234f9db90
0x234f9db64: a84645f9 ldr x8, [x21, #0xa88]
0x234f9db68: 093540f9 ldr x9, [x8, #0x68]
0x234f9db6c: 290140f9 ldr x9, [x9]
0x234f9db70: 090100b4 cbz x9, #0x234f9db90
0x234f9db74: 083140f9 ldr x8, [x8, #0x60]
0x234f9db78: 080540f9 ldr x8, [x8, #8]
0x234f9db7c: 000140f9 ldr x0, [x8]
0x234f9db80: 2041601e fmov d0, d9
0x234f9db84: 809ffe97 bl #0x234f45984
0x234f9db88: a09e04fd str d0, [x21, #0x938]
0x234f9db8c: a9a604fd str d9, [x21, #0x948]
0x234f9db90: bf0a2a39 strb wzr, [x21, #0xa82]
0x234f9db94: a88e6639 ldrb w8, [x21, #0x9a3]
0x234f9db98: 680a0037 tbnz w8, #0, #0x234f9dce4
0x234f9db9c: a8826639 ldrb w8, [x21, #0x9a0]
0x234f9dba0: 280a0036 tbz w8, #0, #0x234f9dce4
0x234f9dba4: e90700fd str d9, [sp, #8]
0x234f9dba8: a89e6639 ldrb w8, [x21, #0x9a7]
0x234f9dbac: 1f050071 cmp w8, #1
0x234f9dbb0: 61000054 b.ne #0x234f9dbbc
0x234f9dbb4: e00315aa mov x0, x21
0x234f9dbb8: d59efe97 bl #0x234f4570c
0x234f9dbbc: f70700b9 str w23, [sp, #4]
0x234f9dbc0: bf8a09b9 str wzr, [x21, #0x988]
0x234f9dbc4: e00315aa mov x0, x21
0x234f9dbc8: e10317aa mov x1, x23
0x234f9dbcc: 2041601e fmov d0, d9
0x234f9dbd0: f09efe97 bl #0x234f45790
0x234f9dbd4: f60300aa mov x22, x0
0x234f9dbd8: e00300b9 str w0, [sp]
0x234f9dbdc: e1130091 add x1, sp, #4
0x234f9dbe0: 02c30091 add x2, x24, #0x30
0x234f9dbe4: e3230091 add x3, sp, #8
0x234f9dbe8: e00315aa mov x0, x21
0x234f9dbec: 3ea0fe97 bl #0x234f45ce4
0x234f9dbf0: b7926639 ldrb w23, [x21, #0x9a4]
0x234f9dbf4: a98a48bd ldr s9, [x21, #0x888]
0x234f9dbf8: 77000037 tbnz w23, #0, #0x234f9dc04
0x234f9dbfc: e00315aa mov x0, x21
0x234f9dc00: 23a1fe97 bl #0x234f4608c
0x234f9dc04: e80740b9 ldr w8, [sp, #4]
0x234f9dc08: a80e0034 cbz w8, #0x234f9dddc
0x234f9dc0c: 08008052 mov w8, #0
0x234f9dc10: 39008052 mov w25, #1
0x234f9dc14: 0a102e1e fmov s10, #1.00000000
0x234f9dc18: c8000037 tbnz w8, #0, #0x234f9dc30
0x234f9dc1c: a85249b9 ldr w8, [x21, #0x950]
0x234f9dc20: aa5a49b9 ldr w10, [x21, #0x958]
0x234f9dc24: a99248b9 ldr w9, [x21, #0x890]
0x234f9dc28: 08010a4b sub w8, w8, w10
0x234f9dc2c: 0c000014 b #0x234f9dc5c
0x234f9dc30: e1030091 mov x1, sp
0x234f9dc34: e00315aa mov x0, x21
0x234f9dc38: 5d9ffe97 bl #0x234f459ac
0x234f9dc3c: e0050035 cbnz w0, #0x234f9dcf8
0x234f9dc40: 39040034 cbz w25, #0x234f9dcc4
0x234f9dc44: a85249b9 ldr w8, [x21, #0x950]
0x234f9dc48: a95a49b9 ldr w9, [x21, #0x958]
0x234f9dc4c: 0801094b sub w8, w8, w9
0x234f9dc50: a99248b9 ldr w9, [x21, #0x890]
0x234f9dc54: 3f01086b cmp w9, w8
0x234f9dc58: ed000054 b.le #0x234f9dc74
0x234f9dc5c: ea0340b9 ldr w10, [sp]
0x234f9dc60: 2801086b subs w8, w9, w8
0x234f9dc64: 40c9407a ccmp w10, #0, #0, gt
0x234f9dc68: 41feff54 b.ne #0x234f9dc30
0x234f9dc6c: e80300b9 str w8, [sp]
0x234f9dc70: f0ffff17 b #0x234f9dc30
0x234f9dc74: 20212a1e fcmp s9, s10
0x234f9dc78: e2079f1a cset w2, ne
0x234f9dc7c: e00315aa mov x0, x21
0x234f9dc80: e10317aa mov x1, x23
0x234f9dc84: dfa1fe97 bl #0x234f46400
0x234f9dc88: e1130091 add x1, sp, #4
0x234f9dc8c: 02c30091 add x2, x24, #0x30
0x234f9dc90: e3230091 add x3, sp, #8
0x234f9dc94: e00315aa mov x0, x21
0x234f9dc98: 13a0fe97 bl #0x234f45ce4
0x234f9dc9c: f90740b9 ldr w25, [sp, #4]
0x234f9dca0: 39010034 cbz w25, #0x234f9dcc4
0x234f9dca4: a85249b9 ldr w8, [x21, #0x950]
0x234f9dca8: a95a49b9 ldr w9, [x21, #0x958]
0x234f9dcac: 0801094b sub w8, w8, w9
0x234f9dcb0: a99248b9 ldr w9, [x21, #0x890]
0x234f9dcb4: 3f01086b cmp w9, w8
0x234f9dcb8: edfdff54 b.le #0x234f9dc74
0x234f9dcbc: 08008052 mov w8, #0
0x234f9dcc0: d6ffff17 b #0x234f9dc18
0x234f9dcc4: 19008052 mov w25, #0
0x234f9dcc8: 16008052 mov w22, #0
0x234f9dccc: e90340b9 ldr w9, [sp]
0x234f9dcd0: 28008052 mov w8, #1
0x234f9dcd4: 29faff35 cbnz w9, #0x234f9dc18
0x234f9dcd8: 09000014 b #0x234f9dcfc
0x234f9dcdc: 564c8512 mov w22, #-0x2a63
0x234f9dce0: 4e000014 b #0x234f9de18
0x234f9dce4: 02c30091 add x2, x24, #0x30
0x234f9dce8: e00315aa mov x0, x21
0x234f9dcec: e10317aa mov x1, x23
0x234f9dcf0: 2041601e fmov d0, d9
0x234f9dcf4: 6ca1fe97 bl #0x234f462a4
0x234f9dcf8: f60300aa mov x22, x0
0x234f9dcfc: 68a641f9 ldr x8, [x19, #0x348]
0x234f9dd00: c80800b4 cbz x8, #0x234f9de18
0x234f9dd04: 68e641f9 ldr x8, [x19, #0x3c8]
0x234f9dd08: 080140f9 ldr x8, [x8]
0x234f9dd0c: e80200b4 cbz x8, #0x234f9dd68
0x234f9dd10: 600e41f9 ldr x0, [x19, #0x218]
0x234f9dd14: 800240fd ldr d0, [x20]
0x234f9dd18: b6a2fe97 bl #0x234f467f0
0x234f9dd1c: 0940601e fmov d9, d0
0x234f9dd20: 600e41f9 ldr x0, [x19, #0x218]
0x234f9dd24: 800240fd ldr d0, [x20]
0x234f9dd28: 0028681e fadd d0, d0, d8
0x234f9dd2c: 01107e1e fmov d1, #-1.00000000
0x234f9dd30: 0028611e fadd d0, d0, d1
0x234f9dd34: afa2fe97 bl #0x234f467f0
0x234f9dd38: 01e4002f movi d1, #0000000000000000
0x234f9dd3c: 2269611e fmaxnm d2, d9, d1
0x234f9dd40: 0038621e fsub d0, d0, d2
0x234f9dd44: 0168611e fmaxnm d1, d0, d1
0x234f9dd48: 68a641f9 ldr x8, [x19, #0x348]
0x234f9dd4c: 60aa41f9 ldr x0, [x19, #0x350]
0x234f9dd50: e10314aa mov x1, x20
0x234f9dd54: 4040601e fmov d0, d2
0x234f9dd58: 1f093fd6 blraaz x8
0x234f9dd5c: 2f000014 b #0x234f9de18
0x234f9dd60: 16008012 mov w22, #-1
0x234f9dd64: 2d000014 b #0x234f9de18
0x234f9dd68: 68f64c39 ldrb w8, [x19, #0x33d]
0x234f9dd6c: 1f050071 cmp w8, #1
0x234f9dd70: c1000054 b.ne #0x234f9dd88
0x234f9dd74: 7ff60c39 strb wzr, [x19, #0x33d]
0x234f9dd78: 600e41f9 ldr x0, [x19, #0x218]
0x234f9dd7c: 800240fd ldr d0, [x20]
0x234f9dd80: 9ca2fe97 bl #0x234f467f0
0x234f9dd84: 60a201fd str d0, [x19, #0x340]
0x234f9dd88: 600e41f9 ldr x0, [x19, #0x218]
0x234f9dd8c: 800240fd ldr d0, [x20]
0x234f9dd90: 0028681e fadd d0, d0, d8
0x234f9dd94: 01107e1e fmov d1, #-1.00000000
0x234f9dd98: 0028611e fadd d0, d0, d1
0x234f9dd9c: 95a2fe97 bl #0x234f467f0
0x234f9dda0: 0840601e fmov d8, d0
0x234f9dda4: 63a241fd ldr d3, [x19, #0x340]
0x234f9dda8: 0238631e fsub d2, d0, d3
0x234f9ddac: 00e4002f movi d0, #0000000000000000
0x234f9ddb0: 4820601e fcmp d2, #0.0
0x234f9ddb4: 01e4002f movi d1, #0000000000000000
0x234f9ddb8: 64020054 b.mi #0x234f9de04
0x234f9ddbc: 00106e1e fmov d0, #1.00000000
0x234f9ddc0: 6028601e fadd d0, d3, d0
0x234f9ddc4: 0820601e fcmp d0, #0.0
0x234f9ddc8: c5010054 b.pl #0x234f9de00
0x234f9ddcc: 4128601e fadd d1, d2, d0
0x234f9ddd0: 00e4002f movi d0, #0000000000000000
0x234f9ddd4: 2168601e fmaxnm d1, d1, d0
0x234f9ddd8: 0b000014 b #0x234f9de04
0x234f9dddc: 16f9ff34 cbz w22, #0x234f9dcfc
0x234f9dde0: e1030091 mov x1, sp
0x234f9dde4: e00315aa mov x0, x21
0x234f9dde8: f19efe97 bl #0x234f459ac
0x234f9ddec: f60300aa mov x22, x0
0x234f9ddf0: 60f8ff35 cbnz w0, #0x234f9dcfc
0x234f9ddf4: e80340b9 ldr w8, [sp]
0x234f9ddf8: 48ffff35 cbnz w8, #0x234f9dde0
0x234f9ddfc: c0ffff17 b #0x234f9dcfc
0x234f9de00: 4140601e fmov d1, d2
0x234f9de04: 68a641f9 ldr x8, [x19, #0x348]
0x234f9de08: 60aa41f9 ldr x0, [x19, #0x350]
0x234f9de0c: e10314aa mov x1, x20
0x234f9de10: 1f093fd6 blraaz x8
0x234f9de14: 68a201fd str d8, [x19, #0x340]
0x234f9de18: e00316aa mov x0, x22
0x234f9de1c: fd7b47a9 ldp x29, x30, [sp, #0x70]
0x234f9de20: f44f46a9 ldp x20, x19, [sp, #0x60]
0x234f9de24: f65745a9 ldp x22, x21, [sp, #0x50]
0x234f9de28: f85f44a9 ldp x24, x23, [sp, #0x40]
0x234f9de2c: fa6743a9 ldp x26, x25, [sp, #0x30]
0x234f9de30: e923426d ldp d9, d8, [sp, #0x20]
0x234f9de34: eb2b416d ldp d11, d10, [sp, #0x10]
0x234f9de38: ff030291 add sp, sp, #0x80
0x234f9de3c: ff0f5fd6 retab 
0x234f9de40: f60300aa mov x22, x0
0x234f9de44: f5ffff17 b #0x234f9de18
