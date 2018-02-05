package youten.redo.speechrecognition.plugin;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

// ref. https://github.com/IhnoL/UnityAndroidSpeechRecognitionFragment
// ref. https://github.com/fcrisciani/android-speech-recognition
// ref. https://qiita.com/KAKKA/items/d15cb9187fd1bcca8770
// ref. https://qiita.com/iKimishima/items/215eb7b816aa99ed0704

// Android SpeechRecognition and Text-To-Speech
public class SpeechRecFragment extends Fragment implements RecognitionListener,
        TextToSpeech.OnInitListener {
    private static final String TAG = "SpeechRecFragment";

    // for SpeechRecognizer
    private static SpeechRecFragment instance;
    private static String gameObjectName;
    private static boolean isOffline = false;
    private SpeechRecognizer speech = null;
    private Timer silenceTimer = null;
    // for TTS
    private TextToSpeech tts;
    private String ttsText;

    public SpeechRecFragment() {
    }

    public static void init(String gameObjectName, boolean isOffline) {
        FragmentTransaction ft =  UnityPlayer.currentActivity.getFragmentManager().beginTransaction();
        if (instance != null) {
            ft.remove(instance);
            instance = null;
        }
        instance = new SpeechRecFragment();
        instance.gameObjectName = gameObjectName;
        instance.isOffline = isOffline;
        ft.add(instance, SpeechRecFragment.TAG).commit();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retain between configuration changes (like device rotation)
        setRetainInstance(true);
    }

    public static void startRecognition() {
        if (instance == null) {
            return;
        }
        instance.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                startImpl();
            }
        });
    }

    public static void startImpl() {
        if (instance == null) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (instance.isOffline) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }
        instance.getSpeechRecognizer().startListening(intent);
    }

    public static void stopRecognition() {
        if (instance == null) {
            return;
        }
        if (instance.silenceTimer != null) {
            instance.silenceTimer.cancel();
        }
        if (instance.speech != null) {
            instance.speech.destroy();
            instance.speech = null;
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params1) {
        Log.d(TAG, "onReadyForSpeech");
        silenceTimer = new Timer();
        silenceTimer.schedule(new SilenceTimer(), 5000);
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech");
        silenceTimer.cancel();
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.d(TAG, "onBufferReceived");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech");
    }

    public void onError(int error) {
        String message;
        boolean restart = true;
        switch (error)
        {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                restart = false;
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                restart = false;
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Not recognised";
                break;
        }
        Log.d(TAG,"onError code:" + error + " message: " + message);

        if (restart) {
            stopRecognition();
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    startImpl();
                }
            });
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        Log.d(TAG, "onEvent");
    }

    public void onPartialResults(Bundle partialResults) {
        Log.d(TAG,"onPartialResults");
        List<String> message = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (message != null) {
            UnityPlayer.UnitySendMessage(gameObjectName, "onPartialSpeechRecognition", message.get(0));
        }
    }

    @Override
    public void onResults(Bundle results) {
        // Restart new dictation cycle
        startRecognition();

        StringBuilder scores = new StringBuilder();
        for (int i = 0; i < results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES).length; i++) {
            scores.append(results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)[i] + " ");
        }
        Log.d(TAG,"onResults: " + results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) + " scores: " + scores.toString());
        List<String> message = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (message != null) {
            UnityPlayer.UnitySendMessage(gameObjectName, "onSpeechRecognition", message.get(0));
        }
    }

    @Override
    public void onRmsChanged(float v) {
        // do nothing
    }

    // Lazy instantiation method for getting the speech recognizer
    private SpeechRecognizer getSpeechRecognizer(){
        if (speech == null) {
            speech = SpeechRecognizer.createSpeechRecognizer(getActivity());
            speech.setRecognitionListener(this);
        }
        return speech;
    }

    // Timer task used to reproduce the timeout input error that seems not be called on android 4.1.2
    public class SilenceTimer extends TimerTask {
        @Override
        public void run() {
            onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
        }
    }

    // TTS
    public static void speech(String text) {
        if (instance == null || text == null) {
            return;
        }
        if (instance.tts == null) {
            instance.ttsText = text;
            instance.tts = new TextToSpeech(instance.getActivity(), instance);
        } else {
            ttsSpeak(text);
        }
    }

    private static void ttsSpeak(String text) {
        stopRecognition();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            instance.tts.speak(text, TextToSpeech.QUEUE_ADD, null, TAG);
        } else {
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TAG);
            instance.tts.speak(text, TextToSpeech.QUEUE_ADD, params);
        }
    }

    @Override
    public void onInit(int i) {
        if (i == TextToSpeech.SUCCESS) {
            Log.d(TAG,"TTS onInit: success");
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG,"TTS onStart");
                }
                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG,"TTS onDone");
                    ttsText = null;
                    startRecognition();
                }
                @Override
                public void onError(String utteranceId) {
                    Log.d(TAG,"TTS onError");
                    ttsText = null;
                    startRecognition();
                }
            });
            tts.setPitch(1.2f);
            tts.setSpeechRate(1.2f);
            if (ttsText != null) {
                ttsSpeak(ttsText);
            }
        } else {
            Log.d(TAG,"TTS onInit: failure");
            tts = null;
        }
    }
}