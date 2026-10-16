#include "../include/xydesk_host_core.h"
#include "../include/xydesk_quic.h"

#ifdef _WIN32
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <winhttp.h>
#include <wtsapi32.h>
#endif

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <cwchar>
#include <fstream>
#include <map>
#include <set>
#include <string>
#include <vector>

// ---------------------------------------------------------------------------
// Host Agent punya dua wajah: konsol (dipakai CI, --setup-only, --no-ui) dan
// jendela Win32 (dipakai manusia). Kode UI sengaja ada di file ini, bukan di
// pustaka terpisah: hanya exe yang memakainya, dan menambah DLL baru berarti
// menambah satu import lib + satu baris link lagi yang bisa gagal diam-diam.
// ---------------------------------------------------------------------------

static XyQuicHostMetadata g_meta{};
static int g_quic_rc = 0;

// ---------------------------------------------------------------------------
// Relay direktori ID PC global (Tahap ② koneksi PC nyata):
// ID global = "XY" + 8 hex FNV-1a(MachineGuid) — stabil lintas reboot, beda
// antar mesin, dan TIDAK mengode IP (beda dari ID PC 10 digit lama yang hanya
// berlaku satu LAN). Heartbeat WinHTTP POST ke worker /api/pc/relay tiap 60
// detik; rekaman worker ber-TTL 300 detik sehingga PC mati hilang sendiri.
// Kode relay ditaruh di exe (bukan DLL baru) mengikuti alasan kode UI di atas.
// ---------------------------------------------------------------------------
static char g_global_id[16] = {0};

