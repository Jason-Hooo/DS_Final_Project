package com.example;

import com.google.gson.Gson;
import com.microsoft.playwright.*;
import java.util.*;
import java.util.concurrent.*;
import static spark.Spark.*;

public class ApiMain {

    private static final String GOOGLE_API_KEY = "AIzaSyCqNnL9jwvx80f_RYkv9j6nsAhRFnAS384";
    // AIzaSyDLmb1Ft_jm-i1A2xN2vyhrfFbTx6DRekM
    private static final String GOOGLE_CX_ID = "053fe703ca8d645f3";

    private static final List<String> LAUNCH_ARGS = Arrays.asList(
        "--disable-blink-features=AutomationControlled",
        "--disable-infobars",
        "--no-sandbox",
        "--disable-gpu"
    );

    private static final Gson gson = new Gson();
    private static List<WebTree> CACHED_RESULTS = new ArrayList<>();
    private static List<String> CACHED_EXPANDED_KEYWORDS = new ArrayList<>();
    private static String CACHED_QUERY = "";
    private static volatile boolean IS_PROCESSING = false;
    private static volatile boolean SHOULD_STOP = false;
    private static Future<?> currentTaskFuture;
    
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {

        if (ApiMain.class.getResource("/static") != null) {
            staticFiles.location("/static");
        } else {
            externalStaticFileLocation("src/main/resources/static");
        }
        
        port(8080);
        get("/", (req, res) -> {
            res.redirect("/searchweb.html");
            return null;
        });

        System.out.println(" API 伺服器已啟動: http://localhost:8080");
        System.out.println(" 等待前端發送搜尋請求...");

        post("/api/stop", (request, response) -> {
            System.out.println("🛑 收到停止請求，正在中斷任務...");
            SHOULD_STOP = true;
            
            if (currentTaskFuture != null && !currentTaskFuture.isDone()) {
                currentTaskFuture.cancel(true);
            }
            
            IS_PROCESSING = false;
            return "{\"status\":\"stopped\"}";
        });

        get("/api/search-tree", (request, response) -> {
            if (IS_PROCESSING) {
                System.out.println("⚠️ 發現舊任務仍在執行，強制停止...");
                SHOULD_STOP = true;
                if (currentTaskFuture != null && !currentTaskFuture.isDone()) {
                    currentTaskFuture.cancel(true);
                }
                try { Thread.sleep(500); } catch (Exception e) {}
            }

            String query = request.queryParams("q");
            if (query == null || query.trim().isEmpty()) query = "puma speedcat";
            
            final String keyword = query;
            CACHED_QUERY = keyword;
            
            System.out.println("收到前端請求，關鍵字: " + keyword);
            
            CACHED_RESULTS.clear();
            CACHED_EXPANDED_KEYWORDS.clear();
            IS_PROCESSING = true;
            SHOULD_STOP = false;

            currentTaskFuture = EXECUTOR.submit(() -> {
                try {
                    runCrawlingTask(keyword);
                } finally {
                    IS_PROCESSING = false;
                }
            });

            response.status(202);
            return "{\"status\":\"processing\"}";
        });

        get("/api/results", (request, response) -> {
            response.type("application/json; charset=utf-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("results", CACHED_RESULTS);
            payload.put("expandedKeywords", CACHED_EXPANDED_KEYWORDS);
            payload.put("query", CACHED_QUERY);
            payload.put("status", IS_PROCESSING ? "processing" : "completed");
            return gson.toJson(payload);
        });
    }

    private static void runCrawlingTask(String originalKeyword) {
        if (SHOULD_STOP) return;
        System.out.println(" 開始執行背景爬蟲任務...");
        long startTime = System.currentTimeMillis();

        String keyword = WordExpander.correctKeyword(originalKeyword);
        if (SHOULD_STOP) return;
        
        if (!keyword.equalsIgnoreCase(originalKeyword)) {
            System.out.println("🔄 關鍵字已修正: " + originalKeyword + " -> " + keyword);
            CACHED_QUERY = keyword;
        }

        GoogleSearchApi searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);

        String searchQueryNormal = keyword + "評價"; 
        int targetRootUrlCountNormal = 6;
        int maxDepthNormal = 0; 

        String searchQueryForum = keyword + " site:ptt.cc OR site:dcard.tw"; 
        int targetRootUrlCountForum = 6; 
        int maxDepthForum = 0; 

        CompletableFuture<List<WebTree>> normalFuture = CompletableFuture.supplyAsync(() -> {
            if (SHOULD_STOP) return new ArrayList<>();
            System.out.println("   [Normal] ");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") 
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryNormal, targetRootUrlCountNormal, maxDepthNormal, () -> SHOULD_STOP);
            } catch (Exception e) {
                System.err.println("Normal ");
                return new ArrayList<>();
            }
        });

        CompletableFuture<List<WebTree>> forumFuture = CompletableFuture.supplyAsync(() -> {
            if (SHOULD_STOP) return new ArrayList<>();
            System.out.println("   [Forum] ");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") 
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryForum, targetRootUrlCountForum, maxDepthForum, () -> SHOULD_STOP);
            } catch (Exception e) {
                System.err.println("Forum ");
                return new ArrayList<>();
            }
        });

        try {
            CompletableFuture.allOf(normalFuture, forumFuture).join();
            if (SHOULD_STOP) return;
            
            List<WebTree> allTrees = new ArrayList<>();
            allTrees.addAll(normalFuture.get());
            allTrees.addAll(forumFuture.get());

            try {
                if (SHOULD_STOP) return;
                System.out.println("正在擴展關鍵字...");
                List<String> expandedKeywords = WordExpander.expandKeywords(keyword);
                CACHED_EXPANDED_KEYWORDS = expandedKeywords;
                
                System.out.println("🔍 [DEBUG] 開始計算分數，使用關鍵字: " + keyword);
                
                for (WebTree webTree : allTrees) {
                    if (SHOULD_STOP) break;
                    webTree.setScore(ScoreCalculator.calculate(webTree.getUrl(), keyword, expandedKeywords, webTree.getTitle(), webTree.getContent()));
                }
                 
                if (!SHOULD_STOP) {
                    allTrees.sort(Comparator.comparingDouble(WebTree::getScore).reversed());
                }
            } catch (Exception e) {
                System.out.println(" 跳過分數計算 (ScoreCalculator 未找到或出錯): " + e.getMessage());
                CACHED_EXPANDED_KEYWORDS = Collections.singletonList(keyword);
            }

            if (SHOULD_STOP) {
                System.out.println("🛑 任務已中斷，不更新結果。");
                return;
            }

            CACHED_RESULTS = allTrees;

            long endTime = System.currentTimeMillis();
            System.out.println(" 爬蟲任務完成！共找到 " + allTrees.size() + " 筆資料。耗時: " + (endTime - startTime)/1000.0 + " 秒");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
