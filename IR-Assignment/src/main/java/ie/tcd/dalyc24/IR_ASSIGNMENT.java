package ie.tcd.dalyc24;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Scanner;
import java.nio.file.Paths;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.queryparser.classic.ParseException;


public class IR_ASSIGNMENT
{

	static Analyzer analyzer = null;

	// Directory where the search index will be saved
	private static String INDEX_DIRECTORY = "../index";

	private static int score;

	// Tunable weights for query construction
	private static final float TITLE_BOOST         = 1.5f;  // extra weight for a term matching the title
	private static final float PHRASE_BOOST        = 2.0f;  // adjacent-word phrase in content
	private static final float TITLE_PHRASE_BOOST  = 3.0f;  // adjacent-word phrase in title
	private static final int   PHRASE_SLOP         = 1;     // words allowed between phrase terms
	private static final float MIN_IDF             = 0.0f;  // terms with idf below this are dropped (try 0.5f)
	private static final float REPEAT_BOOST        = 0.5f;  // extra weight per repeat of a term in the query (0 = off)

	public static void main(String[] args) throws IOException, ParseException
	{
		System.out.println("Starting");

		//reformat();

		System.out.println("What analyzer to use");
		System.out.println("1 - Custom Analyzer");
		System.out.println("2 - Standard Analyzer");

		Scanner in = new Scanner(System.in);
		int type = in.nextInt();

		if(type == 1) {
			analyzer = new MyCustomAnalyzer();
		} else {
			analyzer = new StandardAnalyzer();
		}

		System.out.println("What score to use");
		System.out.println("1 - Boolean");
		System.out.println("2 - TF-IDF");
		System.out.println("3 - BM25");
		score = in.nextInt();


		buildIndex();
		queryDocs();
		System.out.println("Finishing");
	}

	// Adds one parsed Cranfield document to the list.
	// Raw text is passed in: the IndexWriter runs the analyzer once, so indexed terms
	// are processed exactly the same way as the query terms.
	private static void addDoc(ArrayList<Document> documents, Document_File d, int counter) {
		Document doc = new Document();
		doc.add(new TextField("counter_for_id", String.valueOf(counter), Field.Store.YES));
		doc.add(new TextField("ID", d.ID, Field.Store.YES));
		doc.add(new TextField("title", d.title, Field.Store.YES));
		doc.add(new TextField("author", d.author, Field.Store.YES));
		doc.add(new TextField("source", d.source, Field.Store.YES));
		doc.add(new TextField("content", d.content, Field.Store.YES));
		documents.add(doc);
	}

	public static void buildIndex() throws IOException {
		// ArrayList of documents in the corpus
		ArrayList<Document> documents = new ArrayList<Document>();

		// Open the directory that contains the search index
		Directory directory = FSDirectory.open(Paths.get(INDEX_DIRECTORY));

		// Set up an index writer to add process and save documents to the index
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
		IndexWriter iwriter = new IndexWriter(directory, config);

		String filePath = "../cran/cran.all.1400";
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line  = null;
			String currentSection = null;
			Document_File currentDocument = null;

			int counter_for_id = 0;
			while ((line = reader.readLine()) != null) {

				if (line.startsWith(".I")) {
					counter_for_id++;
					if (currentDocument != null) {
						// Save the previous document before starting a new one
						addDoc(documents, currentDocument, counter_for_id - 1);
					}
					currentDocument = new Document_File();
					currentDocument.ID = line.substring(3);
				} else if (line.startsWith(".T")) {
					currentSection = "title";
				} else if (line.startsWith(".A")) {
					currentSection = "author";
				} else if (line.startsWith(".B")) {
					currentSection = "source";
				} else if (line.startsWith(".W")) {
					currentSection = "content";
				} else if (currentDocument != null && currentSection != null) {

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

			// Save last document (there is no following ".I" line to trigger it)
			if (currentDocument != null) {
				addDoc(documents, currentDocument, counter_for_id);
			}

		} catch (IOException e) {
			System.err.println("An error occurred while reading the file: " + e.getMessage());
			e.printStackTrace();
		}

		// close everything and quit
		iwriter.addDocuments(documents);
		System.out.println("Indexed " + iwriter.numRamDocs() + " documents");
		iwriter.close();
		directory.close();
	}



	// Limit the number of search results we get
	private static int MAX_RESULTS = 50; // I changed this from 10
	private static String results = "../results.txt";

	// Builds a weighted query from the raw query text:
	//  - every distinct analyzed term is searched in content and title, boosted by its IDF
	//    so rare (more meaningful) terms count for more than common ones
	//  - every pair of adjacent terms is added as a phrase query so documents containing
	//    the words together score higher
	private static Query buildWeightedQuery(String queryText, DirectoryReader ireader) throws IOException {

		// 1. Analyze the query, keeping token order for phrases
		ArrayList<String> tokens = new ArrayList<String>();
		try (TokenStream stream = analyzer.tokenStream(null, queryText)) {
			CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
			stream.reset();
			while (stream.incrementToken()) {
				tokens.add(attr.toString());
			}
			stream.end();
		}

		int N = ireader.numDocs();
		BooleanQuery.Builder bool_query = new BooleanQuery.Builder();

		// Count how many times each term occurs in the query (insertion order kept)
		LinkedHashMap<String, Integer> termCounts = new LinkedHashMap<String, Integer>();
		for (String t : tokens) {
			termCounts.merge(t, 1, Integer::sum);
		}

		// 2. Weighted single terms (one clause per distinct term)
		for (java.util.Map.Entry<String, Integer> entry : termCounts.entrySet()) {
			String t = entry.getKey();
			int count = entry.getValue();

			// 1.0 for a term used once, +REPEAT_BOOST for each extra repeat
			float repeatWeight = 1.0f + REPEAT_BOOST * (count - 1);

			int dfContent = ireader.docFreq(new Term("content", t));
			int dfTitle   = ireader.docFreq(new Term("title", t));
			if (dfContent == 0 && dfTitle == 0) continue; // term not in the index at all

			// BM25-style IDF: large for rare terms, small for common ones
			int df = Math.max(dfContent, dfTitle);
			float idf = (float) Math.log(1 + (N - df + 0.5) / (df + 0.5));
			if (idf < MIN_IDF) continue;

			// Final term weight = IDF x repeat weight
			idf *= repeatWeight;

			if (dfContent > 0) {
				bool_query.add(
						new BoostQuery(new TermQuery(new Term("content", t)), idf),
						BooleanClause.Occur.SHOULD);
			}
			if (dfTitle > 0) {
				bool_query.add(
						new BoostQuery(new TermQuery(new Term("title", t)), idf * TITLE_BOOST),
						BooleanClause.Occur.SHOULD);
			}
		}

		// 3. Adjacent-word phrases (bigrams)
		for (int i = 0; i < tokens.size() - 1; i++) {
			String a = tokens.get(i);
			String b = tokens.get(i + 1);
			if (a.equals(b)) continue;

			bool_query.add(
					new BoostQuery(new PhraseQuery(PHRASE_SLOP, "content", a, b), PHRASE_BOOST),
					BooleanClause.Occur.SHOULD);
			bool_query.add(
					new BoostQuery(new PhraseQuery(PHRASE_SLOP, "title", a, b), TITLE_PHRASE_BOOST),
					BooleanClause.Occur.SHOULD);
		}

		return bool_query.build();
	}

	public static void queryDocs() throws IOException, ParseException{

		String filePath = "../cran/cran.qry";

		String line  = null;
		String currentSection = null;
		Query_File currentQuery = null;


		ArrayList<Query_File> queries = new ArrayList<Query_File>();

		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith(".I")) {
					if (currentQuery == null) {
						currentQuery = new Query_File();
						currentQuery.ID = line.substring(3);
					} else {
						// Add current query to queries list
						queries.add(currentQuery);
						currentQuery = new Query_File();
						currentQuery.ID = line.substring(3);
					}
				} else if (line.startsWith(".W")) {
					currentSection = "query";
				} else if (currentQuery != null && currentSection != null) {
					switch (currentSection) {
					case "query":
						currentQuery.query += line.trim() + " ";
						break;
					}
				}
			}

