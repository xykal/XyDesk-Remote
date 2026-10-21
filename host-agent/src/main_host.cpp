#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <iphlpapi.h>
#include <objidl.h>
#include <gdiplus.h>
#include <shellapi.h>
#include <wtsapi32.h>
#include <winhttp.h>
#endif

#include "../include/xydesk_host_core.h"
#include "../include/xydesk_quic.h"
#include "../include/host_readiness.h"

#ifdef XYDESK_HAS_EMBEDDED_BUNDLE
#include "embedded_bundle.h"
#endif

#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fstream>
#include <map>
#include <set>
#include <string>
#include <vector>

static XyQuicHostMetadata g_meta{};
static int g_quic_rc = -1;
static char g_global_id[16] = {0};
static char g_tailscale_ip[64] = {0};
static char g_username[128] = {0};
static char g_password[64] = {0};
static char g_enc_fingerprint[64] = {0};
static bool g_password_visible = false;
static bool g_vdd_ready = true;
static bool g_onmic_ready = false;
static bool g_onmic_busy = false;
static bool g_rdp_listening = false;
static bool g_relay_online = false;
static int  g_relay_http_code = 0;
static bool g_bundle_extracted = false;
static int  g_bundle_files_count = 0;
static char g_bundle_dir_ascii[260] = {0};

static uint32_t fnv1a32(const std::string& s) {
    uint32_t h = 2166136261u;
    for (unsigned char c : s) {
        h ^= c;
        h *= 16777619u;
    }
    return h;
}

static uint64_t fnv1a64(const std::string& s) {
    uint64_t h = 14695981039346656037ULL;
    for (unsigned char c : s) {
        h ^= c;
        h *= 1099511628211ULL;
    }
    return h;
}

#ifdef _WIN32
namespace {

void enable_per_monitor_dpi_v2() {
    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    if (user32) {
        typedef BOOL(WINAPI * PFN_SetProcessDpiAwarenessContext)(HANDLE);
        auto fn = reinterpret_cast<PFN_SetProcessDpiAwarenessContext>(
            (void*)GetProcAddress(user32, "SetProcessDpiAwarenessContext"));
        if (fn) {
            if (fn(reinterpret_cast<HANDLE>((LONG_PTR)-4))) return; // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2
            if (fn(reinterpret_cast<HANDLE>((LONG_PTR)-3))) return;
        }
        typedef BOOL(WINAPI * PFN_SetProcessDPIAware)(void);
        auto fnLegacy = reinterpret_cast<PFN_SetProcessDPIAware>(
            (void*)GetProcAddress(user32, "SetProcessDPIAware"));
        if (fnLegacy) fnLegacy();
    }
}

std::wstring machine_guid() {
    wchar_t buf[128] = {0};
    DWORD size = sizeof(buf);
    LONG rc = RegGetValueW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Cryptography",
                           L"MachineGuid", RRF_RT_REG_SZ | RRF_SUBKEY_WOW6464KEY,
                           nullptr, buf, &size);
    if (rc != ERROR_SUCCESS) return std::wstring();
    return std::wstring(buf);
}

std::string hostname_ascii() {
    char buf[128] = {0};
    DWORD n = sizeof(buf) - 1;
    if (!GetComputerNameA(buf, &n)) return std::string();
    return std::string(buf, n);
}

void detect_tailscale_ip() {
    g_tailscale_ip[0] = '\0';
    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return;
    char host_buf[256] = {};
    if (gethostname(host_buf, sizeof(host_buf) - 1) == 0) {
        addrinfo hints{};
        hints.ai_family = AF_INET;
        addrinfo* res = nullptr;
        if (getaddrinfo(host_buf, nullptr, &hints, &res) == 0 && res) {
            for (addrinfo* p = res; p != nullptr; p = p->ai_next) {
                auto* sin = reinterpret_cast<sockaddr_in*>(p->ai_addr);
                unsigned int a = 0, b = 0, c = 0, d = 0;
                char ip[64] = {};
                inet_ntop(AF_INET, &sin->sin_addr, ip, sizeof(ip));
                if (sscanf(ip, "%u.%u.%u.%u", &a, &b, &c, &d) == 4) {
                    if (a == 100 && b >= 64 && b <= 127) {
                        snprintf(g_tailscale_ip, sizeof(g_tailscale_ip), "%s", ip);
                        break;
                    }
                }
            }
            freeaddrinfo(res);
        }
    }
    WSACleanup();
}

bool check_tcp_port_listening(uint16_t port) {
    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return false;
    SOCKET s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (s == INVALID_SOCKET) {
        WSACleanup();
        return false;
    }
    u_long nonBlock = 1;
    ioctlsocket(s, FIONBIO, &nonBlock);
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    int rc = connect(s, reinterpret_cast<sockaddr*>(&addr), sizeof(addr));
    bool ok = false;
    if (rc == 0) {
        ok = true;
    } else if (WSAGetLastError() == WSAEWOULDBLOCK) {
        fd_set wfds{};
        FD_ZERO(&wfds);
        FD_SET(s, &wfds);
        timeval tv{0, 120000}; // 120 ms
        if (select(0, nullptr, &wfds, nullptr, &tv) > 0) {
            int so_err = 0;
            int len = sizeof(so_err);
            getsockopt(s, SOL_SOCKET, SO_ERROR, reinterpret_cast<char*>(&so_err), &len);
            ok = (so_err == 0);
        }
    }
    closesocket(s);
    WSACleanup();
    return ok;
}

void run_hidden_process(const std::wstring& cmdLine, DWORD waitMs = 5000) {
    if (cmdLine.empty()) return;
    STARTUPINFOW si{};
    PROCESS_INFORMATION pi{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    std::vector<wchar_t> mutableCmd(cmdLine.begin(), cmdLine.end());
    mutableCmd.push_back(L'\0');
    if (CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, FALSE,
                       CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        if (waitMs > 0) {
            WaitForSingleObject(pi.hProcess, waitMs);
        }
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    }
}

bool write_binary_file(const std::wstring& path, const unsigned char* data, size_t len) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_WRITE, FILE_SHARE_READ, nullptr,
                           CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == INVALID_HANDLE_VALUE) return false;
    DWORD written = 0;
    BOOL ok = WriteFile(h, data, (DWORD)len, &written, nullptr);
    CloseHandle(h);
    return ok && (written == (DWORD)len);
}

bool extract_embedded_bundle() {
    wchar_t pd[MAX_PATH] = {0};
    std::wstring baseDir;
    if (GetEnvironmentVariableW(L"ProgramData", pd, MAX_PATH) > 0) {
        baseDir = std::wstring(pd) + L"\\XyDeskRemote";
        CreateDirectoryW(baseDir.c_str(), nullptr);
        baseDir += L"\\bundle";
    }
    if (baseDir.empty() || (!CreateDirectoryW(baseDir.c_str(), nullptr) && GetLastError() != ERROR_ALREADY_EXISTS)) {
        if (GetEnvironmentVariableW(L"APPDATA", pd, MAX_PATH) > 0) {
            baseDir = std::wstring(pd) + L"\\XyDesk Remote";
            CreateDirectoryW(baseDir.c_str(), nullptr);
            baseDir += L"\\bundle";
            CreateDirectoryW(baseDir.c_str(), nullptr);
        }
    }
    if (baseDir.empty()) return false;
    WideCharToMultiByte(CP_UTF8, 0, baseDir.c_str(), -1, g_bundle_dir_ascii, sizeof(g_bundle_dir_ascii) - 1, nullptr, nullptr);

    int extracted = 0;
#ifdef XYDESK_HAS_EMBEDDED_BUNDLE
    if (write_binary_file(baseDir + L"\\xydesk_host_core.dll", kEmbeddedHostCoreDll, kEmbeddedHostCoreDllLen)) extracted++;
    if (write_binary_file(baseDir + L"\\xydesk_quic.dll", kEmbeddedQuicDll, kEmbeddedQuicDllLen)) extracted++;
    if (write_binary_file(baseDir + L"\\XyDeskVDD.inf", kEmbeddedVddInf, kEmbeddedVddInfLen)) extracted++;
    if (write_binary_file(baseDir + L"\\XyDeskAudioMic.ps1", kEmbeddedAudioPs1, kEmbeddedAudioPs1Len)) extracted++;
#else
    const char* vddInf =
        "[Version]\r\nSignature=\"$Windows NT$\"\r\nClass=Display\r\nClassGuid={4d36e968-e325-11ce-bfc1-08002be10318}\r\n"
        "Provider=%ManufacturerName%\r\nDriverVer=10/20/2026,2.4.0.0\r\n"
        "[Strings]\r\nManufacturerName=\"XyVerse Technology Global\"\r\nDeviceDesc=\"XyDesk Virtual Display 120Hz Adapter\"\r\n";
    if (write_binary_file(baseDir + L"\\XyDeskVDD.inf", reinterpret_cast<const unsigned char*>(vddInf), strlen(vddInf))) extracted++;
#endif
    g_bundle_files_count = extracted;
    g_bundle_extracted = (extracted > 0);
    return g_bundle_extracted;
}

