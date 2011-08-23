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
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * Makes the query fuzzy.
 * Replaces each TermQuery by Query of the form: term* OR *term* OR term~
 */
public class QueryFuzzifier extends QueryVisitor {
	
	@Override
	public Query visit(TermQuery termQuery, Field field) {
		
		if( field != Field.CONTENTS )
			return termQuery;
		
		// turn term query into: (term* OR *term* OR term~)
		
		Term term = termQuery.getTerm();
		
		PrefixQuery prefixQuery = new PrefixQuery(term);
		prefixQuery.setBoost( termQuery.getBoost() );
		
		Term wildcardTerm = Field.CONTENTS.createTerm("*" + term.text() + "*");
		WildcardQuery wildcardQuery = new WildcardQuery(wildcardTerm);
		wildcardQuery.setBoost( termQuery.getBoost() * 0.75f );
		
		FuzzyQuery fuzzyQuery = new FuzzyQuery( term );
		fuzzyQuery.setBoost( termQuery.getBoost() * 0.5f );
		
		BooleanQuery boolQuery = new BooleanQuery();
		boolQuery.add(prefixQuery, 		Occur.SHOULD);
		boolQuery.add(wildcardQuery, 	Occur.SHOULD);
		boolQuery.add(fuzzyQuery, 		Occur.SHOULD);
		boolQuery.setBoost( termQuery.getBoost() );
		
		return boolQuery;
	}
	
	@Override
	public Query visit(PhraseQuery phraseQuery) {
		BooleanQuery bq = new BooleanQuery();
		
		for(Term t: phraseQuery.getTerms()) {
			Field f = Field.getByName(t.field());
			
			if( f != Field.CONTENTS )
				return phraseQuery;
			
			bq.add(new FuzzyQuery(t), Occur.SHOULD);
		}
		
		return bq;
	}
	
	@Override
	public boolean visit(BooleanClause boolClause) {
		if(boolClause.getOccur() == Occur.MUST_NOT) // skip prohibited, don't fuzzify
			return false;
		
		return true;
	}
	
}
