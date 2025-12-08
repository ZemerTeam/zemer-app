cmake_minimum_required(VERSION 3.18)
project(coverart)

# Size optimization flags
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -Os -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Os -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--strip-all")

# Enable LTO for release builds
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELEASE ON)

# Build Bento4 as static library
set(BUILD_APPS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
add_subdirectory(bento4)

# Our JNI wrapper library
add_library(coverart SHARED
    coverart_jni.cpp
)

target_include_directories(coverart PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/bento4/Source/C++/Core
    ${CMAKE_CURRENT_SOURCE_DIR}/bento4/Source/C++/Codecs
    ${CMAKE_CURRENT_SOURCE_DIR}/bento4/Source/C++/Crypto
    ${CMAKE_CURRENT_SOURCE_DIR}/bento4/Source/C++/MetaData
)

target_link_libraries(coverart
    ap4
    log
)
