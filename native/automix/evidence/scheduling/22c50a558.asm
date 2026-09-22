; libswiftCoreMedia 23A341; SHA256 1e4ee107668a374859a626bdaecd8452181385fe0c6202df02c53b629687308d
22c50a558: 7f2303d5 pacibsp 
22c50a55c: ff0301d1 sub sp, sp, #0x40
22c50a560: fd7b03a9 stp x29, x30, [sp, #0x30]
22c50a564: fdc30091 add x29, sp, #0x30
22c50a568: e50301a9 stp x5, x0, [sp, #0x10]
22c50a56c: 28fc60d3 lsr x8, x1, #0x20
22c50a570: e1230429 stp w1, w8, [sp, #0x20]
22c50a574: e21700f9 str x2, [sp, #0x28]
22c50a578: 88fc60d3 lsr x8, x4, #0x20
22c50a57c: e30300f9 str x3, [sp]
22c50a580: e4230129 stp w4, w8, [sp, #8]
22c50a584: e0630091 add x0, sp, #0x18
22c50a588: e1030091 mov x1, sp
22c50a58c: a580ee94 bl #0x2300aa820
22c50a590: 1f000071 cmp w0, #0
22c50a594: e0179f1a cset w0, eq
22c50a598: fd7b43a9 ldp x29, x30, [sp, #0x30]
22c50a59c: ff030191 add sp, sp, #0x40
22c50a5a0: ff0f5fd6 retab 
