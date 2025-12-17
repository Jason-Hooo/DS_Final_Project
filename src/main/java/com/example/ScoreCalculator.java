package com.example;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class ScoreCalculator {

    private static final Set<String> SHOPPING_DOMAINS = new HashSet<>(Arrays.asList(
        "shopee.tw", "momo.com.tw", "pchome.com.tw", "ruten.com.tw", 
        "books.com.tw", "rakuten.com.tw", "carousell.com.tw", "biggo.com.tw", 
        "feebee.com.tw", "findprice.com.tw", "taobao.com", "amazon.com",
        "coupang.com", "costco.com.tw", "buy123.com.tw", "pcone.com.tw",
        "etmall.com.tw", "friday.tw", "yahoo.com"
    ));

    private static final List<String> SHOPPING_URL_PATHS = List.of(
        "/products/", "/item/", "/goods/", "/pdp/", "/mall/", "/product/", "/view/"
    );

    private static final List<String> EVALUATION_KEYWORDS = List.of(
        "好穿", "舒適", "軟彈", "踩屎感", "Q彈", "回彈", "透氣", "包覆", "穩定", "抓地", "支撐", "神鞋", "必買", "CP值", "腳感",
        "磨腳", "咬腳", "太硬", "太重", "悶熱", "打滑", "版型偏小", "版型偏大", "壓腳背", "掉跟"
    );

    private static final List<String> SHOPPING_KEYWORDS = List.of(
        "購物", "拍賣", "商城", "售價", "價格", "下單", "免運", "現貨", "代購", "二手", 
        "賣場", "加入購物車", "立即購買", "shop", "price", "sale", "cart", "buy"
    );

    public static double calculate(String url, String keyword, List<String> expandedKeywords, String title, String content) {
        if (content == null || content.isEmpty()) return 0.0;

        if (isShoppingUrl(url)) {
            return -1000.0;
        }

        String lowerTitle = (title != null) ? title.toLowerCase() : "";
        String lowerContent = content.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();

        if (isShoppingPattern(lowerTitle, lowerContent)) {
            return -500.0;
        }

        double score = 0.0;
        Set<String> allTargetWords = new HashSet<>(expandedKeywords);
        allTargetWords.add(lowerKeyword);

        for (String word : allTargetWords) {
            score += (countOccurrences(lowerContent, word.toLowerCase()) * 1.0);
        }

        for (String evalWord : EVALUATION_KEYWORDS) {
            score += (countOccurrences(lowerContent, evalWord) * 10.0);
        }

        if (lowerTitle.contains(lowerKeyword)) {
            score += 5.0;
        }

        return score;
    }

    private static boolean isShoppingUrl(String urlString) {
        if (urlString == null || urlString.isEmpty()) return false;
        try {
            String lowerUrl = urlString.toLowerCase();
            URI uri = new URI(lowerUrl);
            String host = uri.getHost();
            if (host == null) return false;

            for (String domain : SHOPPING_DOMAINS) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return true;
                }
            }

            String path = uri.getPath();
            if (path != null) {
                for (String pathKey : SHOPPING_URL_PATHS) {
                    if (path.contains(pathKey)) return true;
                }
            }
        } catch (URISyntaxException e) {
            return urlString.contains("shop") || urlString.contains("product") || urlString.contains("mall");
        }
        return false;
    }

    private static boolean isShoppingPattern(String title, String content) {
        for (String badWord : SHOPPING_KEYWORDS) {
            if (title.contains(badWord)) {
                return true;
            }
        }
        int shoppingTermCount = 0;
        if (content.contains("加入購物車")) shoppingTermCount += 5;
        if (content.contains("立即購買")) shoppingTermCount += 5;
        if (content.contains("庫存")) shoppingTermCount += 2;
        if (content.contains("全家取貨") || content.contains("7-11取貨")) shoppingTermCount += 3;
        return shoppingTermCount >= 5;
    }

    private static int countOccurrences(String text, String target) {
        if (target == null || target.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}