package com.example;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ScoreCalculator {

    // Python Render 服務網址
    private static final String PYTHON_VECTOR_URL = "https://your-app-name.onrender.com/calculate-similarity";
    
    // 權重設定
    private static final double WEIGHT_TEXT_ANALYSIS = 0.4; // 傳統關鍵字+評價分析佔 40%
    private static final double WEIGHT_VECTOR_AI = 0.6;     // AI 向量語意佔 60%

    // 依賴注入 RestTemplate (用於呼叫 Python)
    private final static RestTemplate restTemplate = new RestTemplate();

    /**
     * 主計算方法
     */
    public static double calculate(String keyword, String content) {
        if (content == null || content.isEmpty()) return 0.0;
        
        // 1. 擴展關鍵字 (Nike -> Nike, 勾勾牌, 運動鞋...)
        Set<String> expandedKeywords = SynonymDictionary.expand(keyword);

        // 2. 計算「關鍵字 + 評價詞」的上下文分數
        double textScore = calculateContextScore(content, expandedKeywords);

        // 3. 呼叫 Python 計算向量分數
        double vectorScore = getVectorScoreFromPython(keyword, content);

        // 4. 加權總分
        // return (textScore * WEIGHT_TEXT_ANALYSIS) + (vectorScore * WEIGHT_VECTOR_AI);
        return 1.0;
    }

    // ==========================================================
    //  邏輯 1: 上下文評價分析 (Contextual Sentiment Analysis)
    // ==========================================================
    
    private static double calculateContextScore(String content, Set<String> keywords) {
        String lowerContent = content.toLowerCase();
        int baseMatchCount = 0;
        int sentimentBonusCount = 0;

        // 掃描每個關鍵字
        for (String k : keywords) {
            String lowerK = k.toLowerCase();
            int index = 0;
            while ((index = lowerContent.indexOf(lowerK, index)) != -1) {
                baseMatchCount++;
                
                // 檢查關鍵字前後 20 個字元內，是否有「好評/壞評」描述詞
                String contextWindow = getContextWindow(lowerContent, index, lowerK.length(), 20);
                if (QualityDictionary.containsEvaluationWord(contextWindow)) {
                    sentimentBonusCount++;
                }
                
                index += lowerK.length();
            }
        }

        // 評分公式：
        // 基礎命中 1 分，旁邊有評價詞加 5 分
        // 上限設為 100 分，避免長文無限疊加
        double rawScore = (baseMatchCount * 1.0) + (sentimentBonusCount * 5.0);
        return Math.min(rawScore, 100.0); // 歸一化
    }

    // 取得關鍵字前後的文字片段
    private static String getContextWindow(String content, int keywordIndex, int keywordLen, int windowSize) {
        int start = Math.max(0, keywordIndex - windowSize);
        int end = Math.min(content.length(), keywordIndex + keywordLen + windowSize);
        return content.substring(start, end);
    }

    // ==========================================================
    //  邏輯 2: 呼叫 Python Vector Service
    // ==========================================================

    private static double getVectorScoreFromPython(String query, String content) {
        try {
            // 為了節省頻寬與運算，截斷內文 (只傳前 1000 字)
            String truncatedContent = content.length() > 1000 ? content.substring(0, 1000) : content;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("content", truncatedContent); // 或是傳 chunks

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 假設 Python 回傳格式: {"score": 85.5}
            Map response = restTemplate.postForObject(PYTHON_VECTOR_URL, entity, Map.class);

            if (response != null && response.get("score") != null) {
                Object scoreObj = response.get("score");
                return Double.parseDouble(scoreObj.toString());
            }
        } catch (Exception e) {
            System.err.println("Vector Service Error: " + e.getMessage());
            // 如果 Render 休眠或連線失敗，回傳一個中位數或 0，避免程式崩潰
            return 50.0; 
        }
        return 0.0;
    }

    // ==========================================================
    //  資料庫: 同義詞與評價詞庫 (建議實務上移至 DB 或 Config)
    // ==========================================================

    static class SynonymDictionary {
        private static final Map<String, List<String>> MAP = new HashMap<>();
        static {
            // 鞋類同義詞定義
            MAP.put("緩震", List.of("避震", "軟Q", "回彈", "軟彈", "踩屎感", "absorb", "cushion"));
            MAP.put("透氣", List.of("散熱", "涼爽", "不悶", "通風", "breathable"));
            MAP.put("抓地", List.of("防滑", "止滑", "耐磨", "大底", "traction", "grip"));
            MAP.put("支撐", List.of("穩定", "包覆", "足弓", "support", "lockdown"));
        }

        public static Set<String> expand(String input) {
            Set<String> result = new HashSet<>();
            result.add(input); // 加入原始詞
            
            // 簡易擴展：如果輸入包含 key，就加入 value 列表
            MAP.forEach((key, values) -> {
                if (input.contains(key)) {
                    result.addAll(values);
                }
                // 反向檢查：如果輸入是 value 之一，加入 key
                if (values.contains(input)) {
                    result.add(key);
                }
            });
            return result;
        }
    }

    static class QualityDictionary {
        // 正向與負向評價詞 (包含 PTT/Dcard 常見用語)
        private static final List<String> EVAL_WORDS = List.of(
            // 正向
            "舒服", "好穿", "推薦", "高CP", "神鞋", "輕便", "輕盈", "反應快", "推進",
            "excellent", "good", "great", "best",
            // 負向 (雖然是負評，但代表是「評價文」，所以也要加分，我們要找的是有觀點的文章)
            "磨腳", "咬腳", "太硬", "太重", "悶熱", "打滑", "後悔", "失望", "退貨"
        );

        public static boolean containsEvaluationWord(String contextWindow) {
            for (String word : EVAL_WORDS) {
                if (contextWindow.contains(word)) return true;
            }
            return false;
        }
    }
}