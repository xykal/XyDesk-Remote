#pragma once
#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <string>
// Read-only Windows inventory; no driver install, policy writes or security bypass.
std::wstring xydesk_host_readiness_report();
void xydesk_show_readiness(HWND owner);
#endif
