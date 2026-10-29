package ie.tcd.dalyc24;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import java.nio.file.Paths;
import java.nio.file.Files;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.BytesRef;

import org.apache.lucene.document.Document;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.StringField;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.DocIdSetIterator;

public class QueryIndex
{
    
    // Directory where the search index will be saved
    private static String INDEX_DIRECTORY = "../index";

    private Analyzer analyzer;
    private Directory directory;

	private ArrayList<Document> documents;

	private static ArrayList<Query_File> queries; 

	
    public QueryIndex() throws IOException
    {
        // Need to use the same analyzer and index directory throughout, so
        // initialize them here
        this.analyzer = new StandardAnalyzer();
        this.directory = FSDirectory.open(Paths.get(INDEX_DIRECTORY));
        this.documents = new ArrayList<>();
        this.queries = new ArrayList<Query_File>();
    }

    public void buildIndex() throws IOException
    {
        // Create a new field type which will store term vector information
        FieldType ft = new FieldType(TextField.TYPE_STORED);
        ft.setTokenized(true); //done as default
        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorPositions(true);
        ft.setStoreTermVectorOffsets(true);
        ft.setStoreTermVectorPayloads(true);

        // create and configure an index writer
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter iwriter = new IndexWriter(directory, config);  

        // Add all input documents to the index
        
        
		String filePath = "../cran/cran.all.1400";
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line  = null;
			String currentSection = null;
			Document_File currentDocument = null;            

			int counter_for_id = 0;
			while ((line = reader.readLine()) != null) {
				//System.out.println(line);

				if (line.startsWith(".I")) {
					counter_for_id++;
					if (currentDocument == null) {
						currentDocument = new Document_File();
						currentDocument.ID = line.substring(3);
					} else {
						currentDocument.print();

						
						System.out.println("NEW CODE");
						TokenStream stream_title = analyzer.tokenStream(null, currentDocument.title);
						CharTermAttribute attr = stream_title.addAttribute(CharTermAttribute.class);
						stream_title.reset();
						
						//StringBuffer cur = new StringBuffer();
						String title_words = "";
						while(stream_title.incrementToken()) {
							title_words += (attr.toString()+" ");
						}
						stream_title.close();
						
						TokenStream stream_content = analyzer.tokenStream(null, currentDocument.content);
						attr = stream_content.addAttribute(CharTermAttribute.class);
						stream_content.reset();
						
						
						String content_words = "";
						while(stream_content.incrementToken()) {
							content_words += (attr.toString()+" ");
						}
						stream_content.close();
						
						//System.out.println(cur);
						//System.exit(0);
						// Save processed document

						Document doc = new Document();
						doc.add(new StringField("counter_for_id", String.valueOf(counter_for_id), Field.Store.YES));
						doc.add(new StringField("ID", currentDocument.ID, Field.Store.YES));
						doc.add(new TextField("title",currentDocument.title, Field.Store.YES));
						doc.add(new TextField("author",currentDocument.author, Field.Store.YES));
						doc.add(new TextField("source",currentDocument.source, Field.Store.YES));
						doc.add(new Field("content",currentDocument.content, ft));
						documents.add(doc);
						
						
						// Create a new Document
						currentDocument = new Document_File();
						currentDocument.ID = line.substring(3);
					}
				} else if (line.startsWith(".T")) {
					currentSection = "title";
				} else if (line.startsWith(".A")) {
					currentSection = "author";
				} else if (line.startsWith(".B")) {
					currentSection = "source";
				} else if (line.startsWith(".W")) {
					currentSection = "content";	
				} else if (currentDocument != null && currentSection != null) {

					line = line.replace("?", "\\?");
					switch (currentSection) {
					case "title":
						currentDocument.title += line.trim() + " ";
						break;
					case "author":
						currentDocument.author += line.trim() + " ";
						break;
					case "source":
						currentDocument.source += line.trim() + " ";
						break;
					case "content":
						currentDocument.content += line.trim() + " ";
						break;
					}
				}
			}

			// Save last document, I didnt get a new ".I" to save document maybe do on EOF
			currentDocument.print();
			
			Document doc = new Document();
			doc.add(new StringField("counter_for_id", String.valueOf(counter_for_id), Field.Store.YES));
			doc.add(new StringField("ID", currentDocument.ID, Field.Store.YES));
			doc.add(new TextField("title",currentDocument.title, Field.Store.YES));
			doc.add(new TextField("author",currentDocument.author, Field.Store.YES));
			doc.add(new TextField("source",currentDocument.source, Field.Store.YES));
			doc.add(new Field("content",currentDocument.content, ft));
			documents.add(doc);

		} catch (IOException e) {
			System.err.println("An error occurred while reading the file: " + e.getMessage());
			e.printStackTrace();
		}

