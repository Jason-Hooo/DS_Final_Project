package com.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import static spark.Spark.*;
import org.jsoup.Jsoup;

/**
 * (資料類別) 用於定義從 Python API 接收的「扁平」資料結構
 * 這只包含「母體」和它們的「加總分數」
 */
record RankedParentPage(String url, double aggregatedScore, Map<String, Integer> keywordCounts) {}


/**
 * 類別 6 (已更新): API 伺服器主程式
 * 它的工作：啟動 Web 伺服器，提供 API 端點給前端 JS 呼叫。
 */
public class ApiMain {

    // !! 重要：請將這裡替換為您自己的金鑰 !!
    private static final String GOOGLE_API_KEY = "在這裡貼上您的_API_KEY";
    private static final String GOOGLE_CX_ID = "在這裡貼上您的_CX_ID";

    // --- 假設您朋友的 Python 伺服器跑在... ---
    private static final String PYTHON_API_URL = "http://localhost:5000/rank"; 

    // --- 建立共用的服務物件 ---
    private static GoogleSearchApi searchApi;
    private static PageScraper scraper;
    private static ContentExtractor extractor;
    private static Gson gson = new Gson(); // 用於轉換 JSON

    public static void main(String[] args) {
        
        // 檢查金鑰是否已設定
        if (GOOGLE_API_KEY.startsWith("在這裡") || GOOGLE_CX_ID.startsWith("在這裡")) {
            System.err.println("!!! 錯誤：請先在 ApiMain.java 中設定 GOOGLE_API_KEY 和 GOOGLE_CX_ID !!!");
            return;
        }

        // 1. 建立所有後端物件
        searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);
        scraper = new PageScraper();
        extractor = new ContentExtractor(searchApi, scraper);

        // 2. 設定伺服器監聽的埠號 (例如 8080)
        port(8080);
        
        System.out.println("伺服器已啟動於 http://localhost:8080");
        System.out.println("前端 JS 請呼叫: /api/search-tree?q=關鍵字&num=5&depth=2");

        // 3. 建立 API 端點 (Endpoint)： /api/search-tree
        get("/api/search-tree", (request, response) -> {
            
            try {
                // 4. 從 JS 傳來的 URL 中取得參數
                String query = request.queryParams("q");
                int numResults = parseIntWithDefault(request.queryParams("num"), 5);
                int maxDepth = parseIntWithDefault(request.queryParams("depth"), 2); // 預設深度為 2

                if (query == null || query.isEmpty()) {
                    response.status(400); // Bad Request
                    return "{\"error\":\"缺少 'q' (關鍵字) 參數\"}";
                }

                System.out.println("收到 API 請求：q=" + query + ", num=" + numResults + ", depth=" + maxDepth);

                // 5. 執行您核心的「樹狀爬取」邏輯
                // (這一步不變，我們仍需抓取完整的樹)
                List<WebTree> siteTrees = extractor.fetchContentTrees(query, numResults, maxDepth);

                if (siteTrees.isEmpty()) {
                    response.type("application/json; charset=utf-8");
                    return "[]"; // 回傳空列表
                }
                
                // 6. 將「完整的樹」轉為 JSON，準備傳給 Python
                String treesAsJson = gson.toJson(siteTrees);

                // 7. !! 呼叫 Python API 進行評分 !!
                // (我們仍將「完整的樹」傳給 Python，以便它能加總分數)
                System.out.println("正在呼叫 Python API (" + PYTHON_API_URL + ") 進行加總評分...");
                
                String pythonResponseJson = Jsoup.connect(PYTHON_API_URL)
                        .requestBody(treesAsJson) // 將「完整的樹」(JSON) 放入請求主體
                        .header("Content-Type", "application/json") 
                        .ignoreContentType(true) 
                        .timeout(30000) // 評分可能很慢，給予 30 秒超時
                        .post()         
                        .body()         
                        .text();        
                
                System.out.println("Python API 回應完畢。");

                // 8. !! 關鍵修改 !!
                // 我們不再解析一棵樹，而是解析 Python 回傳的「扁平列表」
                // 我們使用上面定義的 `RankedParentPage` record
                TypeToken<List<RankedParentPage>> listType = new TypeToken<List<RankedParentPage>>() {};
                List<RankedParentPage> rankedFlatResults = gson.fromJson(pythonResponseJson, listType.getType());

                // 9. 將這個「扁平的、已排好序的」列表回傳給前端 JS
                response.type("application/json; charset=utf-8");
                return gson.toJson(rankedFlatResults);

            } catch (Exception e) {
                e.printStackTrace();
                response.status(500); // Internal Server Error
                return "{\"error\":\"伺服器內部錯誤: " + e.getMessage() + "\"}";
            }
        });
    }

    /**
     * (輔助方法) 安全地將字串轉為整數，若失敗則回傳預設值
     */
    private static int parseIntWithDefault(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            return defaultValue; // 若前端沒傳或格式錯誤，使用預設值
        }
    }
}