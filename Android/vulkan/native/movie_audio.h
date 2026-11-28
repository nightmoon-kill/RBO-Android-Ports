#pragma once
typedef struct MovieAudio MovieAudio;
typedef void (*MovieAudioSink)(const float *stereo, int frames);
MovieAudio *movie_audio_open(const char *path);
void movie_audio_close(MovieAudio *audio);
void movie_audio_rewind(MovieAudio *audio);
void movie_audio_pump(MovieAudio *audio, double seconds, float gain, MovieAudioSink sink);
