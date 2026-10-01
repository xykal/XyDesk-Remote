#define XYDESK_QUIC_EXPORTS
#include "../include/xydesk_quic.h"

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <objbase.h>
#include <mmreg.h>
#include <mmsystem.h>
#include <mmdeviceapi.h>
#include <audioclient.h>
#endif

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

namespace {

constexpr char kDiscoverMagic[] = "XYDESK_QUIC_DISCOVER_V1";
constexpr char kAckMagic[] = "XYDESK_QUIC_ACK_V1";
constexpr char kAudioSubMagic[] = "XYDESK_QUIC_AUDIO_SUB_V1";
constexpr char kAudioPktMagic[4] = {'X', 'Y', 'A', '1'};
constexpr char kMicPktMagic[4] = {'X', 'Y', 'M', '1'};

std::atomic<bool> g_running{false};
std::atomic<uint64_t> g_packets_served{0};
XyQuicHostMetadata g_meta{};

#ifdef _WIN32
SOCKET g_sock = INVALID_SOCKET;
std::mutex g_sub_mutex;
sockaddr_in g_audio_sub_addr{};
uint64_t g_audio_sub_until_ms = 0;

std::mutex g_mic_mutex;
std::vector<int16_t> g_mic_pcm_queue; // 24 kHz mono i16 samples from phone mic

static const CLSID kCLSID_MMDeviceEnumerator = {
    0xBCDE0395, 0xE52F, 0x467C, {0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E}};
static const IID kIID_IMMDeviceEnumerator = {
    0xA95664D2, 0x9614, 0x4F35, {0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6}};
static const IID kIID_IAudioClient = {
    0x1CB9AD4C, 0xDBFA, 0x4C32, {0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2}};
static const IID kIID_IAudioCaptureClient = {
    0xC8ADBD64, 0xE71E, 0x48A0, {0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17}};
static const IID kIID_IAudioRenderClient = {
    0xF294ACFC, 0x3146, 0x4483, {0xA7, 0xBF, 0xAD, 0xDC, 0xA7, 0xC2, 0x60, 0xE2}};
static const GUID kSubFormatFloat = {
    0x00000003, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71}};
static const GUID kSubFormatPcm = {
    0x00000001, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71}};
#endif

std::thread g_worker;
std::thread g_audio_worker;
std::thread g_mic_worker;

size_t BuildQuicAckPacket(uint8_t* out, size_t cap, const uint8_t* rx, size_t rx_len) {
    if (cap < 256 || rx_len < 24) return 0;
    size_t off = 0;
    out[off++] = 0xC0;
    out[off++] = 0x00;
    out[off++] = 0x00;
    out[off++] = 0x00;
    out[off++] = 0x01;
    out[off++] = 8;
    memcpy(out + off, rx + 15, 8);
    off += 8;
    out[off++] = 8;
    const char scid[8] = {'X', 'Y', 'H', 'O', 'S', 'T', '0', '1'};
    memcpy(out + off, scid, 8);
    off += 8;
    out[off++] = 0x00;

    const size_t ack_len = sizeof(kAckMagic);
    memcpy(out + off, kAckMagic, ack_len);
    off += ack_len;

    char json[320] = {};
    snprintf(json, sizeof(json),
             "{\"pcId\":\"%s\",\"hostname\":\"%s\",\"ip\":\"%s\",\"gpu\":\"%s\","
             "\"encoder\":\"%s\",\"rdpPort\":%u,\"quicPort\":%u,\"maxFps\":%u,\"avc444\":true}",
             g_meta.pc_id, g_meta.hostname, g_meta.primary_ip,
             g_meta.gpu_name, g_meta.gpu_encoder,
             static_cast<unsigned>(g_meta.rdp_port),
             static_cast<unsigned>(g_meta.quic_port),
             static_cast<unsigned>(g_meta.max_fps));
    const size_t jlen = strlen(json) + 1;
    if (off + jlen <= cap) {
        memcpy(out + off, json, jlen);
        off += jlen;
    }
    return off;
}

#ifdef _WIN32
bool IsFloatFormat(const WAVEFORMATEX* fmt) {
    if (!fmt) return false;
    if (fmt->wFormatTag == WAVE_FORMAT_IEEE_FLOAT) return true;
    if (fmt->wFormatTag == WAVE_FORMAT_EXTENSIBLE && fmt->cbSize >= 22) {
        const auto* ext = reinterpret_cast<const WAVEFORMATEXTENSIBLE*>(fmt);
        return memcmp(&ext->SubFormat, &kSubFormatFloat, sizeof(GUID)) == 0;
    }
    return false;
}

