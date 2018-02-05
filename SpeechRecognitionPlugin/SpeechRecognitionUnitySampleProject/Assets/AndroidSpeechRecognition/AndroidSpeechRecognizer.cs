using UnityEngine;
using UnityEngine.UI;

public class AndroidSpeechRecognizer : MonoBehaviour {
    public Text uiText;
    private bool isRecognizing;

    void Start()
    {
        AndroidJavaClass pluginClass = new AndroidJavaClass("youten.redo.speechrecognition.plugin.SpeechRecFragment");
        pluginClass.CallStatic("init", gameObject.name, true); // 3rd param(boolean): isOffline
    }

    void Update()
    {
        if (Input.touchCount > 0) {
            if (!isRecognizing) {
                startRecognition();
            } else {
                stopRecognition();
            }
        }
    }

    private void startRecognition()
    {
        AndroidJavaClass pluginClass = new AndroidJavaClass("youten.redo.speechrecognition.plugin.SpeechRecFragment");
        pluginClass.CallStatic("startRecognition");
        isRecognizing = true;
    }

    private void stopRecognition()
    {
        AndroidJavaClass pluginClass = new AndroidJavaClass("youten.redo.speechrecognition.plugin.SpeechRecFragment");
        pluginClass.CallStatic("stopRecognition");
        isRecognizing = false;
    }

    public void onPartialSpeechRecognition(string result)
    {
        Debug.Log("onPartialSpeechRecognition:" + result);
        uiText.text = result;
    }

    public void onSpeechRecognition(string result)
    {
        Debug.Log("onSpeechRecognition:" + result);
        uiText.text = result;
        AndroidJavaClass pluginClass = new AndroidJavaClass("youten.redo.speechrecognition.plugin.SpeechRecFragment");
        pluginClass.CallStatic("speech", result);
    }
}
