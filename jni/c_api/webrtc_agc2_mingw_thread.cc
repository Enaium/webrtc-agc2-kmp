/*
 *  Copyright (c) 2026 The WebRTC AGC2 Kotlin project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree.
 */

/*
 * MinGW build of rtc_base/platform_thread_types.cc.
 *
 * The extraction's own translation unit is upstream WebRTC code whose Windows
 * branch sets the thread name through MSVC's structured exception handling
 * (__try / __except), which GCC cannot parse. It is therefore excluded from the
 * MinGW build (see jni/CMakeLists.txt) and the same four functions are provided
 * here instead, in the shape the sibling webrtc-aec3 extraction uses for MinGW.
 *
 * Nothing else in the library sees a difference: the names are the ones the
 * header declares, and rtc_base/logging.cc is the only caller
 * (CurrentThreadId()).
 */
#if defined(__MINGW32__)

#include <windows.h>

#include "rtc_base/platform_thread_types.h"

namespace webrtc {

PlatformThreadId CurrentThreadId() {
  return ::GetCurrentThreadId();
}

PlatformThreadRef CurrentThreadRef() {
  return ::GetCurrentThreadId();
}

bool IsThreadRefEqual(const PlatformThreadRef& a, const PlatformThreadRef& b) {
  return a == b;
}

void SetCurrentThreadName(const char* name) {
  // SetThreadDescription exists from Windows 10 1607 on and is what names the
  // thread for the debugger. Older systems only had the MSVC exception, which
  // MinGW cannot raise, so there the name is left unset.
  using SetThreadDescriptionFn = HRESULT(WINAPI*)(HANDLE, PCWSTR);
  static const SetThreadDescriptionFn set_thread_description =
      reinterpret_cast<SetThreadDescriptionFn>(::GetProcAddress(
          ::GetModuleHandleW(L"kernel32.dll"), "SetThreadDescription"));
  if (set_thread_description == nullptr) return;

  constexpr int kMaxThreadNameChars = 64;
  wchar_t wide[kMaxThreadNameChars];
  const int written =
      ::MultiByteToWideChar(CP_UTF8, 0, name, -1, wide, kMaxThreadNameChars);
  if (written > 0) {
    set_thread_description(::GetCurrentThread(), wide);
  }
}

}  // namespace webrtc

#endif  // defined(__MINGW32__)
