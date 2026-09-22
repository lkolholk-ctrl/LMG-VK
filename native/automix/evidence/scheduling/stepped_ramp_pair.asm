; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27227b34c
; Symbol: FUN_27227b34c
; Signature: undefined FUN_27227b34c()
27227b34c: pacibsp
27227b350: sub sp,sp,#0xa0
27227b354: stp d9,d8,[sp, #0x70]
27227b358: stp x20,x19,[sp, #0x80]
27227b35c: stp x29,x30,[sp, #0x90]
27227b360: add x29,sp,#0x90
27227b364: mov x19,x8
27227b368: ldr d8,[x0]
27227b36c: ldr d0,[x0, #0x18]
27227b370: ldr d9,[x1]
27227b374: ldr d1,[x1, #0x18]
27227b378: ldur q2,[x0, #0x8]
27227b37c: str q2,[sp, #0x20]
27227b380: str d0,[sp, #0x30]
27227b384: ldur q0,[x1, #0x8]
27227b388: str q0,[sp]
27227b38c: str d1,[sp, #0x10]
27227b390: add x8,sp,#0x40
27227b394: add x0,sp,#0x20
27227b398: mov x1,sp
27227b39c: bl 0x27227e888
27227b3a0: ldp q0,q1,[sp, #0x40]
27227b3a4: ldr q2,[sp, #0x60]
27227b3a8: stp d8,d9,[x19]
27227b3ac: stp q0,q1,[x19, #0x10]
27227b3b0: str q2,[x19, #0x30]
27227b3b4: ldp x29,x30,[sp, #0x90]
27227b3b8: ldp x20,x19,[sp, #0x80]
27227b3bc: ldp d9,d8,[sp, #0x70]
27227b3c0: add sp,sp,#0xa0
27227b3c4: retab

