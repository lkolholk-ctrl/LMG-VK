; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fe4bcc..0x234fe4ce4
; Code SHA256: ffbda29bf71b65d70816a9d96d89875c51b5dca7b3cd36458c8a3f048efa8672
0x234fe4bcc: 7f2303d5 pacibsp 
0x234fe4bd0: e923bc6d stp d9, d8, [sp, #-0x40]!
0x234fe4bd4: f65701a9 stp x22, x21, [sp, #0x10]
0x234fe4bd8: f44f02a9 stp x20, x19, [sp, #0x20]
0x234fe4bdc: fd7b03a9 stp x29, x30, [sp, #0x30]
0x234fe4be0: fdc30091 add x29, sp, #0x30
0x234fe4be4: 0840601e fmov d8, d0
0x234fe4be8: f30300aa mov x19, x0
0x234fe4bec: e00301aa mov x0, x1
0x234fe4bf0: b4bfff97 bl #0x234fd4ac0
0x234fe4bf4: 603600b9 str w0, [x19, #0x34]
0x234fe4bf8: 08040051 sub w8, w0, #1
0x234fe4bfc: 0811c05a clz w8, w8
0x234fe4c00: e803084b neg w8, w8
0x234fe4c04: 29008052 mov w9, #1
0x234fe4c08: 2121c81a lsl w1, w9, w8
0x234fe4c0c: 28040051 sub w8, w1, #1
0x234fe4c10: 61a20429 stp w1, w8, [x19, #0x24]
0x234fe4c14: e00313aa mov x0, x19
0x234fe4c18: 42affd97 bl #0x234f50920
0x234fe4c1c: 68a640a9 ldp x8, x9, [x19, #8]
0x234fe4c20: 610240f9 ldr x1, [x19]
0x234fe4c24: 290101cb sub x9, x9, x1
0x234fe4c28: 0a0101cb sub x10, x8, x1
0x234fe4c2c: 3f010aeb cmp x9, x10
0x234fe4c30: 69030054 b.ls #0x234fe4c9c
0x234fe4c34: 54fd4293 asr x20, x10, #2
0x234fe4c38: 1f0101eb cmp x8, x1
0x234fe4c3c: 00010054 b.eq #0x234fe4c5c
0x234fe4c40: e00314aa mov x0, x20
0x234fe4c44: 78bffc97 bl #0x234f14a24
0x234fe4c48: e80301aa mov x8, x1
0x234fe4c4c: 690a40f9 ldr x9, [x19, #0x10]
0x234fe4c50: 610240f9 ldr x1, [x19]
0x234fe4c54: 290101cb sub x9, x9, x1
0x234fe4c58: 03000014 b #0x234fe4c64
0x234fe4c5c: 000080d2 mov x0, #0
0x234fe4c60: 080080d2 mov x8, #0
0x234fe4c64: 1f0989eb cmp x8, x9, asr #2
0x234fe4c68: 62010054 b.hs #0x234fe4c94
0x234fe4c6c: 1508088b add x21, x0, x8, lsl #2
0x234fe4c70: 1608148b add x22, x0, x20, lsl #2
0x234fe4c74: 680640f9 ldr x8, [x19, #8]
0x234fe4c78: 020101cb sub x2, x8, x1
0x234fe4c7c: d40202cb sub x20, x22, x2
0x234fe4c80: e00314aa mov x0, x20
0x234fe4c84: 1e5e0094 bl #0x234ffc4fc
0x234fe4c88: 600240f9 ldr x0, [x19]
0x234fe4c8c: 745a00a9 stp x20, x22, [x19]
0x234fe4c90: 750a00f9 str x21, [x19, #0x10]
0x234fe4c94: 400000b4 cbz x0, #0x234fe4c9c
0x234fe4c98: 4d5c0094 bl #0x234ffbdcc
0x234fe4c9c: 682640b9 ldr w8, [x19, #0x24]
0x234fe4ca0: 88000034 cbz w8, #0x234fe4cb0
0x234fe4ca4: 600240f9 ldr x0, [x19]
0x234fe4ca8: 01f57ed3 lsl x1, x8, #2
0x234fe4cac: 71827d94 bl #0x236f45670
0x234fe4cb0: 603640bd ldr s0, [x19, #0x34]
0x234fe4cb4: 00d8617e ucvtf d0, d0
0x234fe4cb8: 0018681e fdiv d0, d0, d8
0x234fe4cbc: 601e00fd str d0, [x19, #0x38]
0x234fe4cc0: fd7b43a9 ldp x29, x30, [sp, #0x30]
0x234fe4cc4: f44f42a9 ldp x20, x19, [sp, #0x20]
0x234fe4cc8: f65741a9 ldp x22, x21, [sp, #0x10]
0x234fe4ccc: e923c46c ldp d9, d8, [sp], #0x40
0x234fe4cd0: ff0f5fd6 retab 
0x234fe4cd4: df817d94 bl #0x236f45450
0x234fe4cd8: e2817d94 bl #0x236f45460
0x234fe4cdc: f0ffff17 b #0x234fe4c9c
0x234fe4ce0: bb52fd97 bl #0x234f397cc
