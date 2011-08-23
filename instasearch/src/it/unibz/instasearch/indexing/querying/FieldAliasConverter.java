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

import java.util.HashMap;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * Converts field aliases to actual field names
 */
public class FieldAliasConverter extends QueryVisitor {

	private static HashMap<String, Field> aliases = new HashMap<String, Field>();

	static {
		aliases.put("project", 		Field.PROJ);
		aliases.put("filetype",		Field.EXT);
		aliases.put("type", 		Field.EXT);
		aliases.put("workingset",	Field.WS);
		aliases.put("age",			Field.MODIFIED);
		aliases.put("folder",		Field.DIR);
	}
	
	/**
	 * 
	 */
	public FieldAliasConverter() {
	}
	
	@Override
	public Query visit(TermQuery termQuery, Field termField) {
		
		Term t = termQuery.getTerm();
		
		if( termField == null && aliases.containsKey(t.field()) ) {
			Field field = aliases.get(t.field());
			Term newTerm = field.createTerm(t.text());
			TermQuery newTermQuery = new TermQuery(newTerm);
			return newTermQuery;
		}
		
		return super.visit(termQuery, termField);
	}
	
	@Override
	public Query visit(PrefixQuery prefixQuery, Field termField) {
		
		Term t = prefixQuery.getPrefix();
		
		if( termField == null && aliases.containsKey(t.field()) ) {
			Field field = aliases.get(t.field());
			Term newTerm = field.createTerm(t.text());
			PrefixQuery newTermQuery = new PrefixQuery(newTerm);
			return newTermQuery;
		}
		
		return super.visit(prefixQuery, termField);
	}
	
	@Override
	public Query visit(WildcardQuery wildcardQuery, Field termField) {
		
		Term t = wildcardQuery.getTerm();
		
		if( termField == null && aliases.containsKey(t.field()) ) {
			Field field = aliases.get(t.field());
			Term newTerm = field.createTerm(t.text());
			WildcardQuery newTermQuery = new WildcardQuery(newTerm);
			return newTermQuery;
		}
		
		return super.visit(wildcardQuery, termField);
	}
}
