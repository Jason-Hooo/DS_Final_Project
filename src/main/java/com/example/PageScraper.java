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

record PageData(String textContent, List<String> foundLinks) {}

public class PageScraper {

    private final Browser browser;
    private final Pattern IGNORED_EXTENSIONS = Pattern.compile(".*\\.(jpg|jpeg|png|gif|bmp|svg|mp4|avi|mov|pdf|zip|rar|exe|iso|css|js)$", Pattern.CASE_INSENSITIVE);

    public PageScraper(Browser browser) {
        this.browser = browser;
    }

    public PageData fetchAndParse(String url) {
        if (browser == null) return null;
        if (IGNORED_EXTENSIONS.matcher(url).matches()) return null;

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("zh-TW")
                .setJavaScriptEnabled(true);

        try (BrowserContext context = browser.newContext(contextOptions)) {

            context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
            context.addInitScript("window.navigator.chrome = { runtime: {} };");

            context.route("**/*", route -> {
                String type = route.request().resourceType();
                if ("image".equals(type) || "media".equals(type) || "font".equals(type) || "stylesheet".equals(type)) {
                    route.abort();
                } else {
                    route.resume();
                }
            });

            Page page = context.newPage();
            page.setDefaultTimeout(15000);

            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(30000));

                if (url.contains("dcard.tw")) {
                    handleDcardSmartWait(page);
                } else {
                    handleGeneralSmartWait(page);
                }

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

    private void handleDcardSmartWait(Page page) {
        try {
            try {
                page.waitForSelector("article", new Page.WaitForSelectorOptions().setTimeout(5000));
            } catch (PlaywrightException e) {
            }

            for (int i = 0; i < 3; i++) {
                page.mouse().wheel(0, 1500);
                page.waitForTimeout(200); 
            }
        } catch (Exception e) {
        }
    }

    private void handleGeneralSmartWait(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, 
                new Page.WaitForLoadStateOptions().setTimeout(3000));
        } catch (PlaywrightException e) {
        }
    }

    private PageData parseHtml(String html, String url) {
        Document doc = Jsoup.parse(html, url);
        Element body = doc.body();

        String text = "";
        List<String> links = new ArrayList<>();

        if (body != null) {
            body.select("script, style, noscript, iframe, svg, button, form, .ad, header, footer, nav").remove();

            text = body.text();

            Elements linkElements = body.select("a[href]");
            for (Element link : linkElements) {
                String absUrl = link.attr("abs:href");
                if (isValidLink(absUrl)) {
                    links.add(absUrl);
                }
            }
        }

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
