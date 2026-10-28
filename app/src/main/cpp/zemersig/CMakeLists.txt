cmake_minimum_required(VERSION 3.22.1)
project(zemersig C)

# Built only when the build type has a signing key (see app/build.gradle.kts); the key header is generated into
# the build directory, never the source tree. A build type without one gets no library.
if(ZEMER_SIG_SKIP)
    return()
endif()

add_library(zemersig SHARED zemer_sig_jni.c hmac_sha256.c)
target_include_directories(zemersig PRIVATE ${ZEMER_SIG_KEY_DIR})
target_compile_options(zemersig PRIVATE -O2 -fvisibility=hidden -Wall)
# 16 KB page alignment (Android 15+ devices; checked by CI's scripts/check-16kb-alignment.sh)
target_link_options(zemersig PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--gc-sections" "-s")
