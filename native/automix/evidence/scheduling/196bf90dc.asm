; CoreMedia iOS 23A341, SHA256 0b6fc4decae765a863e6cf91c4f321f25969b61c3755aeb19adcb784da960066
196bf90dc: 480000ca eor x8, x2, x0
196bf90e0: 2803f8b7 tbnz x8, #0x3f, #0x196bf9144
196bf90e4: 1f0000f1 cmp x0, #0
196bf90e8: 085480da cneg x8, x0, mi
196bf90ec: 5f0000f1 cmp x2, #0
196bf90f0: 495482da cneg x9, x2, mi
196bf90f4: 0afd60d3 lsr x10, x8, #0x20
196bf90f8: 087da19b umull x8, w8, w1
196bf90fc: 0bfd60d3 lsr x11, x8, #0x20
196bf9100: 4a2da19b umaddl x10, w10, w1, x11
196bf9104: 2bfd60d3 lsr x11, x9, #0x20
196bf9108: 297da39b umull x9, w9, w3
196bf910c: 2cfd60d3 lsr x12, x9, #0x20
196bf9110: 6b31a39b umaddl x11, w11, w3, x12
196bf9114: 5f010beb cmp x10, x11
196bf9118: ea239f5a csetm w10, lo
196bf911c: 4a959f1a csinc w10, w10, wzr, ls
196bf9120: e803082a mov w8, w8
196bf9124: 1f4129eb cmp x8, w9, uxtw
196bf9128: e8239f5a csetm w8, lo
196bf912c: 08959f1a csinc w8, w8, wzr, ls
196bf9130: 5f010071 cmp w10, #0
196bf9134: 4811881a csel w8, w10, w8, ne
196bf9138: 1f010071 cmp w8, #0
196bf913c: 001840fa ccmp x0, #0, #0, ne
196bf9140: 03000014 b #0x196bf914c
196bf9144: 1f0000f1 cmp x0, #0
196bf9148: 28008052 mov w8, #1
196bf914c: 00a5885a cneg w0, w8, lt
196bf9150: c0035fd6 ret 