float ReadSampleNormalized(const uint8_t* frame_ptr, int ch, int total_ch, int bits, bool is_float) {
    if (ch >= total_ch) ch = 0;
    const int bytes_per_sample = bits / 8;
    const uint8_t* p = frame_ptr + ch * bytes_per_sample;
    if (is_float && bits == 32) {
        float v = 0.0f;
        memcpy(&v, p, sizeof(float));
        return std::clamp(v, -1.0f, 1.0f);
    }
    if (bits == 16) {
        int16_t v = 0;
        memcpy(&v, p, sizeof(int16_t));
        return static_cast<float>(v) / 32768.0f;
    }
    if (bits == 24) {
        int32_t v = static_cast<int32_t>(p[0]) |
                    (static_cast<int32_t>(p[1]) << 8) |
                    (static_cast<int32_t>(p[2]) << 16);
        if (v & 0x800000) v |= ~0xFFFFFF;
        return static_cast<float>(v) / 8388608.0f;
    }
    if (bits == 32) {
        int32_t v = 0;
        memcpy(&v, p, sizeof(int32_t));
        return static_cast<float>(v) / 2147483648.0f;
    }
    return 0.0f;
}

void AudioLoopbackThread() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    uint16_t seq = 0;
    constexpr int kTargetRate = 24000;
    constexpr int kPacketFrames = 240; // 10 ms @ 24 kHz stereo = 960 bytes PCM

    while (g_running.load()) {
        bool has_sub = false;
        {
            std::lock_guard<std::mutex> lk(g_sub_mutex);
            has_sub = (GetTickCount64() < g_audio_sub_until_ms);
        }
        if (!has_sub) {
            Sleep(100);
            continue;
        }

        IMMDeviceEnumerator* enumerator = nullptr;
        if (FAILED(CoCreateInstance(kCLSID_MMDeviceEnumerator, nullptr, CLSCTX_ALL,
                                    kIID_IMMDeviceEnumerator,
                                    reinterpret_cast<void**>(&enumerator))) ||
            !enumerator) {
            Sleep(500);
            continue;
        }

        IMMDevice* device = nullptr;
        if (FAILED(enumerator->GetDefaultAudioEndpoint(eRender, eMultimedia, &device)) || !device) {
            enumerator->Release();
            Sleep(500);
            continue;
        }

        // Keepalive render client so virtual endpoints (CABLE Input) tick continuously
        IAudioClient* ka_client = nullptr;
        if (SUCCEEDED(device->Activate(kIID_IAudioClient, CLSCTX_ALL, nullptr,
                                       reinterpret_cast<void**>(&ka_client))) &&
            ka_client) {
            WAVEFORMATEX* ka_fmt = nullptr;
            if (SUCCEEDED(ka_client->GetMixFormat(&ka_fmt)) && ka_fmt) {
                if (SUCCEEDED(ka_client->Initialize(AUDCLNT_SHAREMODE_SHARED, 0, 1000000, 0,
                                                    ka_fmt, nullptr))) {
                    ka_client->Start();
                }
                CoTaskMemFree(ka_fmt);
            }
        }

        IAudioClient* client = nullptr;
        WAVEFORMATEX* mix = nullptr;
        IAudioCaptureClient* capture = nullptr;
        if (FAILED(device->Activate(kIID_IAudioClient, CLSCTX_ALL, nullptr,
                                    reinterpret_cast<void**>(&client))) ||
            !client || FAILED(client->GetMixFormat(&mix)) || !mix ||
            FAILED(client->Initialize(AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_LOOPBACK,
                                      500000, 0, mix, nullptr)) ||
            FAILED(client->GetService(kIID_IAudioCaptureClient,
                                      reinterpret_cast<void**>(&capture))) ||
            !capture || FAILED(client->Start())) {
            if (capture) capture->Release();
            if (mix) CoTaskMemFree(mix);
            if (client) client->Release();
            if (ka_client) {
                ka_client->Stop();
                ka_client->Release();
            }
            device->Release();
            enumerator->Release();
            Sleep(500);
            continue;
        }

        const int src_rate = static_cast<int>(mix->nSamplesPerSec);
        const int src_ch = static_cast<int>(mix->nChannels);
        const int src_bits = static_cast<int>(mix->wBitsPerSample);
        const int block_align = static_cast<int>(mix->nBlockAlign);
        const bool is_float = IsFloatFormat(mix);

        std::vector<int16_t> out_pcm;
        out_pcm.reserve(kPacketFrames * 2 * 2);
        double resample_pos = 0.0;
        const double step = static_cast<double>(src_rate) / static_cast<double>(kTargetRate);

        while (g_running.load()) {
            sockaddr_in sub_addr{};
            {
                std::lock_guard<std::mutex> lk(g_sub_mutex);
                if (GetTickCount64() >= g_audio_sub_until_ms) break;
                sub_addr = g_audio_sub_addr;
            }

            UINT32 packet_len = 0;
            HRESULT hr = capture->GetNextPacketSize(&packet_len);
            if (FAILED(hr)) break;
            if (packet_len == 0) {
                Sleep(2);
                continue;
            }

            while (packet_len > 0 && g_running.load()) {
                BYTE* data = nullptr;
                UINT32 frames = 0;
                DWORD flags = 0;
                if (FAILED(capture->GetBuffer(&data, &frames, &flags, nullptr, nullptr))) {
                    hr = E_FAIL;
                    break;
                }
                const bool silent = (flags & AUDCLNT_BUFFERFLAGS_SILENT) != 0 || !data;
                while (resample_pos < static_cast<double>(frames)) {
                    const int f_idx = static_cast<int>(resample_pos);
                    float l = 0.0f;
                    float r = 0.0f;
                    if (!silent) {
                        const uint8_t* fp = data + f_idx * block_align;
                        l = ReadSampleNormalized(fp, 0, src_ch, src_bits, is_float);
                        r = ReadSampleNormalized(fp, src_ch > 1 ? 1 : 0, src_ch, src_bits, is_float);
                    }
                    out_pcm.push_back(static_cast<int16_t>(std::clamp(l, -1.0f, 1.0f) * 32767.0f));
                    out_pcm.push_back(static_cast<int16_t>(std::clamp(r, -1.0f, 1.0f) * 32767.0f));

                    if (out_pcm.size() >= static_cast<size_t>(kPacketFrames * 2)) {
                        uint8_t pkt[8 + kPacketFrames * 4];
                        memcpy(pkt, kAudioPktMagic, 4);
                        pkt[4] = static_cast<uint8_t>(seq & 0xFF);
                        pkt[5] = static_cast<uint8_t>((seq >> 8) & 0xFF);
                        pkt[6] = 240; // 240 * 100 = 24000 Hz
                        pkt[7] = 2;   // 2 channels
                        memcpy(pkt + 8, out_pcm.data(), kPacketFrames * 4);
                        ++seq;
                        sendto(g_sock, reinterpret_cast<const char*>(pkt), sizeof(pkt), 0,
                               reinterpret_cast<const sockaddr*>(&sub_addr), sizeof(sub_addr));
                        out_pcm.clear();
                    }
                    resample_pos += step;
                }
                resample_pos -= static_cast<double>(frames);
                if (resample_pos < 0.0) resample_pos = 0.0;
                capture->ReleaseBuffer(frames);
                if (FAILED(capture->GetNextPacketSize(&packet_len))) {
                    hr = E_FAIL;
                    break;
                }
            }
            if (FAILED(hr)) break;
        }

        client->Stop();
        capture->Release();
        CoTaskMemFree(mix);
        client->Release();
        if (ka_client) {
            ka_client->Stop();
            ka_client->Release();
        }
        device->Release();
        enumerator->Release();
    }
    CoUninitialize();
}

