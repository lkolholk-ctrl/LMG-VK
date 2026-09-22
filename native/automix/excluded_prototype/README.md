# Rejected implementation assumptions — NOT BUILT

These files preserve the previous prototype for audit only. They are excluded
from all CMake targets following the user's requirement to implement only behavior
supported by the decompilation. They must not be integrated into Media3 or the app.

Unsupported choices include RBJ substitution for AudioUnit filters, resonance dB
to Q mapping, Nyquist clamping, delay filtering/interpolation policy, a four-comb
reverb, seeded delay layout, decay splitting, live coefficient updates, DSP reset
semantics and the alternative nested style-time policies. Prototype tests establish
only internal consistency, not Apple equivalence. Passing them does not authorize
restoring these algorithms to the implementation.

The former renderer depends on those effects and is excluded with them. The user
has not authorized substituting JUCE, Oboe, another reverb or a generic stretcher.
