# kookyar_piano — کوک یار پیانو

Piano tuner in the KookYar family. Same stack as the other native tuners
(Java, `kookyar_common`, libpd), built from the guitar app skeleton.

- 88-key scrollable keyboard (`PianoKeyboardView`), octave jump buttons,
  auto key detection, reference tone per key.
- Pd patch in `app/src/main/res/raw/temp` (zip containing `path`):
  `fiddle~ 4096` so the lowest octave (A0 = 27.5 Hz) is tracked, and a
  3-harmonic additive reference tone with a 1.5 s decay.
- applicationId `com.TuningApps.PianoTuner`, namespace `com.PouyaApp.kookyarpiano`.

Build: `./gradlew assembleDebug` (needs `local.properties` → `sdk.dir`).
Release AAB: `../build_play_store.sh` includes this app.
