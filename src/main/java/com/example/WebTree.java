package com.example;

import java.util.ArrayList;
import java.util.List;

public class WebTree {

    private String url;
    private String title;
    private String snippet;
    private String thumbnail;
    private String content;
    private List<WebTree> children;
    private double score;

    public WebTree(String url) {
        this.url = url;
        this.title = "";
        this.snippet = "";
        this.thumbnail = "";
        this.content = null;
        this.children = new ArrayList<>();
        this.score = 0;
    }
    
    public WebTree(String url, String title, String snippet, String thumbnail) {
        this.url = url;
        this.title = title != null ? title : "";
        this.snippet = snippet != null ? snippet : "";
        this.thumbnail = thumbnail != null ? thumbnail : "";
        this.content = null;
        this.children = new ArrayList<>();
        this.score = 0;
    }

    public String getUrl() {
        return url;
    }

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
