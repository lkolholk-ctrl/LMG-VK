; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27227b000
; Symbol: FUN_27227b000
; Signature: undefined FUN_27227b000()
27227b000: pacibsp
27227b004: sub sp,sp,#0x1d0
27227b008: stp d15,d14,[sp, #0x130]
27227b00c: stp d13,d12,[sp, #0x140]
27227b010: stp d11,d10,[sp, #0x150]
27227b014: stp d9,d8,[sp, #0x160]
27227b018: stp x28,x27,[sp, #0x170]
27227b01c: stp x26,x25,[sp, #0x180]
27227b020: stp x24,x23,[sp, #0x190]
27227b024: stp x22,x21,[sp, #0x1a0]
27227b028: stp x20,x19,[sp, #0x1b0]
27227b02c: stp x29,x30,[sp, #0x1c0]
27227b030: add x29,sp,#0x1c0
27227b034: ldr x9,[x20, #0x8]
27227b038: ldr d0,[x20, #0x20]
27227b03c: str d0,[sp, #0x28]
27227b040: ldp x8,x23,[x20, #0x30]
27227b044: stp x8,x9,[sp, #0x50]
27227b048: ldr x0,[x20, #0x40]
27227b04c: ldp d0,d9,[x20, #0x48]
27227b050: ldp d10,d12,[x20, #0x58]
27227b054: ldp x9,x8,[x20, #0x68]
27227b058: stp x8,x9,[sp, #0x40]
27227b05c: ldp x9,x8,[x20, #0x78]
27227b060: stp x8,x9,[sp, #0x30]
27227b064: ldrb w28,[x20, #0x88]
27227b068: bl 0x272252970
27227b06c: ldr x25,[x0, #0x10]
27227b070: cbz x25,0x27227b2b4
27227b074: mov x22,x0
27227b078: adrp x8,0x2780e3000
27227b07c: ldr x8,[x8, #0xc50]
27227b080: stur x8,[x29, #-0xc8]
27227b084: sub x20,x29,#0xc8
27227b088: mov w0,#0x0
27227b08c: mov x1,x25
27227b090: mov w2,#0x0
27227b094: bl 0x272263c70
27227b098: mov x24,#0x0
27227b09c: mov x21,#0x0
27227b0a0: ldur x26,[x29, #-0xc8]
27227b0a4: ldr x19,[x23, #0x10]
27227b0a8: add x8,x22,#0x20
27227b0ac: str x8,[sp, #0x60]
27227b0b0: mov w8,#0x18
27227b0b4: madd x8,x19,x8,x23
27227b0b8: stp x22,x8,[sp, #0x8]
27227b0bc: stp d10,d9,[sp, #0x18]
27227b0c0: ldr x8,[sp, #0x60]
27227b0c4: ldr d13,[x8,x24, lsl #3]
27227b0c8: fcmp d13,d12
27227b0cc: b.mi 0x27227b12c
27227b0d0: cmp w28,#0xfb
27227b0d4: b.hi 0x27227b12c
27227b0d8: stp d9,d10,[sp, #0x98]
27227b0dc: str d12,[sp, #0xa8]
27227b0e0: ldp x9,x8,[sp, #0x40]
27227b0e4: stp x8,x9,[sp, #0xb0]
27227b0e8: ldp x11,x10,[sp, #0x30]
27227b0ec: stp x10,x11,[sp, #0xc0]
27227b0f0: strb w28,[sp, #0xd0]
27227b0f4: str d13,[sp, #0x90]
27227b0f8: stp x8,x9,[sp, #0x68]
27227b0fc: stp x10,x11,[sp, #0x78]
27227b100: strb w28,[sp, #0x88]
27227b104: add x8,sp,#0xe0
27227b108: add x0,sp,#0x90
27227b10c: add x1,sp,#0x68
27227b110: add x20,sp,#0x98
27227b114: bl 0x27224fc0c
27227b118: ldp d15,d14,[sp, #0xe0]
27227b11c: ldr d13,[sp, #0xf0]
27227b120: cbnz x19,0x27227b140
27227b124: ldr d8,[sp, #0x28]
27227b128: b 0x27227b254
27227b12c: fsub d0,d13,d12
27227b130: fadd d14,d10,d0
27227b134: fsub d0,d14,d10
27227b138: fadd d15,d9,d0
27227b13c: cbz x19,0x27227b124
27227b140: ldr x8,[x23, #0x10]
27227b144: cbz x8,0x27227b2f4
27227b148: cmp x19,x8
27227b14c: b.hi 0x27227b2f8
27227b150: ldr d0,[x23, #0x28]
27227b154: fcmp d15,d0
27227b158: b.pl 0x27227b164
27227b15c: ldr d8,[x23, #0x20]
27227b160: b 0x27227b254
27227b164: ldr x8,[sp, #0x10]
27227b168: ldur d8,[x8, #0x8]
27227b16c: mov x0,x23
27227b170: mov w1,#0x3
27227b174: bl 0x2743ddd10
27227b178: ldr x0,[sp, #0x58]
27227b17c: bl 0x2743ddd00
27227b180: ldr x0,[sp, #0x50]
27227b184: bl 0x2743ddd00
27227b188: mov w0,#0x1
27227b18c: mov x1,x23
27227b190: bl 0x272271d7c
27227b194: mov x27,x0
27227b198: stp x23,x0,[x29, #-0xc0]
27227b19c: stp x1,x2,[x29, #-0xb0]
27227b1a0: stur x3,[x29, #-0xa0]
27227b1a4: sub x0,x29,#0xc0
27227b1a8: bl 0x27227086c
27227b1ac: mov x20,x0
27227b1b0: mov x0,x27
27227b1b4: bl 0x2743ddf20
27227b1b8: mov x0,x23
27227b1bc: bl 0x2743ddce0
27227b1c0: ldr x8,[x20, #0x10]
27227b1c4: mov w9,#0x28
27227b1c8: madd x9,x8,x9,x20
27227b1cc: add x9,x9,#0x20
27227b1d0: add x8,x8,#0x1
27227b1d4: subs x8,x8,#0x1
27227b1d8: b.eq 0x27227b230
27227b1dc: mov x10,x9
27227b1e0: sub x9,x9,#0x28
27227b1e4: ldur d9,[x10, #-0x18]
27227b1e8: fcmp d15,d9
27227b1ec: b.mi 0x27227b1d4
27227b1f0: ldur d10,[x10, #-0x10]
27227b1f4: fcmp d10,d15
27227b1f8: b.mi 0x27227b1d4
27227b1fc: ldp d8,d11,[x9]
27227b200: ldrb w22,[x9, #0x20]
27227b204: mov x0,x20
27227b208: bl 0x2743ddce0
27227b20c: stp d8,d11,[sp, #0x98]
27227b210: stp d9,d10,[sp, #0xa8]
27227b214: strb w22,[sp, #0xb8]
27227b218: str d15,[sp, #0x68]
27227b21c: add x0,sp,#0x68
27227b220: add x20,sp,#0x98
27227b224: bl 0x272275f84
27227b228: fmov d8,d0
27227b22c: b 0x27227b238
27227b230: mov x0,x20
27227b234: bl 0x2743ddce0
27227b238: mov x0,x23
27227b23c: bl 0x2743ddce0
27227b240: ldr x0,[sp, #0x50]
27227b244: bl 0x2743ddce0
27227b248: ldr x0,[sp, #0x58]
27227b24c: bl 0x2743ddce0
27227b250: ldp d10,d9,[sp, #0x18]
27227b254: stur x26,[x29, #-0xc8]
27227b258: ldp x22,x8,[x26, #0x10]
27227b25c: add x27,x22,#0x1
27227b260: cmp x22,x8, LSR #0x1
27227b264: b.cs 0x27227b288
27227b268: add x24,x24,#0x1
27227b26c: str x27,[x26, #0x10]
27227b270: add x8,x26,x22, LSL #0x5
27227b274: stp d8,d15,[x8, #0x20]
27227b278: stp d14,d13,[x8, #0x30]
27227b27c: cmp x24,x25
27227b280: b.ne 0x27227b0c0
27227b284: b 0x27227b2a8
27227b288: cmp x8,#0x1
27227b28c: cset w0,hi
27227b290: sub x20,x29,#0xc8
27227b294: mov x1,x27
27227b298: mov w2,#0x1
27227b29c: bl 0x272263c70
27227b2a0: ldur x26,[x29, #-0xc8]
27227b2a4: b 0x27227b268
27227b2a8: ldr x0,[sp, #0x8]
27227b2ac: bl 0x2743ddce0
27227b2b0: b 0x27227b2c0
27227b2b4: bl 0x2743ddce0
27227b2b8: adrp x26,0x2780e3000
27227b2bc: ldr x26,[x26, #0xc50]
27227b2c0: mov x0,x26
27227b2c4: ldp x29,x30,[sp, #0x1c0]
27227b2c8: ldp x20,x19,[sp, #0x1b0]
27227b2cc: ldp x22,x21,[sp, #0x1a0]
27227b2d0: ldp x24,x23,[sp, #0x190]
27227b2d4: ldp x26,x25,[sp, #0x180]
27227b2d8: ldp x28,x27,[sp, #0x170]
27227b2dc: ldp d9,d8,[sp, #0x160]
27227b2e0: ldp d11,d10,[sp, #0x150]
27227b2e4: ldp d13,d12,[sp, #0x140]
27227b2e8: ldp d15,d14,[sp, #0x130]
27227b2ec: add sp,sp,#0x1d0
27227b2f0: retab
27227b2f4: brk #0x1
27227b2f8: brk #0x1

