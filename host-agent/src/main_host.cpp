#include "../include/xydesk_host_core.h"
#include "../include/xydesk_quic.h"

#ifdef _WIN32
#include <windows.h>
#endif

#include <cstdio>
#include <cstring>

int main(int argc, char** argv) {
    bool setup_only = false;
    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "--setup-only") == 0 || strcmp(argv[i], "--version") == 0) {
            setup_only = true;
        }
    }

    XyQuicHostMetadata meta{};
    xydesk_host_inspect_system(&meta);
    xydesk_host_apply_windows_policies();

    printf("================================================================\n");
    printf("  XyDesk Remote Host Agent — XyVerse Technology Global\n");
    printf("================================================================\n");
    printf("  Core Engine : %s\n", xydesk_host_core_version());
    printf("  QUIC Engine : %s\n", xydesk_quic_dll_version());
    printf("----------------------------------------------------------------\n");
    printf("  Hostname    : %s\n", meta.hostname);
    printf("  IPv4 LAN    : %s (RDP TCP/UDP :%u | QUIC UDP :%u)\n",
           meta.primary_ip, meta.rdp_port, meta.quic_port);
    printf("  GPU Adapter : %s [%s · AVC 4:4:4 + ClearType Ready]\n",
           meta.gpu_name, meta.gpu_encoder);
    printf("  ID PC (10d) : %s\n", meta.pc_id);
    printf("================================================================\n");

    if (setup_only) {
        printf("[OK] Kebijakan Windows RDP + AVC444 + ClearType + QUIC selesai diterapkan.\n");
        return 0;
    }

    int rc = xydesk_quic_server_start(&meta);
    if (rc != 0) {
        printf("[WARN] Gagal mengikat port QUIC UDP %u (kode %d). Pastikan berjalan sebagai Admin.\n",
               meta.quic_port, rc);
    } else {
        printf("[READY] XyDesk Native QUIC v1 Responder aktif di UDP :%u.\n", meta.quic_port);
        printf("        Buka XyDesk Remote di Android -> Koneksi PC -> Scan PC di Wi-Fi\n");
        printf("        atau masukkan ID PC: %s\n", meta.pc_id);
        printf("        Tekan Ctrl+C untuk keluar.\n");
    }

#ifdef _WIN32
    while (true) {
        Sleep(2000);
    }
#endif
    xydesk_quic_server_stop();
    return 0;
}
