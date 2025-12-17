package com.example;

import com.microsoft.playwright.Browser;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ContentExtractor {

    private final GoogleSearchApi searchApi;
    
    private static final int MAX_LINKS_PER_PAGE = 0; 

    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final PageScraper scraper; 

    public ContentExtractor(GoogleSearchApi searchApi, Browser browser) {
        this.searchApi = searchApi;
        this.scraper = new PageScraper(browser);
    }

    public List<WebTree> fetchContentTrees(String searchQuery, int numResults, int maxDepth, java.util.function.Supplier<Boolean> isCancelled) {
        processedCount.set(0);
        List<WebTree> rootNodes = searchApi.search(searchQuery, numResults);
        if (rootNodes.isEmpty()) return new ArrayList<>();

        Set<String> visitedUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());
        
        System.out.println("\n🧹 終極過濾模式：攔截 YouTube / 社交媒體 / 登入驗證頁面...\n");

        List<WebTree> results = new ArrayList<>();

        for (WebTree rootNode : rootNodes) {
            if (isCancelled.get()) {
                System.out.println("🛑 任務已取消，停止爬取。");
                break;
            }

            String url = rootNode.getUrl();
            
            if (isJunkLink(url) || url.contains("footlocker")) continue;
            
            String rootDomain = getDomainName(url);
            if (rootDomain == null) continue;
            
            crawlRecursive(rootNode, rootDomain, maxDepth, visitedUrls, isCancelled);
            
            results.add(rootNode);
        }
        
        return results;
    }

    public List<WebTree> fetchContentTrees(String searchQuery, int numResults, int maxDepth) {
        return fetchContentTrees(searchQuery, numResults, maxDepth, () -> false);
    }
    
    private void crawlRecursive(WebTree node, String targetDomain, int depthLeft, Set<String> visitedUrls, java.util.function.Supplier<Boolean> isCancelled) {
        if (isCancelled.get()) return;
        if (depthLeft < 0) return;
        if (!visitedUrls.add(node.getUrl())) return; 

        int current = processedCount.incrementAndGet();
        System.out.println("  [" + current + "] 🕷️ " + node.getUrl());

        PageData data = scraper.fetchAndParse(node.getUrl());
        
        if (data == null || data.textContent().isEmpty()) return; 

        node.setContent(data.textContent());
        
        if (depthLeft == 0) return;

        int count = 0;
        for (String linkUrl : data.foundLinks()) {
            if (isCancelled.get()) return;
            if (count >= MAX_LINKS_PER_PAGE) break; 
            
            if (isJunkLink(linkUrl)) continue;

            String linkDomain = getDomainName(linkUrl);
            
            if (linkDomain != null && linkDomain.equals(targetDomain) && !visitedUrls.contains(linkUrl)) {
                WebTree childNode = new WebTree(linkUrl);
                node.addChild(childNode);
                
                try { Thread.sleep(250); } catch (Exception e) {}
                
                crawlRecursive(childNode, targetDomain, depthLeft - 1, visitedUrls, isCancelled);
                count++;
            }
        }
    }

    private boolean isJunkLink(String url) {
        String lower = url.toLowerCase();
        
        if (lower.contains("youtube.com") || lower.contains("youtu.be") || 
            lower.contains("facebook.com") || lower.contains("instagram.com") || 
            lower.contains("twitter.com") || lower.contains("tiktok.com") ||
            lower.contains("linkedin.com") || lower.contains("pinterest.com")) {
            return true;
        }

        if (lower.contains("/login") || lower.contains("/signin") || lower.contains("/sign-in") ||
            lower.contains("/signup") || lower.contains("/register") || lower.contains("/auth") ||
            lower.contains("/password") || lower.contains("/account") || lower.contains("/profile") ||
            lower.contains("/verification") || lower.contains("/challenge") ||
            lower.contains("/wishlist") || lower.contains("/favorites")) {
            return true;
        }
        
        if (lower.contains("/cart") || lower.contains("/checkout") || lower.contains("/basket") || 
            lower.contains("/order")) {
            return true;
        }
        
        if (lower.contains("help") || lower.contains("contact") || lower.contains("support") ||
            lower.contains("faq") || lower.contains("delivery") || lower.contains("shipping") ||
            lower.contains("returns") || lower.contains("size-guide") || lower.contains("size-chart") ||
            lower.contains("store-locator") || lower.contains("locations") ||
            lower.contains("sitemap") || lower.contains("careers") || lower.contains("jobs") || 
            lower.contains("about") || lower.contains("terms") || lower.contains("privacy") || 
            lower.contains("legal") || lower.contains("cookie") || lower.contains("accessibility") ||
            lower.contains("share")) {
            return true;
        }

        return false;
    }
    
    private String getDomainName(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) { return null; }
    }
}
