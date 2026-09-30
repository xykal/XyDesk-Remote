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
