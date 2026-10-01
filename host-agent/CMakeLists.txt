cmake_minimum_required(VERSION 3.16)
project(xydesk_remote_host LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(xydesk_quic SHARED src/xydesk_quic.cpp)
target_include_directories(xydesk_quic PUBLIC include)
if(WIN32)
  target_link_libraries(xydesk_quic PRIVATE ws2_32 ole32)
endif()

add_library(xydesk_host_core SHARED src/xydesk_host_core.cpp)
target_include_directories(xydesk_host_core PUBLIC include)
if(WIN32)
  target_link_libraries(xydesk_host_core PRIVATE ws2_32 dxgi advapi32 iphlpapi user32 gdi32)
endif()

add_executable(XyDeskRemoteHost src/main_host.cpp)
target_include_directories(XyDeskRemoteHost PRIVATE include)
target_link_libraries(XyDeskRemoteHost PRIVATE xydesk_quic xydesk_host_core)
