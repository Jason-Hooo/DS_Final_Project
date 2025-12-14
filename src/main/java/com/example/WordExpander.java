package com.example;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.*;

public class WordExpander {
    private static final Map<String, List<String>> KEYWORD_CACHE = new HashMap<>();
    private static ChatLanguageModel chatModel;
    private static final String API_KEY = "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";

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

            // Add original keyword and cache the result
            keywords.add(lowerKeyword);
            KEYWORD_CACHE.put(lowerKeyword, keywords);
            return keywords;

        } catch (Exception e) {
            System.err.println("❌ 關鍵字擴展失敗: " + e.getMessage());
            return Collections.singletonList(lowerKeyword);
        }
    }
}