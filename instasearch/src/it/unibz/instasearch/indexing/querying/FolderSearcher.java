/*
 * Copyright (c) 2009 Andrejs Jermakovics.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrejs Jermakovics - initial implementation
 */
package it.unibz.instasearch.indexing.querying;

import it.unibz.instasearch.indexing.Field;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * 
 */
public class FolderSearcher extends QueryVisitor {

	@Override
	public Query visit(TermQuery termQuery, Field field) {
		
		Term t = termQuery.getTerm();
		
		if( field == Field.DIR ) 
		{
			if( t.text().startsWith("/") ) // already absolute
			{
				Term fileTerm = Field.FILE.createTerm(t.text());
				return new PrefixQuery(fileTerm);
			}
			
			Term fileTerm = Field.FILE.createTerm("/*/" + t.text() + "/*");
			
			return new WildcardQuery(fileTerm);
		}
		
		return super.visit(termQuery, field);
	}
	
	@Override
	public Query visit(PrefixQuery prefixQuery, Field field) {
		Term t = prefixQuery.getPrefix();
		
		if( field == Field.DIR ) 
		{
			String text = t.text();
			
			if( text.startsWith("/") ) // already absolute
			{
				Term fileTerm = Field.FILE.createTerm(text);
				return new PrefixQuery(fileTerm); //change field
			}
			
			Term fileTerm = Field.FILE.createTerm("/*/" + text + "*/*"); // meant to end with * already
			
			return new WildcardQuery(fileTerm);
		}
		
		return super.visit(prefixQuery, field);
	}
	
	@Override
	public Query visit(WildcardQuery wildcardQuery, Field field) {
		
		Term t = wildcardQuery.getTerm();
		
		if( field == Field.DIR ) 
		{
			String text = t.text();
			
			if( text.startsWith("/") ) // already absolute
			{
				Term fileTerm = Field.FILE.createTerm(text);
				return new WildcardQuery(fileTerm); //change field
			}
			
			Term fileTerm = Field.FILE.createTerm("/*/" + t.text());
			
			return new WildcardQuery(fileTerm);
		}
		
		return super.visit(wildcardQuery, field);
	}
	
	
}
