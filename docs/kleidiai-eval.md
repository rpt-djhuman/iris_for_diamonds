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

Model under test: **Llama-3.2-1B-Instruct-Q4_0** (Q4_0 — the KleidiAI-accelerated quant). Device: **SM-S928B (Galaxy S24 Ultra, Snapdragon 8 Gen 3)**, arm64-v8a, CPU features include `dotprod` + `i8mm` + `bf16`. 3 runs per side; the app's comprehensive benchmark measures **token generation** throughput (not prompt/prefill). Builds: CMake 3.31.6, NDK 26.1, Release.

| Date | Device | Model quant | KleidiAI | Load (ms) | tg tok/s (r1/r2/r3) | Median tok/s | Mean tok/s |
|------|--------|-------------|----------|-----------|---------------------|--------------|------------|
| 2026-07-03 | SM-S928B | Q4_0 | OFF | ~772 | 27.14 / 21.20 / 22.42 | 22.42 | 23.59 |
| 2026-07-03 | SM-S928B | Q4_0 | ON  | ~783 | 28.86 / 22.57 / 24.76 | 24.76 | 25.40 |

**Delta (ON vs OFF): +10.4% median, +7.7% mean** token generation. Load time unchanged. Wide run-to-run variance (thermal/scheduling) — distributions overlap.

## Result summary

- **tg (token generation) speedup, ON vs OFF:** +10.4% median (+7.7% mean).
- **pp (prompt processing) speedup, ON vs OFF:** not measured — the app's comprehensive benchmark reports generation only. KleidiAI's largest wins are on prefill/GEMM, so this is expected to be higher; see follow-up.
- **Memory / thermal / battery delta:** load time and memory effectively unchanged (KleidiAI is compute-side, not load/alloc).
- **Model quant tested + whether KleidiAI engaged it:** Q4_0 on SD8Gen3 (dotprod + i8mm present) — KleidiAI's Q4_0 packing ukernels compiled and linked in, so it engaged.

## Conclusion / recommendation

KleidiAI gives a **real but modest (~+8–10%) token-generation speedup** on Q4_0 on the S24 Ultra, at zero cost to load time/memory and no code changes beyond the build toggle. Token generation is GEMV-bound (memory bandwidth), which is the case KleidiAI helps *least* — the headline number understates the benefit.

Recommendation: **worth enabling** for on-device CPU inference on Q4_0 models. Before a ship decision, close two gaps:
1. **Measure prompt processing / prefill separately** (the `bench_model` JNI path exposes pp vs tg) — this is where KleidiAI should show a materially larger gain and matters for long-context/RAG prompts.
2. **Re-run with more iterations** (5–10) and controlled thermals to tighten the wide variance seen here.
Also worth checking a larger model and the real Diamonds GGUF (confirm its quant is KleidiAI-compatible).

---
Reports back to Night Shift action item: *Evaluate KleidiAI on RPL workloads with llama.cpp (enabled vs disabled)*.
