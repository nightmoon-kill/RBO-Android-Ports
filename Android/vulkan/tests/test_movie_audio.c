#include "movie_audio.h"
#include <stdio.h>
#include <math.h>
#include <stdint.h>

static uint64_t samples;
static double energy;
static float peak;
static void collect(const float *pcm, int frames) {
    for (int i=0;i<frames*2;i++) {
        if (!isfinite(pcm[i])) { peak=1000; return; }
        if (fabsf(pcm[i])>peak) peak=fabsf(pcm[i]);
        energy+=pcm[i]*pcm[i]; samples++;
    }
}
int main(int argc, char **argv) {
    if (argc!=2) return 2;
    MovieAudio *audio=movie_audio_open(argv[1]);
    if (!audio) return 3;
    for (int i=0;i<5000;i++) movie_audio_pump(audio,i*.02,1,collect);
    printf("Opening: frames=%llu duration=%.3f peak=%.4f rms=%.4f\n",
           (unsigned long long)(samples/2),samples/88200.0,peak,sqrt(energy/samples));
    if (samples<88200*90 || samples>88200*100 || peak<.01 || peak>2) return 4;
    uint64_t full=samples;
    movie_audio_pump(audio,110,1,collect);
    if (samples!=full) return 5; /* EOF cannot repeat the audio. */
    movie_audio_rewind(audio);
    samples=0;energy=0;peak=0;
    movie_audio_pump(audio,1,0,collect);
    if (samples==0 || peak!=0) return 6;
    movie_audio_rewind(audio);
    samples=0;energy=0;peak=0;
    for (int i=0;i<5000;i++) movie_audio_pump(audio,i*.02,1,collect);
    if (samples!=full || peak<.01) return 7;
    movie_audio_close(audio);
    movie_audio_close(NULL);
    movie_audio_pump(NULL,1,1,collect);
    if (movie_audio_open("nonexistent-opening.mpg")) return 8;
    puts("Opening audio, EOF, mute, rewind and missing-file checks passed.");
    return 0;
}
