package it.unibz.instasearch.indexing;

import java.util.Locale;

import org.apache.lucene.index.Term;

/** 
 * Document fields for indexing and searching
 */
public enum Field 
{
	EXT("Extension"), PROJ("Project"), WS("Working Set"),
	CONTENTS, FILE, NAME, JAR, DIR, MODIFIED("Modified");
	
	private String title;
	
	private Field() {
	}
	
	private Field(String title) {
		this.title = title;
	}
	
	/**
	 * @return the title
	 */
	public String getTitle() {
		if( title == null )
			return name();
		
		return title;
	}
	
	/**
	 * Create a term of this field
	 * @param text 
	 * @return Term
	 */
	public Term createTerm(String text) {
		return new Term(name().toLowerCase(Locale.ENGLISH), text);
	}
	
	public static Field fromTerm(Term term)
	{
		return getByName(term.field());
	}
	
	/**
	 * 
	 * @param fieldName (case insensitive)
	 * @return Field or null if no such field exists
	 */
	public static Field getByName(String fieldName)
	{
		try {
			return Field.valueOf(fieldName.toUpperCase());
		} catch(Exception e) {
			return null;
		}
	}
	
	public String toString() { 
		return name().toLowerCase(Locale.ENGLISH);
	}
}