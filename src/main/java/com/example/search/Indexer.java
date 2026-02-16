package com.example.search;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Indexer implements AutoCloseable {

    private final IndexWriter writer;


    //Opens (or creates) a Lucene index at the given path.

    public Indexer(String indexDir) throws IOException {
        FSDirectory dir = FSDirectory.open(Paths.get(indexDir));
        IndexWriterConfig config = new IndexWriterConfig(new EnglishAnalyzer());


        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        this.writer = new IndexWriter(dir, config);
    }

    // Close the index
    @Override
    public void close() throws IOException {
        writer.close();
    }
     // Index every file in a directory
    private void indexDirectory(Path docsDir) throws IOException {
        try (Stream<Path> stream = Files.walk(docsDir)) {
            stream.filter(Files::isRegularFile)
                    .limit(1_000)  //  at least 1,000 docs
                    .forEach(path -> {
                        try {
                            Document doc = new Document();
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            doc.add(new StringField("path", path.toString(), Field.Store.YES));
                            doc.add(new TextField("body", content, Field.Store.YES));
                            writer.addDocument(doc);
                            System.out.println("Indexed (file): " + path.getFileName());
                        } catch (IOException e) {
                            System.err.println("Failed to index " + path + ": " + e.getMessage());
                        }
                    });
        }
    }

     // Parse and index each record in a CSV file
    private void indexCSV(Path csvFile) throws IOException {
        try (Reader in = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
                CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT
                                .withFirstRecordAsHeader()
                                .withIgnoreHeaderCase()
                                .withTrim())) {
            int count = 0;
            for (CSVRecord record : parser) {
                if (count++ >= 1_000) break;  // stop after 1,000 articles

                Document doc = new Document();
                // Exact fields
                doc.add(new StringField("id",            record.get("Index"),           Field.Store.YES));
                doc.add(new StringField("author",        record.get("Author"),          Field.Store.YES));
                doc.add(new StringField("category",      record.get("Category"),        Field.Store.YES));
                doc.add(new StringField("section",       record.get("Section"),         Field.Store.YES));
                doc.add(new StringField("date",          record.get("Date published"),  Field.Store.YES));
                // Stored only
                doc.add(new StoredField("url",            record.get("Url")));
                // Full-text fields
                doc.add(new TextField("headline",        record.get("Headline"),        Field.Store.YES));
                doc.add(new TextField("description",     record.get("Description"),     Field.Store.YES));
                doc.add(new TextField("keywords",        record.get("Keywords"),        Field.Store.YES));
                doc.add(new TextField("secondHeadline",  record.get("Second headline"), Field.Store.YES));
                doc.add(new TextField("body",            record.get("Article text"),    Field.Store.YES));

                doc.add(new SortedDocValuesField("headline", new BytesRef(record.get("Headline"))));
                writer.addDocument(doc);
                System.out.println("Indexed record ID=" + record.get("Index"));
            }
        }
    }

     //Main entry point. Args:
     //  [0] = path to Lucene index directory
     //  [1] = path to docs directory or CSV file
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java com.example.search.Indexer <index-dir> <docs-dir-or-csv>");
            System.exit(1);
        }

        String indexPath = args[0];
        Path sourcePath = Paths.get(args[1]);

        try (Indexer indexer = new Indexer(indexPath)) {
            if (Files.isDirectory(sourcePath)) {
                indexer.indexDirectory(sourcePath);
            } else if (args[1].toLowerCase().endsWith(".csv")) {
                indexer.indexCSV(sourcePath);
            } else {
                System.err.println("Error: second argument must be a folder or a .csv file");
                System.exit(2);
            }
            System.out.println("Indexing complete.");
        }
    }
}
