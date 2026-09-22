; Binary: _SonicKit_MusicKit_Packages
; Address: 0x27227e888
; Symbol: FUN_27227e888
; Signature: undefined FUN_27227e888()
27227e888: ldr q0,[x0]
27227e88c: ldr q1,[x1]
27227e890: fcmgt v2.2D,v0.2D,v1.2D
27227e894: xtn v2.2S,v2.2D
27227e898: fmov w9,s2
27227e89c: tbnz w9,#0x0,0x27227e8cc
27227e8a0: mov w9,v2.S[0x1]
27227e8a4: tbnz w9,#0x0,0x27227e8d0
27227e8a8: ldr d2,[x0, #0x10]
27227e8ac: ldr d3,[x1, #0x10]
27227e8b0: fcmp d3,d2
27227e8b4: b.mi 0x27227e8d4
27227e8b8: mov x9,x8
27227e8bc: st2 {v0.2D,v1.2D},[x9], #0x20
27227e8c0: str d2,[x9]
27227e8c4: str d3,[x8, #0x28]
27227e8c8: ret
27227e8cc: brk #0x1
27227e8d0: brk #0x1
27227e8d4: brk #0x1

