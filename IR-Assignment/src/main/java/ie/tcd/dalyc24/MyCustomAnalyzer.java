package ie.tcd.dalyc24;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.CapitalizationFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.KStemFilter;

public class MyCustomAnalyzer extends Analyzer {

    private final CharArraySet stopWords;

    public MyCustomAnalyzer() {
        this.stopWords = EnglishAnalyzer.getDefaultStopSet();
    }
    
    
    @SuppressWarnings("resource")
	@Override
    protected TokenStreamComponents createComponents(String fieldName) {
        StandardTokenizer src = new StandardTokenizer();
        TokenStream result = src;
        result = new LowerCaseFilter(result);
        result = new StopFilter(result, stopWords);
     
        
        result = new EnglishPossessiveFilter(result);
        result = new EnglishMinimalStemFilter(result);
        result = new PorterStemFilter(result);
     
        // Configure ShingleFilter for n-grams (e.g., bigrams/trigrams)
        //ShingleFilter shingleFilter = new ShingleFilter(result, 2, 2); // bigrams and trigrams
        //shingleFilter.setTokenSeparator(" ");
        //shingleFilter.setOutputUnigrams(true); // If you want unigrams as well
        
        //result = shingleFilter;
        
        result = new KStemFilter(result);
        //result = new CapitalizationFilter(result);
        return new TokenStreamComponents(src, result);
    }
}
