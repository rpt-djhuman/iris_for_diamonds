# KleidiAI on-device evaluation — Iris / Diamonds

Tracks the *"Evaluate KleidiAI on RPL workloads with llama.cpp (enabled vs disabled)"*
benchmark. Iris runs the Diamonds model on-device via llama.cpp; this measures
what ARM's KleidiAI optimized CPU kernels buy us on real hardware.

## What KleidiAI is

KleidiAI is ARM's library of optimized micro-kernels (dotprod / i8mm / SME matmul)
that ggml can call instead of its generic CPU kernels. In llama.cpp it is gated
behind the `GGML_CPU_KLEIDIAI` CMake option (default OFF) and only accelerates
**compatible quantization types** — primarily `Q4_0` via online repacking. If the
Diamonds GGUF is a type KleidiAI does not repack (e.g. some `Q4_K`/`Q6_K` paths),
ON vs OFF will show little or no difference. **Confirm the model quant first.**

## How the toggle works

A Gradle property drives both the native build flag and an in-app label:

```bash
export JAVA_HOME=/home/dirk/android-studio/jbr   # AGP needs JDK 17+ (system default is 11)

# Baseline — KleidiAI OFF (default)
./gradlew :app:assembleRelease

# KleidiAI ON — first build downloads KleidiAI v1.22.0 via CMake FetchContent (needs network)
./gradlew :app:assembleRelease -Pkleidiai=true
```

- `-Pkleidiai=true` → `-DGGML_CPU_KLEIDIAI=ON` into ggml's CMake.
- The same property sets `BuildConfig.KLEIDIAI_ENABLED`, shown on the Benchmark
  screen's device-info card and stamped into every exported CSV
  (`# KleidiAI: Enabled/Disabled`), so ON/OFF runs cannot be confused.

## Procedure

1. Build and install the **OFF** APK. Open the Benchmark screen, pick the Diamonds
   model, run it, export the CSV.
2. Build and install the **ON** APK. Run the **same model**, **same thread count**,
   on the **same device** under comparable thermal/power conditions. Export the CSV.
3. Repeat each side a few times; report the median, not a single run.
4. Fill in the table below from the CSV headers + summary rows.

### Keep the comparison fair
- Same device, same Diamonds GGUF, same `user_thread` count.
- Device plugged in (or same battery band) and thermally settled between runs.
- Note the quant type of the model under test.

## Important: what "enabled vs disabled" means (per ARM)

ARM's own profiling guide (`profiling_ext.md`) defines the intended comparison as
**SME2 on vs off at runtime** (`GGML_KLEIDIAI_SME=0` vs `=1`) on a *single*
KleidiAI-enabled `llama-server` binary built with
`-DGGML_CPU_ARM_ARCH=armv9.2-a+dotprod+i8mm+sve2+sme2` — **not** KleidiAI compiled
in vs out. KleidiAI's headline advantage is its **SME2** kernels.

**The SM-S928B (Galaxy S24 Ultra, Snapdragon 8 Gen 3) has no SME2** (CPU features:
`...asimddp ... i8mm bf16 ...`; no `sme`/`sve`). So KleidiAI's differentiating path
cannot run on this device — it falls back to i8mm, which is the same math ggml's own
kernels use. A meaningful KleidiAI eval needs an **SME2-capable SoC** (Armv9.2 + SME2,
e.g. Snapdragon 8 Elite Cortex-X925/A725, or MediaTek Dimensity 9400).

## Run log — Q4_0, SM-S928B, CMake 3.31.6 / NDK 26.1 / Release

All rows below use `-DGGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod+i8mm` (i8mm active).
pp/tg from the native `bench_model` harness (pp=512, tg=128, 3 reps), measured cold.

| KleidiAI | pp t/s (r1/r2/r3) | tg t/s (r1/r2/r3) | gen avg | mem impact | peak mem |
|----------|-------------------|-------------------|---------|-----------|----------|
| ON (i8mm + KleidiAI) | 239.6 / 285.9 / 302.4 | 35.3 / 40.7 / 43.0 | ~32.5 | ~1586 MB | ~1830 MB |
| OFF (i8mm, ggml only) | 299.2 / 166.4* / 316.7 | 43.1 / 22.8* / 44.7 | ~33 | ~1592 MB | ~1823 MB |

