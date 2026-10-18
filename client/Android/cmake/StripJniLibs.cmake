# XyDesk — APK size diet.
#
# AGP mengemas prebuilt .so dari `jniLibs/<abi>/` apa adanya; pada pipeline
# ini symbol stripping tidak terjadi di sisi Gradle (bukti: libfreerdp3.so
# 20 MB dengan debug_info ikut ter-ship di APK rilis v1.1.21). Script ini
# dipanggil `copyLibs` (ExternalDeps.cmake) lewat `cmake -P` setelah
# copy_directory, jadi setiap .so yang akan dikemas sudah ter-strip.
#
# Input (wajib): -DSTRIP_TOOL=<llvm-strip> -DLIB_DIR=<jniLibs/abi>
if(NOT DEFINED STRIP_TOOL OR NOT DEFINED LIB_DIR)
  message(FATAL_ERROR "StripJniLibs.cmake: STRIP_TOOL dan LIB_DIR wajib diisi")
endif()

file(GLOB _xy_libs "${LIB_DIR}/*.so")
foreach(_xy_lib IN LISTS _xy_libs)
  # --strip-unneeded = perilaku standar NDK untuk .so rilis: buang debug_info
  # dan symbol yang tidak dibutuhkan relocation. SONAME/dynamic symbols aman.
  execute_process(
    COMMAND "${STRIP_TOOL}" --strip-unneeded "${_xy_lib}"
    RESULT_VARIABLE _xy_rc
    ERROR_QUIET
  )
  if(NOT _xy_rc EQUAL 0)
    message(WARNING "StripJniLibs: gagal strip ${_xy_lib} (rc=${_xy_rc})")
  endif()
endforeach()

# Deduplikasi dev-name: `libpng.so` adalah salinan symlink dev dari
# `libpng16.so`; runtime (dlopen/DT_NEEDED) hanya memakai SONAME libpng16.so.
if(EXISTS "${LIB_DIR}/libpng.so" AND EXISTS "${LIB_DIR}/libpng16.so")
  file(REMOVE "${LIB_DIR}/libpng.so")
  message(STATUS "StripJniLibs: hapus duplikat dev-name ${LIB_DIR}/libpng.so")
endif()
