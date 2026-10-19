cmake_minimum_required(VERSION 3.16)
project(xydesk_remote_host LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# One application artifact: no companion XyDesk DLLs or separate Standalone variant.
add_executable(XyDeskRemoteHost WIN32
    src/host_readiness.cpp
    src/main_host.cpp
    src/xydesk_quic.cpp
    src/xydesk_host_core.cpp
)
target_include_directories(XyDeskRemoteHost PRIVATE include)
target_compile_definitions(XyDeskRemoteHost PRIVATE XYDESK_STATIC)
if(WIN32)
    target_link_libraries(XyDeskRemoteHost PRIVATE
        ws2_32 ole32 uuid propsys dxgi advapi32 iphlpapi user32 gdi32 shell32 dwmapi wtsapi32 winhttp
    )
    if(MINGW)
        target_link_options(XyDeskRemoteHost PRIVATE -static -static-libgcc -static-libstdc++ -mwindows)
    endif()
endif()
