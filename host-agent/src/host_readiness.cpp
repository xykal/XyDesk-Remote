#include "../include/host_readiness.h"
#ifdef _WIN32
#include <mmdeviceapi.h>
// Keep the Windows property key local: MinGW's uuid archive does not export it.
#include <propsys.h>
#include <propvarutil.h>
#include <tlhelp32.h>
#include <shellapi.h>
#include <algorithm>
#include <cwctype>
#include <sstream>

namespace {
constexpr PROPERTYKEY kDeviceFriendlyName = {
    {0xa45c254e, 0xdf1c, 0x4efd, {0x80, 0x20, 0x67, 0xd1, 0x46, 0xa8, 0x50, 0xe0}}, 14};
constexpr int REPORT = 101, REFRESH = 102, SOUND = 103, PRIVACY = 104, DRIVER = 105;
const wchar_t* kReadinessClass = L"XyDeskReadOnlyReadiness";
std::wstring friendly(IMMDevice* device) {
    IPropertyStore* store = nullptr;
    if (FAILED(device->OpenPropertyStore(STGM_READ, &store))) return L"(nama tidak tersedia)";
    PROPVARIANT value{};
    PropVariantInit(&value);
    std::wstring name = L"(nama tidak tersedia)";
    if (SUCCEEDED(store->GetValue(kDeviceFriendlyName, &value)) && value.vt == VT_LPWSTR && value.pwszVal) name = value.pwszVal;
    PropVariantClear(&value);
    store->Release();
    return name;
}
void append_endpoints(IMMDeviceEnumerator* enumerator, EDataFlow flow, std::wostringstream& out) {
    out << (flow == eCapture ? L"\r\nINPUT/CAPTURE AKTIF\r\n" : L"\r\nOUTPUT/RENDER AKTIF\r\n");
    IMMDeviceCollection* devices = nullptr;
    const HRESULT hr = enumerator->EnumAudioEndpoints(flow, DEVICE_STATE_ACTIVE, &devices);
    if (FAILED(hr) || !devices) { out << L"Gagal enumerasi: 0x" << std::hex << static_cast<unsigned long>(hr) << std::dec << L"\r\n"; return; }
    UINT count = 0;
    devices->GetCount(&count);
    out << L"Jumlah: " << count << L"\r\n";
    for (UINT i = 0; i < count && i < 64; ++i) {
        IMMDevice* device = nullptr;
        if (SUCCEEDED(devices->Item(i, &device)) && device) { out << L" - " << friendly(device) << L"\r\n"; device->Release(); }
    }
    if (count > 64) out << L"Daftar dipotong pada 64 endpoint.\r\n";
    devices->Release();
}
void layout(HWND window) {
    RECT r{}; GetClientRect(window, &r);
    const int width = std::max(200, static_cast<int>(r.right));
    const int height = std::max(180, static_cast<int>(r.bottom));
    MoveWindow(GetDlgItem(window, REPORT), 12, 12, width - 24, height - 108, TRUE);
    const int buttonWidth = (width - 36) / 2;
    MoveWindow(GetDlgItem(window, REFRESH), 12, height - 84, buttonWidth, 30, TRUE);
    MoveWindow(GetDlgItem(window, SOUND), 24 + buttonWidth, height - 84, buttonWidth, 30, TRUE);
    MoveWindow(GetDlgItem(window, PRIVACY), 12, height - 44, buttonWidth, 30, TRUE);
    MoveWindow(GetDlgItem(window, DRIVER), 24 + buttonWidth, height - 44, buttonWidth, 30, TRUE);
}
LRESULT CALLBACK ReadinessProc(HWND window, UINT message, WPARAM wp, LPARAM lp) {
    switch (message) {
        case WM_CREATE: {
            HWND edit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"Memeriksa perangkat Windows…",
                WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_MULTILINE | ES_READONLY | ES_AUTOVSCROLL,
                0, 0, 0, 0, window, reinterpret_cast<HMENU>(REPORT), nullptr, nullptr);
            SendMessageW(edit, EM_SETLIMITTEXT, 65535, 0);
            SendMessageW(edit, WM_SETFONT, reinterpret_cast<WPARAM>(GetStockObject(DEFAULT_GUI_FONT)), TRUE);
            const int ids[] = {REFRESH, SOUND, PRIVACY, DRIVER};
            const wchar_t* labels[] = {L"Periksa ulang (tanpa mengubah PC)", L"Buka pengaturan suara", L"Buka privasi mikrofon", L"Situs resmi driver audio"};
            for (int i = 0; i < 4; ++i) {
                HWND button = CreateWindowExW(0, L"BUTTON", labels[i], WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_PUSHBUTTON,
                    0, 0, 0, 0, window, reinterpret_cast<HMENU>(static_cast<INT_PTR>(ids[i])), nullptr, nullptr);
                SendMessageW(button, WM_SETFONT, reinterpret_cast<WPARAM>(GetStockObject(DEFAULT_GUI_FONT)), TRUE);
            }
            PostMessageW(window, WM_COMMAND, REFRESH, 0);
            return 0;
        }
        case WM_SIZE: layout(window); return 0;
        case WM_GETMINMAXINFO: {
            auto* limits = reinterpret_cast<MINMAXINFO*>(lp);
            limits->ptMinTrackSize = {480, 400}; return 0;
        }
        case WM_COMMAND:
            switch (LOWORD(wp)) {
                case REFRESH: SetWindowTextW(GetDlgItem(window, REPORT), xydesk_host_readiness_report().c_str()); return 0;
                case SOUND: ShellExecuteW(window, L"open", L"ms-settings:sound", nullptr, nullptr, SW_SHOWNORMAL); return 0;
                case PRIVACY: ShellExecuteW(window, L"open", L"ms-settings:privacy-microphone", nullptr, nullptr, SW_SHOWNORMAL); return 0;
                case DRIVER:
                    if (MessageBoxW(window, L"Buka situs resmi VB-Audio di browser? Tidak ada unduhan/instalasi otomatis. Periksa lisensi, tanda tangan installer dan kebutuhan reboot sebelum memasang driver.", L"Persetujuan membuka situs driver", MB_YESNO | MB_ICONINFORMATION) == IDYES)
                        ShellExecuteW(window, L"open", L"https://vb-audio.com/Cable/", nullptr, nullptr, SW_SHOWNORMAL);
                    return 0;
            }
            break;
        case WM_CLOSE: DestroyWindow(window); return 0;
    }
    return DefWindowProcW(window, message, wp, lp);
}
}

