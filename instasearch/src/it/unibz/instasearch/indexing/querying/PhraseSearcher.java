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

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;

/**
 * Converts a boolean query into a phrase query
 */
public class PhraseSearcher extends QueryVisitor 
{
	/**
	 * 
	 */
	private static final int DEFAULT_SLOP = 10;

	@Override
	public BooleanQuery visit(BooleanQuery boolQuery) 
	{
		PhraseQuery phraseQuery = new PhraseQuery();
		
		for(BooleanClause clause: boolQuery.getClauses())
		{
			if( clause.isProhibited() || !clause.isRequired() || !(clause.getQuery() instanceof TermQuery) )
				return super.visit(boolQuery); // only consider required terms
			
			TermQuery tq = (TermQuery) clause.getQuery();
			
			Field field = Field.getByName( tq.getTerm().field() );
			if( field != Field.CONTENTS )
				continue;
			
			phraseQuery.add(tq.getTerm());
		}
		
		phraseQuery.setSlop(DEFAULT_SLOP);
		
		BooleanQuery bq = new BooleanQuery();
		bq.add(phraseQuery, Occur.SHOULD);
		bq.add(boolQuery, Occur.SHOULD);
		bq.setBoost(boolQuery.getBoost());
		
		phraseQuery.setBoost(boolQuery.getBoost());
		boolQuery.setBoost( phraseQuery.getBoost()*0.5f );
		
		return bq;
	}
	
}
