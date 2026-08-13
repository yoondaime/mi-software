package pe.chef.asistente;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class OfflineSpeechRecognizer {
    public interface Callback {
        void onReady();
        void onState(String message);
        void onResult(String text);
        void onError(String message);
    }

    private static final int RATE = 16000;
    private static final String DIR = "models/moonshine-es";
    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private OfflineRecognizer recognizer;
    private AudioRecord recorder;
    private Thread recordThread;
    private ByteArrayOutputStream pcm;
    private volatile boolean ready;

    public OfflineSpeechRecognizer(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public boolean isReady() { return ready; }
    public boolean isRecording() { return recording.get(); }

    public void initialize() {
        state("🎙️ Preparando voz offline en español…");
        new Thread(() -> {
            try {
                OfflineMoonshineModelConfig moonshine = new OfflineMoonshineModelConfig();
                moonshine.setEncoder(DIR + "/encoder_model.ort");
                moonshine.setMergedDecoder(DIR + "/decoder_model_merged.ort");

                OfflineModelConfig model = new OfflineModelConfig();
                model.setMoonshine(moonshine);
                model.setTokens(DIR + "/tokens.txt");
                model.setNumThreads(2);
                model.setProvider("cpu");
                model.setModelType("moonshine");

                OfflineRecognizerConfig config = new OfflineRecognizerConfig();
                config.setModelConfig(model);
                config.setDecodingMethod("greedy_search");

                recognizer = new OfflineRecognizer(context.getAssets(), config);
                ready = true;
                main.post(callback::onReady);
            } catch (Throwable e) {
                ready = false;
                error("No pude iniciar la voz offline: " + msg(e));
            }
        }, "chef-asr-init").start();
    }

    public void startRecording() {
        if (!ready || recognizer == null) { error("La voz offline todavía no está lista."); return; }
        if (!recording.compareAndSet(false, true)) return;
        try {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min * 2, 8192);
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new Exception("micrófono no disponible");
            pcm = new ByteArrayOutputStream();
            recorder.startRecording();
            state("🎙️ Escuchando SIN INTERNET… toca DETENER cuando termines");
            recordThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (recording.get()) {
                    try {
                        int n = recorder.read(buffer, 0, buffer.length);
                        if (n > 0) pcm.write(buffer, 0, n);
                    } catch (Throwable ignored) { break; }
                }
            }, "chef-asr-record");
            recordThread.start();
        } catch (Throwable e) {
            recording.set(false);
            closeRecorder();
            error("No pude comenzar a escuchar: " + msg(e));
        }
    }

    public void stopAndDecode() {
        if (!recording.compareAndSet(true, false)) return;
        state("⏳ Procesando tu voz dentro del teléfono…");
        try { if (recorder != null) recorder.stop(); } catch (Throwable ignored) {}
        new Thread(() -> {
            try {
                if (recordThread != null) recordThread.join(1000);
                byte[] bytes = pcm == null ? new byte[0] : pcm.toByteArray();
                closeRecorder();
                if (bytes.length < 8000) { error("La grabación fue demasiado corta."); return; }
                float[] samples = toFloat(bytes);
                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(samples, RATE);
                    recognizer.decode(stream);
                    String text = recognizer.getResult(stream).getText();
                    if (text == null || text.trim().isEmpty()) { error("No logré entender la frase."); return; }
                    String result = text.trim();
                    main.post(() -> callback.onResult(result));
                } finally { stream.release(); }
            } catch (Throwable e) {
                closeRecorder();
                error("No pude reconocer la frase: " + msg(e));
            }
        }, "chef-asr-decode").start();
    }

    public void cancel() {
        recording.set(false);
        try { if (recorder != null) recorder.stop(); } catch (Throwable ignored) {}
        closeRecorder();
    }

    public void release() {
        cancel();
        ready = false;
        if (recognizer != null) {
            try { recognizer.release(); } catch (Throwable ignored) {}
            recognizer = null;
        }
    }

    private static float[] toFloat(byte[] b) {
        float[] out = new float[b.length / 2];
        for (int i = 0; i < out.length; i++) {
            int lo = b[i * 2] & 255;
            int hi = b[i * 2 + 1];
            out[i] = (short)((hi << 8) | lo) / 32768f;
        }
        return out;
    }

    private void closeRecorder() {
        AudioRecord r = recorder; recorder = null;
        if (r != null) try { r.release(); } catch (Throwable ignored) {}
    }

    private void state(String s) { main.post(() -> callback.onState(s)); }
    private void error(String s) { main.post(() -> callback.onError(s)); }
    private static String msg(Throwable e) {
        String s = e.getMessage();
        if (s == null || s.trim().isEmpty()) s = e.getClass().getSimpleName();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
