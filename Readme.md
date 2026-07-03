<h2><a href="https://play.google.com/store/apps/details?id=com.nervesparks.irisGPT&hl=en_IN"  style="color: white;">Iris</a></h2>

## Project Description

- This repository contains llama.cpp based offline android chat application cloned from llama.cpp android example. Install, download model and run completely offline privately.
- The app supports downloading GGUF models from Hugging Face and offers customizable parameters for flexible use.
- Being open-source, it allows for easy modifications and improvements, providing a secure, private, and fully offline experience.

## Images

<div style="display: flex; gap: 15px; justify-content: center; flex-wrap: wrap;">
  <div style="text-align: center; width: 200px;">
    <img src="./images/main_screen.png" alt="Main Screen Screenshot" width="200">
    <p><strong>Main Screen</strong></p>
    <p>This is the main interface of the app where users can access all core functionalities.</p>
  </div>
  <div style="text-align: center; width: 200px;">
    <img src="./images/chat_screen.png" alt="Chat Screen Screenshot" width="200">
    <p><strong>Chat Screen</strong></p>
    <p>The chat feature allows users to interact in real-time and access AI-driven responses.</p>
  </div>
  <div style="text-align: center; width: 200px;">
    <img src="./images/settings_screen.png" alt="Settings Screen Screenshot" width="200">
    <p><strong>Settings Screen</strong></p>
    <p>Users can customize app preferences and configure their account settings here.</p>
  </div>
  <div style="text-align: center; width: 200px;">
    <img src="./images/models_screen.png" alt="Models Screen Screenshot" width="200">
    <p><strong>Models Screen</strong></p>
    <p>This screen displays available AI models and allows users to manage them efficiently.</p>
  </div>
  <div style="text-align: center; width: 200px;">
    <img src="./images/parameters_screen.png" alt="Parameters Screen Screenshot" width="200">
    <p><strong>Parameters Screen</strong></p>
    <p>Users can adjust parameters to fine-tune the app's performance based on their needs.</p>
  </div>
</div>

## Installation
- Get IRIS (offline GPT) on Google Play:

  [Get it on Google Play](https://play.google.com/store/apps/details?id=com.nervesparks.irisGPT&hl=en_IN)
## Run

- Go to releases : https://github.com/nerve-sparks/iris_android/releases
- Download app
- Install app

## Features

- Works Offline: Access all features without needing an internet connection.
- Privacy-Focused: All data is processed securely on your device.
- Expandable Models: Download external GGUF models from Hugging Face.
- Open Source: Fully transparent development.
- Customizable Parameters: n_threads, top_k, top_p, and temperature can be adjusted to optimize performance and behavior based on device capabilities and desired output.
- Text To Speech: Support for Text-to-Speech functionality.
- Speech To Text: Support for Speech-to-Text functionality.
- Default Model Selection: Set a default model to load automatically on startup.

## Optimizing Your Experience with Iris

The performance of Iris is directly influenced by the size, speed, and compute requirements of the models you use. These factors also impact the overall user experience. For a faster and smoother experience, consider downloading smaller models.

**Example Recommendation:**  
On opening the app, users can download suggested models to optimize performance based on their preferences and device capabilities.

---

#### Note:

- Smaller models are ideal for quicker interactions but may compromise slightly on response quality.
- Larger models offer more comprehensive responses but require higher compute power and may result in slower speeds.
- Choose a model that best balances speed and quality for your specific use case.

---

#### Disclaimer:

- Iris may produce **inaccurate results** depending on the complexity of queries and model limitations.
- Performance and accuracy are influenced by the size and type of model selected.

## Build

- Download Android studio
- Clone this repository and import into Android Studio

```bash
 git clone https://github.com/nerve-sparks/iris_android.git
```

- Clone llama.cpp as a **sibling folder** of this repo (the native build references it via `../llama.cpp`):

```bash
 git clone https://github.com/ggml-org/llama.cpp
```

  This fork tracks **recent mainline** llama.cpp — it uses the current `llama_memory_*` API and vendors ARM KleidiAI (v1.22.0). The old pinned commit no longer applies; validated against mainline as of mid-2026.

- Install the required build tools via Android Studio's **SDK Manager** (SDK Tools → enable "Show Package Details"):
  - **NDK 26.1.10909125**
  - **CMake 3.31.6** — the NDK-bundled CMake 3.22.1 is too old and fails on KleidiAI's download step. The build is pinned to 3.31.6 in `llama/build.gradle.kts`.

  (Gradle runs under JDK 17+; Android Studio's bundled JBR satisfies this.)

- Open developer options on the Android mobile phone.
- Enable developer options.
- Click on developer options and enable wireless debugging.
- In Android Studio, select the drop down on the left side of the 'app' button on the Navbar.
- Select on 'Pair devices using Wi-Fi'. A QR code appears on screen.
- Click on wireless debugging on Android phone. Select 'Pair device with QR code'. Scan the code. (Make sure both devices are on the same Wi-fi)
- You can use Usb Debugging also to connect your phone.
- Once the phone is connected, select the device name in the drop down menu and click on play button.
- In the app, download at least one model from the given options.
- Now you can run the app offline. (In airplane mode as well)

## Performance & build configuration

Iris runs models on the CPU via llama.cpp. Two build knobs control on-device speed:

- **Optimized ARM kernels (default).** The `:llama` module builds every ARM ISA tier as a separate backend (`GGML_CPU_ALL_VARIANTS`) and picks the best at runtime — `dotprod` + `i8mm` on capable chips, with a safe fallback on older ones (no `SIGILL`). On a Snapdragon 8 Gen 3 this is roughly **5.7× faster prompt processing and 1.6× faster generation** than a plain `armv8-a` build, costing ~+700 MB RAM for repacked weights. No device-specific setup required.
- **ARM KleidiAI (optional, off by default).** Build with `-Pkleidiai=true` to compile in ARM's KleidiAI kernels:

  ```bash
  ./gradlew :app:assembleRelease -Pkleidiai=true
  ```

  On current Snapdragon (i8mm, no SME2) KleidiAI matches ggml's own i8mm kernels — no measurable gain — so it stays off by default. Its real advantage is SME2, which needs newer silicon (Snapdragon 8 Elite / MediaTek Dimensity 9400). See [`docs/kleidiai-eval.md`](docs/kleidiai-eval.md) for the full enabled-vs-disabled evaluation and an SME2 profiling runbook.

**Benchmarking.** The in-app **Benchmark** screen measures prompt-processing (pp) and token-generation (tg) throughput, model load time, memory, and battery, and exports a CSV self-labeled with the build's KleidiAI state.

## Contributing

1. **Fork the repository.**
2. **Create a new feature branch:**
   ```bash
   git checkout -b my-new-feature
   ```
3. **Commit your changes:**
   ```bash
   git commit -m 'Add some feature'
   ```
4. **Push your branch to the remote repository:**
   ```bash
   git push origin my-new-feature
   ```
5. **Open a Pull Request.**