\* the 12:07 OFF run is a thermal outlier (pp/tg/gen all depressed together) — excluded from medians.

**ON vs OFF (clean-run medians): pp ~286 vs ~308, tg ~41 vs ~44 — no KleidiAI advantage** (OFF a hair faster, within the ±15–35% run-to-run noise). Confirmed KleidiAI *was* active on ON via logcat: `primary q4 kernel feature I8MM`, `CPU_KLEIDIAI model buffer size = 504 MiB`. So this is a genuine null result, not an inactive build.

## The bigger finding: the app's ggml build was missing dotprod/i8mm

Until the `GGML_CPU_ARM_ARCH` fix, ggml-cpu cross-compiled at the NDK default
**`armv8-a`** — no dotprod, no i8mm — so *every* build (KleidiAI on and off) ran
plain-NEON kernels. Logcat before the fix: `CPU : NEON=1 | ARM_FMA=1 ...` (no
DOTPROD/MATMUL_INT8) and `kleidiai: ... CPU features mask 0`. Adding
`-DGGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod+i8mm` gave a large speedup on the same
device, **independent of KleidiAI**:

| Metric | armv8-a (before) | +dotprod+i8mm (after) | gain |
|--------|------------------|-----------------------|------|
| pp (prefill) | ~53 t/s | ~300 t/s | **~5.7×** |
| tg (generation) | ~26 t/s | ~43 t/s | ~1.6× |
| mem impact | ~900 MB | ~1590 MB | +~700 MB |

The extra ~700 MB is the **weight-repacking cost** (ggml `CPU_REPACK` + KleidiAI
`CPU_KLEIDIAI` buffers hold repacked copies of the Q4_0 weights) — the RAM-for-speed
tradeoff that comes with the i8mm path.

## Result summary

- **KleidiAI (on/off), Q4_0, SD8Gen3:** no measurable difference — KleidiAI ≈ ggml i8mm.
- **Why:** this device has no SME2, which is KleidiAI's real differentiator; without it, KleidiAI runs the same i8mm math as ggml.
- **Bonus (bigger) finding:** the app shipped a non-optimized ggml build (armv8-a); enabling dotprod/i8mm gives ~5.7× prefill and ~1.6× generation on any modern Android CPU, at +~700 MB RAM.

## Conclusion / recommendation

1. **KleidiAI is not worth enabling for Q4_0 on current Snapdragon (SD8Gen3)** — no gain over ggml's own i8mm kernels. Re-evaluate only on **SME2-capable hardware**, using ARM's `GGML_KLEIDIAI_SME=0/1` `llama-server` flow with a `+sve2+sme2` build.
2. **Ship the optimized-ISA build — done, via `GGML_CPU_ALL_VARIANTS`.** The build now compiles every ARM ISA tier (`armv8.0` … `armv8.2+dotprod` … `armv8.6+i8mm` … `armv9.2+sme`) as separate dlopen-able backends (`GGML_BACKEND_DL` + shared libs) and selects the best at runtime. Verified on the S24 Ultra: it auto-loads `libggml-cpu-android_armv8.6_1.so` (i8mm), matches the hard-pinned build (pp ≈ 301, tg ≈ 46 t/s) with no dispatch cost, and falls back safely on pre-i8mm CPUs — **no SIGILL**. Requires `.so` extraction (`packaging { jniLibs { useLegacyPackaging = true } }`) and a startup `ggml_backend_load_all_from_path()` pointed at the app lib dir (found via `dladdr`). Budget ~+700 MB RAM for the repacked weights.
3. Follow-ups: source an SME2 device for the true KleidiAI test; sanity-check the real Diamonds GGUF quant; more reps + thermal control to tighten variance.

---
Reports back to Night Shift action item: *Evaluate KleidiAI on RPL workloads with llama.cpp (enabled vs disabled)*.