void MicRenderThread() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    constexpr int kInRate = 24000;

    while (g_running.load()) {
        {
            std::lock_guard<std::mutex> lk(g_mic_mutex);
            if (g_mic_pcm_queue.empty()) {
                // Wait until phone mic packets arrive
            }
        }
        bool has_samples = false;
        {
            std::lock_guard<std::mutex> lk(g_mic_mutex);
            has_samples = !g_mic_pcm_queue.empty();
        }
        if (!has_samples) {
            Sleep(20);
            continue;
        }

        IMMDeviceEnumerator* enumerator = nullptr;
        if (FAILED(CoCreateInstance(kCLSID_MMDeviceEnumerator, nullptr, CLSCTX_ALL,
                                    kIID_IMMDeviceEnumerator,
                                    reinterpret_cast<void**>(&enumerator))) ||
            !enumerator) {
            Sleep(500);
            continue;
        }

        IMMDevice* device = nullptr;
        if (FAILED(enumerator->GetDefaultAudioEndpoint(eRender, eMultimedia, &device)) || !device) {
            enumerator->Release();
            Sleep(500);
            continue;
        }

        IAudioClient* client = nullptr;
        WAVEFORMATEX* mix = nullptr;
        IAudioRenderClient* render = nullptr;
        UINT32 buf_frames = 0;
        if (FAILED(device->Activate(kIID_IAudioClient, CLSCTX_ALL, nullptr,
                                    reinterpret_cast<void**>(&client))) ||
            !client || FAILED(client->GetMixFormat(&mix)) || !mix ||
            FAILED(client->Initialize(AUDCLNT_SHAREMODE_SHARED, 0, 1000000, 0, mix, nullptr)) ||
            FAILED(client->GetBufferSize(&buf_frames)) ||
            FAILED(client->GetService(kIID_IAudioRenderClient,
                                      reinterpret_cast<void**>(&render))) ||
            !render || FAILED(client->Start())) {
            if (render) render->Release();
            if (mix) CoTaskMemFree(mix);
            if (client) client->Release();
            device->Release();
            enumerator->Release();
            Sleep(500);
            continue;
        }

        const int dst_rate = static_cast<int>(mix->nSamplesPerSec);
        const int dst_ch = static_cast<int>(mix->nChannels);
        const int dst_bits = static_cast<int>(mix->wBitsPerSample);
        const int block_align = static_cast<int>(mix->nBlockAlign);
        const bool is_float = IsFloatFormat(mix);
        const int ratio = std::max(1, dst_rate / kInRate);

        uint64_t last_active_ms = GetTickCount64();
        while (g_running.load()) {
            std::vector<int16_t> in_samples;
            {
                std::lock_guard<std::mutex> lk(g_mic_mutex);
                if (!g_mic_pcm_queue.empty()) {
                    const size_t take = std::min<size_t>(g_mic_pcm_queue.size(), 960);
                    in_samples.assign(g_mic_pcm_queue.begin(), g_mic_pcm_queue.begin() + take);
                    g_mic_pcm_queue.erase(g_mic_pcm_queue.begin(), g_mic_pcm_queue.begin() + take);
                    last_active_ms = GetTickCount64();
                }
            }
            if (in_samples.empty()) {
                if (GetTickCount64() - last_active_ms > 5000) break;
                Sleep(4);
                continue;
            }

            UINT32 padding = 0;
            if (FAILED(client->GetCurrentPadding(&padding))) break;
            const UINT32 avail = (buf_frames > padding) ? (buf_frames - padding) : 0;
            const UINT32 out_frames =
                std::min<UINT32>(avail, static_cast<UINT32>(in_samples.size() * ratio));
            if (out_frames == 0) {
                Sleep(2);
                continue;
            }

            BYTE* out_buf = nullptr;
            if (FAILED(render->GetBuffer(out_frames, &out_buf)) || !out_buf) break;
            for (UINT32 f = 0; f < out_frames; ++f) {
                const size_t src_idx = std::min<size_t>(f / ratio, in_samples.size() - 1);
                const float norm = std::clamp(
                    (static_cast<float>(in_samples[src_idx]) / 32768.0f) * 1.6f, -1.0f, 1.0f);
                uint8_t* fp = out_buf + f * block_align;
                for (int c = 0; c < dst_ch; ++c) {
                    if (is_float && dst_bits == 32) {
                        memcpy(fp + c * 4, &norm, 4);
                    } else if (dst_bits == 16) {
                        int16_t s16 = static_cast<int16_t>(norm * 32767.0f);
                        memcpy(fp + c * 2, &s16, 2);
                    } else if (dst_bits == 32) {
                        int32_t s32 = static_cast<int32_t>(norm * 2147483647.0f);
                        memcpy(fp + c * 4, &s32, 4);
                    }
                }
            }
            if (FAILED(render->ReleaseBuffer(out_frames, 0))) break;
        }

        client->Stop();
        render->Release();
        CoTaskMemFree(mix);
        client->Release();
        device->Release();
        enumerator->Release();
    }
    CoUninitialize();
}
#endif

