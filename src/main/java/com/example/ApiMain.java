package com.example;

import com.google.gson.Gson;
import com.microsoft.playwright.*;
import java.util.*;
import java.util.concurrent.*;
import static spark.Spark.*; // 👈 這是網頁伺服器的核心，您原本漏掉了

public class ApiMain {

    // 1. 設定 API Key (請確認這裡的金鑰是正確且有額度的)
    private static final String GOOGLE_API_KEY = "AIzaSyCqNnL9jwvx80f_RYkv9j6nsAhRFnAS384"; 
    private static final String GOOGLE_CX_ID = "053fe703ca8d645f3";

    // 啟動參數
    private static final List<String> LAUNCH_ARGS = Arrays.asList(
        "--disable-blink-features=AutomationControlled",
        "--disable-infobars",
        "--no-sandbox",
        "--disable-gpu"
    );

    // 用來存放爬蟲結果的全域變數 (Cache)
    private static final Gson gson = new Gson();
    private static List<WebTree> CACHED_RESULTS = new ArrayList<>();
    
    // 背景執行緒池 (避免卡住主執行緒)
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {

        // =============================================================
        //  設定網頁伺服器 (這一段是讓 localhost:8080 能動的關鍵)
        // =============================================================
        
        // 1. 設定靜態檔案位置 (讓瀏覽器找得到 html 和 css)
        // 嘗試兩個常見位置，確保它能抓到您的 searchweb.html
        if (ApiMain.class.getResource("/static") != null) {
            staticFiles.location("/static");
        } else {
            // 如果您是直接放在專案根目錄的 src/main/resources/static
            externalStaticFileLocation("src/main/resources/static");
        }
        
        port(8080); // 啟動 8080 port
        get("/", (req, res) -> {
            res.redirect("/searchweb.html");
            return null;
        });

        System.out.println("✅ API 伺服器已啟動: http://localhost:8080");
        System.out.println("✅ API 伺服器已啟動: http://localhost:8080");
        System.out.println("⏳ 等待前端發送搜尋請求...");

        // =============================================================
        //  定義 API 接口
        // =============================================================

        // API 1: 接收前端搜尋請求，並觸發背景爬蟲
        get("/api/search-tree", (request, response) -> {
            String query = request.queryParams("q");
            if (query == null || query.trim().isEmpty()) query = "puma speedcat";
            
            final String keyword = query; // 給執行緒用的 final 變數
            
            System.out.println("收到前端請求，關鍵字: " + keyword);
            
            // 清空舊結果
            CACHED_RESULTS.clear();

            // 🔥 在背景啟動您的「雙線爬蟲」邏輯 (不會卡住網頁)
            EXECUTOR.submit(() -> {
                runCrawlingTask(keyword);
            });

            response.status(202); // 回傳 "處理中" 狀態
            return "{\"status\":\"processing\"}";
        });

        // API 2: 前端來拿結果的地方
        get("/api/results", (request, response) -> {
            response.type("application/json; charset=utf-8");
            return gson.toJson(CACHED_RESULTS);
        });
    }

    // =============================================================
    //  您的核心爬蟲邏輯 (被搬移到這裡)
    // =============================================================
    private static void runCrawlingTask(String keyword) {
        System.out.println("🚀 開始執行背景爬蟲任務...");
        long startTime = System.currentTimeMillis();

        GoogleSearchApi searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);

        // 定義搜尋參數
        String searchQueryNormal = keyword + " 評價"; 
        int targetRootUrlCountNormal = 15; // 建議先設少一點測試速度
        int maxDepthNormal = 1; 

        String searchQueryForum = keyword + " site:ptt.cc OR site:dcard.tw"; 
        int targetRootUrlCountForum = 15; 
        int maxDepthForum = 1; 

        // --- 任務 A: Normal ---
        CompletableFuture<List<WebTree>> normalFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("   [Normal] 執行緒啟動...");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") 
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryNormal, targetRootUrlCountNormal, maxDepthNormal);
            } catch (Exception e) {
                System.err.println("Normal 任務失敗: " + e.getMessage());
                return new ArrayList<>();
            }
        });

        // --- 任務 B: Forum ---
        CompletableFuture<List<WebTree>> forumFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("   [Forum] 執行緒啟動...");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") 
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryForum, targetRootUrlCountForum, maxDepthForum);
            } catch (Exception e) {
                System.err.println("Forum 任務失敗: " + e.getMessage());
                return new ArrayList<>();
            }
        });

        try {
            // 等待完成
            CompletableFuture.allOf(normalFuture, forumFuture).join();
            
            List<WebTree> allTrees = new ArrayList<>();
            allTrees.addAll(normalFuture.get());
            allTrees.addAll(forumFuture.get());

            // 計算分數 (假設您有 ScoreCalculator 類別)
            // 如果沒有，請暫時註解掉這行
             try {
                 for (WebTree webTree : allTrees) {
                     webTree.setScore(ScoreCalculator.calculate(keyword, webTree.getContent()));
                 }
                 // 排序
                 allTrees.sort(Comparator.comparingDouble(WebTree::getScore).reversed());
             } catch (Exception e) {
                 System.out.println("⚠️ 跳過分數計算 (ScoreCalculator 未找到或出錯)");
             }

            // 更新結果 Cache，讓前端可以抓到
            CACHED_RESULTS = allTrees;

            long endTime = System.currentTimeMillis();
            System.out.println("🏁 爬蟲任務完成！共找到 " + allTrees.size() + " 筆資料。耗時: " + (endTime - startTime)/1000.0 + " 秒");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}