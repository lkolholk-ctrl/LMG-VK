; Binary: SonicFoundation
; Address: 0x2680a6b28
; Symbol: _$sSS15SonicFoundationE8fourChars6UInt32VSgvg
; Signature: undefined _$sSS15SonicFoundationE8fourChars6UInt32VSgvg()
2680a6b28: pacibsp
2680a6b2c: sub sp,sp,#0x60
2680a6b30: stp x26,x25,[sp, #0x10]
2680a6b34: stp x24,x23,[sp, #0x20]
2680a6b38: stp x22,x21,[sp, #0x30]
2680a6b3c: stp x20,x19,[sp, #0x40]
2680a6b40: stp x29,x30,[sp, #0x50]
2680a6b44: add x29,sp,#0x50
2680a6b48: mov x20,x1
2680a6b4c: mov x21,x0
2680a6b50: bl 0x26d4d11b0
2680a6b54: mov x19,x0
2680a6b58: cmp x0,#0x4
2680a6b5c: b.eq 0x2680a6b68
2680a6b60: mov x8,#0x0
2680a6b64: b 0x2680a6c68
2680a6b68: mov x0,x21
2680a6b6c: mov x1,x20
2680a6b70: bl 0x26d4d1110
2680a6b74: mov x21,x0
2680a6b78: ldr x22,[x0, #0x10]
2680a6b7c: cbz x22,0x2680a6c1c
2680a6b80: adrp x8,0x2780e3000
2680a6b84: ldr x8,[x8, #0xc50]
2680a6b88: str x8,[sp]
2680a6b8c: mov x20,sp
2680a6b90: mov w0,#0x0
2680a6b94: mov x1,x22
2680a6b98: mov w2,#0x0
2680a6b9c: bl 0x268080460
2680a6ba0: mov x24,#0x0
2680a6ba4: ldr x20,[sp]
2680a6ba8: ldr x8,[x21, #0x10]
2680a6bac: cmp x24,x8
2680a6bb0: b.cs 0x2680a6c98
2680a6bb4: add x8,x21,x24
2680a6bb8: ldrsb w25,[x8, #0x20]
2680a6bbc: tbnz w25,#0x1f,0x2680a6c9c
2680a6bc0: str x20,[sp]
2680a6bc4: ldp x26,x8,[x20, #0x10]
2680a6bc8: add x23,x26,#0x1
2680a6bcc: cmp x26,x8, LSR #0x1
2680a6bd0: b.cs 0x2680a6bf0
2680a6bd4: add x24,x24,#0x1
2680a6bd8: str x23,[x20, #0x10]
2680a6bdc: add x8,x20,x26, LSL #0x2
2680a6be0: str w25,[x8, #0x20]
2680a6be4: cmp x22,x24
2680a6be8: b.ne 0x2680a6ba8
2680a6bec: b 0x2680a6c10
2680a6bf0: cmp x8,#0x1
2680a6bf4: cset w0,hi
2680a6bf8: mov x20,sp
2680a6bfc: mov x1,x23
2680a6c00: mov w2,#0x1
2680a6c04: bl 0x268080460
2680a6c08: ldr x20,[sp]
2680a6c0c: b 0x2680a6bd4
2680a6c10: mov x0,x21
2680a6c14: bl 0x26d4d3560
2680a6c18: b 0x2680a6c2c
2680a6c1c: mov x0,x21
2680a6c20: bl 0x26d4d3560
2680a6c24: adrp x20,0x2780e3000
2680a6c28: ldr x20,[x20, #0xc50]
2680a6c2c: ldr x8,[x20, #0x10]
2680a6c30: cbz x8,0x2680a6ca0
2680a6c34: cmp x8,#0x1
2680a6c38: b.eq 0x2680a6ca4
2680a6c3c: cmp x8,#0x3
2680a6c40: b.cc 0x2680a6ca8
2680a6c44: b.eq 0x2680a6cac
2680a6c48: ldp w8,w9,[x20, #0x20]
2680a6c4c: lsl w9,w9,#0x10
2680a6c50: orr w8,w9,w8, LSL #0x18
2680a6c54: ldp w9,w21,[x20, #0x28]
2680a6c58: orr w22,w8,w9, LSL #0x8
2680a6c5c: mov x0,x20
2680a6c60: bl 0x26d4d31b0
2680a6c64: orr w8,w22,w21
2680a6c68: cmp x19,#0x4
2680a6c6c: cset w9,ne
2680a6c70: strb w9,[sp, #0xc]
2680a6c74: and w9,w9,#0xff
2680a6c78: orr x0,x8,x9, LSL #0x20
2680a6c7c: ldp x29,x30,[sp, #0x50]
2680a6c80: ldp x20,x19,[sp, #0x40]
2680a6c84: ldp x22,x21,[sp, #0x30]
2680a6c88: ldp x24,x23,[sp, #0x20]
2680a6c8c: ldp x26,x25,[sp, #0x10]
2680a6c90: add sp,sp,#0x60
2680a6c94: retab
2680a6c98: brk #0x1
2680a6c9c: brk #0x1
2680a6ca0: brk #0x1
2680a6ca4: brk #0x1
2680a6ca8: brk #0x1
2680a6cac: brk #0x1

