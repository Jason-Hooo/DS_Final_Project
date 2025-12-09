package com.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

record PageData(String textContent, List<String> foundLinks) {}

public class PageScraper {
    
    // 這裡只存別人傳給我的 Browser，我不自己建立
    private final Browser browser; 
    private final Random random = new Random();

    // 建構子：必須傳入一個已經啟動好的 Browser
    public PageScraper(Browser browser) {
        this.browser = browser;
    }

    public PageData fetchAndParse(String url) {
        if (browser == null) return null;

        // 🔥 關鍵：每次抓取只建立一個輕量級的 Context (無痕分頁)
        // 這非常快，而且不會有驅動程式衝突的問題
        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
        )) {
            
            // 阻擋圖片與媒體
            context.route("**/*.{png,jpg,jpeg,svg,gif,webp,mp4,woff,woff2}", Route::abort);

            Page page = context.newPage();
            
            try {
                page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(5000)); 

                // 隨機等待
                int renderTime = 500+ random.nextInt(500); 
                Thread.sleep(renderTime);
                
                // 滾動
                page.evaluate("window.scrollBy(0, 1000)");
                Thread.sleep(200);

            } catch (Exception e) {
                // 忽略超時
            }
            
            String renderedHtml = page.content();
            Document doc = Jsoup.parse(renderedHtml);

            String text = "";
            Element body = doc.body();
            if (body != null) {
                Element clean = body.clone();
                clean.select("header, footer, nav, script, style, .ad, .cookie, noscript, iframe, svg, button, form").remove();
                text = clean.text(); 
            }
            
            List<String> links = new ArrayList<>();
            Elements linkElements = doc.select("a[href]");
            for (Element link : linkElements) {
                String absUrl = link.attr("abs:href");
                if (absUrl.startsWith("http") && !absUrl.contains("#")) {
                    links.add(absUrl);
                }
            }
            
            return new PageData(text, links);

        } catch (Exception e) {
            System.err.println("  ⚠️ 爬取異常 (" + url + "): " + e.getMessage());
            return null;
        }
    }
}