void QuicServerLoop() {
#ifdef _WIN32
    while (g_running.load()) {
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(g_sock, &rfds);
        timeval tv{};
        tv.tv_sec = 0;
        tv.tv_usec = 100000;
        int sel = select(0, &rfds, nullptr, nullptr, &tv);
        if (sel > 0 && FD_ISSET(g_sock, &rfds)) {
            sockaddr_in peer{};
            int peer_len = sizeof(peer);
            uint8_t rx[1500] = {};
            int n = recvfrom(g_sock, reinterpret_cast<char*>(rx), sizeof(rx), 0,
                             reinterpret_cast<sockaddr*>(&peer), &peer_len);
            if (n >= 8 && memcmp(rx, kAudioSubMagic, std::min<size_t>(n, sizeof(kAudioSubMagic) - 1)) == 0) {
                std::lock_guard<std::mutex> lk(g_sub_mutex);
                g_audio_sub_addr = peer;
                g_audio_sub_until_ms = GetTickCount64() + 8000;
                g_packets_served.fetch_add(1);
                continue;
            }
            if (n > 8 && memcmp(rx, kMicPktMagic, 4) == 0) {
                const size_t samples = static_cast<size_t>(n - 8) / sizeof(int16_t);
                if (samples > 0) {
                    const auto* pcm = reinterpret_cast<const int16_t*>(rx + 8);
                    std::lock_guard<std::mutex> lk(g_mic_mutex);
                    if (g_mic_pcm_queue.size() > 24000) {
                        g_mic_pcm_queue.erase(g_mic_pcm_queue.begin(),
                                              g_mic_pcm_queue.begin() + 4800);
                    }
                    g_mic_pcm_queue.insert(g_mic_pcm_queue.end(), pcm, pcm + samples);
                }
                continue;
            }
            if (n >= 24 && (rx[0] & 0xC0) == 0xC0) {
                const char* payload = reinterpret_cast<const char*>(rx + 24);
                const size_t payload_len = static_cast<size_t>(n - 24);
                if (payload_len >= sizeof(kDiscoverMagic) - 1 &&
                    memcmp(payload, kDiscoverMagic, sizeof(kDiscoverMagic) - 1) == 0) {
                    uint8_t tx[512] = {};
                    size_t tx_len = BuildQuicAckPacket(tx, sizeof(tx), rx, static_cast<size_t>(n));
                    if (tx_len > 0) {
                        sendto(g_sock, reinterpret_cast<const char*>(tx),
                               static_cast<int>(tx_len), 0,
                               reinterpret_cast<const sockaddr*>(&peer), peer_len);
                        g_packets_served.fetch_add(1);
                    }
                }
            }
        }
    }
#endif
}

}  // namespace

