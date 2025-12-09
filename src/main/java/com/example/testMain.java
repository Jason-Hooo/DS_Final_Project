package com.example;

import com.microsoft.playwright.*;
import java.util.Arrays;
import java.util.List;

public class testMain {

    private static final String GOOGLE_API_KEY = "AIzaSyDLmb1Ft_jm-i1A2xN2vyhrfFbTx6DRekM";
    private static final String GOOGLE_CX_ID = "02dc93011f4fd4726";

    public static void main(String[] args) {
        if (GOOGLE_API_KEY.startsWith("您的")) {
            System.err.println("請設定 API KEY");
            return;
        }

        System.out.println("⏳ 正在啟動 Playwright 引擎 (全域唯一)...");

        // 🔥 1. 在這裡啟動，且只啟動一次！
        try (Playwright playwright = Playwright.create()) {
            
            List<String> argsList = Arrays.asList(
                "--disable-blink-features=AutomationControlled",
                "--disable-infobars",
                "--no-sandbox",
                "--disable-gpu"
            );

            try (Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true).setArgs(argsList))) {

                System.out.println("✅ 引擎啟動成功！開始分發任務...");

                GoogleSearchApi searchApi = new GoogleSearchApi(GOOGLE_API_KEY, GOOGLE_CX_ID);
                
                // 🔥 2. 把這個唯一的 browser 傳進去
                ContentExtractor extractor = new ContentExtractor(searchApi, browser);

                String searchQuery = "puma speedcat"; 
                int targetRootUrlCount = 20; 
                int maxDepth = 1; 

                long start = System.currentTimeMillis();
                List<WebTree> siteTrees = extractor.fetchContentTrees(searchQuery, targetRootUrlCount, maxDepth);
                long end = System.currentTimeMillis();

                System.out.println("\n========= 總耗時: " + (end - start) / 1000 + " 秒 =========");

                if (siteTrees.isEmpty()) {
                    System.out.println("沒抓到資料");
                } else {
                    System.out.println("共抓到 " + siteTrees.size() + " 棵樹：");
                    for (WebTree root : siteTrees) {
                        printTree(root, "");
                    }
                }
            } 
        } 
        System.out.println("🛑 程式結束。");
    }
    
    private static void printTree(WebTree node, String indent) {
        if (node == null) return;
        
        String content = node.getContent();
        int len = (content != null) ? content.length() : 0;
        
        String preview = (len > 0) ? content.trim().substring(0, Math.min(content.length(), 50)) + "..." : "(無內容)";
        System.out.println(indent + "+ " + node.getUrl() + " [" + len + "字] 👉 " + preview);

        for (WebTree child : node.getChildren()) {
            printTree(child, indent + "  ");
        }
    }
}