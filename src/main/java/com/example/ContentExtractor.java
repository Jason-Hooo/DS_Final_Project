package com.example;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 類別 4: 協調者 (Orchestrator) / 蜘蛛 (Spider)
 * 它的工作：指揮 API 和 Scraper，並執行「遞迴」爬取來建立子母關係樹。
 * (此類別無需變動)
 */
public class ContentExtractor {

    private final GoogleSearchApi searchApi;
    private final PageScraper scraper;

    /**
     * 建構函數：傳入它需要指揮的工具
     */
    public ContentExtractor(GoogleSearchApi searchApi, PageScraper scraper) {
        this.searchApi = searchApi;
        this.scraper = scraper;
    }

    /**
     * (公開方法) 執行完整的搜尋和「樹狀」內容提取流程。
     *
     * @param searchQuery 要在 Google 上搜尋的初始關鍵字
     * @param numResults  目標「根網址」數量
     * @param maxDepth    爬取深度 (例如：1=只爬根網址, 2=爬到子網頁)
     * @return 一個 List，包含每個根網址的「網站樹」(WebTree)
     */
    public List<WebTree> fetchContentTrees(String searchQuery, int numResults, int maxDepth) {
        
        // 步驟 1: 指揮 searchApi 取得「根」URL 列表
        List<String> rootUrls = searchApi.search(searchQuery, numResults);
        if (rootUrls.isEmpty()) {
            return new ArrayList<>();
        }

        List<WebTree> siteTrees = new ArrayList<>();
        // 用一個 Set 來追蹤所有爬過的 URL，避免重複和無限迴圈
        Set<String> visitedUrls = new HashSet<>(); 

        // 步驟 2: 為每一個「根網址」啟動一次遞迴爬取
        for (String url : rootUrls) {
            System.out.println("\n========= 開始處理根網域: " + url + " (深度 " + maxDepth + ") =========");
            
            // 取得根網域，用於限制爬取範圍
            String rootDomain = getDomainName(url);
            if (rootDomain == null) {
                System.err.println("無法解析網域: " + url + "，跳過。");
                continue;
            }

            WebTree rootNode = new WebTree(url);
            siteTrees.add(rootNode);
            
            // 啟動遞迴爬取
            crawlRecursive(rootNode, rootDomain, maxDepth, visitedUrls);
        }
        return siteTrees;
    }
    
    /**
     * (私有方法) 遞迴爬取的核心邏輯
     * @param node         當前要爬的節點
     * @param targetDomain 必須符合這個網域才能繼續爬
     * @param depthLeft    剩餘的深度
     * @param visitedUrls  全域的已訪問列表
     */
    private void crawlRecursive(WebTree node, String targetDomain, int depthLeft, Set<String> visitedUrls) {
        
        // --- 停止條件 ---
        if (depthLeft <= 0) {
            System.out.println("  (深度已達 0，停止在 " + node.getUrl() + ")");
            return;
        }
        
        if (visitedUrls.contains(node.getUrl())) {
            System.out.println("  (已訪問過 " + node.getUrl() + "，跳過)");
            return;
        }
        
        // --- 執行爬取 ---
        System.out.println("  -> 正在爬取 (深度 " + depthLeft + "): " + node.getUrl());
        visitedUrls.add(node.getUrl()); // 標記為已訪問

        PageData data = scraper.fetchAndParse(node.getUrl());
        if (data == null || data.textContent().isEmpty()) {
            System.out.println("  (無法取得內容，停止此分支)");
            return; // 爬取失敗，停止此分支
        }

        // 儲存爬取到的內容
        node.setContent(data.textContent());

        // --- 尋找下一個目標 (遞迴) ---
        for (String linkUrl : data.foundLinks()) {
            
            // 檢查是否符合條件
            String linkDomain = getDomainName(linkUrl);
            
            if (linkDomain != null && 
                linkDomain.equals(targetDomain) && // 1. 必須在同一個根網域下
                !visitedUrls.contains(linkUrl))    // 2. 必須是尚未訪問過的
            {
                // 符合條件，建立子節點並準備遞迴
                WebTree childNode = new WebTree(linkUrl);
                node.addChild(childNode);
                
                // 遞迴呼叫
                crawlRecursive(childNode, targetDomain, depthLeft - 1, visitedUrls);
            }
        }
    }
    
    /**
     * (輔助方法) 從 URL 中提取主網域
     * (例如 "https://www.example.com/page" -> "example.com")
     */
    private String getDomainName(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;
            // 處理 "www.example.com" 和 "example.com"
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException e) {
            System.err.println("URL 格式錯誤: " + url);
            return null;
        }
    }
}