#ifdef _WIN32
namespace {

std::wstring machine_guid() {
    wchar_t buf[128] = {0};
    DWORD size = sizeof(buf);
    const LONG rc = RegGetValueW(HKEY_LOCAL_MACHINE,
                                 L"SOFTWARE\\Microsoft\\Cryptography",
                                 L"MachineGuid", RRF_RT_REG_SZ, nullptr, buf, &size);
    if (rc != ERROR_SUCCESS) return std::wstring();
    return std::wstring(buf);
}

std::string hostname_ascii() {
    char buf[256] = {0};
    DWORD size = sizeof(buf);
    if (GetComputerNameA(buf, &size)) return std::string(buf);
    return "xydesk-host";
}

uint32_t fnv1a32(const std::string& text) {
    uint64_t hash = 1469598103934665603ull;
    for (const unsigned char c : text) {
        hash ^= c;
        hash *= 1099511628211ull;
    }
    return (uint32_t)(hash & 0xFFFFFFFFu);
}

bool relay_post_heartbeat(const std::string& body) {
    bool ok = false;
    HINTERNET session = WinHttpOpen(L"XyDeskHost/1.1", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                                    WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!session) return false;
    WinHttpSetTimeouts(session, 10000, 10000, 10000, 15000);
    HINTERNET connect =
        WinHttpConnect(session, L"api.xydeskremote.biz.id", INTERNET_DEFAULT_HTTPS_PORT, 0);
    HINTERNET request = nullptr;
    if (connect) {
        request = WinHttpOpenRequest(connect, L"POST", L"/api/pc/relay", nullptr,
                                     WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES,
                                     WINHTTP_FLAG_SECURE);
    }
    if (request) {
        const wchar_t* headers = L"Content-Type: application/json\r\n";
        if (WinHttpSendRequest(request, headers, (DWORD)-1, (LPVOID)body.data(),
                               (DWORD)body.size(), (DWORD)body.size(), 0) &&
            WinHttpReceiveResponse(request, nullptr)) {
            DWORD status = 0;
            DWORD statusSize = sizeof(status);
            if (WinHttpQueryHeaders(request,
                                    WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                                    WINHTTP_HEADER_NAME_BY_INDEX, &status, &statusSize,
                                    WINHTTP_NO_HEADER_INDEX) &&
                (status == 200 || status == 429)) {
                ok = true;  // 429 = throttle tulis worker; heartbeat berikutnya menyusul
            }
        }
    }
    if (request) WinHttpCloseHandle(request);
    if (connect) WinHttpCloseHandle(connect);
    WinHttpCloseHandle(session);
    return ok;
}

DWORD WINAPI relay_heartbeat_thread(LPVOID) {
    for (;;) {
        char body[512];
        snprintf(body, sizeof(body), "{\"id\":\"%s\",\"hostname\":\"%s\",\"rdp_port\":%u}",
                 g_global_id, g_meta.hostname, (unsigned)g_meta.rdp_port);
        relay_post_heartbeat(body);
        Sleep(60000);
    }
    return 0;
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

static void xydesk_relay_start() {
#ifdef _WIN32
    if (xydesk_global_pc_id()[0] == '\0') return;
    HANDLE thread = CreateThread(nullptr, 0, relay_heartbeat_thread, nullptr, 0, nullptr);
    if (thread) CloseHandle(thread);
#endif
}

#ifdef _WIN32
namespace {

constexpr int IDC_APPLY   = 1001;
constexpr int IDC_COPYID  = 1002;
constexpr int IDC_TOGGLE  = 1003;
constexpr int IDC_EXIT    = 1004;
constexpr int IDC_INFO    = 1010;
constexpr int IDC_STATUS  = 1011;
constexpr int IDC_SESSION = 1012;
constexpr int IDC_LOG     = 1013;
constexpr int IDC_SUB     = 1014;
constexpr int IDC_VER     = 1015;
constexpr int TIMER_ID    = 1;
constexpr UINT WM_TRAY    = WM_APP + 1;
// Baris log dari thread watcher (wParam = std::wstring* di heap; WndProc
// membebaskannya setelah menambahkan ke kotak log).
constexpr UINT WM_XY_LOG  = WM_APP + 2;
constexpr UINT IDM_SHOW   = 2001;
constexpr UINT IDM_TOGGLE = 2002;
constexpr UINT IDM_EXIT   = 2003;

const wchar_t* kClassName = L"XyDeskHostAgentWindow";

// ---------------------------------------------------------------------------
// Tema visual modern (revisi UI ronde 43): dark + kartu rounded + aksen biru.
// Warna dipilih senada dengan app Android (graft/baja/aksen #7C9EFF).
// ---------------------------------------------------------------------------
constexpr COLORREF kBg        = RGB(0x14, 0x17, 0x1D);  // latar jendela
constexpr COLORREF kCard     = RGB(0x1B, 0x1F, 0x27);  // permukaan kartu
constexpr COLORREF kCardEdge = RGB(0x2A, 0x30, 0x3C);  // garis kartu
constexpr COLORREF kLogBg    = RGB(0x10, 0x13, 0x1A);  // panel log
constexpr COLORREF kText     = RGB(0xF2, 0xF5, 0xFA);  // teks utama
constexpr COLORREF kTextDim  = RGB(0x9A, 0xA3, 0xB2);  // teks sekunder
constexpr COLORREF kAccent   = RGB(0x7C, 0x9E, 0xFF);  // aksen XyDesk
constexpr COLORREF kOk       = RGB(0x3D, 0xD6, 0x8C);  // status hijau

HBRUSH g_brBg = nullptr;
HBRUSH g_brCard = nullptr;
HBRUSH g_brLog = nullptr;
HFONT g_fontTitle = nullptr;
HFONT g_fontSub = nullptr;
HFONT g_fontBody = nullptr;
HFONT g_fontLabel = nullptr;
HFONT g_fontMono = nullptr;
bool g_quic_ok_last = false;

void theme_create_resources() {
    if (g_brBg) return;
    g_brBg = CreateSolidBrush(kBg);
    g_brCard = CreateSolidBrush(kCard);
    g_brLog = CreateSolidBrush(kLogBg);
    // "Segoe UI Variable" hadir di Windows 11; Windows 10 otomatis gagal memuat
    // dan CreateFontW memakai fallback sistem (Segoe UI) — jadi aman dipakai.
    g_fontTitle = CreateFontW(26, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE,
                              DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                              CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE,
                              L"Segoe UI Variable Display");
    g_fontSub = CreateFontW(15, 0, 0, 0, FW_NORMAL, FALSE, TRUE /*italic*/, FALSE,
                            DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                            CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE,
                            L"Segoe UI Variable Text");
    g_fontBody = CreateFontW(17, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                             DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                             CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE,
                             L"Segoe UI Variable Text");
    g_fontLabel = CreateFontW(14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                              DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                              CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE,
                              L"Segoe UI Variable Text");
    g_fontMono = CreateFontW(16, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                             DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                             CLEARTYPE_QUALITY, FIXED_PITCH | FF_MODERN,
                             L"Consolas");
}

/** Dark mode caption + kontrol anak via API tema (dimuat dinamis, tanpa link baru). */
void theme_apply_dark(HWND hwnd) {
    // DWMWA_USE_IMMERSIVE_DARK_MODE: 20 (19 pada build lama) — coba keduanya.
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
    // DarkMode_Explorer membuat BUTTON/EDIT ikut dark (Windows 10 1809+).
    typedef HRESULT(WINAPI * PFN_SetWindowTheme)(HWND, LPCWSTR, LPCWSTR);
    HMODULE ux = LoadLibraryW(L"uxtheme.dll");
    if (!ux) return;
    auto setTheme = reinterpret_cast<PFN_SetWindowTheme>(
        (void*)GetProcAddress(ux, "SetWindowTheme"));
    if (!setTheme) return;
    HWND child = nullptr;
    while ((child = FindWindowExW(hwnd, child, nullptr, nullptr)) != nullptr) {
        wchar_t cls[64] = {0};
        RealGetWindowClassW(child, cls, 63);
        if (wcscmp(cls, L"Button") == 0 || wcscmp(cls, L"Edit") == 0) {
            setTheme(child, L"DarkMode_Explorer", nullptr);
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
    // std::vswprintf, bukan _vsnwprintf: build memakai -std=c++17 yang
    // menyalakan __STRICT_ANSI__, dan mingw menyembunyikan nama berprefiks
    // garis bawah ala MSVC di mode itu.
    int n = std::vswprintf(buf, 2047, fmtstr, ap);
    va_end(ap);
    if (n < 0) buf[0] = L'\0';
    buf[2046] = L'\0';
    return std::wstring(buf);
}

void set_text(HWND h, const std::wstring& s) { SetWindowTextW(h, s.c_str()); }

void append_log(HWND hlog, const std::wstring& line) {
    if (!hlog) return;
    int len = GetWindowTextLengthW(hlog);
    SendMessageW(hlog, EM_SETSEL, (WPARAM)len, (LPARAM)len);
    std::wstring text = line + L"\r\n";
    SendMessageW(hlog, EM_REPLACESEL, FALSE, (LPARAM)text.c_str());
}

/**
 * Nama desktop input yang sedang aktif.
 *
 * Windows memindahkan input ke desktop "Winlogon" saat layar kunci atau layar
 * login tampil. Agent berjalan di sesi pengguna biasa, jadi ia tidak bisa — dan
 * memang tidak boleh — membuka kunci itu sendiri; yang bisa ia lakukan adalah
 * melaporkan bahwa layar login sedang aktif supaya pengguna tahu jalur masuknya
 * adalah koneksi RDP dari HP, bukan agent ini.
 */
std::wstring query_input_desktop() {
    HDESK desk = OpenInputDesktop(0, FALSE, GENERIC_READ);
    if (!desk) return L"(tidak bisa dibaca)";
    DWORD need = 0;
    GetUserObjectInformationW(desk, UOI_NAME, nullptr, 0, &need);
    std::vector<wchar_t> buf(need ? (size_t)need / sizeof(wchar_t) + 1 : 64, L'\0');
    DWORD got = 0;
    BOOL ok = GetUserObjectInformationW(desk, UOI_NAME, buf.data(),
                                        (DWORD)(buf.size() * sizeof(wchar_t)), &got);
    CloseDesktop(desk);
    if (!ok) return L"(tidak bisa dibaca)";
    return std::wstring(buf.data());
}

/**
 * Nama keadaan sesi Windows.
 *
 * Switch memakai angka, bukan enumerator: mingw-w64 menamai anggota
 * WTS_CONNECTSTATE_CLASS tanpa akhiran "State" (WTSActive, WTSConnected, ...)
 * sedangkan SDK Microsoft memakai WTSActiveState, WTSConnectedState, ...
 * Nilai numeriknya identik dan stabil di ABI, jadi angka membuat file ini bisa
 * dikompilasi oleh kedua header tanpa #ifdef.
 */
const wchar_t* connect_state_name(int state) {
    switch (state) {
        case 0: return L"Aktif";
        case 1: return L"Terhubung";
        case 2: return L"Menunggu persetujuan";
        case 3: return L"Shadow";
        case 4: return L"Terputus (sesi masih hidup)";
        case 5: return L"Idle";
        case 6: return L"Listen";
        case 7: return L"Reset";
        case 8: return L"Down";
        case 9: return L"Init";
        default: return L"Tidak dikenal";
    }
}

std::wstring query_session_state() {
    WTS_CONNECTSTATE_CLASS* st = nullptr;
    DWORD bytes = 0;
    if (!WTSQuerySessionInformationW(WTS_CURRENT_SERVER_HANDLE, WTS_CURRENT_SESSION,
                                     WTSConnectState, reinterpret_cast<LPWSTR*>(&st),
                                     &bytes) || !st) {
        return L"(tidak tersedia)";
    }
    std::wstring name = connect_state_name(static_cast<int>(*st));
    WTSFreeMemory(st);
    return name;
}

std::wstring copy_to_clipboard(HWND owner, const std::wstring& text) {
    if (!OpenClipboard(owner)) return L"Gagal membuka clipboard.";
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
    return h ? L"ID PC disalin ke clipboard." : L"Gagal menyalin.";
}

struct UiState {
    HWND info = nullptr;
    HWND status = nullptr;
    HWND session = nullptr;
    HWND log = nullptr;
    HWND toggle = nullptr;
    bool quic_running = false;
    bool running = false;
};

UiState* ui_state(HWND hwnd) {
    return reinterpret_cast<UiState*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
}

void refresh_dynamic(HWND hwnd) {
    UiState* st = ui_state(hwnd);
    if (!st) return;
    set_text(st->status, fmt(L"QUIC %s   ·   UDP :%u   ·   %llu paket dilayani",
                             st->quic_running ? L"AKTIF" : L"BERHENTI",
                             (unsigned)g_meta.quic_port,
                             (unsigned long long)xydesk_quic_packets_served()));
    std::wstring desktop = query_input_desktop();
    std::wstring line = fmt(L"Sesi Windows: %s", query_session_state().c_str());
    if (desktop == L"Winlogon") {
        line += L"  ·  layar login aktif — masuk lewat RDP dari HP.";
    } else if (!desktop.empty()) {
        line += fmt(L"  ·  desktop: %s", desktop.c_str());
    }
    set_text(st->session, line);
}

HWND make_static(HWND parent, int id, int x, int y, int w, int h, HFONT font) {
    HWND wnd = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE | SS_LEFT,
                               x, y, w, h, parent, (HMENU)(INT_PTR)id, nullptr, nullptr);
    if (font) SendMessageW(wnd, WM_SETFONT, (WPARAM)font, TRUE);
    return wnd;
}

// ---------------------------------------------------------------------------
// Watcher file HP -> folder Download PC (ronde 45).
//
// Drive redirect RDP memunculkan folder HP sebagai \\tsclient\XyDesk di
// Windows, tetapi file yang dikirim dari HP sebelumnya hanya TERDUDUK di
// drive itu — pengguna harus membuka File Explorer dan menebak letaknya.
// Thread ini menonton drive selama sesi RDP aktif dan memindahkan setiap
// file yang selesai ditulis langsung ke folder Download (%USERPROFILE%\
// Downloads), seperti layaknya aplikasi remote desktop pada umumnya.
//
// Dua varian folder bersama (lihat SessionFileTransfer.transferRoot di HP):
//   - akses "semua file": root drive = seluruh storage HP, folder bersama
//     ada di \\tsclient\XyDesk\XyDesk (dideteksi lebih dulu);
//   - akses terbatas: root drive = folder bersama itu sendiri. Kalau root
//     berisi folder "Android" berarti itu storage penuh dan TIDAK ditonton
//     (menyalin DCIM dkk ke Download pasti salah).
//
// File baru hanya disalin setelah ukurannya stabil dua poll berturut-turut,
// jadi file besar yang masih ditulis HP tidak menghasilkan salinan setengah
// jadi. Tanda tangan nama|ukuran|waktu-tulis disimpan ke
// %APPDATA%\XyDesk Remote\received-files.log supaya file yang sama tidak
// disalin dua kali lintas restart agent.
//
// Semua akses filesystem terjadi di thread ini (bukan UI thread): panggilan
// ke tsclient saat sesi baru mati bisa menggantung berdetik-detik, dan UI
// tidak boleh ikut beku.
// ---------------------------------------------------------------------------

HWND g_ui_hwnd = nullptr;

/** Kirim satu baris log ke jendela utama dari thread apa pun. */
void ui_log_line(const std::wstring& line) {
    if (!g_ui_hwnd) return;
    auto* heap = new std::wstring(line);
    if (!PostMessageW(g_ui_hwnd, WM_XY_LOG, reinterpret_cast<WPARAM>(heap), 0)) {
        delete heap;
    }
}

std::wstring downloads_dir() {
    PWSTR path = nullptr;
    std::wstring out;
    // GUID FOLDERID_Downloads ({374DE290-123F-4565-9164-39C4925E467B})
    // didefinisikan lokal: mingw meletakkannya di libuuid yang belum dipakai
    // build ini, dan menambah flag link baru dilarang (catatan rilis lama).
    static const GUID kFolderIdDownloads = {
        0x374de290, 0x123f, 0x4565,
        {0x91, 0x64, 0x39, 0xc4, 0x92, 0x5e, 0x46, 0x7b}};
    if (SUCCEEDED(SHGetKnownFolderPath(kFolderIdDownloads, 0, nullptr, &path)) && path) {
        out = path;
    }
    if (path) CoTaskMemFree(path);
    return out;  // kosong = tidak ada tujuan; watcher keluar sendiri.
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

/** "a.zip" yang sudah ada di dir menjadi "a (2).zip", "a (3).zip", dst. */
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

    std::map<std::wstring, unsigned long long> seen;  // nama -> ukuran poll pertama
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
            ui_log_line(L"Drive XyDesk aktif — file dari HP otomatis masuk folder Download.");
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
                next[name] = size;  // baru terlihat / masih ditulis HP
                continue;
            }
            const std::wstring target = unique_target(downloads, name);
            if (!target.empty() && CopyFileW((watch + L"\\" + name).c_str(), target.c_str(), TRUE)) {
                recorded.insert(sig);
                std::wofstream out(record_path.c_str(), std::ios::app);
                out << sig << L"\n";
                ui_log_line(L"File dari HP diterima: " + name + L"  \u2192  Download");
            } else {
                next[name] = size;  // gagal salin (mis. masih terkunci) — ulang nanti
            }
        } while (FindNextFileW(find, &fd));
        FindClose(find);
        seen.swap(next);
    }
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    UiState* st = ui_state(hwnd);
    switch (msg) {
        case WM_CREATE: {
            CREATESTRUCTW* cs = reinterpret_cast<CREATESTRUCTW*>(lp);
            st = reinterpret_cast<UiState*>(cs->lpCreateParams);
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(st));

            theme_create_resources();

            RECT rc;
            GetClientRect(hwnd, &rc);
            const int W = (int)rc.right ? (int)rc.right : 584;
            const int H = (int)rc.bottom ? (int)rc.bottom : 481;

            // ---- Header: judul besar + subjudul + versi kanan ----
            HWND title = make_static(hwnd, 0, 24, 16, 340, 34, g_fontTitle);
            set_text(title, L"XyDesk Remote");
            HWND sub = make_static(hwnd, IDC_SUB, 26, 52, 340, 18, g_fontSub);
            set_text(sub, L"HOST AGENT  ·  XYVERSE");
            HWND ver = make_static(hwnd, IDC_VER, W - 190, 22, 166, 34, g_fontLabel);
            set_text(ver, fmt(L"Core %s", to_wide(xydesk_host_core_version()).c_str()));

            // ---- Kartu info (posisi kartu sendiri digambar WM_PAINT) ----
            struct Row { const wchar_t* label; };
            const Row rows[] = {
                {L"Nama komputer"},
                {L"IPv4 LAN"},
                {L"GPU"},
                {L"ID PC (LAN)"},
                {L"ID Global (relay)"},
                {L"Engine"},
            };
            int ry = 94;
            for (int i = 0; i < 6; ++i) {
                HWND lab = make_static(hwnd, 0, 40, ry, 150, 20, g_fontLabel);
                set_text(lab, rows[i].label);
                ry += 25;
            }
            st->info = make_static(hwnd, IDC_INFO, 196, 94, W - 220, 150, g_fontBody);

            // ---- Status & sesi ----
            st->status = make_static(hwnd, IDC_STATUS, 46, 260, W - 70, 22, g_fontBody);
            st->session = make_static(hwnd, IDC_SESSION, 24, 288, W - 48, 20, g_fontLabel);

            // ---- Tombol aksi ----
            const int bw = 138, gap = 10, by = 322;
            struct { int id; const wchar_t* label; } buttons[] = {
                { IDC_APPLY,  L"Terapkan kebijakan" },
                { IDC_COPYID, L"Salin ID PC" },
                { IDC_TOGGLE, L"Berhenti" },
                { IDC_EXIT,   L"Keluar" },
            };
            for (int i = 0; i < 4; ++i) {
                HWND b = CreateWindowExW(0, L"BUTTON", buttons[i].label,
                                         WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                         24 + i * (bw + gap), by, bw, 38,
                                         hwnd, (HMENU)(INT_PTR)buttons[i].id, nullptr, nullptr);
                SendMessageW(b, WM_SETFONT, (WPARAM)g_fontBody, TRUE);
                if (buttons[i].id == IDC_TOGGLE) st->toggle = b;
            }

            // ---- Panel log (kartu rounded digambar parent, EDIT polos di atasnya) ----
            st->log = CreateWindowExW(0, L"EDIT", L"",
                                      WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_MULTILINE |
                                      ES_AUTOVSCROLL | ES_READONLY,
                                      28, 380, W - 56, H - 398, hwnd,
                                      (HMENU)(INT_PTR)IDC_LOG, nullptr, nullptr);
            SendMessageW(st->log, WM_SETFONT, (WPARAM)g_fontMono, TRUE);

            std::wstring info = fmt(
                L"%s\r\n"
                L"%s  ·  RDP :%u · QUIC UDP :%u\r\n"
                L"%s [%s]\r\n"
                L"%s\r\n"
                L"%s  (bisa dari luar LAN)\r\n"
                L"%s / %s",
                to_wide(g_meta.hostname).c_str(),
                to_wide(g_meta.primary_ip).c_str(),
                (unsigned)g_meta.rdp_port, (unsigned)g_meta.quic_port,
                to_wide(g_meta.gpu_name).c_str(),
                to_wide(g_meta.gpu_encoder).c_str(),
                to_wide(g_meta.pc_id).c_str(),
                to_wide(xydesk_global_pc_id()).c_str(),
                to_wide(xydesk_host_core_version()).c_str(),
                to_wide(xydesk_quic_dll_version()).c_str());
            set_text(st->info, info);

            NOTIFYICONDATAW nid{};
            nid.cbSize = sizeof(nid);
            nid.hWnd = hwnd;
            nid.uID = 1;
            nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
            nid.uCallbackMessage = WM_TRAY;
            // Build tidak mendefinisikan UNICODE, jadi IDI_APPLICATION dari <winuser.h>
            // melebar ke MAKEINTRESOURCEA (char*) dan ditolak oleh API berakhiran W.
            // 32512 adalah nilai IDI_APPLICATION.
            nid.hIcon = LoadIconW(nullptr, MAKEINTRESOURCEW(32512));
            wcscpy(nid.szTip, L"XyDesk Remote Host Agent");
            Shell_NotifyIconW(NIM_ADD, &nid);

            theme_apply_dark(hwnd);

            SetTimer(hwnd, TIMER_ID, 1000, nullptr);
            g_ui_hwnd = hwnd;
            append_log(st->log, L"Jendela siap.");
            g_quic_ok_last = st->quic_running;
            refresh_dynamic(hwnd);
            return 0;
        }
        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC hdc = BeginPaint(hwnd, &ps);
            RECT rc;
            GetClientRect(hwnd, &rc);
            const int W = (int)rc.right;
            const int H = (int)rc.bottom;
            // Double buffer supaya kartu tidak berkedip saat timer merefresh.
            HDC mem = CreateCompatibleDC(hdc);
            HBITMAP bmp = CreateCompatibleBitmap(hdc, W, H ? H : 1);
            HBITMAP old = (HBITMAP)SelectObject(mem, bmp);
            HBRUSH oldBr = (HBRUSH)SelectObject(mem, g_brBg);
            PatBlt(mem, 0, 0, W, H, PATCOPY);

            // Kartu info: rounded + border halus.
            HPEN pen = CreatePen(PS_SOLID, 1, kCardEdge);
            HPEN oldPen = (HPEN)SelectObject(mem, pen);
            SelectObject(mem, g_brCard);
            RoundRect(mem, 20, 78, W - 20, 78 + 162, 18, 18);

            // Kartu log.
            RoundRect(mem, 20, 372, W - 20, H - 14, 18, 18);

            // Titik status (hijau saat QUIC aktif, merah saat berhenti).
            const bool ok = st ? st->quic_running : g_quic_ok_last;
            HBRUSH dot = CreateSolidBrush(ok ? kOk : RGB(0xE2, 0x5B, 0x5B));
            SelectObject(mem, dot);
            Ellipse(mem, 26, 264, 40, 278);
            DeleteObject(dot);

            // Garis aksen kecil di bawah header.
            HPEN accentPen = CreatePen(PS_SOLID, 3, kAccent);
            HPEN penSaved = (HPEN)SelectObject(mem, accentPen);
            MoveToEx(mem, 26, 75, nullptr);
            LineTo(mem, 26 + 88, 75);
            SelectObject(mem, penSaved);

            // Salin hasil buffer ke layar sebagai satu gambar utuh, lalu rapikan.
            BitBlt(hdc, 0, 0, W, H, mem, 0, 0, SRCCOPY);
            SelectObject(mem, oldPen);
            SelectObject(mem, oldBr);
            SelectObject(mem, old);
            DeleteObject(pen);
            DeleteObject(accentPen);
            DeleteDC(mem);
            DeleteObject(bmp);
            EndPaint(hwnd, &ps);
            return 0;
        }
        case WM_CTLCOLORSTATIC: {
            HDC hdc = (HDC)wp;
            HWND ctrl = (HWND)lp;
            const int id = GetDlgCtrlID(ctrl);
            if (id == IDC_INFO || id == IDC_STATUS) {
                SetTextColor(hdc, kText);
                SetBkColor(hdc, kCard);
                return (LRESULT)g_brCard;
            }
            if (id == IDC_SUB || id == IDC_VER) {
                SetTextColor(hdc, kTextDim);
                SetBkColor(hdc, kBg);
                return (LRESULT)g_brBg;
            }
            if (id == IDC_SESSION) {
                SetTextColor(hdc, kTextDim);
                SetBkColor(hdc, kBg);
                return (LRESULT)g_brBg;
            }
            // Label kartu (id 0): latar kartu.
            SetTextColor(hdc, kTextDim);
            SetBkColor(hdc, kCard);
            return (LRESULT)g_brCard;
        }
        case WM_CTLCOLOREDIT: {
            // Panel log: latar gelap khas konsol + teks terang.
            HDC hdc = (HDC)wp;
            SetTextColor(hdc, RGB(0xC9, 0xD4, 0xE0));
            SetBkColor(hdc, kLogBg);
            return (LRESULT)g_brLog;
        }
        case WM_TIMER:
            if (wp == TIMER_ID) refresh_dynamic(hwnd);
            return 0;
        case WM_XY_LOG: {
            // Baris log dari thread watcher (heap std::wstring*).
            auto* line = reinterpret_cast<std::wstring*>(wp);
            if (line) {
                append_log(st ? st->log : nullptr, *line);
                delete line;
            }
            return 0;
        }
        case WM_SIZE:
            if (st && st->log) InvalidateRect(st->log, nullptr, TRUE);
            return 0;
        case WM_COMMAND:
            switch (LOWORD(wp)) {
                case IDC_APPLY: {
                    int rc = xydesk_host_apply_windows_policies();
                    append_log(st ? st->log : nullptr,
                               fmt(L"Kebijakan Windows diterapkan (kode %d).", rc));
                    refresh_dynamic(hwnd);
                    return 0;
                }
                case IDC_COPYID:
                    append_log(st ? st->log : nullptr,
                               copy_to_clipboard(hwnd, to_wide(g_meta.pc_id)));
                    return 0;
                case IDC_TOGGLE:
                case IDM_TOGGLE: {
                    if (!st) return 0;
                    if (st->quic_running) {
                        xydesk_quic_server_stop();
                        st->quic_running = false;
                        set_text(st->toggle, L"Mulai");
                        append_log(st->log, L"Responder QUIC dihentikan.");
                    } else {
                        int rc = xydesk_quic_server_start(&g_meta);
                        st->quic_running = (rc == 0);
                        set_text(st->toggle, st->quic_running ? L"Berhenti" : L"Mulai");
                        append_log(st->log, fmt(L"Responder QUIC dimulai (kode %d).", rc));
                    }
                    refresh_dynamic(hwnd);
                    return 0;
                }
                case IDC_EXIT:
                case IDM_EXIT:
                    DestroyWindow(hwnd);
                    return 0;
                case IDM_SHOW:
                    ShowWindow(hwnd, SW_SHOW);
                    SetForegroundWindow(hwnd);
                    return 0;
            }
            return 0;
        case WM_TRAY:
            if (lp == WM_RBUTTONUP || lp == WM_LBUTTONUP) {
                POINT pt;
                GetCursorPos(&pt);
                HMENU menu = CreatePopupMenu();
                AppendMenuW(menu, MF_STRING, IDM_SHOW, L"Tampilkan");
                AppendMenuW(menu, MF_STRING, IDM_TOGGLE,
                            (st && st->quic_running) ? L"Berhenti" : L"Mulai");
                AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
                AppendMenuW(menu, MF_STRING, IDM_EXIT, L"Keluar");
                SetForegroundWindow(hwnd);
                TrackPopupMenu(menu, TPM_RIGHTBUTTON, pt.x, pt.y, 0, hwnd, nullptr);
                DestroyMenu(menu);
            }
            return 0;
        case WM_CLOSE:
            // Menutup jendela menyembunyikan ke tray, bukan mematikan agent:
            // mematikan responder diam-diam akan memutus sesi yang sedang jalan.
            ShowWindow(hwnd, SW_HIDE);
            append_log(st ? st->log : nullptr, L"Disembunyikan ke tray. Agent tetap berjalan.");
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

/**
 * Menjalankan jendela status. Mengembalikan 0 bila jendela dibuat, atau -1 bila
 * gagal (pemanggil kembali ke loop headless). Memblokir sampai pengguna keluar.
 */
static int xydesk_host_ui_run(bool quic_running) {
    HINSTANCE inst = GetModuleHandleW(nullptr);
    theme_create_resources();
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = inst;
    // Lihat catatan di LoadIconW: IDC_ARROW juga melebar ke bentuk ANSI.
    wc.hCursor = LoadCursorW(nullptr, MAKEINTRESOURCEW(32512));
    wc.hbrBackground = g_brBg;
    wc.lpszClassName = kClassName;
    if (!RegisterClassExW(&wc)) return -1;

    UiState state;
    state.quic_running = quic_running;
    state.running = true;

    // WS_EX_COMPOSITED: parent + anak digambar double-buffered sehingga kartu
    // yang dilukis WM_PAINT tidak berkedip saat timer merefresh tiap detik.
    HWND hwnd = CreateWindowExW(WS_EX_COMPOSITED, kClassName, L"XyDesk Remote — Host Agent",
                                WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                                CW_USEDEFAULT, CW_USEDEFAULT, 648, 566,
                                nullptr, nullptr, inst, &state);
    if (!hwnd) return -1;

    // Konsol disembunyikan setelah jendela terbukti ada: kalau jendela gagal
    // dibuat, keluaran konsol tetap terlihat dan agent tetap bisa dipakai.
    HWND console = GetConsoleWindow();
    if (console) ShowWindow(console, SW_HIDE);

    ShowWindow(hwnd, SW_SHOW);
    UpdateWindow(hwnd);

    MSG msg{};
    while (state.running && GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    if (console) ShowWindow(console, SW_SHOW);
    xydesk_quic_server_stop();
    return 0;
}
#endif  // _WIN32

/** Mulai thread watcher file HP → folder Download (ronde 45). */
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

    printf("================================================================\n");
    printf("  XyDesk Remote Host Agent — XyVerse Technology Global\n");
    printf("================================================================\n");
    printf("  Core Engine : %s\n", xydesk_host_core_version());
    printf("  QUIC Engine : %s\n", xydesk_quic_dll_version());
    printf("----------------------------------------------------------------\n");
    printf("  Hostname    : %s\n", g_meta.hostname);
    printf("  IPv4 LAN    : %s (RDP TCP/UDP :%u | QUIC UDP :%u)\n",
           g_meta.primary_ip, g_meta.rdp_port, g_meta.quic_port);
    printf("  GPU Adapter : %s [%s · AVC 4:4:4 + ClearType Ready]\n",
           g_meta.gpu_name, g_meta.gpu_encoder);
    printf("  ID PC (10d) : %s   (LAN saja)\n", g_meta.pc_id);
    printf("  ID Global   : %s   (relay — konek dari luar LAN)\n", xydesk_global_pc_id());
    printf("================================================================\n");

    if (setup_only) {
        printf("[OK] Kebijakan Windows RDP + AVC444 + ClearType + QUIC selesai diterapkan.\n");
        return 0;
    }

    g_quic_rc = xydesk_quic_server_start(&g_meta);
    if (g_quic_rc != 0) {
        printf("[WARN] Gagal mengikat port QUIC UDP %u (kode %d). Pastikan berjalan sebagai Admin.\n",
               g_meta.quic_port, g_quic_rc);
    } else {
        printf("[READY] XyDesk Native QUIC v1 Responder aktif di UDP :%u.\n", g_meta.quic_port);
        printf("        Buka XyDesk Remote di Android -> Koneksi PC -> Scan PC di Wi-Fi\n");
        printf("        atau masukkan ID PC: %s\n", g_meta.pc_id);
        printf("        Tekan Ctrl+C untuk keluar.\n");
    }

    xydesk_relay_start();
    printf("[READY] Relay heartbeat aktif — ID Global %s terdaftar di direktori.\n",
           g_global_id);
    xydesk_downloads_watch_start();
    printf("[READY] Watcher file aktif — kiriman HP masuk otomatis ke folder Download.\n");

#ifdef _WIN32
    if (!no_ui) {
        if (xydesk_host_ui_run(g_quic_rc == 0) == 0) return 0;
        printf("[WARN] Jendela tidak bisa dibuat; lanjut tanpa antarmuka (--no-ui).\n");
    }
    while (true) {
        Sleep(2000);
    }
#endif
    xydesk_quic_server_stop();
    return 0;
}
