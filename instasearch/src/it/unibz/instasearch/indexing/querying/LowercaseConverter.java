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
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * 
 */
public class LowercaseConverter extends QueryVisitor {


	@Override
	public void init(Query query) {
	}
	
	@Override
	public Query visit(TermQuery termQuery, Field field) {
		
		if( field == Field.CONTENTS  )
		{
			Term term = termQuery.getTerm();
			return new TermQuery(field.createTerm( term.text().toLowerCase() ));
		}
			
		return termQuery;
	}
	
	@Override
	public Query visit(PrefixQuery prefixQuery, Field field) {
		
		if( field == Field.CONTENTS  ) 
		{
			Term term = prefixQuery.getPrefix();
			return new PrefixQuery( field.createTerm( term.text().toLowerCase() ) );
		}
		
		return super.visit(prefixQuery, field);
	}
	
	@Override
	public Query visit(WildcardQuery wildcardQuery, Field field) {
		if( field == Field.CONTENTS  ) 
		{
			Term term = wildcardQuery.getTerm();
			return new WildcardQuery( field.createTerm( term.text().toLowerCase() ) );
		}
		return super.visit(wildcardQuery, field);
	}
	
	@Override
	public Query visit(PhraseQuery phraseQuery) 
	{
		PhraseQuery newQuery = new PhraseQuery();
		
		for(Term term: phraseQuery.getTerms())
		{
			Field field = Field.getByName( term.field() );
			if( field != Field.CONTENTS )
				return phraseQuery;
			
			Term newTerm = Field.CONTENTS.createTerm( term.text().toLowerCase() );
			newQuery.add( newTerm );
		}
		
		newQuery.setSlop(phraseQuery.getSlop());
		newQuery.setBoost( phraseQuery.getBoost() );
		
		return newQuery;
	}
}
