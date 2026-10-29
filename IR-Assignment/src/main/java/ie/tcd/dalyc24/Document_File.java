package ie.tcd.dalyc24;

public class Document_File {
	String ID = "";
	String title = "";
	String author = "";
	String source = "";
	String content = "";
	
	public void print() {
		System.out.println("PRINTOUT");
        //currentDocument = new Document(line.split("\\s+")[1]);
		System.out.println("ID");
		System.out.println(ID);
		
		System.out.println("TITLE");
		System.out.println(title);
        
		System.out.println("AUTHOR");
		System.out.println(source);
		
		System.out.println("SOURCE");
		System.out.println(author);
        
		System.out.println("CONTENT");
		System.out.println(content);
	}
}
