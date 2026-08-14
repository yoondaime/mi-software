package pe.chef.asistente;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class OfflineSpeechRecognizer {
    public interface Callback {
        void onReady();
        void onState(String message);
        void onResult(String text);
        void onError(String message);
    }

    private static final int RATE = 16000;
    private static final String MODEL_ASSET = "models/whisper/ggml-base.bin";
    private static final String MODEL_DIR = "whisper-base-v071";
    private static final long MIN_MODEL_BYTES = 120_000_000L;

    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean initFinished = new AtomicBoolean(false);
    private final Object whisperLock = new Object();

    private AudioRecord recorder;
    private Thread recordThread;
    private ByteArrayOutputStream pcm;
    private volatile boolean ready;
    private long whisperContext = 0L;

    public OfflineSpeechRecognizer(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public boolean isReady() { return ready; }
    public boolean isRecording() { return recording.get(); }

    public void initialize() {
        ready = false;
        initFinished.set(false);
        state("🎙️ 1/3 · Preparando Whisper Base en español…");

        main.postDelayed(() -> {
            if (!initFinished.get()) {
                state("⏳ Whisper Base sigue cargando. Puedes seguir escribiendo mientras termina…");
            }
        }, 45000);

        main.postDelayed(() -> {
            if (!initFinished.get()) {
                state("⚠️ Whisper Base está tardando más de lo esperado en este teléfono.");
            }
        }, 120000);

        new Thread(() -> {
            try {
                File modelDir = new File(context.getFilesDir(), "speech/" + MODEL_DIR);
                if (!modelDir.exists() && !modelDir.mkdirs()) {
                    throw new Exception("No se pudo crear la carpeta local de Whisper");
                }

                File modelFile = new File(modelDir, "ggml-base.bin");
                if (!modelFile.exists() || modelFile.length() < MIN_MODEL_BYTES) {
                    state("🎙️ 1/3 · Copiando Whisper Base al teléfono…");
                    copyAsset(MODEL_ASSET, modelFile);
                } else {
                    state("🎙️ 1/3 · Whisper Base local encontrado…");
                }

                state("🎙️ 2/3 · Cargando inteligencia de voz Whisper Base…");
                long ptr = WhisperBridge.initContext(modelFile.getAbsolutePath());
                if (ptr == 0L) {
                    throw new Exception("Whisper Base no pudo abrir el modelo español");
                }

                synchronized (whisperLock) {
                    if (whisperContext != 0L) {
                        try { WhisperBridge.freeContext(whisperContext); } catch (Throwable ignored) {}
                    }
                    whisperContext = ptr;
                }

                state("🎙️ 3/3 · Whisper Base listo para cocina en español");
                ready = true;
                initFinished.set(true);
                main.post(callback::onReady);
            } catch (Throwable e) {
                ready = false;
                initFinished.set(true);
                error("No pude iniciar Whisper Base offline: " + msg(e));
            }
        }, "chef-whisper-init-v071").start();
    }

    private void copyAsset(String assetPath, File target) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        if (temp.exists()) temp.delete();

        try (InputStream in = context.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(temp, false)) {
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }

        if (!temp.exists() || temp.length() < MIN_MODEL_BYTES) {
            throw new Exception("El modelo Whisper Base quedó incompleto");
        }
        if (target.exists() && !target.delete()) {
            throw new Exception("No se pudo reemplazar el modelo Whisper Base");
        }
        if (!temp.renameTo(target)) {
            throw new Exception("No se pudo guardar el modelo Whisper Base");
        }
    }

    public void startRecording() {
        if (!ready || whisperContext == 0L) {
            error("Whisper Base todavía no está listo.");
            return;
        }
        if (!recording.compareAndSet(false, true)) return;

        try {
            int min = AudioRecord.getMinBufferSize(
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int size = Math.max(min * 2, 8192);

            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size
            );

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new Exception("micrófono no disponible");
            }

            pcm = new ByteArrayOutputStream();
            recorder.startRecording();
            state("🎙️ Whisper Base escuchando SIN INTERNET… habla claro y toca DETENER al terminar");

            recordThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (recording.get()) {
                    try {
                        int n = recorder.read(buffer, 0, buffer.length);
                        if (n > 0) pcm.write(buffer, 0, n);
                    } catch (Throwable ignored) {
                        break;
                    }
                }
            }, "chef-whisper-record-v071");
            recordThread.start();
        } catch (Throwable e) {
            recording.set(false);
            closeRecorder();
            error("No pude comenzar a escuchar: " + msg(e));
        }
    }

    public void stopAndDecode() {
        if (!recording.compareAndSet(true, false)) return;
        state("⏳ Whisper Base está entendiendo tu voz dentro del teléfono…");
        try {
            if (recorder != null) recorder.stop();
        } catch (Throwable ignored) {}

        new Thread(() -> {
            try {
                if (recordThread != null) recordThread.join(1200);
                byte[] bytes = pcm == null ? new byte[0] : pcm.toByteArray();
                closeRecorder();

                if (bytes.length < 8000) {
                    error("La grabación fue demasiado corta.");
                    return;
                }

                float[] samples = toFloat(bytes);
                StringBuilder text = new StringBuilder();
                synchronized (whisperLock) {
                    if (whisperContext == 0L) throw new Exception("motor Whisper cerrado");
                    int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
                    WhisperBridge.fullTranscribe(whisperContext, threads, samples);
                    int count = WhisperBridge.getTextSegmentCount(whisperContext);
                    for (int i = 0; i < count; i++) {
                        String segment = WhisperBridge.getTextSegment(whisperContext, i);
                        if (segment != null) text.append(segment).append(' ');
                    }
                }

                String result = text.toString().replaceAll("\\s+", " ").trim();
                if (result.isEmpty()) {
                    error("Whisper Base no logró entender la frase.");
                    return;
                }
                main.post(() -> callback.onResult(result));
            } catch (Throwable e) {
                closeRecorder();
                error("No pude reconocer la frase con Whisper Base: " + msg(e));
            }
        }, "chef-whisper-decode-v071").start();
    }

    public void cancel() {
        recording.set(false);
        try {
            if (recorder != null) recorder.stop();
        } catch (Throwable ignored) {}
        closeRecorder();
    }

    public void release() {
        cancel();
        ready = false;
        synchronized (whisperLock) {
            if (whisperContext != 0L) {
                try { WhisperBridge.freeContext(whisperContext); } catch (Throwable ignored) {}
                whisperContext = 0L;
            }
        }
    }

    private static float[] toFloat(byte[] b) {
        float[] out = new float[b.length / 2];
        for (int i = 0; i < out.length; i++) {
            int lo = b[i * 2] & 255;
            int hi = b[i * 2 + 1];
            out[i] = (short) ((hi << 8) | lo) / 32768f;
        }
        return out;
    }

    private void closeRecorder() {
        AudioRecord r = recorder;
        recorder = null;
        if (r != null) {
            try { r.release(); } catch (Throwable ignored) {}
        }
    }

    private void state(String s) {
        main.post(() -> callback.onState(s));
    }

    private void error(String s) {
        main.post(() -> callback.onError(s));
    }

    private static String msg(Throwable e) {
        String s = e.getMessage();
        if (s == null || s.trim().isEmpty()) s = e.getClass().getSimpleName();
        return s.length() > 180 ? s.substring(0, 180) : s;
    }
}
