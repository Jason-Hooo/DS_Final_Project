package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ScoreCalculator {

    // ⚠️ 請確認您的 Python 伺服器網址
    private static final String PYTHON_API_URL = "http://127.0.0.1:5000/calculate_similarity"; 
    
    // 權重設定
    private static final double WEIGHT_TEXT_ANALYSIS = 0.4;
    private static final double WEIGHT_VECTOR_AI = 0.6;

    private static final Gson gson = new Gson();

    /**
     * 主計算方法
     */
    public static double calculate(String keyword, String content) {
        if (content == null || content.isEmpty()) return 0.0;

        // 1. 本地簡單關鍵字計算 (Basic Score)
        double basicScore = calculateBasicScore(keyword, content);

        // 2. 呼叫 Python AI 計算 (Vector Score)
        double aiScore = callPythonAiScore(keyword, content);

        // 3. 加權總分
        return (basicScore * WEIGHT_TEXT_ANALYSIS) + (aiScore * WEIGHT_VECTOR_AI);
    }

    // --- 內部輔助方法 ---

    private static double calculateBasicScore(String keyword, String content) {
        // 簡單計算：關鍵字出現次數 / (文章長度/100)
        int count = content.split(keyword, -1).length - 1;
        // 避免分母為 0
        double lengthFactor = Math.max(1, content.length() / 500.0); 
        return Math.min(100, (count * 10) / lengthFactor);
    }

    private static double callPythonAiScore(String keyword, String content) {
        HttpURLConnection conn = null;
        try {
            // 準備 JSON Payload
            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("keyword", keyword);
            String safeContent = content.length() > 2000 ? content.substring(0, 2000) : content;
            jsonBody.addProperty("text", safeContent);
            String requestBody = gson.toJson(jsonBody);

            // 建立連線 (使用最傳統的 HttpURLConnection)
            URL url = new URL(PYTHON_API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000); // 5秒連線超時
            conn.setReadTimeout(10000);   // 10秒讀取超時

            // 發送 Request Body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 讀取回應
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    // 解析回傳的 JSON
                    JsonObject responseJson = gson.fromJson(response.toString(), JsonObject.class);
                    if (responseJson.has("similarity")) {
                        return responseJson.get("similarity").getAsDouble() * 100;
                    }
                }
            } else {
                System.err.println("⚠️ Python API 回傳錯誤代碼: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("⚠️ 無法連線至 Python AI 伺服器 (跳過 AI 分數): " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return 0.0;
    }
}