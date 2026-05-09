# Shoogle - Intelligent Sneaker Review Search Engine

這是一個基於 Java 實作的「智慧型球鞋評價搜尋與分析系統」。本專案整合了 Google Search API、無頭瀏覽器 (Headless Browser) 動態爬蟲技術，以及 Llama 大型語言模型 (LLM) 進行關鍵字擴充與精準度排序，為使用者提供最相關、最優質的球鞋評價資訊，特別針對 PTT 與 Dcard 等知名論壇進行深度檢索。

## 核心特色 (Key Features)

* **智能關鍵字處理**：整合 Llama LLM 自動校正使用者的搜尋錯字，並擴充相關關鍵字（例如輸入「puma speedcat」，模型能自動擴展同義詞或相關特徵）。
* **多執行緒非同步爬蟲**：使用 Java `CompletableFuture` 與 `ExecutorService` 進行非同步併發處理，同時抓取一般網頁與特定論壇 (PTT/Dcard) 的內容，大幅提升搜尋效率。
* **動態網頁內容提取 (Playwright)**：內建 Microsoft Playwright 操控 Headless Chrome，能夠有效取得 SPA (Single Page Application) 或需要 JavaScript 渲染的動態網頁內容，並避開基礎反爬蟲機制。
* **自訂評分與排序演算法**：設計 `ScoreCalculator` 根據多維度特徵（包含原始關鍵字與 LLM 擴充關鍵字命中率、標題相關性、內文豐富度等）為搜尋結果進行綜合評分並重新排序。
* **輕量級 RESTful API 伺服器**：後端採用 Spark Java 框架，提供具備非同步中斷能力 (Task Cancellation) 的 API 給前端介面呼叫。

## 技術棧 (Tech Stack)

* **Backend**: Java 17+, Spark Java (Web Framework)
* **Web Scraping**: Microsoft Playwright for Java
* **Concurrency**: `CompletableFuture`, `ExecutorService`
* **APIs & LLM**: Google Custom Search JSON API, Llama LLM API
* **Build Tool**: Maven
* **Frontend**: HTML5, CSS3, Vanilla JavaScript (Fetch API)

## 快速開始 (Getting Started)

### 1. 環境要求
* JDK 17 或以上版本
* Maven
* 網路連線 (需下載 Playwright 瀏覽器二進位檔及呼叫外部 API)

### 2. 環境變數設定配置
本專案依賴外部 API，請在專案根目錄建立 `.env` 檔案，請參考 `.env.example`：

```env
GOOGLE_API_KEY=your_google_api_key_here
GOOGLE_CX_ID=your_google_search_engine_id_here
LLAMA_API_TOKEN=your_llama_api_token_here
```

### 3. 建置與執行
使用 Maven 安裝相依套件並啟動伺服器：

```bash
# 1. 下載並編譯專案
mvn clean install

# 2. 執行主程式啟動 API 伺服器
mvn exec:java -Dexec.mainClass="com.example.ApiMain"
```
*(注意：首次執行 Playwright 時，系統會自動下載必要的 Chromium 瀏覽器核心，需稍候片刻。)*

### 4. 開啟系統
伺服器啟動後，開啟瀏覽器並前往：
**http://localhost:8080**

## 專案架構 (Project Structure)

```text
src/main/java/com/example/
 ├── ApiMain.java           # 系統進入點，Spark Web Server 與路由設定
 ├── ContentExtractor.java  # Playwright 動態網頁爬文邏輯
 ├── GoogleSearchApi.java   # 封裝 Google Custom Search 請求
 ├── LlamaService.java      # 串接 Llama LLM 進行關鍵字校正與擴充
 ├── ScoreCalculator.java   # 實作 TF-IDF/關鍵字權重等相關性評分演算法
 └── WebTree.java           # 資料傳遞結構 (Data Model)
```
