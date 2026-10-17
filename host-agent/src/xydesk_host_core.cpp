#define XYDESK_HOST_CORE_EXPORTS
#include "../include/xydesk_host_core.h"

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <iphlpapi.h>
#include <dxgi.h>
#endif

#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace {

void EncodeIpv4ToPcId(const char* ipv4, char* out, size_t cap) {
    if (!ipv4 || !out || cap < 16) return;
    unsigned int a = 0, b = 0, c = 0, d = 0;
    if (sscanf(ipv4, "%u.%u.%u.%u", &a, &b, &c, &d) != 4 ||
        a > 255 || b > 255 || c > 255 || d > 255) {
        snprintf(out, cap, "000-000-0000");
        return;
    }
    uint32_t num = ((a & 0xFFu) << 24) | ((b & 0xFFu) << 16) | ((c & 0xFFu) << 8) | (d & 0xFFu);
    char digits[16] = {};
    snprintf(digits, sizeof(digits), "%010u", num);
    snprintf(out, cap, "%.3s-%.3s-%.4s", digits, digits + 3, digits + 6);
}

#ifdef _WIN32
void WriteRegDword(HKEY root, const char* subkey, const char* name, DWORD val) {
    HKEY hKey = nullptr;
    if (RegCreateKeyExA(root, subkey, 0, nullptr, REG_OPTION_NON_VOLATILE,
                        KEY_SET_VALUE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        RegSetValueExA(hKey, name, 0, REG_DWORD,
                       reinterpret_cast<const BYTE*>(&val), sizeof(val));
        RegCloseKey(hKey);
    }
}

void WriteRegString(HKEY root, const char* subkey, const char* name, const char* val) {
    if (!val) return;
    HKEY hKey = nullptr;
    if (RegCreateKeyExA(root, subkey, 0, nullptr, REG_OPTION_NON_VOLATILE,
                        KEY_SET_VALUE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        RegSetValueExA(hKey, name, 0, REG_SZ,
                       reinterpret_cast<const BYTE*>(val),
                       static_cast<DWORD>(strlen(val) + 1));
        RegCloseKey(hKey);
    }
}

void RunHiddenCmd(const char* cmdLine) {
    if (!cmdLine || !*cmdLine) return;
    STARTUPINFOA si{};
    PROCESS_INFORMATION pi{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    char mutableCmd[1024] = {};
    snprintf(mutableCmd, sizeof(mutableCmd), "%s", cmdLine);
    if (CreateProcessA(nullptr, mutableCmd, nullptr, nullptr, FALSE,
                       CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        WaitForSingleObject(pi.hProcess, 4000);
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    }
}
#endif

}  // namespace

extern "C" XYDESK_CORE_API const char* xydesk_host_core_version(void) {
    return "xydesk_host_core.dll v0.5.30 (Native C++17 · DXGI GPU + AVC444 + ClearType Engine)";
}

extern "C" XYDESK_CORE_API int xydesk_host_inspect_system(XyQuicHostMetadata* out_meta) {
    if (!out_meta) return -1;
    memset(out_meta, 0, sizeof(*out_meta));
    out_meta->rdp_port = 3389;
    out_meta->quic_port = 4433;
    out_meta->max_fps = 120;
    snprintf(out_meta->hostname, sizeof(out_meta->hostname), "WINDOWS-PC");
    snprintf(out_meta->primary_ip, sizeof(out_meta->primary_ip), "127.0.0.1");
    snprintf(out_meta->gpu_name, sizeof(out_meta->gpu_name), "Microsoft Basic Render Driver");
    snprintf(out_meta->gpu_encoder, sizeof(out_meta->gpu_encoder), "CPU");

#ifdef _WIN32
    DWORD hlen = sizeof(out_meta->hostname) - 1;
    GetComputerNameA(out_meta->hostname, &hlen);

    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) == 0) {
        char host_buf[256] = {};
        if (gethostname(host_buf, sizeof(host_buf) - 1) == 0) {
            addrinfo hints{};
            hints.ai_family = AF_INET;
            addrinfo* res = nullptr;
            if (getaddrinfo(host_buf, nullptr, &hints, &res) == 0 && res) {
                for (addrinfo* p = res; p != nullptr; p = p->ai_next) {
                    auto* sin = reinterpret_cast<sockaddr_in*>(p->ai_addr);
                    char ip[64] = {};
                    inet_ntop(AF_INET, &sin->sin_addr, ip, sizeof(ip));
                    if (strncmp(ip, "127.", 4) != 0 && strncmp(ip, "169.254.", 8) != 0) {
                        snprintf(out_meta->primary_ip, sizeof(out_meta->primary_ip), "%s", ip);
                        if (strncmp(ip, "192.168.", 8) == 0 || strncmp(ip, "10.", 3) == 0 ||
                            strncmp(ip, "172.", 4) == 0) {
                            break;
                        }
                    }
                }
                freeaddrinfo(res);
            }
        }
        WSACleanup();
    }

    static const GUID kIID_IDXGIFactory1 = {
        0x770aae78, 0xf26f, 0x4dba, {0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87}
    };
    IDXGIFactory1* factory = nullptr;
    if (SUCCEEDED(CreateDXGIFactory1(kIID_IDXGIFactory1, reinterpret_cast<void**>(&factory))) && factory) {
        IDXGIAdapter1* adapter = nullptr;
        if (SUCCEEDED(factory->EnumAdapters1(0, &adapter)) && adapter) {
            DXGI_ADAPTER_DESC1 desc{};
            if (SUCCEEDED(adapter->GetDesc1(&desc))) {
                WideCharToMultiByte(CP_UTF8, 0, desc.Description, -1,
                                    out_meta->gpu_name, sizeof(out_meta->gpu_name) - 1,
                                    nullptr, nullptr);
                if (desc.VendorId == 0x10DE) {
                    snprintf(out_meta->gpu_encoder, sizeof(out_meta->gpu_encoder), "NVENC");
                } else if (desc.VendorId == 0x1002 || desc.VendorId == 0x1022) {
                    snprintf(out_meta->gpu_encoder, sizeof(out_meta->gpu_encoder), "AMF");
                } else if (desc.VendorId == 0x8086) {
                    snprintf(out_meta->gpu_encoder, sizeof(out_meta->gpu_encoder), "QSV");
                }
            }
            adapter->Release();
        }
        factory->Release();
    }
#endif

    EncodeIpv4ToPcId(out_meta->primary_ip, out_meta->pc_id, sizeof(out_meta->pc_id));
    return 0;
}

extern "C" XYDESK_CORE_API int xydesk_host_apply_windows_policies(void) {
#ifdef _WIN32
    const char* ts_ctrl = "SYSTEM\\CurrentControlSet\\Control\\Terminal Server";
    const char* ts_rdp = "SYSTEM\\CurrentControlSet\\Control\\Terminal Server\\WinStations\\RDP-Tcp";
    const char* ts_pol = "SOFTWARE\\Policies\\Microsoft\\Windows NT\\Terminal Services";

    // 1. Enable RDP + Multi-User Concurrent Sessions + Audio & On-Mic Capture + Camera
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_ctrl, "fDenyTSConnections", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_ctrl, "fSingleSessionPerUser", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_ctrl, "fDisableAudio", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_ctrl, "fDisableAudioCapture", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_ctrl, "fDisableCam", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_ctrl, "fDisableCdm", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_ctrl, "fDisableClip", 0);

    WriteRegDword(HKEY_LOCAL_MACHINE, ts_rdp, "fDisableAudio", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_rdp, "fDisableAudioCapture", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_rdp, "fDisableCam", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_rdp, "fDisableCdm", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_rdp, "fDisableClip", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_rdp, "MaxMonitors", 16);

    // 2. Enable Hardware GPU H.264 / AVC 4:4:4 Full-Chroma, On-Mic Policy & 120 FPS DWM
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fDenyTSConnections", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fSingleSessionPerUser", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fDisableAudio", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fDisableAudioCapture", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fDisableCam", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fDisableCdm", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fDisableClip", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fNoRemoteDesktopWallpaper", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "AudioQualityMode", 2);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "AVC444ModePreferred", 1);

    // 2a. Unlock Windows Privacy Consent for Microphone & Webcam (Store + Desktop Win32 Games/Discord)
    WriteRegString(HKEY_LOCAL_MACHINE,
                   "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\microphone",
                   "Value", "Allow");
    WriteRegString(HKEY_LOCAL_MACHINE,
                   "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\microphone\\NonPackaged",
                   "Value", "Allow");
    WriteRegString(HKEY_LOCAL_MACHINE,
                   "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\webcam",
                   "Value", "Allow");
    WriteRegString(HKEY_LOCAL_MACHINE,
                   "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\webcam\\NonPackaged",
                   "Value", "Allow");
    WriteRegString(HKEY_CURRENT_USER,
                   "Software\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\microphone",
                   "Value", "Allow");
    WriteRegString(HKEY_CURRENT_USER,
                   "Software\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\microphone\\NonPackaged",
                   "Value", "Allow");
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "AVCHardwareEncodePreferred", 1);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "bEnumerateHWBeforeSW", 1);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "VGAdapter", 1);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "SelectTransport", 0); // TCP + UDP
    // Nama nilai kebijakan resmi: "Do not allow font smoothing" = fNoFontSmoothing.
    // Nilai lama (fAllowFontAntiAlias) BUKAN kunci kebijakan yang dibaca Windows,
    // jadi tulisannya tidak berefek apa pun. 0 = font smoothing/ClearType boleh.
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fNoFontSmoothing", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "fAllowDesktopComposition", 1);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "DWMFRAMEINTERVAL", 8);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "Smoothening", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "RemoteFXCompressionAlgorithm", 0);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "EnableFrameBufferRedirects", 1);
    WriteRegDword(HKEY_LOCAL_MACHINE, ts_pol, "MaxCompressionLevel", 1);
    WriteRegDword(HKEY_LOCAL_MACHINE,
                  "SYSTEM\\CurrentControlSet\\Control\\Terminal Server\\WinStations",
                  "DWMFRAMEINTERVAL", 8);

    // 2b. MMCSS Gaming Priority & Disable Network Throttling + TCP Nagle (TCPNoDelay)
    const char* mm_sys = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile";
    const char* mm_games = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games";
    WriteRegDword(HKEY_LOCAL_MACHINE, mm_sys, "NetworkThrottlingIndex", 0xFFFFFFFFu);
    WriteRegDword(HKEY_LOCAL_MACHINE, mm_sys, "SystemResponsiveness", 10);
    WriteRegDword(HKEY_LOCAL_MACHINE, mm_games, "GPU Priority", 8);
    WriteRegDword(HKEY_LOCAL_MACHINE, mm_games, "Priority", 6);
    WriteRegDword(HKEY_LOCAL_MACHINE, mm_games, "Clock Rate", 10000);

    HKEY hIfaces = nullptr;
    if (RegOpenKeyExA(HKEY_LOCAL_MACHINE,
                      "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces",
                      0, KEY_READ, &hIfaces) == ERROR_SUCCESS) {
        char subName[256] = {};
        DWORD idx = 0;
        DWORD nameLen = sizeof(subName);
        while (RegEnumKeyExA(hIfaces, idx++, subName, &nameLen,
                             nullptr, nullptr, nullptr, nullptr) == ERROR_SUCCESS) {
            char fullPath[512] = {};
            snprintf(fullPath, sizeof(fullPath),
                     "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces\\%s",
                     subName);
            WriteRegDword(HKEY_LOCAL_MACHINE, fullPath, "TcpAckFrequency", 1);
            WriteRegDword(HKEY_LOCAL_MACHINE, fullPath, "TCPNoDelay", 1);
            nameLen = sizeof(subName);
        }
        RegCloseKey(hIfaces);
    }

    // 3. Enable ClearType Smooth Fonts on the active Windows session
    SystemParametersInfoA(SPI_SETFONTSMOOTHING, TRUE, nullptr, SPIF_UPDATEINIFILE | SPIF_SENDCHANGE);
    SystemParametersInfoA(SPI_SETFONTSMOOTHINGTYPE, 0, reinterpret_cast<PVOID>(static_cast<uintptr_t>(2)),
                          SPIF_UPDATEINIFILE | SPIF_SENDCHANGE);
    // WinStations\RDP-Tcp: beberapa build Windows masih membaca nama lama ini.
    WriteRegDword(HKEY_LOCAL_MACHINE,
                  "SYSTEM\\CurrentControlSet\\Control\\Terminal Server\\WinStations\\RDP-Tcp",
                  "AllowFontAntiAlias", 1);

    // 4. Ensure AudioEndpointBuilder & Audiosrv are running + Open Firewall (No Console Flash)
    RunHiddenCmd("sc.exe config AudioEndpointBuilder start= auto");
    RunHiddenCmd("sc.exe start AudioEndpointBuilder");
    RunHiddenCmd("sc.exe config Audiosrv start= auto");
    RunHiddenCmd("sc.exe start Audiosrv");
    RunHiddenCmd("netsh advfirewall firewall add rule name=\"XyDesk Remote RDP TCP\" dir=in action=allow protocol=TCP localport=3389");
    RunHiddenCmd("netsh advfirewall firewall add rule name=\"XyDesk Remote RDP UDP\" dir=in action=allow protocol=UDP localport=3389");
    RunHiddenCmd("netsh advfirewall firewall add rule name=\"XyDesk Remote QUIC UDP\" dir=in action=allow protocol=UDP localport=4433");
    return 0;
#else
    return -1;
#endif
}
