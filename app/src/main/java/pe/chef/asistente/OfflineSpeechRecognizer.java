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
    private static final String ASSET_DIR = "models/moonshine-es";
    private static final String MODEL_DIR = "moonshine-es-v061";

    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean initFinished = new AtomicBoolean(false);

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
        ready = false;
        initFinished.set(false);
        state("🎙️ 1/4 · Preparando archivos de voz offline…");

        main.postDelayed(() -> {
            if (!initFinished.get()) {
                state("⏳ El motor de voz sigue cargando. Puedes seguir escribiendo mientras termina…");
            }
        }, 25000);

        main.postDelayed(() -> {
            if (!initFinished.get()) {
                state("⚠️ La carga de voz está tardando demasiado en este teléfono. La app escrita sigue funcionando.");
            }
        }, 70000);

        new Thread(() -> {
            try {
                File modelDir = new File(context.getFilesDir(), "speech/" + MODEL_DIR);
                if (!modelDir.exists() && !modelDir.mkdirs()) {
                    throw new Exception("No se pudo crear la carpeta local del modelo");
                }

                File marker = new File(modelDir, "ready-v061.marker");
                File encoder = new File(modelDir, "encoder_model.ort");
                File decoder = new File(modelDir, "decoder_model_merged.ort");
                File tokens = new File(modelDir, "tokens.txt");

                boolean valid = marker.exists()
                        && encoder.exists() && encoder.length() > 10_000_000L
                        && decoder.exists() && decoder.length() > 20_000_000L
                        && tokens.exists() && tokens.length() > 100_000L;

                if (!valid) {
                    marker.delete();
                    state("🎙️ 1/4 · Copiando encoder al teléfono…");
                    copyAsset(ASSET_DIR + "/encoder_model.ort", encoder);

                    state("🎙️ 2/4 · Copiando decoder al teléfono…");
                    copyAsset(ASSET_DIR + "/decoder_model_merged.ort", decoder);

                    state("🎙️ 3/4 · Copiando vocabulario español…");
                    copyAsset(ASSET_DIR + "/tokens.txt", tokens);

                    try (FileOutputStream out = new FileOutputStream(marker, false)) {
                        out.write("ok-v061".getBytes());
                        out.flush();
                    }
                } else {
                    state("🎙️ 3/4 · Modelo local encontrado. Preparando motor…");
                }

                state("🎙️ 4/4 · Cargando motor de reconocimiento español…");

                OfflineMoonshineModelConfig moonshine = new OfflineMoonshineModelConfig();
                moonshine.setEncoder(encoder.getAbsolutePath());
                moonshine.setMergedDecoder(decoder.getAbsolutePath());

                OfflineModelConfig model = new OfflineModelConfig();
                model.setMoonshine(moonshine);
                model.setTokens(tokens.getAbsolutePath());
                model.setNumThreads(2);
                model.setProvider("cpu");
                model.setModelType("moonshine");

                OfflineRecognizerConfig config = new OfflineRecognizerConfig();
                config.setModelConfig(model);
                config.setDecodingMethod("greedy_search");

                recognizer = new OfflineRecognizer(null, config);
                ready = true;
                initFinished.set(true);
                main.post(callback::onReady);
            } catch (Throwable e) {
                ready = false;
                initFinished.set(true);
                error("No pude iniciar la voz offline: " + msg(e));
            }
        }, "chef-asr-init-v061").start();
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

        if (!temp.exists() || temp.length() < 1024) {
            throw new Exception("El archivo " + target.getName() + " quedó incompleto");
        }
        if (target.exists() && !target.delete()) {
            throw new Exception("No se pudo reemplazar " + target.getName());
        }
        if (!temp.renameTo(target)) {
            throw new Exception("No se pudo guardar " + target.getName());
        }
    }

    public void startRecording() {
        if (!ready || recognizer == null) {
            error("La voz offline todavía no está lista.");
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
            state("🎙️ Escuchando SIN INTERNET… toca DETENER cuando termines");

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
            }, "chef-asr-record-v061");
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
                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(samples, RATE);
                    recognizer.decode(stream);
                    String text = recognizer.getResult(stream).getText();
                    if (text == null || text.trim().isEmpty()) {
                        error("No logré entender la frase.");
                        return;
                    }
                    String result = text.trim();
                    main.post(() -> callback.onResult(result));
                } finally {
                    stream.release();
                }
            } catch (Throwable e) {
                closeRecorder();
                error("No pude reconocer la frase: " + msg(e));
            }
        }, "chef-asr-decode-v061").start();
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
        if (recognizer != null) {
            try {
                recognizer.release();
            } catch (Throwable ignored) {}
            recognizer = null;
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
            try {
                r.release();
            } catch (Throwable ignored) {}
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
        return s.length() > 160 ? s.substring(0, 160) : s;
    }
}
