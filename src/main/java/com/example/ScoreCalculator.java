package com.example; // 記得改成你的 package

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class ScoreCalculator {

    // 1. LLM 快取
    private static final Map<String, List<String>> LLM_CACHE = new ConcurrentHashMap<>();

    // 2. LLM 模型實例 (建議設為 Static，避免每次計算都重新建立連線)
    private static ChatLanguageModel chatModel;

    // TODO: 請填入你的 Groq API Key
    private static final String API_KEY = "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"; 

    // 初始化模型 (只執行一次)
    static {
        if (API_KEY != null && !API_KEY.contains("填在這裡")) {
            chatModel = OpenAiChatModel.builder()
                .apiKey(API_KEY)
                .baseUrl("https://api.groq.com/openai/v1")
                .modelName("llama-3.3-70b-versatile") // Groq 目前最強免費模型
                .temperature(0.5) // 降低隨機性，讓答案更精準
                .timeout(java.time.Duration.ofSeconds(10)) // 設定超時，避免卡死
                .build();
        }
    }

    // 3. 鞋類評價專用詞庫 (加分項)
    private static final List<String> EVALUATION_KEYWORDS = List.of(
        "好穿", "舒適", "軟彈", "踩屎感", "Q彈", "回彈", "透氣", "包覆", "穩定", "抓地", "支撐", "神鞋", "必買", "CP值", "腳感",
        "磨腳", "咬腳", "太硬", "太重", "悶熱", "打滑", "版型偏小", "版型偏大", "壓腳背", "掉跟"
    );

    // 4. 電商/交易黑名單 (扣分項)
    private static final List<String> SHOPPING_KEYWORDS = List.of(
        "購物", "拍賣", "商城", "售價", "價格", "下單", "免運", "現貨", "代購", "二手", 
        "賣場", "加入購物車", "立即購買", "Shop", "Price", "Sale", "Cart", "Buy"
    );

    /**
     * 核心計算方法
     */
    public static double calculate(String keyword, List<String> expandedKeywords, String title, String content) {
        if (content == null || content.isEmpty()) return 0.0;
        
        String lowerTitle = (title != null) ? title.toLowerCase() : "";
        String lowerContent = content.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();

        // --- 步驟 1: 電商屠殺 ---
        if (isShoppingPattern(lowerTitle, lowerContent)) {
            // System.out.println("偵測到電商/交易網站: " + title);
            return 1.0; 
        }
        
        Set<String> allTargetWords = new HashSet<>(expandedKeywords);
        allTargetWords.add(lowerKeyword);

        // --- 步驟 3: 計算分數 ---
        double score = 0.0;

        // A. 關鍵字命中
        for (String word : allTargetWords) {
            score += (countOccurrences(lowerContent, word) * 2.0);
        }

        // B. 評價詞命中
        for (String evalWord : EVALUATION_KEYWORDS) {
            score += (countOccurrences(lowerContent, evalWord) * 1.5);
        }

        // C. 標題加權
        if (lowerTitle.contains(lowerKeyword)) {
            score += 50.0;
        }

        return score;
    }

    private static boolean isShoppingPattern(String title, String content) {
        for (String badWord : SHOPPING_KEYWORDS) {
            if (title.contains(badWord.toLowerCase())) {
                return true;
            }
        }
        int shoppingTermCount = 0;
        if (content.contains("加入購物車")) shoppingTermCount += 5;
        if (content.contains("立即購買")) shoppingTermCount += 5;
        if (content.contains("庫存")) shoppingTermCount += 2;
        return shoppingTermCount >= 5;
    }

    private static int countOccurrences(String text, String target) {
        if (target.length() == 0) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
