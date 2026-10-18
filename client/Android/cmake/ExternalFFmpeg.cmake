include(ExternalProject)
include(DepVersions)

# Map Android ABI to FFmpeg cross-compile names
set(FFMPEG_EXTRA_ARGS "")
if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(FFMPEG_ARCH "aarch64")
  list(APPEND FFMPEG_EXTRA_ARGS "--cpu=armv8-a;--enable-neon;--enable-asm;--enable-inline-asm")
  set(FFMPEG_LDFLAGS "-march=armv8-a")
  set(FFMPEG_BIGENDIAN "no")
  set(FFMPEG_CLANG_TARGET "aarch64-linux-android${NDK_API_LEVEL}")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
  set(FFMPEG_ARCH "arm")
  list(APPEND FFMPEG_EXTRA_ARGS "--cpu=armv7-a;--enable-neon;--enable-asm;--enable-inline-asm")
  set(FFMPEG_LDFLAGS "-march=armv7-a -mfpu=neon -mfloat-abi=softfp -Wl,--fix-cortex-a8")
  set(FFMPEG_BIGENDIAN "")
  set(FFMPEG_CLANG_TARGET "armv7a-linux-androideabi${NDK_API_LEVEL}")
elseif(ANDROID_ABI STREQUAL "x86_64")
  set(FFMPEG_ARCH "x86_64")
  list(APPEND FFMPEG_EXTRA_ARGS "--cpu=x86-64;--disable-neon;--enable-asm;--enable-inline-asm") # x86_64
  set(FFMPEG_LDFLAGS "-march=x86-64")
  set(FFMPEG_BIGENDIAN "")
  set(FFMPEG_CLANG_TARGET "x86_64-linux-android${NDK_API_LEVEL}")
elseif(ANDROID_ABI STREQUAL "x86")
  set(FFMPEG_ARCH "x86")
  list(APPEND FFMPEG_EXTRA_ARGS "--cpu=i686;--disable-neon;--disable-asm;--disable-inline-asm") # FFmpeg issue #4928
  set(FFMPEG_LDFLAGS "-march=i686")
  set(FFMPEG_BIGENDIAN "")
  set(FFMPEG_CLANG_TARGET "i686-linux-android${NDK_API_LEVEL}")
elseif(ANDROID_ABI STREQUAL "riscv64")
  set(FFMPEG_ARCH "riscv64")
  list(APPEND FFMPEG_EXTRA_ARGS "--disable-neon;--disable-asm;--disable-inline-asm") # FFmpeg issue #4928
  set(FFMPEG_BIGENDIAN "")
  set(FFMPEG_CLANG_TARGET "riscv64-linux-android${NDK_API_LEVEL}")
else()
  message(FATAL_ERROR "ExternalFFmpeg: unsupported ABI '${ANDROID_ABI}'")
endif()

set(NDK_TOOLCHAIN_BIN "${NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_PLATFORM}/bin")
set(NDK_SYSROOT "${NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_PLATFORM}/sysroot")

set(FFMPEG_CC "${NDK_TOOLCHAIN_BIN}/${FFMPEG_CLANG_TARGET}-clang")
set(FFMPEG_CXX "${NDK_TOOLCHAIN_BIN}/${FFMPEG_CLANG_TARGET}-clang++")

find_package(PkgConfig REQUIRED)

# Conditionally add ac_cv_c_bigendian env var (arm64 only)
if(FFMPEG_BIGENDIAN STREQUAL "no")
  set(FFMPEG_BIGENDIAN_ENV "ac_cv_c_bigendian=no")
else()
  set(FFMPEG_BIGENDIAN_ENV "")
endif()

if("openh264" IN_LIST ANDROID_NATIVE_DEPS)
  list(APPEND FFMPEG_EXTRA_ARGS --enable-libopenh264)
endif()
if("opus" IN_LIST ANDROID_NATIVE_DEPS)
  list(APPEND FFMPEG_EXTRA_ARGS --enable-libopus)
endif()

