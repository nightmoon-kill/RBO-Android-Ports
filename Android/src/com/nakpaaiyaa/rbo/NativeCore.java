package com.nakpaaiyaa.rbo;

import android.view.Surface;

final class NativeCore {
    static { System.loadLibrary("rbo_android"); }

    static native int start(String gameRoot, String logPath);
    static native void setSurface(Surface surface);
    static native void setKey(int scanCode, boolean pressed);
    static native void setPaused(boolean paused);
    static native int pullAudio(short[] samples);
    static native GameAudio.MusicRequest pollMusic();
    static native void audioLog(String message);
    static native int exitCode();
    static native void stop();

    private NativeCore() {}
}
