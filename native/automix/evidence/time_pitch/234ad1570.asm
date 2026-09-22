; Original vDSP 0x234ad1570..0x234ad1838
; SHA256 39248559630fa74c44cee2899819930f4495b7fb32aa92f9acf73dfb18475588
0x234ad1570: 7f2303d5 pacibsp 
0x234ad1574: 061600b4 cbz x6, #0x234ad1834
0x234ad1578: 072040a9 ldp x7, x8, [x0]
0x234ad157c: 4a2c40a9 ldp x10, x11, [x2]
0x234ad1580: 8c3440a9 ldp x12, x13, [x4]
0x234ad1584: fd7bbfa9 stp x29, x30, [sp, #-0x10]!
0x234ad1588: fd030091 mov x29, sp
0x234ad158c: df1000f1 cmp x6, #4
0x234ad1590: cb100054 b.lt #0x234ad17a8
0x234ad1594: 200040d2 eor x0, x1, #1
0x234ad1598: 620040d2 eor x2, x3, #1
0x234ad159c: 000002aa orr x0, x0, x2
0x234ad15a0: a20040d2 eor x2, x5, #1
0x234ad15a4: 000002aa orr x0, x0, x2
0x234ad15a8: 1f0000f1 cmp x0, #0
0x234ad15ac: e10f0054 b.ne #0x234ad17a8
0x234ad15b0: 9f0d40f2 tst x12, #0xf
0x234ad15b4: 60030054 b.eq #0x234ad1620
0x234ad15b8: bf0d40f2 tst x13, #0xf
0x234ad15bc: 20030054 b.eq #0x234ad1620
0x234ad15c0: 9f0540f2 tst x12, #3
0x234ad15c4: 80000054 b.eq #0x234ad15d4
0x234ad15c8: bf0540f2 tst x13, #3
0x234ad15cc: 40000054 b.eq #0x234ad15d4
0x234ad15d0: 14000014 b #0x234ad1620
0x234ad15d4: c60400f1 subs x6, x6, #1
0x234ad15d8: cb120054 b.lt #0x234ad1830
0x234ad15dc: fc4440bc ldr s28, [x7], #4
0x234ad15e0: 504540bc ldr s16, [x10], #4
0x234ad15e4: 644540bc ldr s4, [x11], #4
0x234ad15e8: 004540bc ldr s0, [x8], #4
0x234ad15ec: 9493905f fmul s20, s28, v16.s[0]
0x234ad15f0: 9893845f fmul s24, s28, v4.s[0]
0x234ad15f4: 80110091 add x0, x12, #4
0x234ad15f8: a2110091 add x2, x13, #4
0x234ad15fc: 1450845f fmls s20, s0, v4.s[0]
0x234ad1600: 1810905f fmla s24, s0, v16.s[0]
0x234ad1604: 1f0c40f2 tst x0, #0xf
0x234ad1608: 944500bc str s20, [x12], #4
0x234ad160c: b84500bc str s24, [x13], #4
0x234ad1610: 80000054 b.eq #0x234ad1620
0x234ad1614: 5f0c40f2 tst x2, #0xf
0x234ad1618: 40000054 b.eq #0x234ad1620
0x234ad161c: eeffff17 b #0x234ad15d4
0x234ad1620: c64000f1 subs x6, x6, #0x10
0x234ad1624: a4070054 b.mi #0x234ad1718
0x234ad1628: fca8df4c ld1 {v28.4s, v29.4s}, [x7], #32
0x234ad162c: 50a9df4c ld1 {v16.4s, v17.4s}, [x10], #32
0x234ad1630: 64a9df4c ld1 {v4.4s, v5.4s}, [x11], #32
0x234ad1634: 00a9df4c ld1 {v0.4s, v1.4s}, [x8], #32
0x234ad1638: c64000f1 subs x6, x6, #0x10
0x234ad163c: fea8df4c ld1 {v30.4s, v31.4s}, [x7], #32
0x234ad1640: 94df306e fmul v20.4s, v28.4s, v16.4s
0x234ad1644: b5df316e fmul v21.4s, v29.4s, v17.4s
0x234ad1648: 52a9df4c ld1 {v18.4s, v19.4s}, [x10], #32
0x234ad164c: 98df246e fmul v24.4s, v28.4s, v4.4s
0x234ad1650: b9df256e fmul v25.4s, v29.4s, v5.4s
0x234ad1654: 66a9df4c ld1 {v6.4s, v7.4s}, [x11], #32
0x234ad1658: 14cca44e fmls v20.4s, v0.4s, v4.4s
0x234ad165c: 35cca54e fmls v21.4s, v1.4s, v5.4s
0x234ad1660: 02a9df4c ld1 {v2.4s, v3.4s}, [x8], #32
0x234ad1664: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ad1668: 39cc314e fmla v25.4s, v1.4s, v17.4s
0x234ad166c: d6df326e fmul v22.4s, v30.4s, v18.4s
0x234ad1670: f7df336e fmul v23.4s, v31.4s, v19.4s
0x234ad1674: e4030054 b.mi #0x234ad16f0
0x234ad1678: fca8df4c ld1 {v28.4s, v29.4s}, [x7], #32
0x234ad167c: dadf266e fmul v26.4s, v30.4s, v6.4s
0x234ad1680: fbdf276e fmul v27.4s, v31.4s, v7.4s
0x234ad1684: 50a9df4c ld1 {v16.4s, v17.4s}, [x10], #32
0x234ad1688: 56cca64e fmls v22.4s, v2.4s, v6.4s
0x234ad168c: 77cca74e fmls v23.4s, v3.4s, v7.4s
0x234ad1690: 64a9df4c ld1 {v4.4s, v5.4s}, [x11], #32
0x234ad1694: 5acc324e fmla v26.4s, v2.4s, v18.4s
0x234ad1698: 7bcc334e fmla v27.4s, v3.4s, v19.4s
0x234ad169c: 00a9df4c ld1 {v0.4s, v1.4s}, [x8], #32
0x234ad16a0: c64000f1 subs x6, x6, #0x10
0x234ad16a4: fea8df4c ld1 {v30.4s, v31.4s}, [x7], #32
0x234ad16a8: 94a99f4c st1 {v20.4s, v21.4s}, [x12], #32
0x234ad16ac: 52a9df4c ld1 {v18.4s, v19.4s}, [x10], #32
0x234ad16b0: 94df306e fmul v20.4s, v28.4s, v16.4s
0x234ad16b4: b5df316e fmul v21.4s, v29.4s, v17.4s
0x234ad16b8: b8a99f4c st1 {v24.4s, v25.4s}, [x13], #32
0x234ad16bc: 98df246e fmul v24.4s, v28.4s, v4.4s
0x234ad16c0: b9df256e fmul v25.4s, v29.4s, v5.4s
0x234ad16c4: 66a9df4c ld1 {v6.4s, v7.4s}, [x11], #32
0x234ad16c8: 14cca44e fmls v20.4s, v0.4s, v4.4s
0x234ad16cc: 35cca54e fmls v21.4s, v1.4s, v5.4s
0x234ad16d0: 02a9df4c ld1 {v2.4s, v3.4s}, [x8], #32
0x234ad16d4: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ad16d8: 39cc314e fmla v25.4s, v1.4s, v17.4s
0x234ad16dc: 96a99f4c st1 {v22.4s, v23.4s}, [x12], #32
0x234ad16e0: baa99f4c st1 {v26.4s, v27.4s}, [x13], #32
0x234ad16e4: d6df326e fmul v22.4s, v30.4s, v18.4s
0x234ad16e8: f7df336e fmul v23.4s, v31.4s, v19.4s
0x234ad16ec: 65fcff54 b.pl #0x234ad1678
0x234ad16f0: dadf266e fmul v26.4s, v30.4s, v6.4s
0x234ad16f4: fbdf276e fmul v27.4s, v31.4s, v7.4s
0x234ad16f8: 56cca64e fmls v22.4s, v2.4s, v6.4s
0x234ad16fc: 77cca74e fmls v23.4s, v3.4s, v7.4s
0x234ad1700: 5acc324e fmla v26.4s, v2.4s, v18.4s
0x234ad1704: 7bcc334e fmla v27.4s, v3.4s, v19.4s
0x234ad1708: 94a99f4c st1 {v20.4s, v21.4s}, [x12], #32
0x234ad170c: b8a99f4c st1 {v24.4s, v25.4s}, [x13], #32
0x234ad1710: 96a99f4c st1 {v22.4s, v23.4s}, [x12], #32
0x234ad1714: baa99f4c st1 {v26.4s, v27.4s}, [x13], #32
0x234ad1718: c64000b1 adds x6, x6, #0x10
0x234ad171c: a0080054 b.eq #0x234ad1830
0x234ad1720: c62000f1 subs x6, x6, #8
0x234ad1724: 24020054 b.mi #0x234ad1768
0x234ad1728: fca8df4c ld1 {v28.4s, v29.4s}, [x7], #32
0x234ad172c: 50a9df4c ld1 {v16.4s, v17.4s}, [x10], #32
0x234ad1730: 64a9df4c ld1 {v4.4s, v5.4s}, [x11], #32
0x234ad1734: 00a9df4c ld1 {v0.4s, v1.4s}, [x8], #32
0x234ad1738: c62000f1 subs x6, x6, #8
0x234ad173c: 94df306e fmul v20.4s, v28.4s, v16.4s
0x234ad1740: b5df316e fmul v21.4s, v29.4s, v17.4s
0x234ad1744: 98df246e fmul v24.4s, v28.4s, v4.4s
0x234ad1748: b9df256e fmul v25.4s, v29.4s, v5.4s
0x234ad174c: 14cca44e fmls v20.4s, v0.4s, v4.4s
0x234ad1750: 35cca54e fmls v21.4s, v1.4s, v5.4s
0x234ad1754: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ad1758: 39cc314e fmla v25.4s, v1.4s, v17.4s
0x234ad175c: 94a99f4c st1 {v20.4s, v21.4s}, [x12], #32
0x234ad1760: b8a99f4c st1 {v24.4s, v25.4s}, [x13], #32
0x234ad1764: 25feff54 b.pl #0x234ad1728
0x234ad1768: c61000b1 adds x6, x6, #4
0x234ad176c: a4010054 b.mi #0x234ad17a0
0x234ad1770: fc78df4c ld1 {v28.4s}, [x7], #16
0x234ad1774: 5079df4c ld1 {v16.4s}, [x10], #16
0x234ad1778: 6479df4c ld1 {v4.4s}, [x11], #16
0x234ad177c: 0079df4c ld1 {v0.4s}, [x8], #16
0x234ad1780: c61000f1 subs x6, x6, #4
0x234ad1784: 94df306e fmul v20.4s, v28.4s, v16.4s
0x234ad1788: 98df246e fmul v24.4s, v28.4s, v4.4s
0x234ad178c: 14cca44e fmls v20.4s, v0.4s, v4.4s
0x234ad1790: 18cc304e fmla v24.4s, v0.4s, v16.4s
0x234ad1794: 94799f4c st1 {v20.4s}, [x12], #16
0x234ad1798: b8799f4c st1 {v24.4s}, [x13], #16
0x234ad179c: a5feff54 b.pl #0x234ad1770
0x234ad17a0: c61000b1 adds x6, x6, #4
0x234ad17a4: 60040054 b.eq #0x234ad1830
0x234ad17a8: fc0040bd ldr s28, [x7]
0x234ad17ac: 500140bd ldr s16, [x10]
0x234ad17b0: e708018b add x7, x7, x1, lsl #2
0x234ad17b4: 4a09038b add x10, x10, x3, lsl #2
0x234ad17b8: c60400f1 subs x6, x6, #1
0x234ad17bc: 640140bd ldr s4, [x11]
0x234ad17c0: 000140bd ldr s0, [x8]
0x234ad17c4: 0809018b add x8, x8, x1, lsl #2
0x234ad17c8: 6b09038b add x11, x11, x3, lsl #2
0x234ad17cc: 6d020054 b.le #0x234ad1818
0x234ad17d0: 9493905f fmul s20, s28, v16.s[0]
0x234ad17d4: 9893845f fmul s24, s28, v4.s[0]
0x234ad17d8: c60400f1 subs x6, x6, #1
0x234ad17dc: 1450845f fmls s20, s0, v4.s[0]
0x234ad17e0: 1810905f fmla s24, s0, v16.s[0]
0x234ad17e4: fc0040bd ldr s28, [x7]
0x234ad17e8: 500140bd ldr s16, [x10]
0x234ad17ec: e708018b add x7, x7, x1, lsl #2
0x234ad17f0: 4a09038b add x10, x10, x3, lsl #2
0x234ad17f4: 640140bd ldr s4, [x11]
0x234ad17f8: 000140bd ldr s0, [x8]
0x234ad17fc: 0809018b add x8, x8, x1, lsl #2
0x234ad1800: 6b09038b add x11, x11, x3, lsl #2
0x234ad1804: 940100bd str s20, [x12]
0x234ad1808: b80100bd str s24, [x13]
0x234ad180c: 8c09058b add x12, x12, x5, lsl #2
0x234ad1810: ad09058b add x13, x13, x5, lsl #2
0x234ad1814: ecfdff54 b.gt #0x234ad17d0
0x234ad1818: 9493905f fmul s20, s28, v16.s[0]
0x234ad181c: 9893845f fmul s24, s28, v4.s[0]
0x234ad1820: 1450845f fmls s20, s0, v4.s[0]
0x234ad1824: 1810905f fmla s24, s0, v16.s[0]
0x234ad1828: 940100bd str s20, [x12]
0x234ad182c: b80100bd str s24, [x13]
0x234ad1830: fd7bc1a8 ldp x29, x30, [sp], #0x10
0x234ad1834: ff0f5fd6 retab 
