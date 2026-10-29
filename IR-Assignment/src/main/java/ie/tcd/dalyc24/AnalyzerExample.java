package ie.tcd.dalyc24;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class AnalyzerExample {
    public static void main(String[] args) throws Exception {
        MyCustomAnalyzer analyzer = new MyCustomAnalyzer();
        
        String[] inputs = {
            "The quick brown foxes jumped over the lazy dogs",
            "Running, jumping, and playing are good exercises",
            "She was reading a book about artificial intelligence",
            "The cat's whiskers twitched as it dreamed",
            "To be or not to be, that is the question"
        };
        
        for (String input : inputs) {
            System.out.println("Input: " + input);
            System.out.print("Output: ");
            
            TokenStream stream = analyzer.tokenStream(null, input);
            CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            
            while (stream.incrementToken()) {
            	//System.out.println(attr.toString());
                System.out.print(attr.toString() + " ");
            }
            
            stream.end();
            stream.close();
            System.out.println("\n");
        }
        
        analyzer.close();
    }
}