			// Add Last Query to queries list
			if (currentQuery != null) {
				queries.add(currentQuery);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("An error occurred while reading the file: " + e.getMessage());
			e.printStackTrace();
		}
		// END OF BUILD QUERIES LIST


		// START OF QUERIES

		// Open the folder that contains our search index
		Directory directory = FSDirectory.open(Paths.get(INDEX_DIRECTORY));

		// create objects to read and search across the index
		DirectoryReader ireader = DirectoryReader.open(directory);
		IndexSearcher isearcher = new IndexSearcher(ireader);


		// SET SCORING METHOD
		if(score == 1) {
			isearcher.setSimilarity(new BooleanSimilarity());
		}
		else if(score == 2) {
			isearcher.setSimilarity(new ClassicSimilarity());
		}
		else if (score == 3) {
			isearcher.setSimilarity(new BM25Similarity(2.0f, 0.75f));
		}

		BufferedWriter writer = new BufferedWriter(new FileWriter(results));

		// go through queries list and check
		for(int q = 0; q < queries.size(); q++) {
			System.out.println("Query " + queries.get(q).ID);

			Query finalQuery = null;
			try {
				String query_string = queries.get(q).query.trim();
				System.out.println(query_string);

				finalQuery = buildWeightedQuery(query_string, ireader);
				System.out.println(finalQuery);
			}
			catch(Exception e) {
				e.printStackTrace();
			}

			// If the query could not be built, skip it instead of crashing
			if (finalQuery == null) {
				continue;
			}

			// Get the set of results
			ScoreDoc[] hits = isearcher.search(finalQuery, MAX_RESULTS).scoreDocs;

			// Print the results
			System.out.println("Documents: " + hits.length);
			for (int i = 0; i < hits.length; i++)
			{
				Document hitDoc = isearcher.doc(hits[i].doc);
				System.out.println(i + ") " + hitDoc.get("ID") + " " + hitDoc.get("title") + " " + hits[i].score);

				// 6 columns for trec_eval
				// <query_id> Q0 <doc_id> <rank> <score> <run_id>
				String query_id = String.valueOf(q + 1);
				String doc_id = hitDoc.get("ID");
				String rank = String.valueOf(i + 1);
				String scoreStr = String.valueOf(hits[i].score);
				String run_id = "run1";

				String res = query_id + " " + "Q0" + " " + doc_id + " " + rank + " " + scoreStr + " " + run_id + "\n";

				writer.write(res);
			}
		}

		// close everything and quit
		writer.close();
		ireader.close();
		directory.close();
	}



	// this code just formats cranqrel file
	public static void reformat() {
		String inputFile = "../cranqrel";
		String outputFile = "../cranqrel.txt";

		try (BufferedReader br = new BufferedReader(new FileReader(inputFile));
				BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {

			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(" ");

				if (parts.length == 3) {
					// Inserting 0 at the second position
					String modifiedLine = parts[0] + " 0 " + parts[1] + " " + parts[2];
					bw.write(modifiedLine);
					bw.newLine();
				} else {
					System.out.println("Invalid line format: " + line);
				}
			}

			System.out.println("Processing complete. Check output.txt.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}


