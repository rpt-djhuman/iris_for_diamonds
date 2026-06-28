# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Tell ggml which CPU architecture we're targeting so it includes the right
# optimised source files (arm/quants.c etc.) and sets correct compile flags.
if(DEFINED ANDROID_ABI)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
    endif()
endif()

add_subdirectory(../../../../../llama.cpp build-llama)

# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        llama-android.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ../../../../../llama.cpp/vendor/nlohmann)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        llama
        common
        android
        log)
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    message(STATUS "llama.android: Added optimizations for arm64-v8a")
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -O3 -march=armv8.2-a+fp16+dotprod)
else()
    message(STATUS "llama.android: No extra optimizations for ${ANDROID_ABI}")
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -O3)
endif()
