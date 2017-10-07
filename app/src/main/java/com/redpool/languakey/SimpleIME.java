package com.redpool.languakey;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;

import android.os.AsyncTask;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.http.GET;


public class SimpleIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    public static final String TAG = "KEYBOARD";
    public Button LK;
    public String apiKey = "trnsl.1.1.20170824T220453Z.6c245c4dfab4ee52.3b1e21e4d67d63db79d80a505b661c1bba800bfe";
    private KeyboardView mKeyboardView;
    public Spinner spin;
    private Keyboard mKeyboard;
    private Constants.KEYS_TYPE mCurrentLocale;

    private Constants.KEYS_TYPE mPreviousLocale;
    private boolean isCapsOn = false;
    public View root;
    public Toast toast;
    public String currentlang;
   public TextView mTxtDisplay;
    public RequestQueue mRequestQueue;

    public View onCreateInputView() {

        Log.d(TAG, "Keyboard has been opened");
//        mKeyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.keyboard,null);
        root = getLayoutInflater().inflate(R.layout.keyboard, null);

        if (Locale.getDefault().getLanguage().equals("ru"))
        {
            mCurrentLocale = Constants.KEYS_TYPE.RUSSIAN;
        }
        else if(Locale.getDefault().getLanguage().equals("en"))
        {
            mCurrentLocale = Constants.KEYS_TYPE.ENGLISH;
        }
        else
        {
            mCurrentLocale = Constants.KEYS_TYPE.ENGLISH;
        }

        Button LK = (Button) root.findViewById(R.id.lkey);
        LK.setEnabled(false);

        mKeyboard = getKeyboard(mCurrentLocale);
        spin = (Spinner) root.findViewById(R.id.spin);

        LK.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int position = spin.getSelectedItemPosition();
                final Resources res = getResources();
                String[] langs = res.getStringArray(R.array.langs_v);
                final String lang = langs[position];



                getCurrentInputConnection().performContextMenuAction(android.R.id.selectAll);
                CharSequence sData =  getCurrentInputConnection().getSelectedText(0);
                final String text = sData.toString();

                final String URL = "https://translate.yandex.net/api/v1.5/tr.json/detect?key="+apiKey+"&text="+text.replaceAll(" ","%20");
                Cache cache = new DiskBasedCache(getCacheDir(), 1024 * 1024); // 1MB cap

                // Set up the network to use HttpURLConnection as the HTTP client.
               Network network = new BasicNetwork(new HurlStack());
                mRequestQueue = new RequestQueue(cache, network);
                mRequestQueue.start();
                JsonObjectRequest jsobj = new JsonObjectRequest(Request.Method.GET, URL, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                        final String slang = response.getString("lang");




                        String url = "https://translate.yandex.net/api/v1.5/tr.json/translate?key="+apiKey+"&text="+text.replaceAll(" ","%20")+"&lang="+slang+"-"+lang;
                        JsonObjectRequest js = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                try {
                                String text = response.getString("text");
                                    int indexOfOpenBracket = text.indexOf("[");
                                    int indexOfLastBracket = text.lastIndexOf("]");
                                    text = text.substring(indexOfOpenBracket+1, indexOfLastBracket);
                                    int indexOfBracket = text.indexOf("\"");
                                    int indexOBracket = text.lastIndexOf("\"");
                                    text = text.substring(indexOfBracket+1, indexOBracket);

                                    getCurrentInputConnection().commitText(String.valueOf(text),1);
                                }
                                catch(JSONException e)
                                {
                                    e.printStackTrace();
                                    Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }
                        }, new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {


                            }
                        });
                        mRequestQueue.add(js);
                        }catch(JSONException e)
                        {
                            e.printStackTrace();
                            Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                });

                mRequestQueue.add(jsobj);

                return false;

            }
        });

        mKeyboardView = (KeyboardView) root.findViewById(R.id.keyboard);
        mKeyboard.setShifted(isCapsOn);
        mKeyboardView.setKeyboard(mKeyboard);
        mKeyboardView.setOnKeyboardActionListener(this);

        return root;
    }

    /**
     * @param locale - keys of keyboard
     * @return localized keyboard
     */
    public ArrayAdapter<CharSequence> adapter;
    private Keyboard getKeyboard(Constants.KEYS_TYPE locale) {
        switch (locale) {
            case RUSSIAN:

                spin = (Spinner) root.findViewById(R.id.spin);
                 adapter = ArrayAdapter.createFromResource(this,
                        R.array.langs, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_selectable_list_item);
// Apply the adapter to the spinner
                spin.setAdapter(adapter);
                currentlang = "ru";
                return new Keyboard(this, R.xml.keys_definition_ru);
            case ENGLISH:
                spin = (Spinner) root.findViewById(R.id.spin);
                adapter = ArrayAdapter.createFromResource(this,
                        R.array.langs_en, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_selectable_list_item);
// Apply the adapter to the spinner
                spin.setAdapter(adapter);
                currentlang = "en";
                return new Keyboard(this, R.xml.keys_definition_en);
            case SYMBOLS:
                spin = (Spinner) root.findViewById(R.id.spin);
                adapter = ArrayAdapter.createFromResource(this,
                        R.array.langs_en, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_selectable_list_item);
// Apply the adapter to the spinner
                spin.setAdapter(adapter);
                return new Keyboard(this, R.xml.keys_definition_symbols);
            default:

                return new Keyboard(this, R.xml.keys_definition_ru);
        }
    }

    /**
     * Play sound on key press
     *
     * @param keyCode of pressed key
     */
    private void playClick(int keyCode) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch (keyCode) {
            case Constants.KeyCode.SPACE:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;
            case Keyboard.KEYCODE_DONE:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;
            case Constants.KeyCode.RETURN:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;
            case Keyboard.KEYCODE_DELETE:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                break;
            default:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
                break;
        }
    }

    @Override
    public void onPress(int i) {
        Log.d(TAG, "onPress " + i);
    }

    @Override
    public void onRelease(int i) {
        Log.d(TAG, "onRelease " + i);
    }

    @Override
    public void onKey(int primaryCode, int[] ints) {
        Log.d(TAG, "onKey " + primaryCode);
        InputConnection ic = getCurrentInputConnection();
        playClick(primaryCode);

        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                ic.deleteSurroundingText(1, 0);
                break;
            case Keyboard.KEYCODE_SHIFT:
                handleShift();
                break;
            case Keyboard.KEYCODE_DONE:
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                break;
            case Keyboard.KEYCODE_ALT:
                handleSymbolsSwitch();
                break;
            case Keyboard.KEYCODE_MODE_CHANGE:
                handleLanguageSwitch();
                break;
            default:
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCapsOn) {
                    code = Character.toUpperCase(code);
                }

                ic.commitText(String.valueOf(code), 1);
                break;
        }
    }

    @Override
    public void onText(CharSequence charSequence) {
        Log.d(TAG, "onText ");
    }

    @Override
    public void swipeLeft() {
        Log.d(TAG, "swipeLeft ");
    }

    @Override
    public void swipeRight() {
        Log.d(TAG, "swipeRight ");
    }

    @Override
    public void swipeDown() {
        Log.d(TAG, "swipeDown ");
    }

    @Override
    public void swipeUp() {
        Log.d(TAG, "swipeUp ");
    }

    private void handleSymbolsSwitch() {
        if (mCurrentLocale != Constants.KEYS_TYPE.SYMBOLS) {
            mKeyboard = getKeyboard(Constants.KEYS_TYPE.SYMBOLS);
            mPreviousLocale = mCurrentLocale;
            mCurrentLocale = Constants.KEYS_TYPE.SYMBOLS;
        } else {
            mKeyboard = getKeyboard(mPreviousLocale);
            mCurrentLocale = mPreviousLocale;
            mKeyboard.setShifted(isCapsOn);
        }
        mKeyboardView.setKeyboard(mKeyboard);
        mKeyboardView.invalidateAllKeys();
    }

    private void handleShift() {
        isCapsOn = !isCapsOn;
        mKeyboard.setShifted(isCapsOn);
        mKeyboardView.invalidateAllKeys();
    }

    private void handleLanguageSwitch() {
        if (mCurrentLocale == Constants.KEYS_TYPE.RUSSIAN) {
            mCurrentLocale = Constants.KEYS_TYPE.ENGLISH;
            mKeyboard = getKeyboard(Constants.KEYS_TYPE.ENGLISH);
        } else {
            mCurrentLocale = Constants.KEYS_TYPE.RUSSIAN;
            mKeyboard = getKeyboard(Constants.KEYS_TYPE.RUSSIAN);
        }

        mKeyboardView.setKeyboard(mKeyboard);
        mKeyboard.setShifted(isCapsOn);
        mKeyboardView.invalidateAllKeys();
    }

    public void registerEditText(int resid) {
        // Find the EditText 'res_id'
        EditText edittext = (EditText) root.findViewById(resid);
        edittext.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
            @Override
            public void afterTextChanged(Editable editable) {
                CharSequence mS = editable.subSequence(0, editable.length());
                if (!mS.toString().equals("") || mS.toString() != null) {

                    if (editable.length() > 0 && mS.toString().contains("=")) {
                        LK.setEnabled(true);
                        editable.replace(editable.length() - 1, editable.length(), "");
                    }
                }
                else
                {
                    LK.setEnabled(false);
                }
            }
        });
        // Make the custom keyboard appear
        edittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) showCustomKeyboard(v);
                else hideCustomKeyboard();
            }
        });
        edittext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCustomKeyboard(v);
            }
        });
        edittext.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                EditText edittext = (EditText) v;
                int inType = edittext.getInputType();       // Backup the input type
                edittext.setInputType(InputType.TYPE_NULL); // Disable standard keyboard
                edittext.onTouchEvent(event);               // Call native handler
                edittext.setInputType(inType);              // Restore input type
                return true; // Consume touch event
            }
        });
    }
    public void hideCustomKeyboard() {
    mKeyboardView.setVisibility(View.GONE);
    mKeyboardView.setEnabled(false);
}
    public void showCustomKeyboard( View v) {
        mKeyboardView.setVisibility(View.VISIBLE);
        mKeyboardView.setEnabled(true);
        if( v!=null ){
            ((InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }
}