package com.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * (資料類別) 用於儲存 PageScraper 的回傳值
 */
record PageData(String textContent, List<String> foundLinks) {}

/**
 * 類別 3: 網頁爬取與解析類別
 * 它的工作：取得「純文字內容」和「所有絕對連結」
 * (此類別無需變動)
 */
public class PageScraper {

    // 模擬瀏覽器的 User-Agent，已更新為更現代的 Chrome 字串
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 抓取並解析單個 URL 的純文字內容與連結。
     *
     * @param url 要抓取的網頁 URL
     * @return 一個 PageData 物件，包含文字和連結；如果失敗則返回 null
     */
    public PageData fetchAndParse(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(10000) // 10 秒超時
                    // 加入標準瀏覽器標頭，讓請求看起來更像人類
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", "https://www.google.com/")
                    .get();

            // --- 1. 提取純文字 (最終修訂的邏輯：最優化靜態頁面抓取) ---
            String text = "";
            Element body = doc.body();

            if (body != null) {
                // 深度複製 body，以便安全地修改它
                Element cleanBody = body.clone(); 
                
                // 移除常見的雜訊元素 (導航、側邊欄、腳註等)
                cleanBody.select("header, footer, nav, aside, .sidebar, .menu, .meta, .widget").remove();
                
                // 這次不進行精確的 mainContent 選擇，直接對清理後的 body 進行提取
                text = cleanBody.text(); 
            }

            
            // 2. 提取所有連結
            List<String> links = new ArrayList<>();
            // 選取所有 <a> 標籤中帶有 href 屬性的元素
            Elements linkElements = doc.select("a[href]");
            
            for (Element link : linkElements) {
                // 關鍵： Jsoup 的 'abs:href' 會自動將相對路徑轉換為絕對路徑
                String absoluteUrl = link.attr("abs:href");
                
                // 清理 URL (移除 # 錨點)
                if (absoluteUrl.contains("#")) {
                    absoluteUrl = absoluteUrl.substring(0, absoluteUrl.indexOf("#"));
                }

                if (!absoluteUrl.isEmpty() && absoluteUrl.startsWith("http")) {
                    links.add(absoluteUrl);
                }
            }

            return new PageData(text, links);

        } catch (IOException e) {
            System.err.println("爬取 " + url + " 失敗: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("解析 " + url + " 時發生未知錯誤: " + e.getMessage());
            return null;
        }
    }
}