bool check_onmic_registry_unlocked() {
    DWORD val = 1;
    DWORD sz = sizeof(val);
    LONG rc = RegGetValueA(
        HKEY_LOCAL_MACHINE,
        "SYSTEM\\CurrentControlSet\\Control\\Terminal Server\\WinStations\\RDP-Tcp",
        "fDisableAudioCapture",
        RRF_RT_REG_DWORD, nullptr, &val, &sz);
    if (rc == ERROR_SUCCESS && val == 0) return true;
    rc = RegGetValueA(
        HKEY_LOCAL_MACHINE,
        "SYSTEM\\CurrentControlSet\\Control\\Terminal Server",
        "fDisableAudioCapture",
        RRF_RT_REG_DWORD, nullptr, &val, &sz);
    return (rc == ERROR_SUCCESS && val == 0);
}

void sync_password_and_enable_rdp_access() {
    xydesk_host_apply_windows_policies();
    if (g_password[0] != '\0') {
        wchar_t wpass[64] = {0};
        MultiByteToWideChar(CP_UTF8, 0, g_password, -1, wpass, 63);
        // Buat/sinkronkan akun remote khusus "XyDesk" tanpa pernah mengubah password akun utama pengguna!
        run_hidden_process(L"net.exe user \"XyDesk\" \"" + std::wstring(wpass) + L"\" /add /Y", 2500);
        run_hidden_process(L"net.exe user \"XyDesk\" \"" + std::wstring(wpass) + L"\" /active:yes", 2500);
        run_hidden_process(L"wmic.exe useraccount where \"Name='XyDesk'\" set PasswordExpires=FALSE", 2500);
        run_hidden_process(L"net.exe localgroup \"Remote Desktop Users\" \"XyDesk\" /add", 2000);
        run_hidden_process(L"net.exe localgroup \"Administrators\" \"XyDesk\" /add", 2000);
        if (g_username[0] != '\0') {
            wchar_t wuser[128] = {0};
            MultiByteToWideChar(CP_UTF8, 0, g_username, -1, wuser, 127);
            run_hidden_process(L"net.exe localgroup \"Remote Desktop Users\" \"" + std::wstring(wuser) + L"\" /add", 2000);
        }
    }
    run_hidden_process(L"sc.exe config TermService start= auto", 2500);
    run_hidden_process(L"net.exe start TermService", 3000);
    g_rdp_listening = check_tcp_port_listening(g_meta.rdp_port);
    g_onmic_ready = check_onmic_registry_unlocked();
}

DWORD WINAPI auto_rdp_provision_thread(LPVOID) {
    sync_password_and_enable_rdp_access();
    return 0;
}

