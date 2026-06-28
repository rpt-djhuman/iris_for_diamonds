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

## Run log

| Date | Device | Diamonds quant | Threads | KleidiAI | Load (ms) | pp t/s | tg t/s | Avg tok/s | Peak mem (MB) | Batt temp (°C) | Notes |
|------|--------|----------------|---------|----------|-----------|--------|--------|-----------|---------------|----------------|-------|
|      |        |                |         | OFF      |           |        |        |           |               |                |       |
|      |        |                |         | ON       |           |        |        |           |               |                |       |

## Result summary

- **tg (token generation) speedup, ON vs OFF:** _____ %
- **pp (prompt processing) speedup, ON vs OFF:** _____ %
- **Memory / thermal / battery delta:** _____
- **Model quant tested + whether KleidiAI engaged it:** _____

## Conclusion / recommendation

_(Worth shipping with KleidiAI ON? Any quant change needed to benefit? Follow-ups.)_

---
Reports back to Night Shift action item: *Evaluate KleidiAI on RPL workloads with llama.cpp (enabled vs disabled)*.
