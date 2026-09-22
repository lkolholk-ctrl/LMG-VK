; Binary: MusicKitInternal
; Address: 0x1d3e81ad4
; Symbol: _$s16MusicKitInternal12FlexAnalysisV5EventV4time5scoreAESgSd_SitcfC
; Signature: undefined _$s16MusicKitInternal12FlexAnalysisV5EventV4time5scoreAESgSd_SitcfC()
1d3e81ad4: sub x9,x0,#0x12c
1d3e81ad8: cmn x9,#0x65
1d3e81adc: b.hi 0x1d3e81b18
1d3e81ae0: sub x9,x0,#0x1f4
1d3e81ae4: cmn x9,#0x65
1d3e81ae8: b.hi 0x1d3e81b20
1d3e81aec: sub x9,x0,#0x2bc
1d3e81af0: cmn x9,#0x65
1d3e81af4: b.hi 0x1d3e81b28
1d3e81af8: sub x9,x0,#0x384
1d3e81afc: cmn x9,#0x65
1d3e81b00: b.hi 0x1d3e81b30
1d3e81b04: adrp x9,0x1d4497000
1d3e81b08: ldr q0,[x9, #0xfb0]
1d3e81b0c: str q0,[x8]
1d3e81b10: str xzr,[x8, #0x10]
1d3e81b14: ret
1d3e81b18: mov x9,#0x0
1d3e81b1c: b 0x1d3e81b34
1d3e81b20: mov w9,#0x1
1d3e81b24: b 0x1d3e81b34
1d3e81b28: mov w9,#0x2
1d3e81b2c: b 0x1d3e81b34
1d3e81b30: mov w9,#0x3
1d3e81b34: mov w10,#0x64
1d3e81b38: sdiv x11,x0,x10
1d3e81b3c: msub x10,x11,x10,x0
1d3e81b40: scvtf d1,x10
1d3e81b44: mov x10,#0x4059000000000000
1d3e81b48: fmov d2,x10
1d3e81b4c: str d0,[x8]
1d3e81b50: fdiv d0,d1,d2
1d3e81b54: str x9,[x8, #0x8]
1d3e81b58: str d0,[x8, #0x10]
1d3e81b5c: ret