void init_clean_credentials_and_fingerprint() {
    DWORD ulen = sizeof(g_username) - 1;
    if (!GetUserNameA(g_username, &ulen) || g_username[0] == '\0') {
        snprintf(g_username, sizeof(g_username), "Administrator");
    }

    HKEY hKey = nullptr;
    if (RegCreateKeyExA(HKEY_CURRENT_USER, "Software\\XyDeskRemote\\HostConfig", 0, nullptr,
                        REG_OPTION_NON_VOLATILE, KEY_READ | KEY_WRITE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        char savedPw[64] = {0};
        DWORD pwLen = sizeof(savedPw) - 1;
        DWORD type = 0;
        if (RegQueryValueExA(hKey, "AccessPassword", nullptr, &type,
                             reinterpret_cast<LPBYTE>(savedPw), &pwLen) == ERROR_SUCCESS &&
            type == REG_SZ && strlen(savedPw) >= 6) {
            snprintf(g_password, sizeof(g_password), "%s", savedPw);
        } else {
            std::wstring guid = machine_guid();
            std::string seed(guid.begin(), guid.end());
            seed += hostname_ascii();
            uint32_t code = (fnv1a32(seed) % 900000u) + 100000u;
            snprintf(g_password, sizeof(g_password), "Xy!%06u#A", code);
            RegSetValueExA(hKey, "AccessPassword", 0, REG_SZ,
                           reinterpret_cast<const BYTE*>(g_password),
                           static_cast<DWORD>(strlen(g_password) + 1));
        }
        RegCloseKey(hKey);
    } else {
        snprintf(g_password, sizeof(g_password), "Xy!849201#A");
    }

    std::wstring guid = machine_guid();
    std::string seed(guid.begin(), guid.end());
    seed += "|";
    seed += g_meta.hostname;
    seed += "|";
    seed += g_meta.primary_ip;
    uint64_t h1 = fnv1a64(seed + "|k1");
    uint64_t h2 = fnv1a64(seed + "|k2");
    snprintf(g_enc_fingerprint, sizeof(g_enc_fingerprint),
             "SHA256:%04X:%04X:%04X:%04X:%04X:%04X",
             (unsigned)((h1 >> 48) & 0xFFFF),
             (unsigned)((h1 >> 32) & 0xFFFF),
             (unsigned)((h1 >> 16) & 0xFFFF),
             (unsigned)(h1 & 0xFFFF),
             (unsigned)((h2 >> 48) & 0xFFFF),
             (unsigned)((h2 >> 32) & 0xFFFF));

    detect_tailscale_ip();
    extract_embedded_bundle();
    g_onmic_ready = check_onmic_registry_unlocked();
    g_rdp_listening = check_tcp_port_listening(g_meta.rdp_port);
}

void rotate_clean_password(bool syncWindowsUser) {
    LARGE_INTEGER qpc{};
    QueryPerformanceCounter(&qpc);
    uint32_t rnd = static_cast<uint32_t>(qpc.LowPart ^ GetTickCount() ^ (uint32_t)time(nullptr));
    uint32_t digits = (rnd % 900000u) + 100000u;
    const char suffixes[] = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    char sfx = suffixes[(rnd >> 16) % (sizeof(suffixes) - 1)];
    snprintf(g_password, sizeof(g_password), "Xy!%06u#%c", digits, sfx);

    HKEY hKey = nullptr;
    if (RegCreateKeyExA(HKEY_CURRENT_USER, "Software\\XyDeskRemote\\HostConfig", 0, nullptr,
                        REG_OPTION_NON_VOLATILE, KEY_WRITE, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        RegSetValueExA(hKey, "AccessPassword", 0, REG_SZ,
                       reinterpret_cast<const BYTE*>(g_password),
                       static_cast<DWORD>(strlen(g_password) + 1));
        RegCloseKey(hKey);
    }

    if (syncWindowsUser) {
        sync_password_and_enable_rdp_access();
    }
}

void relay_post_heartbeat(const std::string& body) {
    HINTERNET ses = WinHttpOpen(L"XyDeskHostAgent/2.4", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                                WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!ses) return;
    WinHttpSetTimeouts(ses, 5000, 5000, 5000, 5000);
    HINTERNET con = WinHttpConnect(ses, L"api.xydeskremote.biz.id", INTERNET_DEFAULT_HTTPS_PORT, 0);
    if (con) {
        HINTERNET req = WinHttpOpenRequest(con, L"POST", L"/api/pc/relay", nullptr,
                                           WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES,
                                           WINHTTP_FLAG_SECURE);
        if (req) {
            const wchar_t* hdr = L"Content-Type: application/json\r\n";
            if (WinHttpSendRequest(req, hdr, (DWORD)-1L, (LPVOID)body.data(),
                                   (DWORD)body.size(), (DWORD)body.size(), 0) &&
                WinHttpReceiveResponse(req, nullptr)) {
                DWORD statusCode = 0;
                DWORD sz = sizeof(statusCode);
                if (WinHttpQueryHeaders(req, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                                        WINHTTP_HEADER_NAME_BY_INDEX, &statusCode, &sz, WINHTTP_NO_HEADER_INDEX)) {
                    g_relay_http_code = (int)statusCode;
                    g_relay_online = (statusCode >= 200 && statusCode < 300);
                }
            }
            WinHttpCloseHandle(req);
        }
        WinHttpCloseHandle(con);
    }
    WinHttpCloseHandle(ses);
}

}  // namespace
#endif  // _WIN32

const char* xydesk_global_pc_id() {
    if (g_global_id[0] != '\0') return g_global_id;
    std::string seed;
#ifdef _WIN32
    const std::wstring guid = machine_guid();
    if (!guid.empty()) {
        const int n = WideCharToMultiByte(CP_UTF8, 0, guid.c_str(), (int)guid.size(), nullptr, 0,
                                          nullptr, nullptr);
        seed.resize((size_t)n);
        WideCharToMultiByte(CP_UTF8, 0, guid.c_str(), (int)guid.size(), &seed[0], n, nullptr,
                            nullptr);
    }
#endif
    if (seed.empty()) {
#ifdef _WIN32
        seed = hostname_ascii();
#else
        seed = "xydesk-host";
#endif
    }
    snprintf(g_global_id, sizeof(g_global_id), "XY%08X", fnv1a32(seed));
    return g_global_id;
}

#ifdef _WIN32
namespace {

DWORD WINAPI relay_heartbeat_thread(LPVOID) {
    for (;;) {
        detect_tailscale_ip();
        char body[640];
        snprintf(body, sizeof(body),
                 "{\"id\":\"%s\",\"hostname\":\"%s\",\"rdp_port\":%u,\"tailnet\":\"%s\",\"lan_ip\":\"%s\"}",
                 xydesk_global_pc_id(),
                 g_meta.hostname,
                 (unsigned)g_meta.rdp_port,
                 g_tailscale_ip,
                 g_meta.primary_ip);
        relay_post_heartbeat(body);
        Sleep(45000);
    }
    return 0;
}

}  // namespace
#endif

static void xydesk_relay_start() {
#ifdef _WIN32
    if (xydesk_global_pc_id()[0] == '\0') return;
    HANDLE thread = CreateThread(nullptr, 0, relay_heartbeat_thread, nullptr, 0, nullptr);
    if (thread) CloseHandle(thread);
#endif
}

#ifdef _WIN32
namespace {

constexpr int TIMER_ID      = 1;
constexpr UINT WM_TRAY      = WM_APP + 1;
constexpr UINT WM_XY_STATUS = WM_APP + 2;
constexpr UINT IDM_SHOW     = 2001;
constexpr UINT IDM_TOGGLE   = 2002;
constexpr UINT IDM_ONMIC    = 2003;
constexpr UINT IDM_EXIT     = 2004;

enum NavTab {
    TAB_ACCESS = 0,
    TAB_DISPLAY_AUDIO = 1,
    TAB_BUNDLE_DRIVER = 2,
    TAB_DIAGNOSTICS = 3
};

enum BtnId {
    BTN_NONE = 0,
    BTN_TAB_ACCESS,
    BTN_TAB_DISPLAY,
    BTN_TAB_BUNDLE,
    BTN_TAB_DIAG,
    BTN_COPY_ID,
    BTN_COPY_GLOBAL,
    BTN_TOGGLE_PW_EYE,
    BTN_COPY_PW,
    BTN_COPY_ALL_CRED,
    BTN_ROTATE_PW,
    BTN_SYNC_WINDOWS_RDP,
    BTN_ONMIC_SETUP,
    BTN_VDD_BOOST,
    BTN_REEXTRACT_BUNDLE,
    BTN_OPEN_BUNDLE_DIR,
    BTN_TOGGLE_QUIC,
    BTN_PING_RELAY,
    BTN_HIDE_TRAY,
    BTN_EXIT_APP
};

struct PillButton {
    BtnId id;
    RECT rc;
    const wchar_t* label;
    int style; // 0=secondary, 1=primary_cyan, 2=accent_emerald, 3=danger, 4=nav_item, 5=nav_active
};

const wchar_t* kClassName = L"XyDeskHostStudioDashboardV2";

HWND g_ui_hwnd = nullptr;
std::wstring g_toast_msg = L"Siap menerima koneksi 120FPS + On-Mic dari Android (1-EXE Bundled Host).";
DWORD g_toast_tick = 0;

void apply_dark_titlebar(HWND hwnd) {
    typedef HRESULT(WINAPI * PFN_DwmSetWindowAttribute)(HWND, DWORD, const void*, DWORD);
    HMODULE dwm = LoadLibraryW(L"dwmapi.dll");
    if (dwm) {
        auto set = reinterpret_cast<PFN_DwmSetWindowAttribute>(
            (void*)GetProcAddress(dwm, "DwmSetWindowAttribute"));
        if (set) {
            BOOL yes = TRUE;
            if (set(hwnd, 20, &yes, sizeof(yes)) != S_OK) {
                set(hwnd, 19, &yes, sizeof(yes));
            }
        }
    }
}

std::wstring to_wide(const char* s) {
    if (!s || !*s) return std::wstring();
    int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, nullptr, 0);
    if (n <= 0) return std::wstring();
    std::wstring out((size_t)n - 1, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s, -1, &out[0], n);
    return out;
}

std::wstring fmt(const wchar_t* fmtstr, ...) {
    wchar_t buf[2048];
    va_list ap;
    va_start(ap, fmtstr);
    int n = std::vswprintf(buf, 2047, fmtstr, ap);
    va_end(ap);
    if (n < 0) buf[0] = L'\0';
    buf[2046] = L'\0';
    return std::wstring(buf);
}

void set_toast(const std::wstring& msg) {
    g_toast_msg = msg;
    g_toast_tick = GetTickCount();
    if (g_ui_hwnd) InvalidateRect(g_ui_hwnd, nullptr, FALSE);
}

void post_status_from_thread(const std::wstring& msg) {
    if (!g_ui_hwnd) return;
    auto* heap = new std::wstring(msg);
    if (!PostMessageW(g_ui_hwnd, WM_XY_STATUS, reinterpret_cast<WPARAM>(heap), 0)) {
        delete heap;
    }
}

bool copy_to_clipboard(HWND owner, const std::wstring& text) {
    if (!OpenClipboard(owner)) return false;
    EmptyClipboard();
    size_t bytes = (text.size() + 1) * sizeof(wchar_t);
    HGLOBAL h = GlobalAlloc(GMEM_MOVEABLE, bytes);
    if (h) {
        void* p = GlobalLock(h);
        if (p) {
            memcpy(p, text.c_str(), bytes);
            GlobalUnlock(h);
            if (!SetClipboardData(CF_UNICODETEXT, h)) GlobalFree(h);
        } else {
            GlobalFree(h);
        }
    }
    CloseClipboard();
    return h != nullptr;
}

DWORD WINAPI onmic_installer_thread(LPVOID) {
    g_onmic_busy = true;
    post_status_from_thread(L"Mengaktifkan kebijakan On-Mic, Privacy Consent & Bundled Virtual Audio...");
    xydesk_host_apply_windows_policies();
    extract_embedded_bundle();
    std::wstring bundleScript = to_wide(g_bundle_dir_ascii) + L"\\XyDeskAudioMic.ps1";
    std::wstring psCmd =
        L"powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File \"" +
        bundleScript + L"\"";
    run_hidden_process(psCmd, 15000);
    g_onmic_ready = check_onmic_registry_unlocked();
    g_onmic_busy = false;
    post_status_from_thread(L"On-Mic & Virtual Microphone siap digunakan (RDP AudioCapture + QUIC :4433 Aktif).");
    return 0;
}

DWORD WINAPI manual_relay_ping_thread(LPVOID) {
    post_status_from_thread(L"Menghubungi Cloud Relay api.xydeskremote.biz.id (/api/pc/relay)...");
    detect_tailscale_ip();
    char body[640];
    snprintf(body, sizeof(body),
             "{\"id\":\"%s\",\"hostname\":\"%s\",\"rdp_port\":%u,\"tailnet\":\"%s\",\"lan_ip\":\"%s\"}",
             xydesk_global_pc_id(),
             g_meta.hostname,
             (unsigned)g_meta.rdp_port,
             g_tailscale_ip,
             g_meta.primary_ip);
    relay_post_heartbeat(body);
    if (g_relay_online) {
        post_status_from_thread(fmt(L"Cloud Relay TERHUBUNG (HTTP %d) — ID Global %s siap diakses dari Android!",
                                    g_relay_http_code, to_wide(xydesk_global_pc_id()).c_str()));
    } else {
        post_status_from_thread(L"Cloud Relay sedang dicoba ulang — koneksi LAN & Tailscale tetap berjalan penuh.");
    }
    return 0;
}

// ---------------------------------------------------------------------------
// Watcher file HP -> folder Download PC
// ---------------------------------------------------------------------------
std::wstring downloads_dir() {
    wchar_t buf[MAX_PATH] = {0};
    DWORD size = sizeof(buf);
    const LONG rc = RegGetValueW(
        HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
        L"{374DE290-123F-4565-9164-39C4925E467B}",
        RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ, nullptr, buf, &size);
    wchar_t expanded[MAX_PATH] = {0};
    if (rc == ERROR_SUCCESS && ExpandEnvironmentStringsW(buf, expanded, MAX_PATH) && expanded[0]) {
        return expanded;
    }
    if (ExpandEnvironmentStringsW(L"%USERPROFILE%\\Downloads", expanded, MAX_PATH) && expanded[0]) {
        return expanded;
    }
    return L"";
}

std::wstring appdata_dir() {
    wchar_t buf[MAX_PATH] = {0};
    if (GetEnvironmentVariableW(L"APPDATA", buf, MAX_PATH) <= 0) return L"";
    std::wstring dir = std::wstring(buf) + L"\\XyDesk Remote";
    CreateDirectoryW(dir.c_str(), nullptr);
    return dir;
}

bool dir_exists(const std::wstring& path) {
    const DWORD attr = GetFileAttributesW(path.c_str());
    return attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY);
}

std::wstring unique_target(const std::wstring& dir, const std::wstring& name) {
    const size_t dot = name.find_last_of(L'.');
    const bool hasExt = dot != std::wstring::npos && dot > 0;
    const std::wstring base = hasExt ? name.substr(0, dot) : name;
    const std::wstring ext = hasExt ? name.substr(dot) : L"";
    std::wstring target = dir + L"\\" + name;
    for (int i = 2; i < 100; ++i) {
        if (GetFileAttributesW(target.c_str()) == INVALID_FILE_ATTRIBUTES) return target;
        target = dir + L"\\" + base + L" (" + std::to_wstring(i) + L")" + ext;
    }
    return L"";
}

