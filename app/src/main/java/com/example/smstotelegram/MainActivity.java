package com.example.smstotelegram;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private EditText etBotToken, etChatId, etSenders, etKeywords;
    private TextView tvStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        etBotToken = findViewById(R.id.etBotToken);
        etChatId = findViewById(R.id.etChatId);
        etSenders = findViewById(R.id.etSenders);
        etKeywords = findViewById(R.id.etKeywords);
        tvStatus = findViewById(R.id.tvStatus);

        Button btnSave = findViewById(R.id.btnSave);
        Button btnTest = findViewById(R.id.btnTest);

        etBotToken.setText(prefs.getString("bot_token", ""));
        etChatId.setText(prefs.getString("chat_id", ""));
        etSenders.setText(prefs.getString("senders", ""));
        etKeywords.setText(prefs.getString("keywords", ""));

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                testTelegram();
            }
        });

        requestSmsPermission();
    }

    private void saveSettings() {
        prefs.edit()
                .putString("bot_token", etBotToken.getText().toString().trim())
                .putString("chat_id", etChatId.getText().toString().trim())
                .putString("senders", etSenders.getText().toString().trim())
                .putString("keywords", etKeywords.getText().toString().trim())
                .apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        setStatus("Settings saved at " + nowTime());
    }

    private void testTelegram() {
        final String token = etBotToken.getText().toString().trim();
        final String chatId = etChatId.getText().toString().trim();

        if (token.isEmpty() || chatId.isEmpty()) {
            setStatus("ERROR: Token aur Chat ID dono bharein");
            return;
        }

        setStatus("Sending test message...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result = sendTelegramMessage(
                        token, chatId,
                        "TEST from SMS App at " + nowTime()
                );
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setStatus(result + " at " + nowTime());
                    }
                });
            }
        }).start();
    }

    private String sendTelegramMessage(String token, String chatId, String text) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");

            String postData = "chat_id=" + URLEncoder.encode(chatId, "UTF-8")
                    + "&text=" + URLEncoder.encode(text, "UTF-8")
                    + "&disable_web_page_preview=true";

            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();

            if (code >= 200 && code < 300) {
                return "SUCCESS (HTTP " + code + ")";
            } else {
                String err = "";
                try {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    err = sb.toString();
                } catch (Exception ignored) {}
                return "FAILED (HTTP " + code + "): " + err;
            }
        } catch (Exception e) {
            return "FAILED: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void setStatus(String msg) {
        tvStatus.setText(msg);
    }

    private String nowTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    private void requestSmsPermission() {
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
            }, 1);
        } else {
            setStatus("SMS permission OK. Ready.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 1) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                setStatus("SMS permission granted. Ready.");
            } else {
                setStatus("ERROR: SMS permission denied");
            }
        }
    }
}
