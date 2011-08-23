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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Replaces last TermQuery by (TermQuery OR PrefixQuery) in the query
 */
public class LastTermQueryPrefixer extends QueryVisitor {

	private int clauseCount;
	private int minTermLength;
	
	public LastTermQueryPrefixer(int minTermLength) {
		clauseCount = 0;
		this.minTermLength = minTermLength;
	}
	
	@Override
	public Query visit(TermQuery termQuery, Field field) {
		
		if( clauseCount == 0 ) { // at last query
			Term term = termQuery.getTerm();
			
			if( term.text().length() < minTermLength ) // exclude short queries to avoid clause overload
				return termQuery;
			
			PrefixQuery prefixQuery = new PrefixQuery(term);
			prefixQuery.setBoost( termQuery.getBoost()/4f );
			
			BooleanQuery boolQuery = new BooleanQuery();
			boolQuery.add(termQuery, Occur.SHOULD);
			boolQuery.add(prefixQuery, Occur.SHOULD);
			boolQuery.setBoost( termQuery.getBoost() );
			
			return boolQuery;
		}
		return termQuery;
	}
	
	@Override
	public BooleanQuery visit(BooleanQuery boolQuery) {
		clauseCount += boolQuery.getClauses().length;
		return super.visit(boolQuery);
	}
	
	@Override
	public boolean visit(BooleanClause boolClause) {
		clauseCount--;
		
		if(boolClause.getOccur() == Occur.MUST_NOT) // skip prohibited
			return false;
		
		return true;
	}

}
