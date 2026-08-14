package pe.chef.asistente;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class OfflineMainActivity extends MainActivity {
    private static final int AUDIO_PERMISSION_OFFLINE = 606;

    private OfflineSpeechRecognizer offlineSpeech;
    private Button micButton;
    private Button sendButton;
    private EditText textInput;
    private TextView heardStatus;
    private boolean initializationFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findStableUi(getWindow().getDecorView());
        configureOfflineUi();
        initializeOfflineRecognizer();
    }

    private void configureOfflineUi() {
        if (micButton != null) {
            micButton.setText("🎙️  PREPARANDO WHISPER OFFLINE…");
            micButton.setEnabled(false);
            micButton.setOnClickListener(v -> toggleOfflineVoice());
        }
        if (heardStatus != null) {
            heardStatus.setText("🎙️ Preparando Whisper OFFLINE v0.7 en español…");
        }
    }

    private void initializeOfflineRecognizer() {
        initializationFailed = false;
        if (offlineSpeech != null) offlineSpeech.release();
        if (micButton != null) {
            micButton.setEnabled(false);
            micButton.setText("🎙️  PREPARANDO WHISPER OFFLINE…");
        }

        offlineSpeech = new OfflineSpeechRecognizer(this, new OfflineSpeechRecognizer.Callback() {
            @Override
            public void onReady() {
                initializationFailed = false;
                if (heardStatus != null) {
                    heardStatus.setText("✅ Whisper OFFLINE listo · español · IA local · sin Internet");
                }
                resetMicButton();
            }

            @Override
            public void onState(String message) {
                if (heardStatus != null) heardStatus.setText(message);
            }

            @Override
            public void onResult(String text) {
                resetMicButton();
                if (heardStatus != null) {
                    heardStatus.setText("🎙️ Whisper escuchó: “" + text + "”");
                }
                sendRecognizedText(text);
            }

            @Override
            public void onError(String message) {
                initializationFailed = offlineSpeech == null || !offlineSpeech.isReady();
                if (heardStatus != null) heardStatus.setText("⚠️ " + message);
                if (micButton != null) {
                    if (initializationFailed) {
                        micButton.setEnabled(true);
                        micButton.setText("🔄  REINTENTAR WHISPER OFFLINE");
                    } else {
                        resetMicButton();
                    }
                }
                Toast.makeText(OfflineMainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        offlineSpeech.initialize();
    }

    private void toggleOfflineVoice() {
        if (initializationFailed || offlineSpeech == null) {
            initializeOfflineRecognizer();
            return;
        }

        if (!offlineSpeech.isReady()) {
            Toast.makeText(this, "Whisper todavía se está preparando. Mira el estado encima del botón.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (offlineSpeech.isRecording()) {
            if (micButton != null) {
                micButton.setEnabled(false);
                micButton.setText("⏳  WHISPER ESTÁ ENTENDIENDO…");
            }
            offlineSpeech.stopAndDecode();
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_OFFLINE);
            return;
        }

        startOfflineRecording();
    }

    private void startOfflineRecording() {
        if (heardStatus != null) heardStatus.setText("🎙️ Whisper escuchando SIN INTERNET…");
        if (micButton != null) micButton.setText("⏹  DETENER Y ENTENDER");
        offlineSpeech.startRecording();
    }

    private void sendRecognizedText(String text) {
        if (textInput == null || sendButton == null) {
            Toast.makeText(this, "No pude conectar el texto reconocido con Chef Asistente.", Toast.LENGTH_LONG).show();
            return;
        }
        textInput.setText(text);
        sendButton.performClick();
    }

    private void resetMicButton() {
        if (micButton == null) return;
        boolean enabled = offlineSpeech != null && offlineSpeech.isReady();
        micButton.setEnabled(enabled);
        micButton.setText(enabled ? "🎤  HABLAR CON WHISPER" : "🎙️  PREPARANDO WHISPER OFFLINE…");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != AUDIO_PERMISSION_OFFLINE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startOfflineRecording();
        } else if (heardStatus != null) {
            heardStatus.setText("⚠️ Permite el micrófono para usar Whisper. Puedes seguir escribiendo.");
        }
    }

    private void findStableUi(View view) {
        if (view instanceof EditText) textInput = (EditText) view;

        if (view instanceof Button) {
            Button button = (Button) view;
            String text = String.valueOf(button.getText());
            if (text.contains("HABLAR")) micButton = button;
            if (text.equalsIgnoreCase("Enviar")) sendButton = button;
        }

        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) {
            TextView textView = (TextView) view;
            String text = String.valueOf(textView.getText());
            if (text.startsWith("🎙️")) heardStatus = textView;
            if (text.contains("CHEF ASISTENTE")) {
                textView.setText("CHEF ASISTENTE · WHISPER LOCAL v0.7");
            }
            if (text.contains("Funciona sin internet y actualiza recetas") || text.contains("Recetas híbridas + reconocimiento") || text.contains("Voz local + recetas offline")) {
                textView.setText("Whisper local + recetas offline · IA de voz sin costo por uso");
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findStableUi(group.getChildAt(i));
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (offlineSpeech != null) offlineSpeech.release();
        super.onDestroy();
    }
}