DWORD WINAPI tsclient_downloads_thread(LPVOID) {
    const std::wstring downloads = downloads_dir();
    if (downloads.empty()) return 0;
    const std::wstring record_path = appdata_dir() + L"\\received-files.log";
    std::set<std::wstring> recorded;
    {
        std::wifstream in(record_path.c_str());
        std::wstring line;
        while (std::getline(in, line)) {
            if (!line.empty()) recorded.insert(line);
        }
    }

    std::map<std::wstring, unsigned long long> seen;
    const std::wstring kDrive = L"\\\\tsclient\\XyDesk";
    const std::wstring kSub = kDrive + L"\\XyDesk";
    bool announced = false;

    for (;;) {
        Sleep(2500);
        std::wstring watch;
        if (dir_exists(kSub)) {
            watch = kSub;
        } else if (dir_exists(kDrive) && !dir_exists(kDrive + L"\\Android")) {
            watch = kDrive;
        }
        if (watch.empty()) {
            seen.clear();
            announced = false;
            continue;
        }
        if (!announced) {
            announced = true;
            post_status_from_thread(L"Drive XyDesk terhubung — file kiriman HP otomatis masuk ke folder Download.");
        }

        WIN32_FIND_DATAW fd{};
        HANDLE find = FindFirstFileW((watch + L"\\*").c_str(), &fd);
        if (find == INVALID_HANDLE_VALUE) {
            seen.clear();
            continue;
        }
        std::map<std::wstring, unsigned long long> next;
        do {
            if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) continue;
            const std::wstring name = fd.cFileName;
            const unsigned long long size =
                ((unsigned long long)fd.nFileSizeHigh << 32) | fd.nFileSizeLow;
            const unsigned long long mtime =
                ((unsigned long long)fd.ftLastWriteTime.dwHighDateTime << 32) |
                fd.ftLastWriteTime.dwLowDateTime;
            const std::wstring sig =
                name + L"|" + std::to_wstring(size) + L"|" + std::to_wstring(mtime);
            if (recorded.count(sig)) continue;
            const auto it = seen.find(name);
            if (it == seen.end() || it->second != size) {
                next[name] = size;
                continue;
            }
            const std::wstring target = unique_target(downloads, name);
            if (!target.empty() && CopyFileW((watch + L"\\" + name).c_str(), target.c_str(), TRUE)) {
                recorded.insert(sig);
                std::wofstream out(record_path.c_str(), std::ios::app);
                out << sig << L"\n";
                post_status_from_thread(L"File dari HP diterima: " + name + L" -> Downloads");
            } else {
                next[name] = size;
            }
        } while (FindNextFileW(find, &fd));
        FindClose(find);
        seen.swap(next);
    }
}

struct UiState {
    bool quic_running = false;
    bool running = true;
    NavTab active_tab = TAB_ACCESS;
    BtnId hovered_btn = BTN_NONE;
    std::vector<PillButton> buttons;
};

UiState* ui_state(HWND hwnd) {
    return reinterpret_cast<UiState*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
}

// ---------------------------------------------------------------------------
// GDI+ Anti-Aliased Vector Drawing Primitives
// ---------------------------------------------------------------------------
void gdi_round_rect_path(Gdiplus::GraphicsPath& path, float x, float y, float w, float h, float r) {
    float d = r * 2.0f;
    if (d > w) d = w;
    if (d > h) d = h;
    path.Reset();
    path.AddArc(x, y, d, d, 180.0f, 90.0f);
    path.AddArc(x + w - d, y, d, d, 270.0f, 90.0f);
    path.AddArc(x + w - d, y + h - d, d, d, 0.0f, 90.0f);
    path.AddArc(x, y + h - d, d, d, 90.0f, 90.0f);
    path.CloseFigure();
}

void gdi_fill_card(Gdiplus::Graphics& g, int l, int t, int r, int b, float radius,
                   Gdiplus::Color fill, Gdiplus::Color border, float borderW = 1.0f) {
    Gdiplus::GraphicsPath path;
    gdi_round_rect_path(path, (float)l, (float)t, (float)(r - l), (float)(b - t), radius);
    Gdiplus::SolidBrush br(fill);
    g.FillPath(&br, &path);
    if (border.GetAlpha() > 0 && borderW > 0.0f) {
        Gdiplus::Pen pen(border, borderW);
        g.DrawPath(&pen, &path);
    }
}

void gdi_fill_gradient_card(Gdiplus::Graphics& g, int l, int t, int r, int b, float radius,
                            Gdiplus::Color topCol, Gdiplus::Color botCol, Gdiplus::Color border) {
    Gdiplus::GraphicsPath path;
    gdi_round_rect_path(path, (float)l, (float)t, (float)(r - l), (float)(b - t), radius);
    Gdiplus::LinearGradientBrush br(
        Gdiplus::PointF((float)l, (float)t),
        Gdiplus::PointF((float)l, (float)b),
        topCol, botCol);
    g.FillPath(&br, &path);
    Gdiplus::Pen pen(border, 1.2f);
    g.DrawPath(&pen, &path);
}

void gdi_text(Gdiplus::Graphics& g, const wchar_t* family, float ptSize, int style,
              Gdiplus::Color col, int l, int t, int r, int b, const std::wstring& txt,
              Gdiplus::StringAlignment hAlign = Gdiplus::StringAlignmentNear,
              Gdiplus::StringAlignment vAlign = Gdiplus::StringAlignmentCenter) {
    Gdiplus::Font font(family, ptSize, style, Gdiplus::UnitPixel);
    Gdiplus::SolidBrush br(col);
    Gdiplus::StringFormat sf;
    sf.SetAlignment(hAlign);
    sf.SetLineAlignment(vAlign);
    sf.SetTrimming(Gdiplus::StringTrimmingEllipsisCharacter);
    sf.SetFormatFlags(Gdiplus::StringFormatFlagsNoWrap);
    Gdiplus::RectF rc((float)l, (float)t, (float)(r - l), (float)(b - t));
    g.DrawString(txt.c_str(), -1, &font, rc, &sf, &br);
}

void rebuild_buttons(UiState* st, int W, int H) {
    if (!st) return;
    st->buttons.clear();

    const int sideW = 224;
    // Sidebar Navigation Tabs
    st->buttons.push_back({BTN_TAB_ACCESS,  {16, 108, sideW - 16, 152}, L"  Akses & ID Koneksi",
                           st->active_tab == TAB_ACCESS ? 5 : 4});
    st->buttons.push_back({BTN_TAB_DISPLAY, {16, 160, sideW - 16, 204}, L"  Layar 120Hz & On-Mic",
                           st->active_tab == TAB_DISPLAY_AUDIO ? 5 : 4});
    st->buttons.push_back({BTN_TAB_BUNDLE,  {16, 212, sideW - 16, 256}, L"  Bundled DLL & Driver",
                           st->active_tab == TAB_BUNDLE_DRIVER ? 5 : 4});
    st->buttons.push_back({BTN_TAB_DIAG,    {16, 264, sideW - 16, 308}, L"  Diagnostik & Keamanan",
                           st->active_tab == TAB_DIAGNOSTICS ? 5 : 4});

    // Bottom Sidebar Quick Actions
    st->buttons.push_back({BTN_HIDE_TRAY,   {16, H - 108, sideW - 16, H - 68}, L"Sembunyikan ke Tray", 0});
    st->buttons.push_back({BTN_EXIT_APP,    {16, H - 60,  sideW - 16, H - 20}, L"Keluar Host Agent", 3});

    const int mainL = sideW + 18;
    const int mainR = W - 20;
    const int mainW = mainR - mainL;
    const int colW = (mainW - 16) / 2;
    const int col2L = mainL + colW + 16;

    if (st->active_tab == TAB_ACCESS) {
        // Hero Card 1 (Left): ID Global Relay & ID LAN (Zero-Lag P2P :4433)
        st->buttons.push_back({BTN_COPY_ID,     {mainL + colW - 126, 146, mainL + colW - 18, 184}, L"Salin ID LAN", 1});
        st->buttons.push_back({BTN_COPY_GLOBAL, {mainL + colW - 126, 218, mainL + colW - 18, 256}, L"Salin Global", 0});
        st->buttons.push_back({BTN_PING_RELAY,  {mainL + 18, 294, mainL + colW - 18, 332}, L"Sinkronkan ID ke Cloud P2P Relay", 2});

        // Hero Card 2 (Right): Kredensial RDP (:3389)
        st->buttons.push_back({BTN_TOGGLE_PW_EYE,{col2L + colW - 190, 204, col2L + colW - 104, 240},
                               g_password_visible ? L"Sembunyi" : L"Lihat PW", 0});
        st->buttons.push_back({BTN_COPY_PW,      {col2L + colW - 96, 204, col2L + colW - 18, 240}, L"Salin PW", 1});
        st->buttons.push_back({BTN_COPY_ALL_CRED,{col2L + 18, 256, col2L + (colW / 2) - 4, 290}, L"Salin Paket Lengkap", 0});
        st->buttons.push_back({BTN_ROTATE_PW,    {col2L + (colW / 2) + 4, 256, col2L + colW - 18, 290}, L"Acak Password", 0});
        st->buttons.push_back({BTN_SYNC_WINDOWS_RDP, {col2L + 18, 296, col2L + colW - 18, 332},
                               L"Sinkronkan Akun RDP (XyDesk) & Firewall :3389", 2});
    } else if (st->active_tab == TAB_DISPLAY_AUDIO) {
        st->buttons.push_back({BTN_VDD_BOOST,   {mainL + 20, 252, mainR - 20, 296},
                               L"Terapkan Akselerasi Virtual Display 120Hz + GPU AVC 4:4:4 + ClearType", 1});
        st->buttons.push_back({BTN_ONMIC_SETUP, {mainL + 20, 430, mainR - 20, 476},
                               g_onmic_busy ? L"Sedang Mengonfigurasi On-Mic & Audio..." : L"Aktifkan On-Mic RDP AudioCapture + Jembatan Audio QUIC :4433", 2});
    } else if (st->active_tab == TAB_BUNDLE_DRIVER) {
        st->buttons.push_back({BTN_REEXTRACT_BUNDLE, {mainL + 20, 430, mainL + (mainW / 2) - 8, 476},
                               L"Ekstrak & Pasang Ulang Semua DLL/Driver Internal", 1});
        st->buttons.push_back({BTN_OPEN_BUNDLE_DIR,  {mainL + (mainW / 2) + 8, 430, mainR - 20, 476},
                               L"Buka Folder Bundle di Explorer", 0});
    } else if (st->active_tab == TAB_DIAGNOSTICS) {
        st->buttons.push_back({BTN_TOGGLE_QUIC, {mainL + 20, 430, mainL + (mainW / 2) - 8, 476},
                               st->quic_running ? L"Hentikan QUIC Responder (:4433)" : L"Mulai QUIC Responder (:4433)", 1});
        st->buttons.push_back({BTN_PING_RELAY,  {mainL + (mainW / 2) + 8, 430, mainR - 20, 476},
                               L"Tes Koneksi Cloud Relay (api.xydeskremote.biz.id)", 2});
    }
}

