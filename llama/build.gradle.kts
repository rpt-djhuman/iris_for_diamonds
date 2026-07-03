plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// KleidiAI A/B toggle. Build with -Pkleidiai=true to compile ggml's KleidiAI
// optimized ARM kernels (GGML_CPU_KLEIDIAI=ON); defaults OFF so the baseline
// build is unchanged. Note: the ON build pulls KleidiAI sources via CMake
// FetchContent on first configure, so it needs network access once.
val kleidiAiEnabled = (project.findProperty("kleidiai") as? String)?.toBoolean() ?: false

android {
    namespace = "android.llama.cpp"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // Add NDK properties if wanted, e.g.
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DGGML_CPU_KLEIDIAI=${if (kleidiAiEnabled) "ON" else "OFF"}"
                cppFlags += listOf()
                arguments += listOf()

                cppFlags("")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            // 3.31.x needed for KleidiAI's FetchContent (DOWNLOAD_EXTRACT_TIMESTAMP,
            // introduced in CMake 3.24); the NDK-bundled 3.22.1 is too old.
            version = "3.31.6"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.runtime:runtime-android:1.5.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
