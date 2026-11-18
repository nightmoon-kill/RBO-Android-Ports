package com.nakpaaiyaa.rbo;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;

final class GameAudio {
    private static final int RATE = 44100;
    private static final int CHANNELS = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int PCM = AudioFormat.ENCODING_PCM_16BIT;
    private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build();

    private final AudioManager manager;
    private final AudioFocusRequest focusRequest;
    private final Output output = new Output();
    private boolean active, hasFocus, musicReady;
    private MusicRequest musicRequest;
    private MediaPlayer player;

    GameAudio(Context context) {
        manager = context.getSystemService(AudioManager.class);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(ATTRIBUTES)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(change -> {
                if (!active) return;
                hasFocus = change > 0;
                NativeCore.audioLog("Audio focus change=" + change);
                updatePlayback();
            })
            .build();
        output.start();
    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (active) {
            int focus = manager.requestAudioFocus(focusRequest);
            hasFocus = focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            NativeCore.audioLog("Audio focus=" + focus + ", media volume="
                + manager.getStreamVolume(AudioManager.STREAM_MUSIC) + "/"
                + manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } else {
            hasFocus = false;
            manager.abandonAudioFocusRequest(focusRequest);
        }
        updatePlayback();
    }

    void close() {
        setActive(false);
        releaseMusic();
        output.shutdown();
    }

    void pollMusic() {
        MusicRequest request = NativeCore.pollMusic();
        if (request == null) return;
        boolean changed = musicRequest == null || musicRequest.generation != request.generation
            || !musicRequest.path.equals(request.path);
        musicRequest = request;
        if (changed) {
            releaseMusic();
            if (!request.path.isEmpty()) openMusic(request.path);
        }
        updatePlayback();
    }

    private void updatePlayback() {
        boolean audible = active && hasFocus;
        output.setAudible(audible);
        if (!musicReady) return;
        try {
            float gain = musicRequest.volume <= -10000 ? 0 :
                (float) Math.pow(10, Math.min(0, musicRequest.volume) / 2000.0);
            player.setVolume(gain, gain);
            if (audible && musicRequest.playing) {
                if (!player.isPlaying()) player.start();
            } else if (player.isPlaying()) {
                player.pause();
            }
        } catch (IllegalStateException failure) {
            NativeCore.audioLog("DirectShow playback failed: " + failure);
            releaseMusic();
        }
    }

    private void openMusic(String path) {
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(ATTRIBUTES);
            player.setOnPreparedListener(ready -> {
                if (ready != player) return;
                musicReady = true;
                NativeCore.audioLog("DirectShow track ready");
                updatePlayback();
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (failed == player) {
                    NativeCore.audioLog("DirectShow decoder error=" + what + "/" + extra);
                    releaseMusic();
                }
                return true;
            });
            player.setDataSource(path);
            player.prepareAsync();
        } catch (IOException | RuntimeException failure) {
            NativeCore.audioLog("DirectShow open failed: " + failure);
            releaseMusic();
        }
    }

    private void releaseMusic() {
        if (player != null) player.release();
        player = null;
        musicReady = false;
    }

