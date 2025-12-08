cmake_minimum_required(VERSION 3.18)
project(coverart)

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