std::wstring xydesk_host_readiness_report() {
    std::wostringstream out;
    out << L"XYDESK · KESIAPAN MIC & GAME (PEMERIKSAAN LOKAL)\r\n"
        << L"Tidak mengubah registry, firewall, password, driver atau proteksi game.\r\n\r\n"
        << L"Jenis sesi menurut Windows: " << (GetSystemMetrics(SM_REMOTESESSION) ? L"RDP/remote" : L"bukan RDP") << L"\r\n"
        << L"Bukan RDP tidak membuktikan PC fisik atau dukungan anti-cheat.\r\n";
    HANDLE token = nullptr;
    TOKEN_ELEVATION elevation{}; DWORD returned = 0;
    const bool queried = OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token) != FALSE;
    const bool known = queried && GetTokenInformation(token, TokenElevation, &elevation, sizeof(elevation), &returned);
    if (token) CloseHandle(token);
    out << L"Elevasi: " << (!known ? L"tidak diketahui" : elevation.TokenIsElevated ? L"administrator" : L"pengguna biasa") << L"\r\n";
    const HRESULT init = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool comAvailable = SUCCEEDED(init) || init == RPC_E_CHANGED_MODE;
    IMMDeviceEnumerator* enumerator = nullptr;
    const HRESULT hr = comAvailable ? CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_INPROC_SERVER,
        __uuidof(IMMDeviceEnumerator), reinterpret_cast<void**>(&enumerator)) : init;
    if (SUCCEEDED(hr) && enumerator) {
        append_endpoints(enumerator, eRender, out);
        append_endpoints(enumerator, eCapture, out);
        IMMDevice* device = nullptr;
        if (SUCCEEDED(enumerator->GetDefaultAudioEndpoint(eCapture, eCommunications, &device)) && device) {
            out << L"\r\nInput komunikasi default: " << friendly(device) << L"\r\n"; device->Release();
        } else out << L"\r\nInput komunikasi default: tidak tersedia.\r\n";
        enumerator->Release();
    } else out << L"\r\nInventaris audio gagal: 0x" << std::hex << static_cast<unsigned long>(hr) << std::dec << L"\r\n";
    if (SUCCEEDED(init)) CoUninitialize();
    out << L"\r\nEndpoint aktif hanya bukti inventaris Windows; BELUM membuktikan suara HP sampai atau game memilih input ini.\r\n"
        << L"Mic bridge membutuhkan OUTPUT virtual yang terhubung ke INPUT/capture virtual. Level dan routing harus diuji.\r\n\r\nPROSES GAME TERLIHAT (BUKAN BUKTI BERHASIL BERMAIN)\r\n";
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32W entry{}; entry.dwSize = sizeof(entry);
        bool any = false;
        if (Process32FirstW(snapshot, &entry)) do {
            std::wstring name(entry.szExeFile);
            std::transform(name.begin(), name.end(), name.begin(), [](wchar_t c) { return static_cast<wchar_t>(std::towlower(c)); });
            if (name == L"gta_sa.exe" || name == L"samp.exe" || name == L"robloxplayerbeta.exe" || name == L"robloxplayerlauncher.exe") {
                any = true; out << L" - " << name << L" · PID " << entry.th32ProcessID << L"\r\n";
            }
        } while (Process32NextW(snapshot, &entry));
        if (!any) out << L"Tidak ada proses yang cocok saat pemeriksaan.\r\n";
        CloseHandle(snapshot);
    } else out << L"Gagal membaca snapshot proses.\r\n";
    out << L"\r\nSA-MP: jika Connect tidak memunculkan GTA, periksa path GTA/versi launcher, proses, Event Viewer dan crash log. Daftar server bukan uji peluncuran.\r\n"
        << L"Roblox: bila lingkungan/VM ditolak, gunakan lingkungan yang didukung. Tidak ada penyamaran VM atau bypass anti-cheat.\r\n"
        << L"GPU/encoder, capture frame, kompatibilitas game dan autentikasi transport belum diuji oleh panel ini.\r\n";
    return out.str();
}
void xydesk_show_readiness(HWND owner) {
    WNDCLASSW klass{};
    klass.lpfnWndProc = ReadinessProc; klass.hInstance = GetModuleHandleW(nullptr);
    klass.lpszClassName = kReadinessClass; klass.hCursor = LoadCursorW(nullptr, MAKEINTRESOURCEW(32512));
    klass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
    RegisterClassW(&klass);
    HWND window = CreateWindowExW(WS_EX_CONTROLPARENT, kReadinessClass, L"XyDesk · Kesiapan Mic & Game",
        WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 720, 640, owner, nullptr, klass.hInstance, nullptr);
    if (window) ShowWindow(window, SW_SHOW);
}
#endif
