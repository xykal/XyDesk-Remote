#ifndef XYDESK_QUIC_DLL_H
#define XYDESK_QUIC_DLL_H

#include <stdint.h>

#ifdef _WIN32
#ifdef XYDESK_QUIC_EXPORTS
#define XYDESK_QUIC_API __declspec(dllexport)
#else
#define XYDESK_QUIC_API __declspec(dllimport)
#endif
#else
#define XYDESK_QUIC_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct XyQuicHostMetadata {
    char pc_id[32];
    char hostname[128];
    char primary_ip[64];
    char gpu_name[128];
    char gpu_encoder[32];
    uint16_t rdp_port;
    uint16_t quic_port;
    uint32_t max_fps;
} XyQuicHostMetadata;

/*
 * Kontrak jembatan audio/mic (lihat docs/AUDIO_BRIDGE.md):
 *  - HP kirim "XYDESK_QUIC_AUDIO_SUB_V1" ke UDP :4433 tiap 2 detik (subscribe).
 *  - Host kirim "XYA1" + seq:u16 + frames:u16 + PCM16 stereo 24 kHz (suara PC
 *    hasil WASAPI loopback; endpoint dipilih otomatis + rotasi bila senyap).
 *  - HP kirim "XYM1" + seq:u16 + frames:u16 + PCM16 mono 24 kHz (mic HP);
 *    host merender ke endpoint "CABLE Input"/"XyDesk Virtual Microphone"
 *    (fallback: default render) supaya jadi input mic virtual di PC.
 *  - Host kirim "XYST1" (16 B + teks) tiap 1 detik: flags, level mic, jumlah
 *    paket, lalu "audio=<endpoint>|mic=<endpoint>".
 */

/** Returns the native QUIC DLL version & protocol summary. */
XYDESK_QUIC_API const char* xydesk_quic_dll_version(void);

/** Starts the background UDP/QUIC v1 responder thread on quic_port (default 4433). */
XYDESK_QUIC_API int xydesk_quic_server_start(const XyQuicHostMetadata* meta);

/** Stops the background UDP/QUIC v1 responder thread. */
XYDESK_QUIC_API void xydesk_quic_server_stop(void);

/** Returns total number of QUIC Initial/Datagram probes served. */
XYDESK_QUIC_API uint64_t xydesk_quic_packets_served(void);

#ifdef __cplusplus
}
#endif

#endif /* XYDESK_QUIC_DLL_H */
