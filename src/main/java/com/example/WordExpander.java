package com.example;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.*;

public class WordExpander {
    private static final Map<String, List<String>> KEYWORD_CACHE = new HashMap<>();
    private static ChatLanguageModel chatModel;
    private static final String API_KEY = "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";
    // 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

    static {
        if (API_KEY != null && !API_KEY.isEmpty()) {
            chatModel = OpenAiChatModel.builder()
                .apiKey(API_KEY)
                .baseUrl("https://api.groq.com/openai/v1")
                .modelName("llama-3.3-70b-versatile")
                .temperature(0.5)
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
        }
    }

    public static List<String> expandKeywords(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            System.out.println("⚠️ 空關鍵字，返回空列表");
            return Collections.emptyList();
        }

        String lowerKeyword = keyword.toLowerCase();
        if (KEYWORD_CACHE.containsKey(lowerKeyword)) {
            System.out.println("🔑 使用快取關鍵字: " + lowerKeyword);
            return KEYWORD_CACHE.get(lowerKeyword);
        }

        if (chatModel == null) {
            System.err.println("⚠️ Warning: LLM Model not initialized. Check API Key.");
            return Collections.singletonList(lowerKeyword);
        }

        try {
            String prompt = String.format(
                "你是一個專業的球鞋分析師。請列出 5 個與鞋類關鍵字「%s」高度相關的同義詞、型號暱稱、相關名人或風格描述。\n" +
                "規則：\n" +
                "1. 只回傳關鍵詞，不要有任何解釋或開場白。\n" +
                "2. 使用繁體中文或英文。\n" +
                "3. 詞彙之間用逗號分隔。\n" +
                "範例輸入：Puma Speedcat\n" +
                "範例輸出：賽車鞋,薄底鞋,Rosé同款,復古,Blackpink\n" +
                "現在請輸出：", 
                keyword
            );

            String response = chatModel.generate(prompt);
            System.out.println("🤖 擴展關鍵字: " + keyword + " → " + response);

            List<String> keywords = new ArrayList<>();
            for (String word : response.split("[,，、]")) {
                String cleanWord = word.trim().toLowerCase();
                if (!cleanWord.isEmpty() && cleanWord.length() > 1) {
                    keywords.add(cleanWord);
                }
            }

            keywords.add(lowerKeyword);
            KEYWORD_CACHE.put(lowerKeyword, keywords);
            return keywords;

        } catch (Exception e) {
            System.err.println("❌ 關鍵字擴展失敗: " + e.getMessage());
            return Collections.singletonList(lowerKeyword);
        }
    }

    public static String correctKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return keyword;
        }

        if (chatModel == null) {
            System.err.println("⚠️ Warning: LLM Model not initialized. Skipping keyword correction.");
            return keyword;
        }

        try {
            String prompt = String.format(
                "你是一個搜尋引擎關鍵字修正助手。請檢查使用者的搜尋關鍵字「%s」是否有錯別字或語意不清。\n" +
                "規則：\n" +
                "1. 如果關鍵字有明顯錯別字（例如 'iphoe' -> 'iphone', 'addidas' -> 'adidas'），請回傳修正後的正確關鍵字。\n" +
                "2. 如果關鍵字看起來正確，請直接回傳原本的關鍵字。\n" +
                "3. 只回傳修正後的關鍵字，不要有任何解釋、標點符號或額外文字。\n" +
                "4. 如果是英文，請維持適當的大小寫慣例（通常全小寫或首字大寫皆可，視情況而定）。\n" +
                "範例輸入：iphoe\n" +
                "範例輸出：iphone\n" +
                "現在請輸出：",
                keyword
            );

            String corrected = chatModel.generate(prompt).trim();
            corrected = corrected.replaceAll("^[\"']+|[\"']+$", "");
            
            System.out.println("🔍 關鍵字檢查: " + keyword + " → " + corrected);
            return corrected;

        } catch (Exception e) {
            System.err.println("❌ 關鍵字修正失敗: " + e.getMessage());
            return keyword;
        }
    }
}