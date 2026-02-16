package com.example.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.document.Document;
import java.nio.file.Paths;

import java.util.Scanner;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.io.Closeable;
import java.io.IOException;
//import java.nio.file.Paths;

public class Searcher implements Closeable {
    private final IndexSearcher searcher;
    private final Analyzer analyzer;
    private final MultiFieldQueryParser parser;

    public Searcher(String indexDir) throws IOException {
        var reader   = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
        this.searcher = new IndexSearcher(reader);
        this.analyzer  = new EnglishAnalyzer();
        this.parser    = new MultiFieldQueryParser(new String[] { "body", "headline", "description" }, analyzer);
    }


    public static class SearchResult {
        public final Query query;
        public final TopDocs topDocs;
        public SearchResult(Query q, TopDocs td) {
            this.query = q;
            this.topDocs = td;
        }
    }

    public SearchResult searchWithQuery(String q, int topN, boolean sortByTitle)
            throws IOException, ParseException {
        Query query = parser.parse(q);
        TopDocs hits = sortByTitle
                ? searcher.search(query, topN, new Sort(new SortField("headline", SortField.Type.STRING)))
                : searcher.search(query, topN);
        return new SearchResult(query, hits);
    }

    public void displayPage(SearchResult result, int page)
            throws IOException, InvalidTokenOffsetsException {
        Query   query = result.query;
        TopDocs hits  = result.topDocs;

        // Deduplicate by URL, keeping first occurrence only
        List<ScoreDoc> uniqueHits = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (ScoreDoc sd : hits.scoreDocs) {
            Document d = searcher.storedFields().document(sd.doc);
            String url = d.get("url");
            if (seenUrls.add(url)) {
                uniqueHits.add(sd);
            }
        }

        // Compute paging bounds over uniqueHits
        int hitsPerPage  = 10;
        int totalUniques = uniqueHits.size();
        int start        = page * hitsPerPage;
        if (start < 0 || start >= totalUniques) {
            System.out.println("[No more results]");
            return;
        }
        int end = Math.min(start + hitsPerPage, totalUniques);

        System.out.printf("Showing results %d–%d of %d:%n", start+1, end, totalUniques);

        // Prepare ANSI‐bold formatter + two highlighters
        String ANSI_BOLD  = "\u001B[1m";
        String ANSI_RESET = "\u001B[0m";
        SimpleHTMLFormatter fmt = new SimpleHTMLFormatter(ANSI_BOLD, ANSI_RESET);

        QueryScorer scorerBody    = new QueryScorer(query, "body");
        Highlighter hlBody        = new Highlighter(fmt, scorerBody);

        QueryScorer scorerTitle   = new QueryScorer(query, "headline");
        Highlighter hlTitle       = new Highlighter(fmt, scorerTitle);

        // Loop and print each unique hit in [start,end)
        for (int i = start; i < end; i++) {
            ScoreDoc sd  = uniqueHits.get(i);
            Document doc = searcher.storedFields().document(sd.doc);

            // highlight body snippet
            String text = doc.get("body");
            TokenStream tsBody = analyzer.tokenStream("body", text);
            String snippet = hlBody.getBestFragment(tsBody, text);
            if (snippet == null) {
                snippet = text.substring(0, Math.min(200, text.length())) + "...";
            }

            // highlight headline
            String rawHead = doc.get("headline");
            TokenStream tsHead = analyzer.tokenStream("headline", rawHead);
            String hHead = hlTitle.getBestFragment(tsHead, rawHead);
            if (hHead == null) {
                hHead = rawHead;
            }

            //  print with score, highlighted headline, snippet, URL
            System.out.printf("%2d. [%.2f] %s%n   %s%n   (%s)%n%n", i + 1, sd.score, hHead, snippet, doc.get("url"));
        }
    }

    @Override
    public void close() throws IOException {
        searcher.getIndexReader().close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java com.example.search.Searcher <index-dir>");
            System.exit(1);
        }
        String indexDir = args[0];

        Scanner in = new Scanner(System.in);
        boolean sortAlpha = false;
        SearchResult result = null;
        Deque<SearchResult> history = new ArrayDeque<>();

        try (Searcher s = new Searcher(indexDir)) {
            int page = 0;

            while (true) {
                System.out.print("\nEnter query, `n`/`p`, `sort`, `history`, or `exit`: ");
                String line = in.nextLine().trim();
                if (line.equalsIgnoreCase("exit")) {
                    break;
                }

                if (line.equalsIgnoreCase("sort")) {
                    sortAlpha = !sortAlpha;
                    System.out.println("Alphabetical sort: " + sortAlpha);
                    if (result != null) {
                        result = s.searchWithQuery(result.query.toString(), 1000, sortAlpha);
                        page = 0;
                        System.out.printf("Total hits: %d%n", result.topDocs.totalHits.value());
                        s.displayPage(result, page);
                    }
                    continue;
                }

                // Show search history (deduced per URL, top 5 only)
                if (line.equalsIgnoreCase("history")) {
                    if (history.isEmpty()) {
                        System.out.println("No history yet.");
                    } else {
                        int idx = 1;
                        for (SearchResult past : history) {
                            System.out.printf("%2d) \"%s\" → top 5 unique headlines:%n", idx++, past.query);
                            TopDocs td = past.topDocs;

                            Set<String> seenUrls = new HashSet<>();
                            int printed = 0;
                            for (ScoreDoc sd : td.scoreDocs) {
                                Document d = s.searcher.storedFields().document(sd.doc);
                                String url = d.get("url");
                                if (seenUrls.add(url)) {
                                    System.out.printf("    - %s%n", d.get("headline"));
                                    if (++printed >= 5) break;
                                }
                            }
                            System.out.println();
                        }
                    }
                    continue;
                }


                if (result != null && line.equalsIgnoreCase("n")) {
                    page++;
                    s.displayPage(result, page);
                    continue;
                }
                if (result != null && line.equalsIgnoreCase("p")) {
                    page--;
                    s.displayPage(result, page);
                    continue;
                }

                // new search
                result = s.searchWithQuery(line, 1000, sortAlpha);
                history.addFirst(result);
                if (history.size() > 20) history.removeLast();

                page = 0;
                System.out.printf("Total hits: %d%n", result.topDocs.totalHits.value());
                s.displayPage(result, page);
            }
        }

        in.close();
    }
}
