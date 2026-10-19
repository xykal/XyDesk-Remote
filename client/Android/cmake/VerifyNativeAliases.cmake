# Only these three libraries are exported by AGP's CMake SHARED IMPORTED model.
# Verify the copies first, then remove exactly those redundant jniLibs aliases.
# The originals under SOURCE_LIB_DIR remain for linking and AGP packaging.
# No pickFirst/excludes rule masks native collisions; codecs stay in jniLibs.
if(NOT DEFINED SOURCE_LIB_DIR OR NOT DEFINED PACKAGED_LIB_DIR)
  message(FATAL_ERROR "VerifyNativeAliases: SOURCE_LIB_DIR and PACKAGED_LIB_DIR required")
endif()
foreach(_xy_name IN ITEMS libfreerdp3.so libfreerdp-client3.so libwinpr3.so)
  set(_xy_source "${SOURCE_LIB_DIR}/${_xy_name}")
  set(_xy_packaged "${PACKAGED_LIB_DIR}/${_xy_name}")
  if(NOT EXISTS "${_xy_source}" OR NOT EXISTS "${_xy_packaged}")
    message(FATAL_ERROR "VerifyNativeAliases: missing ${_xy_name}")
  endif()
  file(SHA256 "${_xy_source}" _xy_source_hash)
  file(SHA256 "${_xy_packaged}" _xy_packaged_hash)
  if(NOT _xy_source_hash STREQUAL _xy_packaged_hash)
    message(FATAL_ERROR "VerifyNativeAliases: differing binaries for ${_xy_name}")
  endif()
  message(STATUS "VerifyNativeAliases: identical ${_xy_name} ${_xy_source_hash}")
endforeach()
# Validate all three before removing anything, so a mismatch cannot partially
# select providers. AGP packages each imported original exactly once.
foreach(_xy_name IN ITEMS libfreerdp3.so libfreerdp-client3.so libwinpr3.so)
  file(REMOVE "${PACKAGED_LIB_DIR}/${_xy_name}")
  if(EXISTS "${PACKAGED_LIB_DIR}/${_xy_name}")
    message(FATAL_ERROR "VerifyNativeAliases: cannot remove duplicate ${_xy_name}")
  endif()
endforeach()
