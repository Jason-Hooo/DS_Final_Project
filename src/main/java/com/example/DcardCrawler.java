package com.example;

import com.microsoft.playwright.*;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class DcardCrawler {

    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            
            // 1. 啟動選項：開啟 Headless 模式以達到最快速度
            // 若發現程式一直卡住或被 Dcard 阻擋，請將 setHeadless 改為 false
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true) 
                    .setChannel("chrome");

            Browser browser = playwright.chromium().launch(launchOptions);

            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
                    .setLocale("zh-TW");

            BrowserContext context = browser.newContext(contextOptions);
            Page page = context.newPage();

            // === [核心優化] 攔截並不下載「不必要資料」 ===
            // 這段程式碼會封鎖圖片 (image)、媒體 (media)、字體 (font)
            // 這樣瀏覽器只會下載 HTML 和必要的 Script，速度會快非常多
            page.route("**/*", route -> {
                String type = route.request().resourceType();
                // 這裡定義要封鎖的資源類型
                if ("image".equals(type) || "media".equals(type) || "font".equals(type)) {
                    route.abort(); // 中斷請求，不下載
                } else {
                    route.resume(); // 其他請求 (如 HTML, API 資料) 放行
                }
            });

            // 2. 前往網址
            String url = "https://www.dcard.tw/f/dressup/p/257894920";
            long startTime = System.currentTimeMillis();
            System.out.println("正在載入頁面 (已封鎖圖片模式)...");
            
            page.navigate(url);

            try {
                // 等待文章內容載入
                page.waitForSelector("article", new Page.WaitForSelectorOptions().setTimeout(10000));

                // 3. 快速捲動載入留言
                // 這裡將等待時間縮短為 400ms，只要網路不是太差通常都來得及
                System.out.println("正在快速捲動...");
                for (int i = 0; i < 8; i++) { // 滾動 8 次以載入更多留言
                    page.mouse().wheel(0, 2000); // 大幅度滾動
                    page.waitForTimeout(1000);    // 短暫等待資料載入 (比之前的 1000ms 快很多)
                }

                // 4. 開始抓取資料
                // 標題
                Locator titleLocator = page.locator("h1").first();
                if (titleLocator.isVisible()) {
                    System.out.println("\n=== 文章標題 ===");
                    System.out.println(titleLocator.innerText());
                }

                // 內文
                Locator contentLocator = page.locator("article").first();
                if (contentLocator.isVisible()) {
                    System.out.println("\n=== 文章內文 (前 100 字預覽) ===");
                    String content = contentLocator.innerText();
                    // 為了版面乾淨，只印出前 100 字
                    System.out.println(content.length() > 100 ? content.substring(0, 100) + "..." : content);
                }

                // 留言 (抓取 id 開頭為 comment- 的 div)
                // 這種寫法比抓 class 穩定，因為 Dcard 的 class 常常變
                Locator comments = page.locator("div[id^='comment-']");
                int count = comments.count();

                System.out.println("\n=== 留言列表 (共抓取到 " + count + " 則) ===");
                
                // 為了展示速度，這裡只印出樓層資訊，不印全部內文
                for (int i = 0; i < count; i++) {
                    Locator comment = comments.nth(i);
                    // 嘗試只抓取第一行文字 (通常包含樓層 B1, B2... 和時間)
                    String fullText = comment.innerText();
                    String firstLine = fullText.split("\n")[0]; 
                    
                    System.out.println(firstLine);
                }

            } catch (Exception e) {
                System.out.println("發生錯誤 (可能是 Cloudflare 阻擋或逾時): " + e.getMessage());
                // 發生錯誤時截圖
                page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("error_debug.png")));
            }

            long endTime = System.currentTimeMillis();
            System.out.println("\n執行結束，總耗時: " + (endTime - startTime) / 1000.0 + " 秒");

            browser.close();
        }
    }
}