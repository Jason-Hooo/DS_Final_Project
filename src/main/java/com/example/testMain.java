package com.example;

import java.util.List;

/**
 * 類別 5: 專案的執行入口 (本機測試用)
 * (此類別無需變動，它讓我們能驗證「樹」是否被正確抓取)
 */
public class testMain {

    // !! 重要：請將這裡替換為您自己的金鑰 !!
    private static final String GOOGLE_API_KEY = "AIzaSyDLmb1Ft_jm-i1A2xN2vyhrfFbTx6DRekM";
    private static final String GOOGLE_CX_ID = "02dc93011f4fd4726";


    public static void main(String[] args) {
        
        // 檢查金鑰是否已設定
        if (GOOGLE_API_KEY.startsWith("在這裡") || GOOGLE_CX_ID.startsWith("在這裡")) {
            System.err.println("!!! 錯誤：請先在 Main.java 中設定 GOOGLE_API_KEY 和 GOOGLE_CX_ID !!!");
            return;
        }

        // 1. 建立各個工具類別的實例
        GoogleSearchApi searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);
        PageScraper scraper = new PageScraper();

        // 2. 建立協調者
        ContentExtractor extractor = new ContentExtractor(searchApi, scraper);

        // 3. 設定您要搜尋的主要關鍵字
        String searchQuery = "puma speedcat 評測";
        
        // 4. 設定您想抓取的「根網址」數量
        int targetRootUrlCount = 2; // (測試時建議用 3-5 個)
        
        // 5. !! 新增：設定爬取深度 !!
        // 1 = 只爬 Google 搜到的頁面
        // 2 = 爬 Google 搜到的頁面 + 該頁面下的子頁面
        int maxDepth = 1; // 可以在測試時調整這個值

        System.out.println("準備執行任務：搜尋 '" + searchQuery + "'，抓取 " + targetRootUrlCount + " 個根網站，深度為 " + maxDepth + "...");

        // 6. 執行內容提取
        List<WebTree> siteTrees = extractor.fetchContentTrees(searchQuery, targetRootUrlCount, maxDepth);

        // 7. 輸出結果 (樹狀結構)
        System.out.println("\n\n========= 爬取結果總覽 (共 " + siteTrees.size() + " 棵樹) =========");
        
        if (siteTrees.isEmpty()) {
            System.out.println("沒有抓取到任何資料，請檢查 API 金鑰和網路連線。");
        } else {
            int treeCount = 1;
            for (WebTree rootNode : siteTrees) {
                System.out.println("\n--- [樹 " + (treeCount++) + "] ---");
                // 呼叫輔助函式來印出樹
                printTree(rootNode, ""); 
            }
        }
        
        System.out.println("\n========= 任務結束 =========");
    }
    
    /**
     * (輔助方法) 遞迴印出樹狀結構
     */
    private static void printTree(WebTree node, String indent) {
        if (node == null) return;
        
        // 取得內容片段
        String content = node.getContent();
        String contentSnippet = "(內容為空)";
        if (content != null && !content.isEmpty()) {
            contentSnippet = content.length() > 60 ? content.substring(0, 60) + "..." : content;
            contentSnippet = contentSnippet.replace("\n", " ").trim();
        }

        // 印出當前節點
        System.out.println(indent + "+ " + node.getUrl());
        System.out.println(indent + "  L 內容: " + contentSnippet);

        // 遞迴印出子節點
        for (WebTree child : node.getChildren()) {
            printTree(child, indent + "    "); // 增加縮排
        }
    }
}