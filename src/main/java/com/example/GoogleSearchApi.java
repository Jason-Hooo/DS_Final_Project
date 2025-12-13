package com.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GoogleSearchApi {

    private final String apiKey;
    private final String cx;
    private static final String API_ENDPOINT = "https://www.googleapis.com/customsearch/v1";

    public GoogleSearchApi(String apiKey, String cx) {
        this.apiKey = apiKey;
        this.cx = cx;
    }

    public List<String> search(String query, int numResults) {
        Set<String> urlSet = new HashSet<>();
        int start = 1;

        System.out.println("Google API 搜尋: " + query + ", 目標: " + numResults);

        try {
            while (urlSet.size() < numResults) {
                int numToFetch = Math.min(10, numResults - urlSet.size());
                
                String apiUrl = String.format("%s?key=%s&cx=%s&q=%s&start=%d&num=%d&lr=lang_zh-TW&gl=tw",
                        API_ENDPOINT, apiKey, cx,
                        URLEncoder.encode(query, StandardCharsets.UTF_8), start, numToFetch);

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());

                if (!json.has("items")) break;

                JSONArray items = json.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    if (urlSet.size() >= numResults) break;
                    String linkUrl = items.getJSONObject(i).getString("link");
                    if (!_isExcluded(linkUrl)) {
                        urlSet.add(linkUrl);
                    }
                }
                start += 10;
            }
        } catch (Exception e) {
            System.err.println("Google Search API 錯誤: " + e.getMessage());
        }

        return new ArrayList<>(urlSet);
    }
    
    private boolean _isExcluded(String url) {
        String lower = url.toLowerCase();
        return lower.contains("facebook.com") || lower.contains("instagram.com") || 
               lower.contains("youtube.com") || lower.contains("twitter.com") ||
               lower.contains("/login") || lower.contains("/signup");
    }
}