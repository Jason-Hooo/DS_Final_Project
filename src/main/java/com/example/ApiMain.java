package com.example;

import com.microsoft.playwright.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ApiMain {

    private static final String GOOGLE_API_KEY = "AIzaSyCqNnL9jwvx80f_RYkv9j6nsAhRFnAS384"; 
    private static final String GOOGLE_CX_ID = "053fe703ca8d645f3";

    private static final String KEYWORD = "puma speedcat";

    // 定義啟動參數 (這裡保留原本設定，這對反爬蟲很重要)
    private static final List<String> LAUNCH_ARGS = Arrays.asList(
        "--disable-blink-features=AutomationControlled",
        "--disable-infobars",
        "--no-sandbox",
        "--disable-gpu"
    );

    public static void main(String[] args) {
        // 1. 開始計時
        long startTime = System.currentTimeMillis();
        System.out.println("⏱️ 程式開始執行...");

        GoogleSearchApi searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);

        // 定義搜尋參數
        String searchQueryNormal = KEYWORD + " 評價"; 
        int targetRootUrlCountNormal = 10; // 為了測試顯示效果，建議先設小一點 (例如 3~5)
        int maxDepthNormal = 1; 

        String searchQueryForum = KEYWORD + " site:ptt.cc OR site:dcard.tw"; 
        int targetRootUrlCountForum = 20; // 為了測試顯示效果，建議先設小一點
        int maxDepthForum = 1; 

        // --- 任務 A: Normal ---
        CompletableFuture<List<WebTree>> normalFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("🚀 [Normal] 執行緒啟動...");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") // 關鍵：使用本機 Chrome
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryNormal, targetRootUrlCountNormal, maxDepthNormal);
            }
        });

        // --- 任務 B: Forum ---
        CompletableFuture<List<WebTree>> forumFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("🚀 [Forum] 執行緒啟動...");
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChannel("chrome") // 關鍵：使用本機 Chrome
                         .setArgs(LAUNCH_ARGS))) {
                
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);
                return extractor.fetchContentTrees(searchQueryForum, targetRootUrlCountForum, maxDepthForum);
            }
        });

        // 等待兩者完成
        CompletableFuture.allOf(normalFuture, forumFuture).join();

        try {
            List<WebTree> siteTreesNormal = normalFuture.get();
            List<WebTree> siteTreesForum = forumFuture.get();
            List<WebTree> webTrees = new ArrayList<>();
            webTrees.addAll(siteTreesNormal);
            webTrees.addAll(siteTreesForum);

            for (WebTree webTree : webTrees) {
                webTree.setScore(ScoreCalculator.calculate(webTree.getContent()));
            }
            


            
            System.out.println("\n\n==========================================");
            System.out.println("📊 爬取結果匯總 (共 " + webTrees.size() + " 個根網頁)");
            System.out.println("==========================================\n");

            int index = 1;
            for (WebTree rootTree : webTrees) {
                // 2. 輸出詳細資訊 (父頁面)
                printPageInfo(index++, "ROOT", rootTree);

                // 3. 輸出子頁面資訊 (如果有的話)
                if (rootTree.getChildren() != null && !rootTree.getChildren().isEmpty()) {
                    System.out.println("   └── 🔗 發現 " + rootTree.getChildren().size() + " 個子連結:");
                    int subIndex = 1;
                    for (WebTree childTree : rootTree.getChildren()) {
                        printPageInfo(subIndex++, "CHILD", childTree);
                    }
                }
                System.out.println("------------------------------------------------------------\n");
            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        // 4. 計算並輸出總執行時間
        long endTime = System.currentTimeMillis();
        double duration = (endTime - startTime) / 1000.0;
        
        System.out.println("🏁 程式執行結束");
        System.out.println("⏱️ 總共耗時: " + duration + " 秒");
    }

    // 輔助方法：統一列印格式
    private static void printPageInfo(int index, String type, WebTree tree) {
        String url = tree.getUrl();
        String content = tree.getContent();
        int wordCount = content != null ? content.length() : 0;
        
        // 為了避免 Console 被塞爆，這裡預設只印出前 200 個字
        // 如果你需要完整內容，請把 substring 的邏輯拿掉，直接印 content
        String contentPreview = "無內容";
        if (content != null && !content.isEmpty()) {
            contentPreview = content;
        }

        String prefix = type.equals("ROOT") ? "🔴 [" + index + "] 主頁面: " : "   🟢 (" + index + ") 子頁面: ";
        
        System.out.println(prefix + url);
        System.out.println((type.equals("ROOT") ? "    " : "      ") + "📄 字數: " + wordCount);
        System.out.println((type.equals("ROOT") ? "    " : "      ") + "📝 內容預覽: " + contentPreview);
        
        // 如果你真的想要印出「全部」內容，請把上面那行註解掉，改用下面這行：
        // System.out.println((type.equals("ROOT") ? "    " : "      ") + "📝 完整內容:\n" + content);
    }
}