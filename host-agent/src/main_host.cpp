#include "../include/xydesk_host_core.h"
#include "../include/xydesk_quic.h"
#include "../include/host_readiness.h"

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

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <shellapi.h>
#include <wtsapi32.h>
#include <winhttp.h>
#endif

static XyQuicHostMetadata g_meta{};
static int g_quic_rc = -1;
// Direct UDP is legacy-only until the approved secure pairing/transport migration.
static bool g_legacy_private_udp = false;

#ifdef _WIN32
namespace {

constexpr int TIMER_ID      = 1;
constexpr UINT WM_TRAY      = WM_APP + 1;
constexpr UINT WM_XY_STATUS = WM_APP + 2;
constexpr UINT IDM_SHOW     = 2001;
constexpr UINT IDM_TOGGLE   = 2002;
constexpr UINT IDM_ONMIC    = 2003;
constexpr UINT IDM_EXIT     = 2004;

// Custom Hit-Test Button IDs (100% Owner-Rendered Modern Pills)
enum BtnId {
    BTN_NONE = 0,
    BTN_COPY_ID,
    BTN_COPY_GLOBAL,
    BTN_TOGGLE_PW_EYE,
    BTN_COPY_PW,
    BTN_COPY_ALL_CRED,
    BTN_ROTATE_PW,
    BTN_ONMIC_SETUP,
    BTN_VDD_BOOST,
    BTN_TOGGLE_QUIC,
    BTN_HIDE_TRAY,
    BTN_EXIT_APP
};

struct PillButton {
    BtnId id;
    RECT rc;
    const wchar_t* label;
    bool primary;
    bool accentGreen;
    bool danger;
};

const wchar_t* kClassName = L"XyDeskHostNativeModernUI";

// Palette: Deep Carbon Obsidian + Crisp Monochrome + Emerald/Blue Accents
constexpr COLORREF kBg          = RGB(0x0B, 0x0E, 0x14);
constexpr COLORREF kHeaderBg    = RGB(0x10, 0x15, 0x1F);
constexpr COLORREF kCard        = RGB(0x13, 0x18, 0x22);
constexpr COLORREF kCardInner   = RGB(0x0E, 0x12, 0x1A);
constexpr COLORREF kCardEdge    = RGB(0x23, 0x2C, 0x3D);
constexpr COLORREF kCardHiEdge  = RGB(0x34, 0x45, 0x63);
constexpr COLORREF kText        = RGB(0xF5, 0xF7, 0xFA);
constexpr COLORREF kTextSub     = RGB(0x94, 0xA3, 0xB8);
constexpr COLORREF kTextDim     = RGB(0x64, 0x74, 0x8B);
constexpr COLORREF kAccent      = RGB(0x68, 0x9D, 0xFF);
constexpr COLORREF kAccentHover = RGB(0x82, 0xB1, 0xFF);
constexpr COLORREF kBtnBg       = RGB(0x1C, 0x24, 0x33);
constexpr COLORREF kBtnHover    = RGB(0x28, 0x33, 0x47);
constexpr COLORREF kOk          = RGB(0x22, 0xC5, 0x5E);
constexpr COLORREF kOkBg        = RGB(0x0F, 0x29, 0x1E);
constexpr COLORREF kWarn        = RGB(0xF5, 0x9E, 0x0B);
constexpr COLORREF kDanger      = RGB(0xEF, 0x44, 0x44);

HFONT g_fBrand    = nullptr;
HFONT g_fTitle    = nullptr;
HFONT g_fSub      = nullptr;
HFONT g_fCardHdr  = nullptr;
HFONT g_fBigId    = nullptr;
HFONT g_fMono     = nullptr;
HFONT g_fMonoSm   = nullptr;
HFONT g_fBody     = nullptr;
HFONT g_fBodyBold = nullptr;
HFONT g_fSmall    = nullptr;

HWND g_ui_hwnd = nullptr;
std::wstring g_toast_msg = L"Periksa kesiapan PC. Transport aman belum siap rilis.";
DWORD g_toast_tick = 0;

void create_fonts() {
    if (g_fTitle) return;
    g_fBrand    = CreateFontW(22, 0, 0, 0, FW_EXTRABOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI Variable Display");
    g_fTitle    = CreateFontW(22, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI Variable Display");
    g_fSub      = CreateFontW(14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI Variable Text");
    g_fCardHdr  = CreateFontW(13, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI Variable Text");
    g_fBigId    = CreateFontW(28, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              FIXED_PITCH | FF_MODERN, L"Consolas");
    g_fMono     = CreateFontW(18, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              FIXED_PITCH | FF_MODERN, L"Consolas");
    g_fMonoSm   = CreateFontW(14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              FIXED_PITCH | FF_MODERN, L"Consolas");
    g_fBody     = CreateFontW(15, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI Variable Text");
    g_fBodyBold = CreateFontW(15, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI Variable Text");
    g_fSmall    = CreateFontW(13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                              OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                              DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI Variable Text");
}

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
    BtnId hovered_btn = BTN_NONE;
    std::vector<PillButton> buttons;
};

UiState* ui_state(HWND hwnd) {
    return reinterpret_cast<UiState*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
}

void draw_round_box(HDC hdc, int l, int t, int r, int b, int rad, COLORREF fill, COLORREF border) {
    HBRUSH br = CreateSolidBrush(fill);
    HPEN pen = CreatePen(PS_SOLID, 1, border);
    HBRUSH oldBr = (HBRUSH)SelectObject(hdc, br);
    HPEN oldPen = (HPEN)SelectObject(hdc, pen);
    RoundRect(hdc, l, t, r, b, rad, rad);
    SelectObject(hdc, oldBr);
    SelectObject(hdc, oldPen);
    DeleteObject(br);
    DeleteObject(pen);
}

void draw_text(HDC hdc, HFONT font, COLORREF color, int l, int t, int r, int b,
               const std::wstring& txt, UINT flags = DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_NOPREFIX) {
    HFONT oldF = (HFONT)SelectObject(hdc, font);
    SetTextColor(hdc, color);
    RECT rc{l, t, r, b};
    DrawTextW(hdc, txt.c_str(), -1, &rc, flags);
    SelectObject(hdc, oldF);
}

void rebuild_buttons(UiState* st, int W, int H) {
    if (!st) return;
    st->buttons.clear();
    const int pad = 22;
    const int colGap = 14;
    const int halfW = (W - pad * 2 - colGap) / 2;
    const int leftX = pad;
    const int rightX = pad + halfW + colGap;

    // Connection information is metadata, never a password or cryptographic fingerprint.
    st->buttons.push_back({BTN_COPY_ID, {leftX + halfW - 96, 128, leftX + halfW - 16, 158}, L"Salin IP", true, false, false});
    st->buttons.push_back({BTN_COPY_GLOBAL, {leftX + halfW - 96, 174, leftX + halfW - 16, 202}, L"Salin nama", false, false, false});
    st->buttons.push_back({BTN_COPY_ALL_CRED, {leftX + 16, 284, leftX + halfW - 16, 314}, L"Salin info koneksi (bukan kredensial)", true, false, false});
    st->buttons.push_back({BTN_ONMIC_SETUP, {leftX + 16, 456, leftX + halfW - 16, 488}, L"Kesiapan Mic & Game", false, true, false});
    st->buttons.push_back({BTN_VDD_BOOST, {leftX + 16, 496, leftX + halfW - 16, 526}, L"Buka pengaturan suara Windows", false, false, false});

    // Card 4 (Right Bottom): Quick Controls
    st->buttons.push_back({BTN_TOGGLE_QUIC,  {rightX + 16, 456, rightX + halfW - 16, 488},
                           st->quic_running ? L"Hentikan UDP lama" : L"Mulai UDP lama (uji privat)",
                           false, false, false});
    st->buttons.push_back({BTN_HIDE_TRAY,    {rightX + 16, 496, rightX + (halfW / 2) - 4, 526},
                           L"Sembunyi ke Tray", false, false, false});
    st->buttons.push_back({BTN_EXIT_APP,     {rightX + (halfW / 2) + 4, 496, rightX + halfW - 16, 526},
                           L"Keluar", false, false, true});
    (void)H;
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
    SetBkMode(mem, TRANSPARENT);

    // 1. Background Fill
    HBRUSH bgBr = CreateSolidBrush(kBg);
    FillRect(mem, &rc, bgBr);
    DeleteObject(bgBr);

    // 2. Top Header Bar
    draw_round_box(mem, 16, 14, W - 16, 76, 16, kHeaderBg, kCardEdge);
    draw_round_box(mem, 28, 24, 68, 64, 12, RGB(0x1D, 0x28, 0x3A), kAccent);
    draw_text(mem, g_fBrand, kText, 28, 24, 68, 64, L"XY", DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    draw_text(mem, g_fTitle, kText, 80, 21, 430, 46, L"XyDesk Remote Host");
    draw_text(mem, g_fSub, kTextSub, 80, 45, 480, 66,
              L"Host satu aplikasi  ·  Kesiapan Mic & Game");

    // Live Online Pill in Header Right
    const bool online = st && st->quic_running;
    draw_round_box(mem, W - 246, 28, W - 30, 62, 17,
                   online ? kOkBg : RGB(0x2A, 0x14, 0x18),
                   online ? kOk : kDanger);
    HBRUSH dotBr = CreateSolidBrush(online ? kOk : kDanger);
    HBRUSH oldBr = (HBRUSH)SelectObject(mem, dotBr);
    Ellipse(mem, W - 232, 39, W - 220, 51);
    SelectObject(mem, oldBr);
    DeleteObject(dotBr);
    draw_text(mem, g_fBodyBold, online ? kOk : kDanger, W - 212, 28, W - 38, 62,
              online ? L"UDP LAMA · BERJALAN" : L"LISTENER BERHENTI");

    const int pad = 22;
    const int colGap = 14;
    const int halfW = (W - pad * 2 - colGap) / 2;
    const int leftX = pad;
    const int rightX = pad + halfW + colGap;

    // =========================================================================
    // CARD 1 (Left Top): CLEAN ID & PASSWORD
    // =========================================================================
    draw_round_box(mem, leftX, 88, leftX + halfW, 328, 16, kCard, kCardEdge);
    draw_text(mem, g_fCardHdr, kAccent, leftX + 16, 98, leftX + halfW - 16, 118,
              L"PC STREAM  ·  HOST DIRECT");

    draw_round_box(mem, leftX + 14, 122, leftX + halfW - 14, 164, 12, kCardInner, kCardEdge);
    draw_text(mem, g_fSmall, kTextDim, leftX + 24, 124, leftX + 110, 142, L"IP HOST");
    draw_text(mem, g_fMono, kText, leftX + 24, 140, leftX + halfW - 104, 162, to_wide(g_meta.primary_ip));
    draw_round_box(mem, leftX + 14, 170, leftX + halfW - 14, 206, 10, kCardInner, kCardEdge);
    draw_text(mem, g_fSmall, kTextSub, leftX + 24, 170, leftX + halfW - 104, 206, to_wide(g_meta.hostname));
    draw_text(mem, g_fSmall, kWarn, leftX + 24, 214, leftX + halfW - 24, 244, L"Pairing aman: belum tersedia pada jalur lama");
    draw_text(mem, g_fSmall, kTextSub, leftX + 24, 246, leftX + halfW - 24, 276, L"Tidak memakai / mengubah password Windows");

    // =========================================================================
    // CARD 2 (Right Top): ENKRIPSI & PROTEKSI END-TO-END
    // =========================================================================
    draw_round_box(mem, rightX, 88, rightX + halfW, 328, 16, kCard, kCardEdge);
    draw_text(mem, g_fCardHdr, kOk, rightX + 16, 98, rightX + halfW - 16, 118,
              L"STATUS KEAMANAN TRANSPORT");

    draw_round_box(mem, rightX + 14, 122, rightX + halfW - 14, 172, 12, kCardInner, kCardEdge);
    draw_text(mem, g_fBodyBold, kText, rightX + 24, 126, rightX + halfW - 24, 148,
              L"UDP lama: tanpa autentikasi / enkripsi aplikasi");
    draw_text(mem, g_fSmall, kOk, rightX + 24, 148, rightX + halfW - 24, 168,
              L"Jangan buka listener lama ke internet");

    draw_round_box(mem, rightX + 14, 178, rightX + halfW - 14, 228, 12, kCardInner, kCardEdge);
    draw_text(mem, g_fSmall, kTextDim, rightX + 24, 182, rightX + halfW - 24, 200,
              L"Migrasi pairing dan transport aman disetujui:");
    draw_text(mem, g_fMonoSm, kAccent, rightX + 24, 200, rightX + halfW - 24, 224,
              L"Belum diimplementasikan / belum siap rilis");

    draw_round_box(mem, rightX + 14, 234, rightX + halfW - 14, 314, 12, kCardInner, kCardEdge);
    draw_text(mem, g_fSmall, kTextSub, rightX + 24, 240, rightX + halfW - 24, 260,
              fmt(L"Host: %s   ·   GPU: %s [%s]",
                  to_wide(g_meta.hostname).c_str(),
                  to_wide(g_meta.gpu_name).c_str(),
                  to_wide(g_meta.gpu_encoder).c_str()));
    draw_text(mem, g_fSmall, kTextSub, rightX + 24, 264, rightX + halfW - 24, 284,
              L"Direct lama: LAN/Tailscale privat, opsi eksplisit");
    draw_text(mem, g_fSmall, kOk, rightX + 24, 288, rightX + halfW - 24, 308,
              L"RDP dan Hub adalah jalur host berbeda");

    // =========================================================================
    // CARD 3 (Left Bottom): VIRTUAL DEVICE & ON-MIC AUDIO BRIDGE
    // =========================================================================
    draw_round_box(mem, leftX, 340, leftX + halfW, 538, 16, kCard, kCardEdge);
    draw_text(mem, g_fCardHdr, kAccent, leftX + 16, 350, leftX + halfW - 16, 370,
              L"KESIAPAN PC  ·  PERIKSA, JANGAN ASUMSI");

    draw_round_box(mem, leftX + 14, 376, leftX + halfW - 14, 410, 10, kCardInner, kCardEdge);
    draw_text(mem, g_fBodyBold, kText, leftX + 24, 376, leftX + 210, 410,
              L"GPU / encoder / FPS");
    draw_text(mem, g_fSmall, kWarn, leftX + 210, 376, leftX + halfW - 22, 410,
              L"BELUM DIUJI", DT_RIGHT | DT_VCENTER | DT_SINGLELINE);

    draw_round_box(mem, leftX + 14, 414, leftX + halfW - 14, 448, 10, kCardInner, kCardEdge);
    draw_text(mem, g_fBodyBold, kText, leftX + 24, 414, leftX + 210, 448,
              L"Input mic Windows");
    draw_text(mem, g_fSmall, kWarn, leftX + 210, 414, leftX + halfW - 22, 448,
              L"BUKA PANEL KESIAPAN",
              DT_RIGHT | DT_VCENTER | DT_SINGLELINE);

    // =========================================================================
    // CARD 4 (Right Bottom): TELEMETRI & KONTROL AGENT
    // =========================================================================
    draw_round_box(mem, rightX, 340, rightX + halfW, 538, 16, kCard, kCardEdge);
    draw_text(mem, g_fCardHdr, kTextSub, rightX + 16, 350, rightX + halfW - 16, 370,
              L"TELEMETRI STREAMING  ·  KONTROL HOST");

    draw_round_box(mem, rightX + 14, 376, rightX + halfW - 14, 448, 10, kCardInner, kCardEdge);
    draw_text(mem, g_fBodyBold, kText, rightX + 24, 382, rightX + halfW - 24, 404,
              fmt(L"Paket UDP dilayani: %llu  (port %u)",
                  (unsigned long long)xydesk_quic_packets_served(),
                  (unsigned)g_meta.quic_port));
    draw_text(mem, g_fSmall, kTextSub, rightX + 24, 406, rightX + halfW - 24, 426,
              L"Sinkronisasi File HP: Otomatis masuk ke folder Downloads PC");
    draw_text(mem, g_fSmall, kAccent, rightX + 24, 426, rightX + halfW - 24, 444,
              L"Perangkat dan jalur suara harus diuji di panel kesiapan");

    // =========================================================================
    // RENDER INTERACTIVE PILL BUTTONS
    // =========================================================================
    if (st) {
        for (const auto& b : st->buttons) {
            const bool hov = (st->hovered_btn == b.id);
            COLORREF fill = kBtnBg;
            COLORREF edge = hov ? kCardHiEdge : kCardEdge;
            COLORREF txtCol = kText;
            if (b.primary) {
                fill = hov ? kAccentHover : kAccent;
                edge = fill;
                txtCol = RGB(0x09, 0x0D, 0x14);
            } else if (b.accentGreen) {
                fill = hov ? RGB(0x16, 0x42, 0x2B) : kOkBg;
                edge = kOk;
                txtCol = kOk;
            } else if (b.danger) {
                fill = hov ? RGB(0x3B, 0x16, 0x1B) : RGB(0x24, 0x12, 0x16);
                edge = kDanger;
                txtCol = kDanger;
            } else if (hov) {
                fill = kBtnHover;
            }
            draw_round_box(mem, b.rc.left, b.rc.top, b.rc.right, b.rc.bottom, 10, fill, edge);
            draw_text(mem, g_fBodyBold, txtCol, b.rc.left, b.rc.top, b.rc.right, b.rc.bottom,
                      b.label, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        }
    }

    // =========================================================================
    // BOTTOM STATUS BAR (NO CONSOLE BOX!)
    // =========================================================================
    draw_round_box(mem, pad, 548, W - pad, H - 14, 12, kHeaderBg, kCardEdge);
    draw_text(mem, g_fSmall, kOk, pad + 14, 548, W - pad - 14, H - 14,
              L"\u25CF  " + g_toast_msg);

    BitBlt(hdc, 0, 0, W, H, mem, 0, 0, SRCCOPY);
    SelectObject(mem, oldBmp);
    DeleteObject(bmp);
    DeleteDC(mem);
}

void handle_button_click(HWND hwnd, UiState* st, BtnId id) {
    if (!st) return;
    switch (id) {
        case BTN_COPY_ID:
            copy_to_clipboard(hwnd, to_wide(g_meta.primary_ip));
            set_toast(L"IP host disalin. Bukan token autentikasi.");
            break;
        case BTN_COPY_GLOBAL:
            copy_to_clipboard(hwnd, to_wide(g_meta.hostname));
            set_toast(L"Nama komputer disalin.");
            break;
        case BTN_COPY_ALL_CRED:
            copy_to_clipboard(hwnd, fmt(L"Host: %s | IP: %s | UDP lama: %u | Hanya jaringan privat; belum pairing aman",
                to_wide(g_meta.hostname).c_str(), to_wide(g_meta.primary_ip).c_str(), (unsigned)g_meta.quic_port));
            set_toast(L"Info koneksi disalin, tanpa kredensial Windows.");
            break;
        case BTN_ONMIC_SETUP:
            xydesk_show_readiness(hwnd);
            break;
        case BTN_VDD_BOOST:
            ShellExecuteW(hwnd, L"open", L"ms-settings:sound", nullptr, nullptr, SW_SHOWNORMAL);
            break;
        case BTN_TOGGLE_QUIC:
            if (!g_legacy_private_udp) {
                MessageBoxW(hwnd, L"Jalur lama belum aman dan tidak dinyalakan otomatis. Pairing/transport aman masih dikerjakan. Pengujian privat oleh pemilik hanya melalui opsi --legacy-private-udp; jangan expose ke internet.", L"Transport belum siap rilis", MB_OK | MB_ICONWARNING);
                break;
            }
            if (st->quic_running) {
                xydesk_quic_server_stop();
                st->quic_running = false;
                set_toast(L"Listener UDP lama dihentikan.");
            } else {
                int rc = xydesk_quic_server_start(&g_meta);
                st->quic_running = (rc == 0);
                set_toast(st->quic_running
                              ? L"Listener UDP lama aktif untuk pengujian privat."
                              : L"Gagal mengikat UDP :4433 — jalankan sebagai Administrator.");
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
            create_fonts();
            apply_dark_titlebar(hwnd);

            NOTIFYICONDATAW nid{};
            nid.cbSize = sizeof(nid);
            nid.hWnd = hwnd;
            nid.uID = 1;
            nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
            nid.uCallbackMessage = WM_TRAY;
            nid.hIcon = LoadIconW(nullptr, MAKEINTRESOURCEW(32512));
            wcscpy(nid.szTip, L"XyDesk Remote Host · Kesiapan PC");
            Shell_NotifyIconW(NIM_ADD, &nid);

            SetTimer(hwnd, TIMER_ID, 1000, nullptr);
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
                SetCursor(LoadCursorW(nullptr, MAKEINTRESOURCEW(32649))); // IDC_HAND
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
                AppendMenuW(menu, MF_STRING, IDM_ONMIC, L"Kesiapan Mic & Game");
                AppendMenuW(menu, MF_STRING, IDM_TOGGLE,
                            (st && st->quic_running) ? L"Hentikan UDP lama" : L"Mulai UDP lama");
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
    // Pastikan tidak ada jendela terminal/console yang muncul
    HWND console = GetConsoleWindow();
    if (console) {
        ShowWindow(console, SW_HIDE);
        FreeConsole();
    }

    HINSTANCE inst = GetModuleHandleW(nullptr);
    create_fonts();
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

    const int winW = 860;
    const int winH = 630;
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    int posX = (screenW > winW) ? (screenW - winW) / 2 : CW_USEDEFAULT;
    int posY = (screenH > winH) ? (screenH - winH) / 2 : CW_USEDEFAULT;

    HWND hwnd = CreateWindowExW(
        0, kClassName,
        L"XyDesk Remote Host · PC Stream & Kesiapan PC",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        posX, posY, winW, winH,
        nullptr, nullptr, inst, &state);
    if (!hwnd) return -1;

    ShowWindow(hwnd, SW_SHOW);
    UpdateWindow(hwnd);

    MSG msg{};
    while (state.running && GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    xydesk_quic_server_stop();
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
        } else if (strcmp(argv[i], "--legacy-private-udp") == 0) {
            g_legacy_private_udp = true;
        } else if (strcmp(argv[i], "--no-ui") == 0) {
            no_ui = true;
        }
    }

    xydesk_host_inspect_system(&g_meta);

    if (setup_only) {
        return 0;
    }

    if (g_legacy_private_udp) g_quic_rc = xydesk_quic_server_start(&g_meta);
    xydesk_downloads_watch_start();

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
