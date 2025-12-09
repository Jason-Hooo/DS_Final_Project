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
    private String content; // 該頁面的純文字內容
    private List<WebTree> children; // 該頁面下的子連結 (節點)

    public WebTree(String url) {
        this.url = url;
        this.content = null; // 預設為 null，等待爬取
        this.children = new ArrayList<>();
    }

    // --- Getter 和 Setter ---
    // (Gson 函式庫會自動使用 these Getter 來序列化 JSON)

    public String getUrl() {
        return url;
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
}