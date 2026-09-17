/*
 *  Copyright (c) 2026 The WebRTC AGC2 Kotlin project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree.
 */

/*
 * Link shim for the Kotlin/Native static libraries (see jni/CMakeLists.txt;
 * compiled only for the cinterop builds, never into the JNI shared libraries).
 *
 * The library is built against the host's headers but linked against the
 * runtimes that Kotlin/Native pins in its sysroot (libstdc++ from gcc 8.3 for
 * linuxX64, MSYS2 gcc 9.2 for mingwX64, and that linuxX64 sysroot's glibc),
 * which predate the symbols the current headers reference:
 *
 *  - std::__throw_bad_array_new_length (libstdc++ 11, GCC 11) is what the
 *    allocator's overflow checks call; audio_buffer.cc and channel_buffer.cc
 *    emit the reference. Neither of the pinned libstdc++ libraries defines it.
 *
 *  - __libc_single_threaded (glibc 2.32) is read by libstdc++'s inline
 *    reference counting: std::string::_Rep::_M_grab and friends call
 *    __gnu_cxx::__is_single_threaded(). Zero means "might be multi threaded",
 *    i.e. the conservative answer, so those helpers take their locked path,
 *    which is correct either way. The definition is weak: on a system whose C
 *    library defines the variable (any glibc 2.32+), the real one wins.
 *
 *  - __isoc23_strtoll/__isoc23_strtoull (glibc 2.38) are what the headers
 *    redirect strtoll/strtoull to in C23 mode, which _GNU_SOURCE - defined by
 *    the libstdc++ headers - turns on; rtc_base/strings/string_to_number.cc is
 *    the caller. They forward to the C99 entry points those callers were
 *    written against, which differ from C23 only in not accepting "0b".
 */
#include <new>

#if defined(_GLIBCXX_RELEASE) && _GLIBCXX_RELEASE >= 11 && \
    __cplusplus <= 202302L /* C++26 declares it constexpr inline */

namespace std {

// Not weak: on PE a weak definition degenerates into a weak external that lld
// reports as unresolved, and neither pinned libstdc++ defines the symbol, so
// there is nothing to defer to.
__attribute__((__noreturn__)) void __throw_bad_array_new_length() {
  throw bad_array_new_length();
}

}  // namespace std

#endif

#if defined(__linux__) && !defined(__ANDROID__)

#include <stdlib.h>

#if defined(__GLIBC__)

extern "C" {

__attribute__((weak)) char __libc_single_threaded = 0;

}  // extern "C"

/* glibc 2.38 introduced the redirect; the C23 mode it depends on is enabled in
 * the C++ translation units by the libstdc++ headers, not by this one, so gate
 * on the version rather than on __GLIBC_USE. */
#if __GLIBC_PREREQ(2, 38)

/* The headers point strtoll/strtoull at __isoc23_* through an asm label, so
 * the C99 symbols are reached under names of their own. */
extern "C" long long
webrtc_agc2_strtoll(const char*, char**, int) __asm__("strtoll");
extern "C" unsigned long long
webrtc_agc2_strtoull(const char*, char**, int) __asm__("strtoull");

extern "C" __attribute__((weak)) long long
__isoc23_strtoll(const char* nptr, char** endptr, int base) {
  return webrtc_agc2_strtoll(nptr, endptr, base);
}

extern "C" __attribute__((weak)) unsigned long long
__isoc23_strtoull(const char* nptr, char** endptr, int base) {
  return webrtc_agc2_strtoull(nptr, endptr, base);
}

#endif /* __GLIBC_PREREQ(2, 38) */
#endif /* __GLIBC__ */
#endif /* __linux__ && !__ANDROID__ */
