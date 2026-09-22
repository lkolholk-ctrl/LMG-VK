; Image: libEmbeddedSystemAUs.dylib iOS 23A341
; Range: 0x234fee538..0x234fee570
; Code SHA256: eff9dd0bf7bcaede844ed325d9dca81d6342bab0466a0a71b1c20d68e173e992
0x234fee538: 0820601e fcmp d0, #0.0
0x234fee53c: 09010054 b.ls #0x234fee55c
0x234fee540: 2820601e fcmp d1, #0.0
0x234fee544: c9000054 b.ls #0x234fee55c
0x234fee548: 680000d0 adrp x8, #0x234ffc000
0x234fee54c: 03c145fd ldr d3, [x8, #0xb80]
0x234fee550: 0020631e fcmp d0, d3
0x234fee554: 60cc601e fcsel d0, d3, d0, gt
0x234fee558: cffeff17 b #0x234fee094
0x234fee55c: 1f7c00a9 stp xzr, xzr, [x0]
0x234fee560: 1ffc01a9 stp xzr, xzr, [x0, #0x18]
0x234fee564: 08fee7d2 mov x8, #0x3ff0000000000000
0x234fee568: 080800f9 str x8, [x0, #0x10]
0x234fee56c: c0035fd6 ret 
