package pe.chef.asistente;

final class WhisperBridge {
    static {
        System.loadLibrary("whisper");
    }

    private WhisperBridge() {}

    static native long initContext(String modelPath);
    static native void freeContext(long contextPtr);
    static native void fullTranscribe(long contextPtr, int numThreads, float[] audioData);
    static native int getTextSegmentCount(long contextPtr);
    static native String getTextSegment(long contextPtr, int index);
    static native String getSystemInfo();
}
