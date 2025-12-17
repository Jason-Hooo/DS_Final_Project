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

    public List<WebTree> search(String query, int numResults) {
        List<WebTree> results = new ArrayList<>();
        Set<String> processedUrls = new HashSet<>();
        int start = 1;

        System.out.println("Google API 搜尋: " + query + ", 目標: " + numResults);

        try {
            while (results.size() < numResults) {
                int numToFetch = Math.min(10, numResults - results.size());
                
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
                    if (results.size() >= numResults) break;
                
                    JSONObject item = items.getJSONObject(i);
                    String linkUrl = item.getString("link");
                    
                    if (!_isExcluded(linkUrl) && !processedUrls.contains(linkUrl)) {
                        String title = item.optString("title", "");
                        String snippet = item.optString("snippet", "");
                        String thumbnail = "";
                        
                        if (item.has("pagemap") && item.getJSONObject("pagemap").has("cse_thumbnail")) {
                            JSONArray thumbnails = item.getJSONObject("pagemap").getJSONArray("cse_thumbnail");
                            if (thumbnails.length() > 0) {
                                thumbnail = thumbnails.getJSONObject(0).getString("src");
                            }
                        }
                        
                        if (thumbnail.isEmpty()) {
                            String domain = new URL(linkUrl).getHost();
                            thumbnail = String.format("https://www.google.com/s2/favicons?domain=%s&sz=128", domain);
                        }
                        
                        WebTree webTree = new WebTree(linkUrl, title, snippet, thumbnail);
                        results.add(webTree);
                        processedUrls.add(linkUrl);
                    }
                }
                start += 10;
            }
        } catch (Exception e) {
            System.err.println("Google Search API 錯誤: " + e.getMessage());
        }

        return results;
    }
    
    private boolean _isExcluded(String url) {
        String lower = url.toLowerCase();
        return lower.contains("facebook.com") || lower.contains("instagram.com") || 
               lower.contains("youtube.com") || lower.contains("twitter.com") ||
               lower.contains("/login") || lower.contains("/signup");
    }
}
