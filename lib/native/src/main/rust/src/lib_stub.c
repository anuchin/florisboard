// Minimal stub: provides dummyAdd so the JNI symbol resolves without the Rust build.
// This is a temporary workaround for environments lacking the MSVC linker needed
// for Rust cross-compilation of proc-macro crates.

#include <jni.h>

JNIEXPORT jint JNICALL
Java_com_voxkb_libnative_TestKt_dummyAdd(
    JNIEnv* env,
    jclass clazz,
    jint a,
    jint b
) {
    return a + b;
}
