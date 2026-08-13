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

    private static final int SAMPLE_RATE = 16000;
    private static final String ASSET_DIR = "models/moonshine-es";
    private static final String MODEL_DIR = "moonshine-es";

    private final Context context;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean recording = new AtomicBoolean(false);

    private OfflineRecognizer recognizer;
    private AudioRecord audioRecord;
    private Thread recordThread;
    private ByteArrayOutputStream audioBytes;
    private volatile boolean ready = false;

    public OfflineSpeechRecognizer(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isRecording() {
        return recording.get();
    }

    public void initialize() {
        postState("🎙️ Preparando voz offline en español…");
        new Thread(() -> {
            try {
                File modelDir = new File(context.getFilesDir(), "models/" + MODEL_DIR);
                if (!modelDir.exists() && !modelDir.mkdirs()) {
                    throw new Exception("No se pudo crear la carpeta del modelo");
                }

                File encoder = copyAssetIfNeeded("encoder_model.ort", modelDir);
                File decoder = copyAssetIfNeeded("decoder_model_merged.ort", modelDir);
                File tokens = copyAssetIfNeeded("tokens.txt", modelDir);

                OfflineMoonshineModelConfig moonshine = OfflineMoonshineModelConfig.builder()
                        .setEncoder(encoder.getAbsolutePath())
                        .setMergedDecoder(decoder.getAbsolutePath())
                        .build();

                OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                        .setMoonshine(moonshine)
                        .setTokens(tokens.getAbsolutePath())
                        .setNumThreads(2)
                        .setDebug(false)
                        .build();

                OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
                        .setOfflineModelConfig(modelConfig)
                        .setDecodingMethod("greedy_search")
                        .build();

                recognizer = new OfflineRecognizer(config);
                ready = true;
                mainHandler.post(callback::onReady);
            } catch (Throwable e) {
                ready = false;
                postError("No pude iniciar la voz offline: " + shortMessage(e));
            }
        }, "chef-offline-init").start();
    }

    public void startRecording() {
        if (!ready || recognizer == null) {
            postError("La voz offline todavía no está lista.");
            return;
        }
        if (!recording.compareAndSet(false, true)) return;

        try {
            int minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferSize = Math.max(minBuffer * 2, 8192);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                recording.set(false);
                audioRecord.release();
                audioRecord = null;
                postError("Android no pudo abrir el micrófono.");
                return;
            }

            audioBytes = new ByteArrayOutputStream();
            audioRecord.startRecording();
            postState("🎙️ Escuchando SIN INTERNET… toca DETENER cuando termines");

            recordThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try {
                    while (recording.get()) {
                        int count = audioRecord.read(buffer, 0, buffer.length);
                        if (count > 0) audioBytes.write(buffer, 0, count);
                    }
                } catch (Throwable ignored) {
                }
            }, "chef-audio-record");
            recordThread.start();
        } catch (Throwable e) {
            recording.set(false);
            releaseAudioRecord();
            postError("No pude comenzar a escuchar: " + shortMessage(e));
        }
    }

    public void stopAndDecode() {
        if (!recording.compareAndSet(true, false)) return;
        postState("⏳ Procesando tu voz dentro del teléfono…");

        try {
            if (audioRecord != null) audioRecord.stop();
        } catch (Throwable ignored) {
        }

        new Thread(() -> {
            try {
                if (recordThread != null) recordThread.join(1500);
                byte[] pcm = audioBytes == null ? new byte[0] : audioBytes.toByteArray();
                releaseAudioRecord();

                if (pcm.length < SAMPLE_RATE / 2) {
                    postError("La grabación fue demasiado corta. Intenta hablar un poco más.");
                    return;
                }

                float[] samples = pcm16LittleEndianToFloat(pcm);
                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(samples, SAMPLE_RATE);
                    recognizer.decode(stream);
                    String text = recognizer.getResult(stream).getText();
                    if (text == null || text.trim().isEmpty()) {
                        postError("No logré entender la frase. Inténtalo otra vez hablando cerca del teléfono.");
                        return;
                    }
                    String finalText = text.trim();
                    mainHandler.post(() -> callback.onResult(finalText));
                } finally {
                    stream.release();
                }
            } catch (Throwable e) {
                releaseAudioRecord();
                postError("No pude reconocer la frase: " + shortMessage(e));
            }
        }, "chef-offline-decode").start();
    }

    public void cancel() {
        recording.set(false);
        try {
            if (audioRecord != null) audioRecord.stop();
        } catch (Throwable ignored) {
        }
        releaseAudioRecord();
    }

    public void release() {
        cancel();
        ready = false;
        if (recognizer != null) {
            try {
                recognizer.release();
            } catch (Throwable ignored) {
            }
            recognizer = null;
        }
    }

    private File copyAssetIfNeeded(String name, File modelDir) throws Exception {
        File target = new File(modelDir, name);
        if (target.exists() && target.length() > 1024) return target;

        postState("🎙️ Preparando modelo: " + name);
        File temp = new File(modelDir, name + ".tmp");
        try (InputStream in = context.getAssets().open(ASSET_DIR + "/" + name);
             FileOutputStream out = new FileOutputStream(temp, false)) {
            byte[] buffer = new byte[1024 * 256];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        if (target.exists() && !target.delete()) {
            throw new Exception("No se pudo reemplazar " + name);
        }
        if (!temp.renameTo(target)) {
            throw new Exception("No se pudo guardar " + name);
        }
        return target;
    }

    private static float[] pcm16LittleEndianToFloat(byte[] pcm) {
        int sampleCount = pcm.length / 2;
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int lo = pcm[i * 2] & 0xff;
            int hi = pcm[i * 2 + 1];
            short value = (short) ((hi << 8) | lo);
            samples[i] = value / 32768.0f;
        }
        return samples;
    }

    private void releaseAudioRecord() {
        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record != null) {
            try {
                record.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private void postState(String message) {
        mainHandler.post(() -> callback.onState(message));
    }

    private void postError(String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private static String shortMessage(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return e.getClass().getSimpleName();
        if (message.length() > 120) return message.substring(0, 120);
        return message;
    }
}