ExternalProject_Add(
  ffmpeg
  INSTALL_DIR ${DEPS_INSTALL_DIR}
  DOWNLOAD_EXTRACT_TIMESTAMP OFF
  SOURCE_DIR ${CMAKE_SOURCE_DIR}/external/ffmpeg
  BINARY_DIR ${CMAKE_BINARY_DIR}/external/ffmpeg
  URL https://github.com/FFmpeg/FFmpeg/archive/refs/tags/${FFMPEG_VERSION}.tar.gz
  URL_HASH ${FFMPEG_HASH}
  LIST_SEPARATOR |
  DEPENDS ${ANDROID_NATIVE_DEPS}
  CONFIGURE_COMMAND
    ${CMAKE_COMMAND} -E env "PATH=${NDK_TOOLCHAIN_BIN}:$ENV{PATH}"
    "PKG_CONFIG_PATH=${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR}/pkgconfig" ${FFMPEG_BIGENDIAN_ENV}
    <SOURCE_DIR>/configure --cross-prefix=${NDK_TOOLCHAIN_BIN}/llvm- --cc=${FFMPEG_CC} --cxx=${FFMPEG_CXX}
    --pkg-config=${PKG_CONFIG_EXECUTABLE} --sysroot=${NDK_SYSROOT} --arch=${FFMPEG_ARCH} ${FFMPEG_CPU}
    --extra-ldflags="${FFMPEG_LDFLAGS}" ${FFMPEG_EXTRA_ARGS} --target-os=android --enable-cross-compile --enable-pic
    --enable-lto --enable-jni --enable-mediacodec --enable-shared --disable-static --disable-programs --disable-doc
    # --- XyDesk slim-FFmpeg (APK size diet) -----------------------------------
    # FreeRDP 3.32 hanya meng-include header avcodec/avutil/swresample/swscale;
    # avformat/avfilter/avdevice/postproc tidak pernah dipakai (dead weight
    # ~8 MB/ABI di v1.1.21). Daftar codec = pemakaian nyata di dsp_ffmpeg.c
    # (audio rdpsnd/audin: AAC/AAC-LATM/MP3/OPUS/GSM/G723_1/ADPCM/PCM) dan
    # h264_ffmpeg.c (decode H264 GFX; encoder H264 memang tidak ada native di
    # FFmpeg — fallback openh264, WITH_FFMPEG_HWACCEL=OFF).
    # CATATAN URUTAN: configure FFmpeg diproses berurutan (last-wins), jadi
    # --disable-everything di bawah sengaja mematikan --enable-jni/mediacodec
    # di atas (WITH_MEDIACODEC=OFF di ExternalDeps).
    # Divalidasi terhadap source FFmpeg n8.1 (allcodecs.c/parsers.c/configure):
    # postproc sudah DIHAPUS di FFmpeg 8 (jangan pakai --disable-postproc),
    # parser MP3 namanya 'mpegaudio' (bukan 'mp3'/'mpeg4audio').
    --disable-everything
    --disable-avdevice --disable-avfilter --disable-avformat
    --enable-decoder=aac,aac_latm,mp3,mp3float,opus,h264,gsm,gsm_ms,adpcm_ms,adpcm_ima_oki,g723_1,pcm_alaw,pcm_mulaw,pcm_s16le,pcm_u8
    --enable-encoder=aac,opus,pcm_s16le,pcm_u8,pcm_alaw,pcm_mulaw
    --enable-parser=aac,aac_latm,h264,mpegaudio
    --prefix=${DEPS_INSTALL_DIR} --libdir=${DEPS_INSTALL_DIR}/${CMAKE_INSTALL_LIBDIR}
  BUILD_COMMAND ${CMAKE_COMMAND} -E env "PATH=${NDK_TOOLCHAIN_BIN}:$ENV{PATH}" make -j
  INSTALL_COMMAND ${CMAKE_COMMAND} -E env "PATH=${NDK_TOOLCHAIN_BIN}:$ENV{PATH}" make -j install
)
