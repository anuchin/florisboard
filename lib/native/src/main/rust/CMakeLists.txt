cmake_minimum_required(VERSION 3.22)

project(voxkb)

### VoxKB ###

# Build a minimal C-only stub library. The real build uses Rust (see
# CMakeLists.rust.txt) but requires the MSVC linker for proc-macro crates.
# This stub provides the dummyAdd JNI symbol directly in C so the app can
# build and run on machines without the full Rust cross-compile toolchain.
add_library(fl_native SHARED src/lib_stub.c)

find_library(log-lib log)

target_link_libraries(fl_native
    android
    ${log-lib}
)
