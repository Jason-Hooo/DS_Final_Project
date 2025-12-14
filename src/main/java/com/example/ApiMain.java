package com.example;

import com.google.gson.Gson;
import com.microsoft.playwright.*;
import java.util.*;
import java.util.concurrent.*;
import static spark.Spark.*; // 

public class ApiMain {

    // 1. (請確認這裡的金鑰是正確且有額度的)
    private static final String GOOGLE_API_KEY = "AIzaSyDLmb1Ft_jm-i1A2xN2vyhrfFbTx6DRekM";
    // "AIzaSyDLmb1Ft_jm-i1A2xN2vyhrfFbTx6DRekM"; 
    // "AIzaSyCqNnL9jwvx80f_RYkv9j6nsAhRFnAS384"
    private static final String GOOGLE_CX_ID = "053fe703ca8d645f3";

    // 
    private static final List<String> LAUNCH_ARGS = Arrays.asList(
        "--disable-blink-features=AutomationControlled",
        "--disable-infobars",
        "--no-sandbox",
        "--disable-gpu"
    );

    // (Cache)
    private static final Gson gson = new Gson();
    private static List<WebTree> CACHED_RESULTS = new ArrayList<>();
    private static List<String> CACHED_EXPANDED_KEYWORDS = new ArrayList<>();
    private static String CACHED_QUERY = "";
    
    // (避免卡住主執行緒)
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {

        // =============================================================
        //  (這一段是讓 localhost:8080 能動的關鍵)
        // =============================================================
        
        // 1. (讓瀏覽器找得到 html 和 css)
        // 
        if (ApiMain.class.getResource("/static") != null) {
            staticFiles.location("/static");
        } else {
            // 
            externalStaticFileLocation("src/main/resources/static");
        }
        
        port(8080); // 8080 port
        get("/", (req, res) -> {
            res.redirect("/searchweb.html");
            return null;
        });

        System.out.println(" API 伺服器已啟動: http://localhost:8080");
        System.out.println(" API 伺服器已啟動: http://localhost:8080");
        System.out.println(" 等待前端發送搜尋請求...");

        // =============================================================
        //  API 
        // =============================================================

        // API 1: (觸發背景爬蟲)
        get("/api/search-tree", (request, response) -> {
            String query = request.queryParams("q");
            if (query == null || query.trim().isEmpty()) query = "puma speedcat";
            
            final String keyword = query; // 
            CACHED_QUERY = keyword;
            
            System.out.println("收到前端請求，關鍵字: " + keyword);
            
            // 
            CACHED_RESULTS.clear();
            CACHED_EXPANDED_KEYWORDS.clear();

            // 在背景啟動您的「雙線爬蟲」邏輯 (不會卡住網頁)
            EXECUTOR.submit(() -> {
                runCrawlingTask(keyword);
            });

            response.status(202); // "處理中" 狀態
            return "{\"status\":\"processing\"}";
        });

        // API 2: (拿結果的地方)
        get("/api/results", (request, response) -> {
            response.type("application/json; charset=utf-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("results", CACHED_RESULTS);
            payload.put("expandedKeywords", CACHED_EXPANDED_KEYWORDS);
            payload.put("query", CACHED_QUERY);
            return gson.toJson(payload);
        });
    }

    // =============================================================
    //  您的核心爬蟲邏輯 (被搬移到這裡)
    // =============================================================
    private static void runCrawlingTask(String keyword) {
        System.out.println(" 開始執行背景爬蟲任務...");
        long startTime = System.currentTimeMillis();

        GoogleSearchApi searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);

        // 
        String searchQueryNormal = keyword + "評價"; 
        int targetRootUrlCountNormal = 6; // 
        int maxDepthNormal = 0; 

        String searchQueryForum = keyword + " site:ptt.cc OR site:dcard.tw"; 
        int targetRootUrlCountForum = 6; 
        int maxDepthForum = 0; 

        // --- 任務 A: Normal ---
        CompletableFuture<List<WebTree>> normalFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("   [Normal] ");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") 
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryNormal, targetRootUrlCountNormal, maxDepthNormal);
            } catch (Exception e) {
                System.err.println("Normal ");
                return new ArrayList<>();
            }
        });

        // --- 任務 B: Forum ---
        CompletableFuture<List<WebTree>> forumFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("   [Forum] ");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") 
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryForum, targetRootUrlCountForum, maxDepthForum);
            } catch (Exception e) {
                System.err.println("Forum ");
                return new ArrayList<>();
            }
        });

        try {
            // 
            CompletableFuture.allOf(normalFuture, forumFuture).join();
            
            List<WebTree> allTrees = new ArrayList<>();
            allTrees.addAll(normalFuture.get());
            allTrees.addAll(forumFuture.get());

            // (假設您有 ScoreCalculator )
            // 如果沒有，請暫時註解掉這行

            try {
                System.out.println("正在擴展關鍵字...");
                List<String> expandedKeywords = WordExpander.expandKeywords(keyword);
                CACHED_EXPANDED_KEYWORDS = expandedKeywords;
                for (WebTree webTree : allTrees) {
                    webTree.setScore(ScoreCalculator.calculate(keyword, expandedKeywords, webTree.getTitle(), webTree.getContent()));
                }
                 
                allTrees.sort(Comparator.comparingDouble(WebTree::getScore).reversed());
            } catch (Exception e) {
                System.out.println(" 跳過分數計算 (ScoreCalculator 未找到或出錯): " + e.getMessage());
                CACHED_EXPANDED_KEYWORDS = Collections.singletonList(keyword);
            }

            // Cache，讓前端可以抓到
            CACHED_RESULTS = allTrees;

            long endTime = System.currentTimeMillis();
            System.out.println(" 爬蟲任務完成！共找到 " + allTrees.size() + " 筆資料。耗時: " + (endTime - startTime)/1000.0 + " 秒");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}