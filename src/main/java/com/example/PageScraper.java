package com.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// 定義回傳資料結構
record PageData(String textContent, List<String> foundLinks) {}

public class PageScraper {

    private final Browser browser;
    // 忽略檔案格式
    private final Pattern IGNORED_EXTENSIONS = Pattern.compile(".*\\.(jpg|jpeg|png|gif|bmp|svg|mp4|avi|mov|pdf|zip|rar|exe|iso|css|js)$", Pattern.CASE_INSENSITIVE);

    public PageScraper(Browser browser) {
        this.browser = browser;
    }

    public PageData fetchAndParse(String url) {
        if (browser == null) return null;
        if (IGNORED_EXTENSIONS.matcher(url).matches()) return null;

        // 1. 設定 Context (保留你的隱身設定)
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("zh-TW")
                .setJavaScriptEnabled(true);

        try (BrowserContext context = browser.newContext(contextOptions)) {

            // [隱身術] 注入腳本騙過 Cloudflare / Bot Detection
            context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
            context.addInitScript("window.navigator.chrome = { runtime: {} };");

            // [加速] 阻擋圖片、字型、媒體
            context.route("**/*", route -> {
                String type = route.request().resourceType();
                if ("image".equals(type) || "media".equals(type) || "font".equals(type) || "stylesheet".equals(type)) {
                    route.abort();
                } else {
                    route.resume();
                }
            });

            Page page = context.newPage();
            // 設定全域超時，避免卡死
            page.setDefaultTimeout(15000);

            try {
                // 2. 導航：只等待 DOM 載入，不等待圖片
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(30000));

                // 3. [核心優化] 智慧等待取代暴力等待
                if (url.contains("dcard.tw")) {
                    handleDcardSmartWait(page);
                } else {
                    handleGeneralSmartWait(page);
                }

                // 4. 抓取 HTML 並交給 Jsoup 解析
                String html = page.content();
                return parseHtml(html, url);

            } catch (PlaywrightException e) {
                System.err.println("❌ Playwright 錯誤 (" + url + "): " + e.getMessage());
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 專門處理 Dcard 的動態載入邏輯 (優化版)
     */
    private void handleDcardSmartWait(Page page) {
        try {
            // [優化] 不要死等 4 秒。
            // 嘗試等待文章的核心標籤 <article> 出現，最多等 5 秒。
            // 一旦出現就代表內容載入完畢，立刻往下執行。
            try {
                page.waitForSelector("article", new Page.WaitForSelectorOptions().setTimeout(5000));
            } catch (PlaywrightException e) {
                // 如果 5 秒還沒出現 article (可能是列表頁或驗證頁)，就繼續，不報錯
            }

            // [優化] 快速滾動
            // 讓 JS 觸發 lazy loading，但間隔縮短
            for (int i = 0; i < 3; i++) {
                page.mouse().wheel(0, 1500);
                // 只需極短暫停讓 React/Vue 渲染 DOM
                page.waitForTimeout(200); 
            }
        } catch (Exception e) {
            // 忽略滾動錯誤
        }
    }

    /**
     * 處理一般網站的等待邏輯
     */
    private void handleGeneralSmartWait(Page page) {
        try {
            // [優化] 等待網路閒置 (Network Idle)，代表 JS 跑得差不多了
            // 設定 3 秒超時：如果 3 秒內網路沒停下來(例如有廣告一直閃)，也不管了直接抓
            page.waitForLoadState(LoadState.NETWORKIDLE, 
                new Page.WaitForLoadStateOptions().setTimeout(3000));
        } catch (PlaywrightException e) {
            // Timeout 是預期行為，代表網頁比較慢，但我們仍可抓取當下內容
        }
    }

    /**
     * Jsoup 解析邏輯 (保持不變，負責清洗雜訊)
     */
    private PageData parseHtml(String html, String url) {
        Document doc = Jsoup.parse(html, url);
        Element body = doc.body();

        String text = "";
        List<String> links = new ArrayList<>();

        if (body != null) {
            // 移除雜訊
            body.select("script, style, noscript, iframe, svg, button, form, .ad, header, footer, nav").remove();

            text = body.text();

            // 抓取連結
            Elements linkElements = body.select("a[href]");
            for (Element link : linkElements) {
                String absUrl = link.attr("abs:href");
                if (isValidLink(absUrl)) {
                    links.add(absUrl);
                }
            }
        }

        // 檢查 Cloudflare 驗證失敗
        if (text.contains("Checking your connection") || text.contains("Dcard 需要確認")) {
            System.out.println("⚠️ [警告] 內容似乎是 Cloudflare 驗證頁面: " + url);
        }

        return new PageData(text, links);
    }

    private boolean isValidLink(String url) {
        return url.startsWith("http") 
                && !url.contains("#") 
                && !IGNORED_EXTENSIONS.matcher(url).matches();
    }
}

// 原版

// package com.example;

// import com.microsoft.playwright.*;
// import com.microsoft.playwright.options.WaitUntilState;
// import org.jsoup.Jsoup;
// import org.jsoup.nodes.Document;
// import org.jsoup.nodes.Element;
// import org.jsoup.select.Elements;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.Random;

// record PageData(String textContent, List<String> foundLinks) {}

// public class PageScraper {
    
//     // 這裡只存別人傳給我的 Browser，我不自己建立
//     private final Browser browser; 
//     private final Random random = new Random();

//     // 建構子：必須傳入一個已經啟動好的 Browser
//     public PageScraper(Browser browser) {
//         this.browser = browser;
//     }

//     public PageData fetchAndParse(String url) {
//         if (browser == null) return null;

//         // 🔥 關鍵：每次抓取只建立一個輕量級的 Context (無痕分頁)
//         // 這非常快，而且不會有驅動程式衝突的問題
//         try (BrowserContext context = browser.newContext(
//                 new Browser.NewContextOptions()
//                     .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
//                     .setViewportSize(1920, 1080)
//         )) {
            
//             // 阻擋圖片與媒體
//             context.route("**/*.{png,jpg,jpeg,svg,gif,webp,mp4,woff,woff2}", Route::abort);

//             Page page = context.newPage();
            
//             try {
//                 page.navigate(url, new Page.NavigateOptions()
//                     .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//                     .setTimeout(5000)); 

//                 // 隨機等待
//                 int renderTime = 500+ random.nextInt(500); 
//                 Thread.sleep(renderTime);
                
//                 // 滾動
//                 page.evaluate("window.scrollBy(0, 1000)");
//                 Thread.sleep(200);

//             } catch (Exception e) {
//                 // 忽略超時
//             }
            
//             String renderedHtml = page.content();
//             Document doc = Jsoup.parse(renderedHtml);

//             String text = "";
//             Element body = doc.body();
//             if (body != null) {
//                 Element clean = body.clone();
//                 clean.select("header, footer, nav, script, style, .ad, .cookie, noscript, iframe, svg, button, form").remove();
//                 text = clean.text(); 
//             }
            
//             List<String> links = new ArrayList<>();
//             Elements linkElements = doc.select("a[href]");
//             for (Element link : linkElements) {
//                 String absUrl = link.attr("abs:href");
//                 if (absUrl.startsWith("http") && !absUrl.contains("#")) {
//                     links.add(absUrl);
//                 }
//             }
            
//             return new PageData(text, links);

//         } catch (Exception e) {
//             System.err.println("  ⚠️ 爬取異常 (" + url + "): " + e.getMessage());
//             return null;
//         }
//     }
// }