    private static AudioTrack createTrack() {
        int minimum = AudioTrack.getMinBufferSize(RATE, CHANNELS, PCM);
        if (minimum <= 0) throw new IllegalStateException("AudioTrack minimum buffer=" + minimum);
        AudioTrack track = new AudioTrack.Builder()
            .setAudioAttributes(ATTRIBUTES)
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                .setChannelMask(CHANNELS).setEncoding(PCM).build())
            .setBufferSizeInBytes(Math.max(minimum, RATE * 4 / 10))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IllegalStateException("AudioTrack not initialized");
        }
        return track;
    }

    // The launcher probe uses the same AudioTrack format and audio route as the game.
    static void playTest(Handler handler, Runnable finished) {
        new Thread(() -> {
            AudioTrack track = null;
            try {
                track = createTrack();
                short[] tone = new short[882];
                int blocks = 35;
                int framesInBlock = tone.length / 2;
                int lastFrame = blocks * framesInBlock - 1;
                track.play();
                for (int block = 0; block < blocks; block++) {
                    for (int i = 0; i < framesInBlock; i++) {
                        int frame = block * framesInBlock + i;
                        double envelope = Math.min(1, Math.min(frame, lastFrame - frame) / 300.0);
                        short sample = (short) (6000 * envelope * Math.sin(2 * Math.PI * 440 * frame / RATE));
                        tone[2 * i] = tone[2 * i + 1] = sample;
                    }
                    if (track.write(tone, 0, tone.length, AudioTrack.WRITE_BLOCKING) < 0) break;
                }
                track.stop();
                SystemClock.sleep(125);
            } catch (RuntimeException failure) {
                android.util.Log.w("RBO", "Audio test failed", failure);
            } finally {
                if (track != null) track.release();
                handler.post(finished);
            }
        }, "rbo-audio-test").start();
    }

    static final class MusicRequest {
        final String path;
        final int volume;
        final boolean playing;
        final int generation;

        MusicRequest(String path, int volume, boolean playing, int generation) {
            this.path = path;
            this.volume = volume;
            this.playing = playing;
            this.generation = generation;
        }
    }

    private static final class Output extends Thread {
        private volatile boolean audible, closed;

        Output() { super("rbo-audio"); }

        synchronized void setAudible(boolean value) {
            audible = value;
            notifyAll();
        }

        synchronized void shutdown() {
            closed = true;
            notifyAll();
        }

        private synchronized boolean awaitAudio() {
            while (!audible && !closed) {
                try { wait(); } catch (InterruptedException ignored) {}
            }
            return !closed;
        }

        @Override public void run() {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            } catch (SecurityException denied) {
                NativeCore.audioLog("Unable to set audio thread priority");
            }
            while (awaitAudio()) {
                try {
                    pump();
                } catch (RuntimeException failure) {
                    NativeCore.audioLog("AudioTrack failed: " + failure);
                }
                if (!closed && audible) SystemClock.sleep(750);
            }
        }

        private void pump() {
            AudioTrack track = createTrack();
            try {
                short[] samples = new short[1024];
                track.write(samples, 0, samples.length, AudioTrack.WRITE_NON_BLOCKING);
                track.play();
                NativeCore.audioLog("AudioTrack started 44100 Hz stereo, bufferFrames=" + track.getBufferSizeInFrames());

                long deadline = SystemClock.elapsedRealtime() + 5000;
                int produced = 0, peak = 0, silentIntervals = 0;
                boolean signalLogged = false;
                while (!closed && audible) {
                    int frames = NativeCore.pullAudio(samples);
                    if (!signalLogged) {
                        produced += frames;
                        for (int i = 0; i < frames * 2; i++) peak = Math.max(peak, Math.abs((int) samples[i]));
                        if (peak > 0 || SystemClock.elapsedRealtime() >= deadline) {
                            AudioDeviceInfo route = track.getRoutedDevice();
                            NativeCore.audioLog("PCM frames=" + produced + ", peak=" + peak
                                + ", playbackPosition=" + track.getPlaybackHeadPosition()
                                + ", route=" + (route == null ? "pending" : route.getType()));
                            signalLogged = peak > 0 || ++silentIntervals >= 3;
                            produced = 0;
                            deadline = SystemClock.elapsedRealtime() + 5000;
                        }
                    }
                    for (int offset = 0; !closed && audible && offset < samples.length;) {
                        int count = track.write(samples, offset, samples.length - offset, AudioTrack.WRITE_NON_BLOCKING);
                        if (count < 0) throw new IllegalStateException("write returned " + count);
                        if (count == 0) SystemClock.sleep(3);
                        offset += count;
                    }
                }
            } finally {
                track.release();
            }
        }
    }
}