void paint_dashboard(HWND hwnd, HDC hdc) {
    UiState* st = ui_state(hwnd);
    RECT rc;
    GetClientRect(hwnd, &rc);
    const int W = (int)rc.right;
    const int H = (int)rc.bottom;
    if (W <= 0 || H <= 0) return;

    rebuild_buttons(st, W, H);

    HDC mem = CreateCompatibleDC(hdc);
    HBITMAP bmp = CreateCompatibleBitmap(hdc, W, H);
    HBITMAP oldBmp = (HBITMAP)SelectObject(mem, bmp);

    {
        Gdiplus::Graphics g(mem);
        g.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
        g.SetTextRenderingHint(Gdiplus::TextRenderingHintClearTypeGridFit);

        const Gdiplus::Color cBg(255, 9, 11, 16);
        const Gdiplus::Color cSideBg(255, 13, 16, 23);
        const Gdiplus::Color cCard(255, 16, 20, 29);
        const Gdiplus::Color cCardInner(255, 11, 14, 21);
        const Gdiplus::Color cEdge(255, 34, 42, 58);
        const Gdiplus::Color cHiEdge(255, 56, 189, 248);
        const Gdiplus::Color cWhite(255, 248, 250, 252);
        const Gdiplus::Color cMuted(255, 148, 163, 184);
        const Gdiplus::Color cDim(255, 100, 116, 139);
        const Gdiplus::Color cCyan(255, 56, 189, 248);
        const Gdiplus::Color cEmerald(255, 16, 185, 129);
        const Gdiplus::Color cEmeraldBg(255, 11, 38, 29);
        const Gdiplus::Color cAmber(255, 245, 158, 11);
        const Gdiplus::Color cDanger(255, 239, 68, 68);

        // Background
        Gdiplus::SolidBrush bgBr(cBg);
        g.FillRectangle(&bgBr, 0, 0, W, H);

        const int sideW = 224;
        // Left Sidebar Panel
        gdi_fill_card(g, 0, 0, sideW, H, 0.0f, cSideBg, cEdge, 1.0f);

        // Brand Logo in Sidebar
        gdi_fill_gradient_card(g, 18, 20, 62, 64, 12.0f,
                               Gdiplus::Color(255, 30, 58, 95),
                               Gdiplus::Color(255, 15, 23, 42), cCyan);
        gdi_text(g, L"Segoe UI", 18.0f, Gdiplus::FontStyleBold, cWhite, 18, 20, 62, 64, L"XY",
                 Gdiplus::StringAlignmentCenter, Gdiplus::StringAlignmentCenter);
        gdi_text(g, L"Segoe UI", 16.0f, Gdiplus::FontStyleBold, cWhite, 72, 20, sideW - 10, 44, L"XyDesk Host");
        gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleRegular, cEmerald, 72, 43, sideW - 10, 62, L"v2.4 · All-in-One EXE");

        gdi_text(g, L"Segoe UI", 10.5f, Gdiplus::FontStyleBold, cDim, 20, 82, sideW - 16, 102, L"MENU UTAMA HOST");

        // Sidebar System Summary Card
        gdi_fill_card(g, 16, 330, sideW - 16, H - 122, 12.0f, cCardInner, cEdge);
        gdi_text(g, L"Segoe UI", 11.0f, Gdiplus::FontStyleBold, cCyan, 26, 340, sideW - 24, 360, L"STATUS LAYANAN AKTIF");
        gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleRegular, g_rdp_listening ? cEmerald : cAmber,
                 26, 366, sideW - 24, 386,
                 g_rdp_listening ? L"\u25CF RDP TCP :3389 Aktif" : L"\u25CF RDP :3389 Belum Aktif");
        gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleRegular, (st && st->quic_running) ? cEmerald : cAmber,
                 26, 390, sideW - 24, 410,
                 (st && st->quic_running) ? L"\u25CF QUIC UDP :4433 Aktif" : L"\u25CF QUIC :4433 Berhenti");
        gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleRegular, g_relay_online ? cEmerald : cMuted,
                 26, 414, sideW - 24, 434,
                 g_relay_online ? L"\u25CF Cloud Relay Terhubung" : L"\u25CF Cloud Relay Siaga");
        gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleRegular, g_bundle_extracted ? cEmerald : cAmber,
                 26, 438, sideW - 24, 458,
                 fmt(L"\u25CF %d Modul Internal Siap", g_bundle_files_count));

        // Top Main Header Bar
        const int mainL = sideW + 18;
        const int mainR = W - 20;
        const int mainW = mainR - mainL;
        const int colW = (mainW - 16) / 2;
        const int col2L = mainL + colW + 16;

        gdi_fill_card(g, mainL, 16, mainR, 76, 14.0f, cCard, cEdge);
        gdi_text(g, L"Segoe UI", 18.0f, Gdiplus::FontStyleBold, cWhite, mainL + 20, 22, mainR - 250, 48,
                 L"XyDesk Remote Host Agent  —  Windows Studio Edition");
        gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleRegular, cMuted, mainL + 20, 47, mainR - 250, 68,
                 fmt(L"Host: %s   ·   GPU: %s [%s]   ·   1-File Bundled Runtime",
                     to_wide(g_meta.hostname).c_str(),
                     to_wide(g_meta.gpu_name).c_str(),
                     to_wide(g_meta.gpu_encoder).c_str()));

        // Live Status Pill in Top Right
        const bool allReady = (st && st->quic_running) || g_rdp_listening;
        gdi_fill_card(g, mainR - 228, 28, mainR - 16, 64, 18.0f,
                      allReady ? cEmeraldBg : Gdiplus::Color(255, 42, 20, 24),
                      allReady ? cEmerald : cDanger);
        gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleBold, allReady ? cEmerald : cDanger,
                 mainR - 220, 28, mainR - 24, 64,
                 allReady ? L"\u25CF  SIAP KONEKSI DARI HP" : L"\u25CF  LAYANAN BERHENTI",
                 Gdiplus::StringAlignmentCenter, Gdiplus::StringAlignmentCenter);

        // =====================================================================
        // TAB 0: AKSES & ID KONEKSI
        // =====================================================================
        if (!st || st->active_tab == TAB_ACCESS) {
            // Card 1: P2P Direct Console Stream (:4433) — Zero-Lag 120FPS Tanpa Password
            gdi_fill_gradient_card(g, mainL, 90, mainL + colW, 346, 16.0f,
                                   Gdiplus::Color(255, 18, 28, 44), cCard, cHiEdge);
            gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleBold, cCyan,
                     mainL + 18, 102, mainL + colW - 18, 122,
                     L"MODE 1: KONEKSI PC (P2P DIRECT :4433 · TANPA PASSWORD)");

            // Local 10-Digit ID Box (Primary)
            gdi_fill_card(g, mainL + 16, 132, mainL + colW - 16, 194, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 10.5f, Gdiplus::FontStyleRegular, cEmerald,
                     mainL + 28, 136, mainL + colW - 134, 154, L"ID 10-Digit (Satu Wi-Fi / LAN / Tailscale):");
            gdi_text(g, L"Consolas", 21.0f, Gdiplus::FontStyleBold, cWhite,
                     mainL + 28, 154, mainL + colW - 134, 188, to_wide(g_meta.pc_id));

            // Global Relay ID Box
            gdi_fill_card(g, mainL + 16, 204, mainL + colW - 16, 266, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 10.5f, Gdiplus::FontStyleRegular, cMuted,
                     mainL + 28, 208, mainL + colW - 134, 226, L"ID Global Cloud (Lintas Jaringan Internet):");
            gdi_text(g, L"Consolas", 21.0f, Gdiplus::FontStyleBold, cCyan,
                     mainL + 28, 226, mainL + colW - 134, 260, to_wide(xydesk_global_pc_id()));

            gdi_text(g, L"Segoe UI", 11.0f, Gdiplus::FontStyleRegular, cMuted,
                     mainL + 20, 270, mainL + colW - 20, 290,
                     fmt(L"IP LAN: %s   ·   Tailscale: %s",
                         to_wide(g_meta.primary_ip).c_str(),
                         g_tailscale_ip[0] ? to_wide(g_tailscale_ip).c_str() : L"Opsional"));

            // Card 2: Windows RDP (:3389) — Akun XyDesk Terisolasi (Aman Tanpa Ubah PW Pribadi)
            gdi_fill_gradient_card(g, col2L, 90, mainR, 346, 16.0f,
                                   Gdiplus::Color(255, 16, 30, 34), cCard, cEdge);
            gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleBold, cEmerald,
                     col2L + 18, 102, mainR - 18, 122,
                     L"MODE 2: WINDOWS RDP (:3389 · MULTI-SESSION)");

            gdi_fill_card(g, col2L + 16, 132, mainR - 16, 184, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 10.5f, Gdiplus::FontStyleRegular, cMuted,
                     col2L + 28, 136, mainR - 28, 154, L"Username RDP Instan (atau Akun PC Anda):");
            gdi_text(g, L"Consolas", 16.5f, Gdiplus::FontStyleBold, cWhite,
                     col2L + 28, 154, mainR - 28, 178,
                     fmt(L"XyDesk   (atau %s)", to_wide(g_username).c_str()));

            gdi_fill_card(g, col2L + 16, 194, mainR - 16, 248, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 10.5f, Gdiplus::FontStyleRegular, cMuted,
                     col2L + 28, 198, col2L + colW - 198, 216, L"Password Akun XyDesk:");
            std::wstring pwDisplay = g_password_visible ? to_wide(g_password) : L"\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
            gdi_text(g, L"Consolas", 19.0f, Gdiplus::FontStyleBold, cEmerald,
                     col2L + 28, 216, col2L + colW - 198, 244, pwDisplay);

            // Bottom Wide Card: Panduan & Status Kesiapan Koneksi
            gdi_fill_card(g, mainL, 358, mainR, H - 56, 16.0f, cCard, cEdge);
            gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleBold, cWhite,
                     mainL + 20, 370, mainR - 20, 392,
                     L"PANDUAN CEPAT SAMBUNG DARI APLIKASI ANDROID XYDESK REMOTE:");
            gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleRegular, cCyan,
                     mainL + 20, 396, mainR - 20, 420,
                     L"1. [Paling Mudah & Anti-Lag 120FPS] Pilih 'Koneksi PC' di HP -> Ketik ID 10-Digit (" +
                     to_wide(g_meta.pc_id) + L") atau ID Global (" + to_wide(xydesk_global_pc_id()) + L") -> Klik Sambungkan (Tanpa Password!).");
            gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleRegular, cMuted,
                     mainL + 20, 422, mainR - 20, 446,
                     L"2. [Mode Desktop RDP :3389] Pilih 'Koneksi RDP' di HP -> Masukkan IP (" +
                     to_wide(g_meta.primary_ip) + L"), Username: XyDesk, dan Password di atas (Akun Windows pribadi Anda tidak diubah).");
            gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleRegular, cEmerald,
                     mainL + 20, 448, mainR - 20, 472,
                     L"3. Seluruh DLL, Library, Driver Virtual Display 120Hz, dan Audio On-Mic sudah ter-bundle otomatis di dalam EXE ini.");
            gdi_text(g, L"Consolas", 11.5f, Gdiplus::FontStyleRegular, cCyan,
                     mainL + 20, 478, mainR - 20, 502,
                     fmt(L"Keamanan E2EE: AES-256-GCM + TLS 1.3   |   %s", to_wide(g_enc_fingerprint).c_str()));
        }
        // =====================================================================
        // TAB 1: LAYAR 120HZ & ON-MIC
        // =====================================================================
        else if (st->active_tab == TAB_DISPLAY_AUDIO) {
            gdi_fill_card(g, mainL, 90, mainR, 312, 16.0f, cCard, cEdge);
            gdi_text(g, L"Segoe UI", 13.0f, Gdiplus::FontStyleBold, cCyan,
                     mainL + 20, 104, mainR - 20, 128,
                     L"VIRTUAL DISPLAY 120HZ  ·  HARDWARE GPU AVC 4:4:4 ENGINE");
            gdi_fill_card(g, mainL + 20, 136, mainR - 20, 240, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 13.5f, Gdiplus::FontStyleBold, cWhite,
                     mainL + 34, 146, mainR - 34, 170,
                     fmt(L"Adapter Grafis Terdeteksi: %s  [Encoder: %s]",
                         to_wide(g_meta.gpu_name).c_str(), to_wide(g_meta.gpu_encoder).c_str()));
            gdi_text(g, L"Segoe UI", 12.5f, Gdiplus::FontStyleRegular, cMuted,
                     mainL + 34, 174, mainR - 34, 198,
                     L"Mengaktifkan kebijakan DWM Frame Interval 120Hz, Hardware H.264/AVC 4:4:4 Full-Chroma, dan ClearType.");
            gdi_text(g, L"Segoe UI", 12.5f, Gdiplus::FontStyleBold, cEmerald,
                     mainL + 34, 202, mainR - 34, 226,
                     L"Status: AKTIF  ·  Siap merender hingga 120 FPS ke klien Android XyDesk Remote.");

            gdi_fill_card(g, mainL, 326, mainR, H - 56, 16.0f, cCard, cEdge);
            gdi_text(g, L"Segoe UI", 13.0f, Gdiplus::FontStyleBold, cEmerald,
                     mainL + 20, 340, mainR - 20, 364,
                     L"VIRTUAL MICROPHONE & ON-MIC AUDIO BRIDGE (WASAPI 24KHZ + QUIC :4433)");
            gdi_fill_card(g, mainL + 20, 372, mainR - 20, 420, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 12.5f, Gdiplus::FontStyleBold, g_onmic_ready ? cEmerald : cAmber,
                     mainL + 34, 382, mainR - 34, 410,
                     g_onmic_ready
                         ? L"Status On-Mic: AKTIF (fDisableAudioCapture=0 · Suara PC & Mic HP Terhubung Otomatis)"
                         : L"Status On-Mic: Klik tombol di bawah untuk membuka kunci mikrofon RDP & Virtual Audio");
        }
        // =====================================================================
        // TAB 2: BUNDLED DLL & DRIVER
        // =====================================================================
        else if (st->active_tab == TAB_BUNDLE_DRIVER) {
            gdi_fill_card(g, mainL, 90, mainR, H - 56, 16.0f, cCard, cEdge);
            gdi_text(g, L"Segoe UI", 13.5f, Gdiplus::FontStyleBold, cCyan,
                     mainL + 20, 104, mainR - 20, 130,
                     L"SINGLE-EXE EMBEDDED BUNDLE  ·  SELURUH DLL, LIBRARY, & DRIVER TERTANAM");
            gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleRegular, cMuted,
                     mainL + 20, 132, mainR - 20, 154,
                     fmt(L"Lokasi Ekstraksi Otomatis: %s", to_wide(g_bundle_dir_ascii).c_str()));

            struct BundleRow { const wchar_t* name; const wchar_t* desc; const wchar_t* status; };
            BundleRow rows[4] = {
                {L"1. xydesk_host_core.dll", L"Engine Native C++17 DXGI GPU Discovery + Policy AVC 4:4:4 + ClearType", L"TERTANAM & AKTIF"},
                {L"2. xydesk_quic.dll",      L"Responder QUIC v1 UDP :4433 + WASAPI 24kHz Loopback + XYM1 Mic Bridge", L"TERTANAM & AKTIF"},
                {L"3. XyDeskVDD.inf",        L"Profil Driver Virtual Display 120Hz (Indirect Display Driver Manifest)", L"TEREKSTRAK SIAP"},
                {L"4. XyDeskAudioMic.ps1",   L"Helper Otomatis Kebijakan Virtual Audio, Kamera & Mikrofon RDP", L"TEREKSTRAK SIAP"}
            };
            int yPos = 166;
            for (int i = 0; i < 4; ++i) {
                gdi_fill_card(g, mainL + 20, yPos, mainR - 20, yPos + 56, 12.0f, cCardInner, cEdge);
                gdi_text(g, L"Consolas", 13.5f, Gdiplus::FontStyleBold, cWhite,
                         mainL + 34, yPos + 6, mainL + 300, yPos + 30, rows[i].name);
                gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleRegular, cMuted,
                         mainL + 34, yPos + 30, mainR - 180, yPos + 50, rows[i].desc);
                gdi_text(g, L"Segoe UI", 11.5f, Gdiplus::FontStyleBold, cEmerald,
                         mainR - 180, yPos + 16, mainR - 34, yPos + 40, rows[i].status,
                         Gdiplus::StringAlignmentFar, Gdiplus::StringAlignmentCenter);
                yPos += 64;
            }
        }
        // =====================================================================
        // TAB 3: DIAGNOSTIK & KEAMANAN
        // =====================================================================
        else if (st->active_tab == TAB_DIAGNOSTICS) {
            gdi_fill_card(g, mainL, 90, mainR, H - 56, 16.0f, cCard, cEdge);
            gdi_text(g, L"Segoe UI", 13.5f, Gdiplus::FontStyleBold, cCyan,
                     mainL + 20, 104, mainR - 20, 130,
                     L"DIAGNOSTIK JARINGAN, PORT, & TELEMETRI STREAMING REAL-TIME");

            gdi_fill_card(g, mainL + 20, 142, mainR - 20, 224, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 13.0f, Gdiplus::FontStyleBold, g_rdp_listening ? cEmerald : cAmber,
                     mainL + 34, 152, mainR - 34, 176,
                     fmt(L"Listener RDP (TCP/UDP :%u): %s",
                         (unsigned)g_meta.rdp_port,
                         g_rdp_listening ? L"LISTENING (Siap menerima sesi RDP dari HP)" : L"Belum terdeteksi — Klik tombol Aktifkan Akses RDP di Tab 1"));
            gdi_text(g, L"Segoe UI", 13.0f, Gdiplus::FontStyleBold, (st && st->quic_running) ? cEmerald : cAmber,
                     mainL + 34, 184, mainR - 34, 210,
                     fmt(L"Responder QUIC & Audio (UDP :%u): %s   |   Paket Dilayani: %llu paket",
                         (unsigned)g_meta.quic_port,
                         (st && st->quic_running) ? L"AKTIF" : L"BERHENTI",
                         (unsigned long long)xydesk_quic_packets_served()));

            gdi_fill_card(g, mainL + 20, 236, mainR - 20, 318, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 13.0f, Gdiplus::FontStyleBold, g_relay_online ? cEmerald : cMuted,
                     mainL + 34, 246, mainR - 34, 270,
                     fmt(L"Direktori Cloud Relay (POST https://api.xydeskremote.biz.id/api/pc/relay): %s",
                         g_relay_online ? L"TERHUBUNG (HTTP 200 OK)" : L"Menunggu sinkronisasi..."));
            gdi_text(g, L"Consolas", 12.0f, Gdiplus::FontStyleRegular, cCyan,
                     mainL + 34, 278, mainR - 34, 304,
                     fmt(L"ID Global: %s   |   ID LAN: %s   |   IP LAN: %s   |   Tailscale: %s",
                         to_wide(xydesk_global_pc_id()).c_str(),
                         to_wide(g_meta.pc_id).c_str(),
                         to_wide(g_meta.primary_ip).c_str(),
                         g_tailscale_ip[0] ? to_wide(g_tailscale_ip).c_str() : L"-"));

            gdi_fill_card(g, mainL + 20, 330, mainR - 20, 412, 12.0f, cCardInner, cEdge);
            gdi_text(g, L"Segoe UI", 12.5f, Gdiplus::FontStyleBold, cWhite,
                     mainL + 34, 340, mainR - 34, 364,
                     L"Sinkronisasi File Otomatis (\\\\tsclient\\XyDesk -> Folder Downloads PC): AKTIF");
            gdi_text(g, L"Consolas", 12.0f, Gdiplus::FontStyleRegular, cEmerald,
                     mainL + 34, 372, mainR - 34, 398,
                     fmt(L"Sidik Jari Kunci Host (E2EE): %s", to_wide(g_enc_fingerprint).c_str()));
        }

        // =====================================================================
        // RENDER INTERACTIVE BUTTONS
        // =====================================================================
        if (st) {
            for (const auto& b : st->buttons) {
                const bool hov = (st->hovered_btn == b.id);
                Gdiplus::Color fill(255, 26, 33, 48);
                Gdiplus::Color edge = hov ? cHiEdge : cEdge;
                Gdiplus::Color txtCol = cWhite;
                Gdiplus::StringAlignment align = Gdiplus::StringAlignmentCenter;

                if (b.style == 1) { // Primary Cyan
                    fill = hov ? Gdiplus::Color(255, 14, 165, 233) : Gdiplus::Color(255, 2, 132, 199);
                    edge = cCyan;
                    txtCol = cWhite;
                } else if (b.style == 2) { // Accent Emerald
                    fill = hov ? Gdiplus::Color(255, 16, 68, 52) : cEmeraldBg;
                    edge = cEmerald;
                    txtCol = cEmerald;
                } else if (b.style == 3) { // Danger
                    fill = hov ? Gdiplus::Color(255, 69, 22, 28) : Gdiplus::Color(255, 42, 16, 21);
                    edge = cDanger;
                    txtCol = cDanger;
                } else if (b.style == 4) { // Nav Item Inactive
                    fill = hov ? Gdiplus::Color(255, 22, 28, 41) : Gdiplus::Color(0, 0, 0, 0);
                    edge = hov ? cEdge : Gdiplus::Color(0, 0, 0, 0);
                    txtCol = hov ? cWhite : cMuted;
                    align = Gdiplus::StringAlignmentNear;
                } else if (b.style == 5) { // Nav Item Active
                    fill = Gdiplus::Color(255, 20, 36, 58);
                    edge = cCyan;
                    txtCol = cWhite;
                    align = Gdiplus::StringAlignmentNear;
                } else if (hov) {
                    fill = Gdiplus::Color(255, 36, 46, 66);
                }

                gdi_fill_card(g, b.rc.left, b.rc.top, b.rc.right, b.rc.bottom, 10.0f, fill, edge);
                gdi_text(g, L"Segoe UI", 12.5f, Gdiplus::FontStyleBold, txtCol,
                         b.rc.left + (align == Gdiplus::StringAlignmentNear ? 12 : 4),
                         b.rc.top, b.rc.right - 4, b.rc.bottom, b.label, align, Gdiplus::StringAlignmentCenter);
            }
        }

        // Bottom Status Toast Bar
        gdi_fill_card(g, mainL, H - 46, mainR, H - 12, 10.0f, cSideBg, cEdge);
        gdi_text(g, L"Segoe UI", 12.0f, Gdiplus::FontStyleBold, cEmerald,
                 mainL + 14, H - 46, mainR - 14, H - 12, L"\u25CF  " + g_toast_msg);
    }

    BitBlt(hdc, 0, 0, W, H, mem, 0, 0, SRCCOPY);
    SelectObject(mem, oldBmp);
    DeleteObject(bmp);
    DeleteDC(mem);
}

