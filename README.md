# News Search Engine

A full-text news search engine built in Java using **Apache Lucene 10.2.1**. Indexes up to 1,000 CNN news articles from a CSV dataset and provides an interactive terminal-based search interface with pagination, highlighting, sorting, and search history.

---

## Features

- **Full-text search** across `body`, `headline`, and `description` fields simultaneously
- **Field-specific search** — e.g. `headline:olympics` or `category:politics`
- **Pagination** — 10 results per page, navigate with `n` (next) and `p` (previous)
- **ANSI bold highlighting** — matched terms highlighted in terminal output
- **Alphabetical sort** — toggle title sorting with `sort` command
- **Search history** — stores last 20 queries, display with `history`
- **URL deduplication** — filters duplicate results, shows only unique articles
- **English stemming & stop words** via Lucene's `EnglishAnalyzer`

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Core language |
| Apache Lucene | 10.2.1 | Indexing & search engine |
| Apache Commons CSV | 1.10.0 | CSV parsing |
| Lucene Highlighter | 10.2.1 | Query term highlighting |
| Maven | 3.x | Build & dependency management |

---

## Project Structure

```
Information-Retrieval/
├── src/main/java/com/example/search/
│   ├── Indexer.java      # Builds the Lucene index from a CSV file
│   └── Searcher.java     # Interactive search interface
├── pom.xml               # Maven build configuration
└── README.md
```

### `Indexer.java`
- Opens or creates a Lucene `FSDirectory` index with `EnglishAnalyzer`
- Supports indexing from a **folder of text files** or a **CSV file**
- For CSV: indexes fields — `id`, `author`, `category`, `section`, `date`, `url`, `headline`, `description`, `keywords`, `secondHeadline`, `body`
- Adds `SortedDocValuesField` on `headline` to support alphabetical sorting
- Processes up to 1,000 records

### `Searcher.java`
- Uses `MultiFieldQueryParser` to search `body`, `headline`, `description` simultaneously
- Paginates results 10 per page
- Deduplicates results by URL
- Highlights matched terms in terminal using ANSI bold escape codes
- Maintains a search history deque (last 20 queries)

---

## Dataset

This project uses the **CNN Articles After Basic Cleaning** dataset from Kaggle:

> https://www.kaggle.com/datasets/hadasu92/cnn-articles-after-basic-cleaning/data

Download the CSV and use it as input when running the Indexer.

**CSV columns used:**
`Index`, `Author`, `Category`, `Section`, `Date published`, `Url`, `Headline`, `Description`, `Keywords`, `Second headline`, `Article text`

---

## How to Build & Run

### Requirements
- Java 21+
- Maven 3.x

### 1. Build
```bash
mvn clean package
```

### 2. Index the dataset
```bash
mvn exec:java -Dexec.mainClass="com.example.search.Indexer" \
  -Dexec.args="<index-dir> <path-to-dataset.csv>"
```

Example:
```bash
mvn exec:java -Dexec.mainClass="com.example.search.Indexer" \
  -Dexec.args="./index ./cnn_articles.csv"
```

### 3. Search
```bash
mvn exec:java -Dexec.mainClass="com.example.search.Searcher" \
  -Dexec.args="<index-dir>"
```

Example:
```bash
mvn exec:java -Dexec.mainClass="com.example.search.Searcher" \
  -Dexec.args="./index"
```

---

## Search Commands

| Command | Description |
|---|---|
| `<query>` | Run a new search |
| `n` | Next page of results |
| `p` | Previous page of results |
| `sort` | Toggle alphabetical sorting by headline |
| `history` | Show last 20 queries with top 5 results each |
| `exit` | Exit the program |

### Query Examples
```
climate change
headline:olympics
author:john
category:politics
COVID vaccine
```

---

## Academic Context

Built as a university project for the **Information Retrieval** course at the
**Department of Computer Engineering, University of Ioannina, Greece**.

Key concepts implemented:
- Inverted index construction with Apache Lucene
- TF-IDF based relevance scoring
- Multi-field query parsing
- English language analysis (stemming, stop word removal)
- Result pagination and deduplication
- Query term highlighting
