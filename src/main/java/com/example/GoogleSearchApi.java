package com.example;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 類別 1: 專門負責 Google Custom Search API
 * 它的工作：取得「根網址」(Root URLs) 列表
 */
public class GoogleSearchApi {

    private final String apiKey;
    private final String cx;
    private static final String API_ENDPOINT = "https://www.googleapis.com/customsearch/v1";

    public GoogleSearchApi(String apiKey, String cx) {
        if (apiKey == null || apiKey.isEmpty() || cx == null || cx.isEmpty()) {
            throw new IllegalArgumentException("API Key 和 CX (自訂搜尋引擎 ID) 不可為空");
        }
        this.apiKey = apiKey;
        this.cx = cx;
    }

    /**
     * 執行搜尋
     *
     * @param query      搜尋關鍵字
     * @param numResults 想要的總結果數 (例如 100)
     * @return URL 列表
     */
    public List<String> search(String query, int numResults) {
        Set<String> urlSet = new HashSet<>();
        // API 限制一次最多 10 筆，所以 'start' 索引 1, 11, 21...
        int start = 1;

        System.out.println("開始使用 Google API 搜尋 '" + query + "'，目標 " + numResults + " 個結果...");

        try {
            while (urlSet.size() < numResults) {
                // Google API 一次最多返回 10 筆
                int numToFetch = Math.min(10, numResults - urlSet.size());
                
                String apiUrl = String.format("%s?key=%s&cx=%s&q=%s&start=%d&num=%d",
                        API_ENDPOINT,
                        apiKey,
                        cx,
                        URLEncoder.encode(query, StandardCharsets.UTF_8),
                        start,
                        numToFetch
                );

                System.out.println("正在呼叫 API (索引 " + start + "): " + apiUrl.substring(0, apiUrl.indexOf("q=") + 10) + "...");

                // Jsoup 也可以用來發送 GET 請求和解析 JSON
                String jsonResponse = Jsoup.connect(apiUrl)
                        .ignoreContentType(true) // 告訴 Jsoup 接受 application/json
                        .timeout(10000)
                        .execute()
                        .body();

                JSONObject json = new JSONObject(jsonResponse);

                if (!json.has("items")) {
                    System.out.println("在索引 " + start + " 找不到更多結果，停止搜尋。");
                    if (json.has("error")) {
                         System.err.println("API 錯誤: " + json.getJSONObject("error").getString("message"));
                    }
                    break;
                }

                JSONArray items = json.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    if (urlSet.size() >= numResults) break;
                    
                    String url = items.getJSONObject(i).getString("link");
                    
                    // 過濾掉不相關的連結，包括系統連結
                    if (!_isExcluded(url)) {
                        urlSet.add(url);
                    }
                }

                // 準備下一次迭代
                start += 10;
                
                // API 有速率限制，每次呼叫之間短暫暫停
                Thread.sleep(500);
            }
        } catch (IOException e) {
            System.err.println("呼叫 API 時發生 IO 錯誤: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("解析 API 回應時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("API 搜尋完畢，共找到 " + urlSet.size() + " 個不重複的 URL。");
        return new ArrayList<>(urlSet);
    }
    
    /**
     * 判斷是否為應排除的網域或系統頁面 
     */
    private boolean _isExcluded(String url) {
        // 1. 排除通用網域
        if (url.contains("google.com") ||
            url.contains("youtube.com") ||
            url.contains("facebook.com") ||
            url.contains("instagram.com") ||
            url.contains("twitter.com") ||
            !url.startsWith("http")) {
            return true;
        }

        // 排除常見的系統/導航頁面
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("/member") ||      // 會員登入/註冊/忘記密碼
            lowerUrl.contains("/login") ||       // 登入頁面
            lowerUrl.contains("/search") ||      // 站內搜尋結果頁
            lowerUrl.contains("/sitemap.aspx") || // 網站地圖
            lowerUrl.contains("/links.aspx") ||  // 外部連結列表
            lowerUrl.contains("/hotarticle.aspx") ||// 熱門文章列表 (非單篇文章)
            lowerUrl.contains("/publish.aspx") || // 某個發佈列表頁面
            lowerUrl.contains("/legal.aspx") ||  // 法律聲明頁面
            lowerUrl.contains("/model.aspx")) { // 模板或模型頁面
             return true;
        }

        return false;
    }
}