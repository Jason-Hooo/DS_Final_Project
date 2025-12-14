package com.example;

import java.util.ArrayList;
import java.util.List;

/**
 * 類別 2: 網站樹 (WebTree)
 * 它的工作：定義樹狀結構，包含 URL、內容，以及子節點列表
 * (此類別無需變動)
 */
public class WebTree {

    private String url;
    private String title;      // 頁面標題
    private String snippet;    // 頁面摘要
    private String thumbnail;  // 縮圖網址
    private String content;    // 該頁面的純文字內容
    private List<WebTree> children; // 該頁面下的子連結 (節點)
    private double score;

    public WebTree(String url) {
        this.url = url;
        this.title = "";
        this.snippet = "";
        this.thumbnail = "";
        this.content = null; // 預設為 null，等待爬取
        this.children = new ArrayList<>();
        this.score = 0;
    }
    
    // 帶有標題和摘要的建構子
    public WebTree(String url, String title, String snippet, String thumbnail) {
        this.url = url;
        this.title = title != null ? title : "";
        this.snippet = snippet != null ? snippet : "";
        this.thumbnail = thumbnail != null ? thumbnail : "";
        this.content = null;
        this.children = new ArrayList<>();
        this.score = 0;
    }

    // --- Getter 和 Setter ---
    // (Gson 函式庫會自動使用 these Getter 來序列化 JSON)

    public String getUrl() {
        return url;
    }

    // Getters and Setters for all fields
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet != null ? snippet : "";
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail != null ? thumbnail : "";
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<WebTree> getChildren() {
        return children;
    }

    public void addChild(WebTree child) {
        this.children.add(child);
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
    
}