		// close everything and quit
		iwriter.addDocuments(documents);
		System.out.println("Indexed " + iwriter.numRamDocs() + " documents");
		iwriter.close();
	}
        /*
        for (String arg : args)
        {
        	
//            System.out.printf("Indexing \"%s\"\n", arg);
//            String content = new String(Files.readAllBytes(Paths.get(arg)));
//            Document doc = new Document();
            doc.add(new StringField("filename", arg, Field.Store.YES));
            doc.add(new Field("content", content, ft));
            iwriter.addDocument(doc);
        }/*
        
        // close the writer
        iwriter.close();
    }
*/
    
    
    public void postingsDemo() throws IOException
    {
        DirectoryReader ireader = DirectoryReader.open(directory);
    
        // Use IndexSearcher to retrieve some arbitrary document from the index        
        IndexSearcher isearcher = new IndexSearcher(ireader);
        Query queryTerm = new TermQuery(new Term("content","airplane"));
        ScoreDoc[] hits = isearcher.search(queryTerm, 1).scoreDocs;
        
        // Make sure we actually found something
        if (hits.length <= 0)
        {
            System.out.println("Failed to retrieve a document");
            return;
        }

        // get the document ID of the first search result
        int docID = hits[0].doc;

        // Get the fields associated with the document (filename and content)
        Fields fields = ireader.getTermVectors(docID);

        for (String field : fields)
        {
            // For each field, get the terms it contains i.e. unique words
            Terms terms = fields.terms(field);

            // Iterate over each term in the field
            BytesRef termByte = null;
            TermsEnum termsEnum = terms.iterator();

            while ((termByte = termsEnum.next()) != null)
            {                                
                int id;

                // for each term retrieve its postings list
                PostingsEnum posting = null;
                posting = termsEnum.postings(posting, PostingsEnum.FREQS);

                // This only processes the single document we retrieved earlier
                while ((id = posting.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS)
                {
                    // convert the term from a byte array to a string
                    String termString = termByte.utf8ToString();
                    
                    // extract some stats from the index
                    Term term = new Term(field, termString);
                    long freq = posting.freq();
                    long docFreq = ireader.docFreq(term);
                    long totFreq = ireader.totalTermFreq(term);

                    // print the results
                    System.out.printf(
                        "%-16s : freq = %4d : totfreq = %4d : docfreq = %4d\n",
                        termString, freq, totFreq, docFreq
                    );
                }
            }
        }

        // close everything when we're done
        ireader.close();
    }

    public void shutdown() throws IOException
    {
        directory.close();
    }

    public static void main(String[] args) throws IOException, ParseException
    {

        System.out.println("START");
        
        QueryIndex qi = new QueryIndex();
        //String corpus[] = {"../corpus/"};
        qi.buildIndex();
        
		qi.buildQueryList();
		
        qi.postingsDemo();
        qi.shutdown();
        
        System.out.println("END");
    }
    
	public static void buildQueryList() throws IOException, ParseException{

		String filePath = "../cran/cran.qry";

		String line  = null;
		String currentSection = null;
		Query_File currentQuery = null;


		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

			while ((line = reader.readLine()) != null)
			{
				System.out.println(line);

				if (line.startsWith(".I")) {
					if (currentQuery == null) {
						currentQuery = new Query_File();
						currentQuery.ID = line.substring(3);
						//documents.add(currentDocument);
					} else {
						currentQuery.print();
						// Add current query to queries list
						queries.add(currentQuery);
						currentQuery = new Query_File();
						currentQuery.ID = line.substring(3);
					}
				} else if (line.startsWith(".W")) {
					currentSection = "query";	
				} else if (currentQuery != null && currentSection != null) {
					line = line.replace("?", "\\?");
					switch (currentSection) {
					case "query":						
						currentQuery.query += line.trim() + " ";
						break;
					}
				}
			}

			// Add Last Query to queries list
			queries.add(currentQuery);
			currentQuery.print();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("An error occurred while reading the file: " + e.getMessage());
			e.printStackTrace();
		}
    }
}
