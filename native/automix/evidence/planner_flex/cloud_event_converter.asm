; Binary: MusicKitInternal
; Address: 0x1d3e5ef4c
; Symbol: FUN_1d3e5ef4c
; Signature: undefined FUN_1d3e5ef4c()
1d3e5ef4c: pacibsp
1d3e5ef50: stp d9,d8,[sp, #-0x70]!
1d3e5ef54: stp x28,x27,[sp, #0x10]
1d3e5ef58: stp x26,x25,[sp, #0x20]
1d3e5ef5c: stp x24,x23,[sp, #0x30]
1d3e5ef60: stp x22,x21,[sp, #0x40]
1d3e5ef64: stp x20,x19,[sp, #0x50]
1d3e5ef68: stp x29,x30,[sp, #0x60]
1d3e5ef6c: add x29,sp,#0x60
1d3e5ef70: sub sp,sp,#0x30
1d3e5ef74: mov x0,#0x0
1d3e5ef78: bl 0x1d4a28470
1d3e5ef7c: mov x19,x0
1d3e5ef80: bl 0x1d3cef1a4
1d3e5ef84: movk x17,#0x2e3f, LSL #48
1d3e5ef88: autda x16,x17
1d3e5ef8c: mov x17,x16
1d3e5ef90: xpacd x17
1d3e5ef94: cmp x16,x17
1d3e5ef98: b.eq 0x1d3e5efa0
1d3e5ef9c: brk #0xc472
1d3e5efa0: mov x21,x16
1d3e5efa4: mov x16,x8
1d3e5efa8: mov x17,x9
1d3e5efac: movk x17,#0x2e3f, LSL #48
1d3e5efb0: autda x16,x17
1d3e5efb4: ldr x8,[x16, #0x40]
1d3e5efb8: mov x9,x8
1d3e5efbc: adrp x17,0x1ef12d000
1d3e5efc0: add x17,x17,#0xd40
1d3e5efc4: ldr x16,[x17]
1d3e5efc8: blraa x16,x17
1d3e5efcc: bl 0x1d3cef380
1d3e5efd0: sub x22,x9,x12
1d3e5efd4: mov sp,x22
1d3e5efd8: mov x9,x8
1d3e5efdc: adrp x17,0x1ef12d000
1d3e5efe0: add x17,x17,#0xd40
1d3e5efe4: ldr x16,[x17]
1d3e5efe8: blraa x16,x17
1d3e5efec: mov x8,sp
1d3e5eff0: sub x25,x8,x12
1d3e5eff4: mov sp,x25
1d3e5eff8: adrp x0,0x1ec95e000
1d3e5effc: add x0,x0,#0x108
1d3e5f000: bl 0x1d3cf4374
1d3e5f004: mov x8,x25
1d3e5f008: stur x0,[x29, #-0x80]
1d3e5f00c: stur x20,[x29, #-0x68]
1d3e5f010: bl 0x1d4a27ad0
1d3e5f014: mov x20,x25
1d3e5f018: bl 0x1d4a28450
1d3e5f01c: mov x8,x0
1d3e5f020: ldr x27,[x21, #0x8]!
1d3e5f024: mov x0,x25
1d3e5f028: stp x21,x19,[x29, #-0x78]
1d3e5f02c: mov x1,x19
1d3e5f030: mov x19,x8
1d3e5f034: mov x17,x21
1d3e5f038: movk x17,#0x4f8, LSL #48
1d3e5f03c: blraa x27,x17
1d3e5f040: ldr x28,[x19, #0x10]
1d3e5f044: stur x19,[x29, #-0x88]
1d3e5f048: cbz x28,0x1d3e5f198
1d3e5f04c: mov x24,#0x0
1d3e5f050: add x19,x19,#0x20
1d3e5f054: adrp x25,0x1e6bed000
1d3e5f058: ldr x25,[x25, #0xc20]
1d3e5f05c: ldr d8,[x19,x24, lsl #3]
1d3e5f060: mov x8,x22
1d3e5f064: ldur x0,[x29, #-0x80]
1d3e5f068: ldur x20,[x29, #-0x68]
1d3e5f06c: bl 0x1d4a27ad0
1d3e5f070: mov x20,x22
1d3e5f074: bl 0x1d4a28460
1d3e5f078: mov x20,x0
1d3e5f07c: mov x0,x22
1d3e5f080: ldp x8,x1,[x29, #-0x78]
1d3e5f084: mov x17,x8
1d3e5f088: movk x17,#0x4f8, LSL #48
1d3e5f08c: blraa x27,x17
1d3e5f090: ldr x8,[x20, #0x10]
1d3e5f094: cmp x24,x8
1d3e5f098: b.cs 0x1d3e5f1d0
1d3e5f09c: add x8,x20,x24, LSL #0x3
1d3e5f0a0: ldr x26,[x8, #0x20]
1d3e5f0a4: mov x0,x20
1d3e5f0a8: bl 0x1d4a2e2c0
1d3e5f0ac: sub x8,x26,#0x12c
1d3e5f0b0: cmn x8,#0x65
1d3e5f0b4: b.hi 0x1d3e5f0e4
1d3e5f0b8: sub x8,x26,#0x1f4
1d3e5f0bc: cmn x8,#0x65
1d3e5f0c0: b.hi 0x1d3e5f0ec
1d3e5f0c4: sub x8,x26,#0x2bc
1d3e5f0c8: cmn x8,#0x65
1d3e5f0cc: b.hi 0x1d3e5f0f4
1d3e5f0d0: sub x8,x26,#0x384
1d3e5f0d4: cmn x8,#0x64
1d3e5f0d8: b.cc 0x1d3e5f148
1d3e5f0dc: mov w23,#0x3
1d3e5f0e0: b 0x1d3e5f0f8
1d3e5f0e4: mov w23,#0x0
1d3e5f0e8: b 0x1d3e5f0f8
1d3e5f0ec: mov w23,#0x1
1d3e5f0f0: b 0x1d3e5f0f8
1d3e5f0f4: mov w23,#0x2
1d3e5f0f8: mov x0,x25
1d3e5f0fc: bl 0x1d4a2e680
1d3e5f100: tbz w0,#0x0,0x1d3e5f158
1d3e5f104: ldp x21,x8,[x25, #0x10]
1d3e5f108: add x20,x21,#0x1
1d3e5f10c: cmp x21,x8, LSR #0x1
1d3e5f110: b.cs 0x1d3e5f178
1d3e5f114: mov w9,#0x64
1d3e5f118: sdiv x8,x26,x9
1d3e5f11c: msub x8,x8,x9,x26
1d3e5f120: scvtf d0,x8
1d3e5f124: mov x8,#0x4059000000000000
1d3e5f128: fmov d1,x8
1d3e5f12c: str x20,[x25, #0x10]
1d3e5f130: mov w8,#0x18
1d3e5f134: madd x8,x21,x8,x25
1d3e5f138: str d8,[x8, #0x20]
1d3e5f13c: fdiv d0,d0,d1
1d3e5f140: strb w23,[x8, #0x28]
1d3e5f144: str d0,[x8, #0x30]
1d3e5f148: add x24,x24,#0x1
1d3e5f14c: cmp x28,x24
1d3e5f150: b.ne 0x1d3e5f05c
1d3e5f154: b 0x1d3e5f1a0
1d3e5f158: ldr x8,[x25, #0x10]
1d3e5f15c: add x1,x8,#0x1
1d3e5f160: mov w0,#0x0
1d3e5f164: mov w2,#0x1
1d3e5f168: mov x3,x25
1d3e5f16c: bl 0x1d3da992c
1d3e5f170: mov x25,x0
1d3e5f174: b 0x1d3e5f104
1d3e5f178: cmp x8,#0x1
1d3e5f17c: cset w0,hi
1d3e5f180: mov x1,x20
1d3e5f184: mov w2,#0x1
1d3e5f188: mov x3,x25
1d3e5f18c: bl 0x1d3da992c
1d3e5f190: mov x25,x0
1d3e5f194: b 0x1d3e5f114
1d3e5f198: adrp x25,0x1e6bed000
1d3e5f19c: ldr x25,[x25, #0xc20]
1d3e5f1a0: ldur x0,[x29, #-0x88]
1d3e5f1a4: bl 0x1d4a2e2c0
1d3e5f1a8: mov x0,x25
1d3e5f1ac: sub sp,x29,#0x60
1d3e5f1b0: ldp x29,x30,[sp, #0x60]
1d3e5f1b4: ldp x20,x19,[sp, #0x50]
1d3e5f1b8: ldp x22,x21,[sp, #0x40]
1d3e5f1bc: ldp x24,x23,[sp, #0x30]
1d3e5f1c0: ldp x26,x25,[sp, #0x20]
1d3e5f1c4: ldp x28,x27,[sp, #0x10]
1d3e5f1c8: ldp d9,d8,[sp], #0x70
1d3e5f1cc: retab
1d3e5f1d0: brk #0x1

