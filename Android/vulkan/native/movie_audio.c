/* MPEG Layer I fallback for opening.mpg; PCM joins the game's existing mixer. */
#include "movie_audio.h"
#include <stdio.h>
#include "pl_mpeg.h"
#define MINIMP3_IMPLEMENTATION
#define MINIMP3_FLOAT_OUTPUT
#include "../vendor/minimp3/minimp3.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

struct MovieAudio {
    plm_demux_t *demux;
    mp3dec_t decoder;
    unsigned char compressed[65536];
    int bytes, ended, clock_started;
    double position, start, video_start;
};
MovieAudio *movie_audio_open(const char *path) {
    plm_buffer_t *buffer = plm_buffer_create_with_filename(path);
    if (!buffer) return NULL;
    MovieAudio *a = calloc(1, sizeof *a);
    if (!a) { plm_buffer_destroy(buffer); return NULL; }
    a->demux = plm_demux_create(buffer, 1);
    if (!a->demux) { plm_buffer_destroy(buffer); free(a); return NULL; }
    a->video_start = plm_demux_get_start_time(a->demux, PLM_DEMUX_PACKET_VIDEO_1);
    plm_demux_rewind(a->demux);
    mp3dec_init(&a->decoder);
    return a;
}
void movie_audio_close(MovieAudio *a) {
    if (!a) return;
    plm_demux_destroy(a->demux);
    free(a);
}
void movie_audio_rewind(MovieAudio *a) {
    if (!a) return;
    plm_demux_rewind(a->demux);
    mp3dec_init(&a->decoder);
    a->bytes = a->ended = a->clock_started = 0;
    a->position = a->start = 0;
}
static void fill(MovieAudio *a) {
    /* Preserve frames crossing PES packet boundaries; bound corrupt streams. */
    for (int packets=0; !a->ended && a->bytes < 16384 && packets < 4096; ++packets) {
        plm_packet_t *p = plm_demux_decode(a->demux);
        if (!p) { a->ended = 1; break; }
        if (p->type != PLM_DEMUX_PACKET_AUDIO_1) continue;
        if (!a->clock_started) {
            a->clock_started = 1;
            a->start = p->pts >= 0 && a->video_start >= 0 ? fmax(0, p->pts-a->video_start) : 0;
        }
        if (p->length > sizeof a->compressed-(size_t)a->bytes) { a->ended=1; break; }
        memcpy(a->compressed+a->bytes, p->data, p->length);
        a->bytes += (int)p->length;
    }
}
void movie_audio_pump(MovieAudio *a, double seconds, float gain, MovieAudioSink sink) {
    if (!a || !sink || !isfinite(seconds) || seconds < 0) return;
    float pcm[MINIMP3_MAX_SAMPLES_PER_FRAME], stereo[MINIMP3_MAX_SAMPLES_PER_FRAME*2];
    /* At most ~0.1 seconds is normally requested per pump. Bound damaged input. */
    for (int frames=0; frames<256 && a->start+a->position < seconds; ++frames) {
        if (a->bytes < 4096) fill(a);
        if (!a->bytes) break;
        mp3dec_frame_info_t info;
        int n = mp3dec_decode_frame(&a->decoder, a->compressed, a->bytes, pcm, &info);
        if (info.frame_bytes <= 0 || info.frame_bytes > a->bytes) { a->bytes=0; continue; }
        a->bytes -= info.frame_bytes;
        memmove(a->compressed,a->compressed+info.frame_bytes,(size_t)a->bytes);
        if (n <= 0 || info.hz < 8000 || info.hz > 48000 || info.channels < 1 || info.channels > 2) continue;
        int count = (int)ceil((a->position+(double)n/info.hz)*44100)- (int)ceil(a->position*44100);
        if (count > (int)(sizeof stereo / sizeof *stereo / 2)) continue;
        for (int i=0;i<count;i++) {
            double source=((ceil(a->position*44100)+i)/44100-a->position)*info.hz;
            int left=(int)fmax(0,fmin(n-1,source)), right=left+1<n?left+1:left;
            float blend=(float)fmax(0,fmin(1,source-left));
            for (int ch=0;ch<2;ch++) {
                int channel=info.channels==1?0:ch;
                float x=pcm[left*info.channels+channel], y=pcm[right*info.channels+channel];
                stereo[2*i+ch]=(x+(y-x)*blend)*gain;
            }
        }
        sink(stereo,count);
        a->position += (double)n/info.hz;
    }
}
