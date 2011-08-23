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
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Searches in the "name" field as well as in contents field if the query is a term query
 */
public class FileNameSearcher extends QueryVisitor {

	private Query originalQuery;
	
	@Override
	public void init(Query query) {
		originalQuery = query;
	}
	
	@Override
	public Query visit(TermQuery termQuery, Field field) {
		
		if( field == Field.CONTENTS && termQuery == originalQuery ) // if our query is a term query
		{
			Term term = termQuery.getTerm();
			Term nameTerm = Field.NAME.createTerm(term.text());
			TermQuery nameQuery = new TermQuery(nameTerm);
			nameQuery.setBoost( termQuery.getBoost() * 2f );
			
			BooleanQuery boolQuery = new BooleanQuery();
			boolQuery.add(nameQuery, Occur.SHOULD);
			boolQuery.add(termQuery, Occur.SHOULD);
			
			return boolQuery;
		}
			
		return termQuery;
	}
	
}