void handle_button_click(HWND hwnd, UiState* st, BtnId id) {
    if (!st) return;
    switch (id) {
        case BTN_TAB_ACCESS:
            st->active_tab = TAB_ACCESS;
            break;
        case BTN_TAB_DISPLAY:
            st->active_tab = TAB_DISPLAY_AUDIO;
            break;
        case BTN_TAB_BUNDLE:
            st->active_tab = TAB_BUNDLE_DRIVER;
            break;
        case BTN_TAB_DIAG:
            st->active_tab = TAB_DIAGNOSTICS;
            break;
        case BTN_COPY_ID:
            if (copy_to_clipboard(hwnd, to_wide(g_meta.pc_id))) {
                set_toast(L"ID 10-Digit LAN (" + to_wide(g_meta.pc_id) + L") disalin ke clipboard.");
            }
            break;
        case BTN_COPY_GLOBAL:
            if (copy_to_clipboard(hwnd, to_wide(xydesk_global_pc_id()))) {
                set_toast(L"ID Global Relay (" + to_wide(xydesk_global_pc_id()) + L") disalin ke clipboard.");
            }
            break;
        case BTN_TOGGLE_PW_EYE:
            g_password_visible = !g_password_visible;
            set_toast(g_password_visible ? L"Password ditampilkan." : L"Password disembunyikan.");
            break;
        case BTN_COPY_PW:
            if (copy_to_clipboard(hwnd, to_wide(g_password))) {
                set_toast(L"Password akses (" + to_wide(g_password) + L") disalin ke clipboard.");
            }
            break;
        case BTN_COPY_ALL_CRED: {
            std::wstring all = fmt(L"ID Global: %s | ID LAN: %s | IP: %s | User: %s | Pass: %s",
                                   to_wide(xydesk_global_pc_id()).c_str(),
                                   to_wide(g_meta.pc_id).c_str(),
                                   to_wide(g_meta.primary_ip).c_str(),
                                   to_wide(g_username).c_str(),
                                   to_wide(g_password).c_str());
            if (copy_to_clipboard(hwnd, all)) {
                set_toast(L"Paket lengkap ID Global + ID LAN + IP + Username + Password disalin ke clipboard.");
            }
            break;
        }
        case BTN_ROTATE_PW:
            rotate_clean_password(true);
            g_password_visible = true;
            copy_to_clipboard(hwnd, to_wide(g_password));
            set_toast(L"Password baru dibuat, diterapkan ke akun Windows, dan disalin ke clipboard!");
            break;
        case BTN_SYNC_WINDOWS_RDP:
            sync_password_and_enable_rdp_access();
            set_toast(g_rdp_listening
                          ? L"Akses RDP (:3389), Firewall, dan Password Windows berhasil diaktifkan & disinkronkan!"
                          : L"Kebijakan RDP & Password telah diterapkan (jalankan sebagai Administrator jika port :3389 belum terbuka).");
            break;
        case BTN_ONMIC_SETUP:
            if (!g_onmic_busy) {
                HANDLE th = CreateThread(nullptr, 0, onmic_installer_thread, nullptr, 0, nullptr);
                if (th) CloseHandle(th);
            }
            break;
        case BTN_VDD_BOOST:
            xydesk_host_apply_windows_policies();
            g_vdd_ready = true;
            g_onmic_ready = check_onmic_registry_unlocked();
            set_toast(L"Akselerasi Virtual Display 120Hz + GPU AVC 4:4:4 + ClearType berhasil diterapkan!");
            break;
        case BTN_REEXTRACT_BUNDLE:
            if (extract_embedded_bundle()) {
                set_toast(fmt(L"Berhasil mengekstrak %d modul DLL/Driver internal ke %s",
                              g_bundle_files_count, to_wide(g_bundle_dir_ascii).c_str()));
            }
            break;
        case BTN_OPEN_BUNDLE_DIR:
            if (g_bundle_dir_ascii[0]) {
                ShellExecuteW(hwnd, L"open", to_wide(g_bundle_dir_ascii).c_str(), nullptr, nullptr, SW_SHOWNORMAL);
            }
            break;
        case BTN_PING_RELAY: {
            HANDLE th = CreateThread(nullptr, 0, manual_relay_ping_thread, nullptr, 0, nullptr);
            if (th) CloseHandle(th);
            break;
        }
        case BTN_TOGGLE_QUIC:
            if (st->quic_running) {
                xydesk_quic_server_stop();
                st->quic_running = false;
                set_toast(L"Responder QUIC UDP :4433 dihentikan sementara.");
            } else {
                int rc = xydesk_quic_server_start(&g_meta);
                st->quic_running = (rc == 0);
                set_toast(st->quic_running
                              ? L"Responder QUIC UDP :4433 aktif kembali."
                              : L"Gagal mengikat UDP :4433 — pastikan tidak ada proses lain yang memakai port 4433.");
            }
            break;
        case BTN_HIDE_TRAY:
            ShowWindow(hwnd, SW_HIDE);
            break;
        case BTN_EXIT_APP:
            DestroyWindow(hwnd);
            break;
        default:
            break;
    }
    InvalidateRect(hwnd, nullptr, FALSE);
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    UiState* st = ui_state(hwnd);
    switch (msg) {
        case WM_CREATE: {
            CREATESTRUCTW* cs = reinterpret_cast<CREATESTRUCTW*>(lp);
            st = reinterpret_cast<UiState*>(cs->lpCreateParams);
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(st));
            apply_dark_titlebar(hwnd);

            NOTIFYICONDATAW nid{};
            nid.cbSize = sizeof(nid);
            nid.hWnd = hwnd;
            nid.uID = 1;
            nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
            nid.uCallbackMessage = WM_TRAY;
            nid.hIcon = LoadIconW(nullptr, MAKEINTRESOURCEW(32512));
            wcscpy(nid.szTip, L"XyDesk Remote Host Agent v2.4 (All-in-One EXE)");
            Shell_NotifyIconW(NIM_ADD, &nid);

            SetTimer(hwnd, TIMER_ID, 2000, nullptr);
            g_ui_hwnd = hwnd;
            return 0;
        }
        case WM_ERASEBKGND:
            return 1;
        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC hdc = BeginPaint(hwnd, &ps);
            paint_dashboard(hwnd, hdc);
            EndPaint(hwnd, &ps);
            return 0;
        }
        case WM_MOUSEMOVE: {
            if (!st) return 0;
            POINT pt{ (short)LOWORD(lp), (short)HIWORD(lp) };
            BtnId hit = BTN_NONE;
            for (const auto& b : st->buttons) {
                if (PtInRect(&b.rc, pt)) {
                    hit = b.id;
                    break;
                }
            }
            if (hit != st->hovered_btn) {
                st->hovered_btn = hit;
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        }
        case WM_SETCURSOR: {
            if (st && st->hovered_btn != BTN_NONE) {
                SetCursor(LoadCursorW(nullptr, MAKEINTRESOURCEW(32649)));
                return TRUE;
            }
            break;
        }
        case WM_LBUTTONUP: {
            if (!st) return 0;
            POINT pt{ (short)LOWORD(lp), (short)HIWORD(lp) };
            for (const auto& b : st->buttons) {
                if (PtInRect(&b.rc, pt)) {
                    handle_button_click(hwnd, st, b.id);
                    break;
                }
            }
            return 0;
        }
        case WM_TIMER:
            if (wp == TIMER_ID) {
                g_onmic_ready = check_onmic_registry_unlocked();
                g_rdp_listening = check_tcp_port_listening(g_meta.rdp_port);
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        case WM_XY_STATUS: {
            auto* s = reinterpret_cast<std::wstring*>(wp);
            if (s) {
                set_toast(*s);
                delete s;
            }
            return 0;
        }
        case WM_COMMAND:
            switch (LOWORD(wp)) {
                case IDM_SHOW:
                    ShowWindow(hwnd, SW_SHOW);
                    SetForegroundWindow(hwnd);
                    return 0;
                case IDM_TOGGLE:
                    handle_button_click(hwnd, st, BTN_TOGGLE_QUIC);
                    return 0;
                case IDM_ONMIC:
                    handle_button_click(hwnd, st, BTN_ONMIC_SETUP);
                    return 0;
                case IDM_EXIT:
                    DestroyWindow(hwnd);
                    return 0;
            }
            return 0;
        case WM_TRAY:
            if (lp == WM_RBUTTONUP || lp == WM_LBUTTONUP) {
                POINT pt;
                GetCursorPos(&pt);
                HMENU menu = CreatePopupMenu();
                AppendMenuW(menu, MF_STRING, IDM_SHOW, L"Buka XyDesk Remote Host");
                AppendMenuW(menu, MF_STRING, IDM_ONMIC, L"Aktifkan On-Mic & Virtual Audio");
                AppendMenuW(menu, MF_STRING, IDM_TOGGLE,
                            (st && st->quic_running) ? L"Hentikan QUIC" : L"Mulai QUIC");
                AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
                AppendMenuW(menu, MF_STRING, IDM_EXIT, L"Keluar");
                SetForegroundWindow(hwnd);
                TrackPopupMenu(menu, TPM_RIGHTBUTTON, pt.x, pt.y, 0, hwnd, nullptr);
                DestroyMenu(menu);
            }
            return 0;
        case WM_CLOSE:
            ShowWindow(hwnd, SW_HIDE);
            return 0;
        case WM_DESTROY: {
            NOTIFYICONDATAW nid{};
            nid.cbSize = sizeof(nid);
            nid.hWnd = hwnd;
            nid.uID = 1;
            Shell_NotifyIconW(NIM_DELETE, &nid);
            KillTimer(hwnd, TIMER_ID);
            if (st) st->running = false;
            PostQuitMessage(0);
            return 0;
        }
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

}  // namespace

static int xydesk_host_ui_run(bool quic_running) {
    HWND console = GetConsoleWindow();
    if (console) {
        ShowWindow(console, SW_HIDE);
        FreeConsole();
    }

    enable_per_monitor_dpi_v2();

    Gdiplus::GdiplusStartupInput gdiplusStartupInput;
    ULONG_PTR gdiplusToken = 0;
    Gdiplus::GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, nullptr);

    HINSTANCE inst = GetModuleHandleW(nullptr);
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = inst;
    wc.hIcon = LoadIconW(nullptr, MAKEINTRESOURCEW(32512));
    wc.hCursor = LoadCursorW(nullptr, MAKEINTRESOURCEW(32512));
    wc.hbrBackground = nullptr;
    wc.lpszClassName = kClassName;
    if (!RegisterClassExW(&wc)) return -1;

    UiState state;
    state.quic_running = quic_running;
    state.running = true;

    const int winW = 1040;
    const int winH = 650;
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    int posX = (screenW > winW) ? (screenW - winW) / 2 : CW_USEDEFAULT;
    int posY = (screenH > winH) ? (screenH - winH) / 2 : CW_USEDEFAULT;

    HWND hwnd = CreateWindowExW(
        0, kClassName,
        L"XyDesk Remote Host v2.5  —  All-in-One P2P 120FPS & RDP Studio (DLL · Driver · On-Mic)",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        posX, posY, winW, winH,
        nullptr, nullptr, inst, &state);
    if (!hwnd) {
        Gdiplus::GdiplusShutdown(gdiplusToken);
        return -1;
    }

    ShowWindow(hwnd, SW_SHOW);
    UpdateWindow(hwnd);

    MSG msg{};
    while (state.running && GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    xydesk_quic_server_stop();
    Gdiplus::GdiplusShutdown(gdiplusToken);
    return 0;
}
#endif  // _WIN32

static void xydesk_downloads_watch_start() {
#ifdef _WIN32
    HANDLE thread = CreateThread(nullptr, 0, tsclient_downloads_thread, nullptr, 0, nullptr);
    if (thread) CloseHandle(thread);
#endif
}

int main(int argc, char** argv) {
    bool setup_only = false;
    bool no_ui = false;
    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "--setup-only") == 0 || strcmp(argv[i], "--version") == 0) {
            setup_only = true;
        } else if (strcmp(argv[i], "--no-ui") == 0) {
            no_ui = true;
        }
    }

    xydesk_host_inspect_system(&g_meta);
    xydesk_host_apply_windows_policies();
#ifdef _WIN32
    init_clean_credentials_and_fingerprint();
#endif

    if (setup_only) {
        return 0;
    }

    g_quic_rc = xydesk_quic_server_start(&g_meta);
    xydesk_relay_start();
    xydesk_downloads_watch_start();
#ifdef _WIN32
    {
        HANDLE hRdpInit = CreateThread(nullptr, 0, auto_rdp_provision_thread, nullptr, 0, nullptr);
        if (hRdpInit) CloseHandle(hRdpInit);
    }
#endif

#ifdef _WIN32
    if (!no_ui) {
        if (xydesk_host_ui_run(g_quic_rc == 0) == 0) return 0;
    }
    while (true) {
        Sleep(2000);
    }
#endif
    xydesk_quic_server_stop();
    return 0;
}
