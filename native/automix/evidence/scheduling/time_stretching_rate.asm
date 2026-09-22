; Binary: _SonicKit_MusicKit_Packages
; Address: 0x272282538
; Symbol: _$s015_SonicKit_MusicB9_Packages10TransitionV15SteppedScheduleV18TimeStretchingStepV12playbackRateSdvg
; Signature: undefined _$s015_SonicKit_MusicB9_Packages10TransitionV15SteppedScheduleV18TimeStretchingStepV12playbackRateSdvg()
272282538: ldp d0,d1,[x20]
27228253c: ldp d2,d3,[x20, #0x10]
272282540: fsub d0,d1,d0
272282544: fsub d1,d3,d2
272282548: fdiv d0,d0,d1
27228254c: fcmp d1,#0.0
272282550: movi d1,#0x0
272282554: fcsel d0,d0,d1,gt
272282558: ret

