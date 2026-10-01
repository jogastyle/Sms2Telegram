package com.example.smstotelegram;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);

        final EditText etBotToken = findViewById(R.id.etBotToken);
        final EditText etChatId = findViewById(R.id.etChatId);
        final EditText etSenders = findViewById(R.id.etSenders);
        final EditText etKeywords = findViewById(R.id.etKeywords);
        Button btnSave = findViewById(R.id.btnSave);

        etBotToken.setText(prefs.getString("bot_token", ""));
        etChatId.setText(prefs.getString("chat_id", ""));
        etSenders.setText(prefs.getString("senders", ""));
        etKeywords.setText(prefs.getString("keywords", ""));

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                .putString("bot_token", etBotToken.getText().toString().trim())
                .putString("chat_id", etChatId.getText().toString().trim())
                .putString("senders", etSenders.getText().toString().trim())
                .putString("keywords", etKeywords.getText().toString().trim())
                .apply();
            Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
        });

        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
            }, 1);
        }
    }
}