extern "C" XYDESK_QUIC_API const char* xydesk_quic_dll_version(void) {
    return "xydesk_quic.dll v0.5.25 (Native C++17 · QUIC v1 + WASAPI Loopback/Mic Audio Bridge)";
}

extern "C" XYDESK_QUIC_API int xydesk_quic_server_start(const XyQuicHostMetadata* meta) {
    if (g_running.load()) return 0;
    if (meta) {
        g_meta = *meta;
    }
    if (g_meta.quic_port == 0) g_meta.quic_port = 4433;
    if (g_meta.rdp_port == 0) g_meta.rdp_port = 3389;
    if (g_meta.max_fps == 0) g_meta.max_fps = 120;

#ifdef _WIN32
    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        return -1;
    }
    g_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (g_sock == INVALID_SOCKET) {
        return -2;
    }
    BOOL reuse = TRUE;
    setsockopt(g_sock, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&reuse), sizeof(reuse));

    sockaddr_in bind_addr{};
    bind_addr.sin_family = AF_INET;
    bind_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    bind_addr.sin_port = htons(g_meta.quic_port);
    if (bind(g_sock, reinterpret_cast<const sockaddr*>(&bind_addr), sizeof(bind_addr)) != 0) {
        closesocket(g_sock);
        g_sock = INVALID_SOCKET;
        return -3;
    }
    g_running.store(true);
    g_worker = std::thread(QuicServerLoop);
    g_audio_worker = std::thread(AudioLoopbackThread);
    g_mic_worker = std::thread(MicRenderThread);
    return 0;
#else
    return -1;
#endif
}

extern "C" XYDESK_QUIC_API void xydesk_quic_server_stop(void) {
    if (!g_running.exchange(false)) return;
#ifdef _WIN32
    if (g_sock != INVALID_SOCKET) {
        closesocket(g_sock);
        g_sock = INVALID_SOCKET;
    }
#endif
    if (g_worker.joinable()) g_worker.join();
    if (g_audio_worker.joinable()) g_audio_worker.join();
    if (g_mic_worker.joinable()) g_mic_worker.join();
#ifdef _WIN32
    WSACleanup();
#endif
}

extern "C" XYDESK_QUIC_API uint64_t xydesk_quic_packets_served(void) {
    return g_packets_served.load();
}
