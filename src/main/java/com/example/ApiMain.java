package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject; // 👈 新增這個
import com.google.gson.reflect.TypeToken;
import com.microsoft.playwright.*; // 👈 新增 Playwright 相關引用

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

// 定義資料結構
record RankedParentPage(String url, double aggregatedScore, Map<String, Integer> keywordCounts) {}

public class ApiMain {

    // ⚠️ 請確認這裡填入正確的金鑰
    private static final String GOOGLE_API_KEY = "您的_API_KEY";
    private static final String GOOGLE_CX_ID = "您的_CX_ID";
    private static final String PYTHON_API_URL = "http://localhost:5000/rank"; 

    // 全域變數
    private static GoogleSearchApi searchApi;
    private static ContentExtractor extractor;
    private static Gson gson = new Gson();
    
    // 🔥 Playwright 核心 (全域唯一)
    private static Playwright playwright;
    private static Browser browser;

    // 執行緒池 (負責處理 API 請求的背景任務)
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8); 
    private static volatile List<RankedParentPage> CACHED_RESULTS = new ArrayList<>();

    public static void main(String[] args) {
        
        if (GOOGLE_API_KEY.startsWith("您的") || GOOGLE_CX_ID.startsWith("您的")) {
            System.err.println("!!! 錯誤：請先設定 API KEY !!!");
            return;
        }

        // ==========================================
        // 1. 初始化 Playwright (單一引擎架構)
        // ==========================================
        System.out.println("⏳ 正在啟動伺服器核心瀏覽器...");
        try {
            playwright = Playwright.create();
            List<String> argsList = Arrays.asList(
                "--disable-blink-features=AutomationControlled",
                "--disable-infobars",
                "--no-sandbox",
                "--disable-gpu"
            );
            // 啟動一個 Headless Chrome
            browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true).setArgs(argsList)
            );
            System.out.println("✅ 瀏覽器啟動成功！");
        } catch (Exception e) {
            System.err.println("❌ 瀏覽器啟動失敗: " + e.getMessage());
            System.exit(1); // 如果瀏覽器開不起來，伺服器就不用跑了
        }

        // ==========================================
        // 2. 初始化其他元件
        // ==========================================
        searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);
        
        // 🔥 關鍵：把 browser 傳進去，讓所有爬蟲任務共用
        extractor = new ContentExtractor(searchApi, browser);
        
        // ==========================================
        // 3. 設定關閉掛鉤 (Shutdown Hook)
        // ==========================================
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("正在關閉伺服器...");
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
            EXECUTOR.shutdown();
            System.out.println("資源已釋放。");
        }));

        // ==========================================
        // 4. 啟動 Web Server
        // ==========================================
        port(8080);
        System.out.println("API 伺服器已啟動: http://localhost:8080");

        staticFiles.location("/static");// 提供靜態檔案服務 (前端頁面)

        // API: 觸發搜尋與爬取
        get("/api/search-tree", (request, response) -> {
            String query = request.queryParams("q");
            int numResults = parseIntWithDefault(request.queryParams("num"), 5);
            int maxDepth = parseIntWithDefault(request.queryParams("depth"), 2);

            if (query == null || query.isEmpty()) {
                response.status(400);
                return "{\"error\":\"缺少 'q' 參數\"}";
            }

            System.out.println("收到請求：q=" + query + ", num=" + numResults + ", depth=" + maxDepth);

            // 提交背景任務
            EXECUTOR.submit(() -> {
                try {
                    System.out.println("開始執行背景爬蟲任務...");
                    List<WebTree> siteTrees = extractor.fetchContentTrees(query, numResults, maxDepth);

                    if (!siteTrees.isEmpty()) {
                        // 🔥 準備傳給 Python 的資料包 (包含 query 和 trees)
                        JsonObject payload = new JsonObject();
                        payload.addProperty("query", query); // 傳入關鍵字供 Python 評分使用
                        payload.add("trees", gson.toJsonTree(siteTrees));

                        String jsonPayload = gson.toJson(payload);

                        try {
                            // 呼叫 Python 計算分數
                            System.out.println("正在呼叫 Python API 進行評分...");
                            String pythonResponseJson = callPythonApi(PYTHON_API_URL, jsonPayload);
                            
                            // 解析回傳結果
                            TypeToken<List<RankedParentPage>> listType = new TypeToken<List<RankedParentPage>>() {};
                            CACHED_RESULTS = gson.fromJson(pythonResponseJson, listType.getType());
                            System.out.println("✅ 任務完成！已更新快取，共 " + CACHED_RESULTS.size() + " 筆結果。");
                            
                        } catch (IOException e) {
                            System.err.println("❌ Python API 連線失敗 (請確認 Python Server 有開): " + e.getMessage());
                            // 即使 Python 失敗，如果不介意，也可以先把未排序的樹存起來或做簡單處理
                        }
                    } else {
                        System.out.println("⚠️ 爬蟲未抓取到任何有效資料。");
                        CACHED_RESULTS = new ArrayList<>();
                    }
                } catch (Exception e) {
                    System.err.println("❌ 背景任務發生例外: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            response.status(202); // 202 Accepted
            response.type("application/json; charset=utf-8");
            return "{\"status\":\"processing\", \"message\":\"Search started.\"}";
        });
        
        // API: 獲取結果
        get("/api/results", (request, response) -> {
            response.type("application/json; charset=utf-8");
            // 允許跨域 (CORS) 以便前端呼叫
            response.header("Access-Control-Allow-Origin", "*"); 
            return gson.toJson(CACHED_RESULTS);
        });
    }
    
    // 輔助方法：呼叫 Python API
    private static String callPythonApi(String apiUrl, String jsonPayload) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        // 設定較長的超時，因為爬蟲+運算可能需要時間
        conn.setConnectTimeout(10000); 
        conn.setReadTimeout(60000); 

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Python API error code: " + responseCode);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);
            return response.toString();
        }
    }

    private static int parseIntWithDefault(String s, int defaultValue) {
        try { return Integer.parseInt(s); } catch (Exception e) { return defaultValue; }
    }
}