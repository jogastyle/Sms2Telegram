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

    @Override
    public void onReceive(Context context, Intent intent) {
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
                if (sender.toLowerCase().contains(s.trim().toLowerCase())) {
                    match = true;
                    break;
                }
            }
            if (!match) return;
        }

        if (!keywordsRaw.trim().isEmpty()) {
            boolean match = false;
            for (String k : keywordsRaw.split(",")) {
                if (body.toLowerCase().contains(k.trim().toLowerCase())) {
                    match = true;
                    break;
                }
            }
            if (!match) return;
        }

        final PendingResult pending = goAsync();
        final String finalSender = sender;
        final String finalBody = body;
        final String finalToken = botToken;
        final String finalChat = chatId;

        new Thread(() -> {
            try {
                sendToTelegram(finalToken, finalChat, finalBody);
            } catch (Exception e) {
                Log.e("SmsReceiver", "Error", e);
            } finally {
                pending.finish();
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
            if (code < 200 || code >= 300) {
                Log.e("SmsReceiver", "Telegram error: " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e("SmsReceiver", "Send error", e);
        }
    }
}
