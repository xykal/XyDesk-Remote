"""Host C lifecycle tests using production function bodies and mock OpenSL/WinPR APIs.
Compiles/runs temporary TEST programs only, not Android/NDK code or an APK/host EXE.
Does not verify real devices, OpenSL threading guarantees, ABI or Android linking.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'channels/audin/client/opensles'


def function(source, name):
    match = re.search(r'(?m)^(?:static )?(?:void|UINT|OPENSL_STREAM\*) ' + name + r'\([^;]*?\)\s*\{', source)
    if not match:
        raise AssertionError('Production function not found: ' + name)
    depth = 1
    end = match.end()
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[match.start():end]


COMMON = r'''
#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#define nullptr NULL
#define WLog_Print(...) ((void)0)
#define WLog_ERR(...) ((void)0)
#define WLog_WARN(...) ((void)0)
#define CHANNEL_RC_OK 0
#define ERROR_INVALID_PARAMETER 87
#define ERROR_INTERNAL_ERROR 1359
#define ERROR_ALREADY_EXISTS 183
#define WLOG_DEBUG 0
#define WLOG_ERROR 0
#define TAG "test"
typedef unsigned int UINT;
'''


class NativeAudinLifecycleTest(unittest.TestCase):
    def run_c(self, source):
        compiler = shutil.which(os.environ.get('CC', 'cc'))
        self.assertIsNotNone(compiler, 'A host C compiler is required for lifecycle verification')
        with tempfile.TemporaryDirectory(prefix='audin-unit-') as directory:
            path = Path(directory)
            (path / 'test.c').write_text(source)
            subprocess.run([compiler, '-std=c11', '-Wall', '-Wextra', '-Werror',
                            '-fsanitize=address,undefined', '-fno-omit-frame-pointer', '-no-pie',
                            str(path / 'test.c'), '-o', str(path / 'unit-test')], check=True)
            subprocess.run([str(path / 'unit-test')], check=True,
                           env={**os.environ, 'ASAN_OPTIONS': 'detect_leaks=1:abort_on_error=1'})

    def test_plugin_initial_callback_duplicate_open_failure_and_free(self):
        production = (SOURCE / 'audin_opensl_es.c').read_text()
        device = re.search(r'typedef struct\s*\{.*?\} AudinOpenSLESDevice;', production, re.S).group()
        prefix = COMMON + r'''
typedef uint32_t UINT32;
typedef unsigned char BYTE;
typedef struct { int unused; } IAudinDevice;
typedef struct { int unused; } rdpContext;
typedef struct { int unused; } wLog;
typedef struct { unsigned int nSamplesPerSec, nChannels, wBitsPerSample; } AUDIO_FORMAT;
typedef UINT (*AudinReceive)(const AUDIO_FORMAT*, const BYTE*, size_t, void*);
typedef struct { int unused; } OPENSL_STREAM;
static OPENSL_STREAM stream;
static int opens, closes, deliveries, fail_open;
static void (*capture_callback)(void*, const void*, size_t);
static void* capture_context;
static UINT audin_opensles_close(IAudinDevice* device);
static void setChannelError(rdpContext* context, UINT error, const char* text) {
    (void)context; (void)error; (void)text; assert(0);
}
static OPENSL_STREAM* android_OpenRecDevice(void* context,
        void (*receive)(void*, const void*, size_t), size_t sr, size_t ch, size_t frames, size_t bits) {
    (void)sr; (void)ch; (void)frames; (void)bits;
    const BYTE sample = 17;
    ++opens;
    capture_context = context; capture_callback = receive;
    receive(context, &sample, 1); /* Adversarial callback BEFORE Open returns. */
    return fail_open ? NULL : &stream;
}
static void android_CloseRecDevice(OPENSL_STREAM* value) {
    if (value) {
        const BYTE sample = 17;
        assert(value == &stream);
        ++closes;
        capture_callback(capture_context, &sample, 1); /* Drain before destroy completes. */
    }
    capture_callback = NULL; capture_context = NULL;
}
'''
        functions = '\n'.join(function(production, name) for name in
                              ['audin_receive', 'audin_opensles_free', 'audin_opensles_open', 'audin_opensles_close'])
        suffix = r'''
static UINT deliver(const AUDIO_FORMAT* format, const BYTE* data, size_t size, void* user) {
    (void)format; assert(data && size == 1 && *data == 17 && user == &deliveries);
    ++deliveries; return CHANNEL_RC_OK;
}
int main(void) {
    AudinOpenSLESDevice* device = calloc(1, sizeof(*device));
    assert(device);
    assert(audin_opensles_open((IAudinDevice*)device, deliver, &deliveries) == CHANNEL_RC_OK);
    assert(deliveries == 1 && opens == 1 && closes == 0);
    assert(audin_opensles_open((IAudinDevice*)device, deliver, &deliveries) == ERROR_ALREADY_EXISTS);
    assert(opens == 1 && closes == 0 && device->stream == &stream);
    assert(audin_opensles_close((IAudinDevice*)device) == CHANNEL_RC_OK);
    assert(closes == 1 && deliveries == 2 && !device->receive && !device->user_data && !device->stream);
    const BYTE sample = 17;
    audin_receive(device, &sample, 1); /* Closed plugin must not dereference a null callback. */
    assert(deliveries == 2);
    assert(audin_opensles_close((IAudinDevice*)device) == CHANNEL_RC_OK && closes == 1);
    fail_open = 1;
    assert(audin_opensles_open((IAudinDevice*)device, deliver, &deliveries) == ERROR_INTERNAL_ERROR);
    assert(!device->stream && !device->receive && !device->user_data);
    fail_open = 0;
    assert(audin_opensles_open((IAudinDevice*)device, deliver, &deliveries) == CHANNEL_RC_OK);
    assert(audin_opensles_free((IAudinDevice*)device) == CHANNEL_RC_OK);
    assert(closes == 2 && !capture_callback && !capture_context);
    return 0;
}
'''
        self.run_c(prefix + device + '\n' + functions + suffix)

    def test_recorder_start_failures_and_destroy_before_buffer_free(self):
        production = (SOURCE / 'opensl_io.c').read_text()
        prefix = COMMON + r'''
typedef unsigned int SLresult;
typedef void (*opensl_receive_t)(void*, const void*, size_t);
#define SL_RESULT_SUCCESS 0
#define SL_RECORDSTATE_RECORDING 3
#define SL_RECORDSTATE_STOPPED 1
typedef struct { SLresult (*Enqueue)(void*, const void*, size_t); } Queue;
typedef struct { SLresult (*SetRecordState)(void*, int); } Recorder;
typedef Queue** SLAndroidSimpleBufferQueueItf;
typedef struct { void* data; size_t size; } queue_element;
typedef struct {
    void* context;
    void (*receive)(void*, const void*, size_t);
    size_t inchannels, sr, buffersize, bits_per_sample;
    queue_element *next, *prep;
    SLAndroidSimpleBufferQueueItf recorderBufferQueue;
    Recorder** recorderRecord;
} OPENSL_STREAM;
static void android_CloseRecDevice(OPENSL_STREAM* p);
static void bqRecorderCallback(SLAndroidSimpleBufferQueueItf bq, void* context);
static int enqueue_calls, fail_enqueue_at, fail_start, ever_started, stopped, destroyed, freed, deliveries;
static SLresult enqueue(void* self, const void* data, size_t size) {
    (void)self; assert(!destroyed && data && size);
    ++enqueue_calls; return enqueue_calls == fail_enqueue_at ? 1 : SL_RESULT_SUCCESS;
}
static SLresult set_state(void* self, int state) {
    (void)self;
    if (state == SL_RECORDSTATE_STOPPED) { stopped = 1; return SL_RESULT_SUCCESS; }
    assert(state == SL_RECORDSTATE_RECORDING);
    if (fail_start) return 1;
    ever_started = 1; return SL_RESULT_SUCCESS;
}
static Queue queue = { enqueue }; static Queue* queue_itf = &queue;
static Recorder recorder = { set_state }; static Recorder* recorder_itf = &recorder;
static SLresult openSLCreateEngine(OPENSL_STREAM* p) { (void)p; return SL_RESULT_SUCCESS; }
static SLresult openSLRecOpen(OPENSL_STREAM* p) {
    p->recorderBufferQueue = &queue_itf; p->recorderRecord = &recorder_itf;
    return SL_RESULT_SUCCESS;
}
static void openSLDestroyEngine(OPENSL_STREAM* p) {
    assert(!freed && stopped); /* Queued data MUST remain alive through destroy. */
    if (ever_started) bqRecorderCallback(p->recorderBufferQueue, p);
    destroyed = 1;
}
static queue_element* opensles_queue_element_new(size_t size) {
    queue_element* q = calloc(1, sizeof(*q)); assert(q);
    q->data = calloc(1, size); assert(q->data); q->size = size; return q;
}
static void opensles_queue_element_free(queue_element* q) {
    if (q) { assert(destroyed); ++freed; free(q->data); free(q); }
}
static void receive(void* context, const void* data, size_t size) {
    assert(context && data && size && !freed); ++deliveries;
}
'''
        functions = '\n'.join(function(production, name) for name in
                              ['android_OpenRecDevice', 'android_CloseRecDevice', 'bqRecorderCallback'])
        suffix = r'''
static void reset(void) {
    enqueue_calls = fail_enqueue_at = fail_start = ever_started = stopped = destroyed = freed = deliveries = 0;
}
int main(void) {
    int context = 1;
    assert(!android_OpenRecDevice(&context, receive, 0, 1, 480, 16));
    assert(!android_OpenRecDevice(&context, receive, 48000, 0, 480, 16));
    assert(!android_OpenRecDevice(&context, receive, 48000, 1, 0, 16));
    for (int mode = 0; mode < 4; ++mode) {
        reset();
        if (mode == 1 || mode == 2) fail_enqueue_at = mode;
        if (mode == 3) fail_start = 1;
        OPENSL_STREAM* p = android_OpenRecDevice(&context, receive, 48000, 1, 480, 16);
        if (mode == 0) {
            assert(p && enqueue_calls == 2 && ever_started);
            android_CloseRecDevice(p);
            assert(deliveries == 1);
        } else { assert(!p && !ever_started && !deliveries); }
        assert(stopped && destroyed && freed == 2);
    }
    android_CloseRecDevice(NULL);
    return 0;
}
'''
        # The OpenSL callback signature includes an unused interface argument in production.
        self.run_c(prefix + functions + suffix)


if __name__ == '__main__':
    unittest.main()
