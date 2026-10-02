package com.example.smstotelegram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SmsReceiver extends BroadcastReceiver {

    // Yahan apna Vercel URL daalo
    private static final String VERCEL_SAVE_URL = "https://telegram-gemini-bot-eosin.vercel.app/save-report";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String botToken = prefs.getString("bot_token", "").trim();
        String chatId = prefs.getString("chat_id", "").trim();
        String sendersRaw = prefs.getString("senders", "");
        String keywordsRaw = prefs.getString("keywords", "");

        if (botToken.isEmpty() || chatId.isEmpty()) return;

        String sender = messages[0].getOriginatingAddress();
        if (sender == null) sender = messages[0].getDisplayOriginatingAddress();
        if (sender == null) sender = "Unknown";

        StringBuilder bodySb = new StringBuilder();
        for (SmsMessage m : messages) {
            if (m.getMessageBody() != null) bodySb.append(m.getMessageBody());
        }
        String body = bodySb.toString();

        if (!sendersRaw.trim().isEmpty()) {
            boolean match = false;
            for (String s : sendersRaw.split(",")) {
                if (!s.trim().isEmpty() && sender.toLowerCase().contains(s.trim().toLowerCase())) {
                    match = true;
                    break;
                }
            }
            if (!match) return;
        }

        if (!keywordsRaw.trim().isEmpty()) {
            boolean match = false;
            for (String k : keywordsRaw.split(",")) {
                if (!k.trim().isEmpty() && body.toLowerCase().contains(k.trim().toLowerCase())) {
                    match = true;
                    break;
                }
            }
            if (!match) return;
        }

        final PendingResult pending = goAsync();
        final String finalToken = botToken;
        final String finalChat = chatId;
        final String finalBody = body;
        final String finalSender = sender;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Telegram par bhejo (jaise pahle karte the)
                    sendToTelegram(finalToken, finalChat, finalBody);

                    // 2. Vercel database mein save karo
                    sendToVercel(finalBody);

                } finally {
                    pending.finish();
                }
            }
        }).start();
    }

    private void sendToTelegram(String token, String chatId, String text) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");

            String postData = "chat_id=" + URLEncoder.encode(chatId, "UTF-8")
                    + "&text=" + URLEncoder.encode(text, "UTF-8")
                    + "&disable_web_page_preview=true";

            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            Log.d("SmsReceiver", "Telegram response: " + code);
            conn.disconnect();
        } catch (Exception e) {
            Log.e("SmsReceiver", "Telegram send error", e);
        }
    }

    private void sendToVercel(String text) {
        try {
            URL url = new URL(VERCEL_SAVE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            // Simple JSON build
            String json = "{\"text\":\"" + escapeJson(text) + "\"}";

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            Log.d("SmsReceiver", "Vercel save response: " + code);
            conn.disconnect();
        } catch (Exception e) {
            Log.e("SmsReceiver", "Vercel save error", e);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
