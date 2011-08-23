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
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * A query that can accepts visitors for its clauses
 * The visitors can modify the query
 */
public class VisitableQuery {
	
	private Query query;
	
	public VisitableQuery(Query q) {
		this.query = q;
	}
	
	/**
	 * Accept a query visitor
	 * @param queryVisitor
	 */
	public void accept(QueryVisitor queryVisitor) {
		queryVisitor.init(query);
		query = accept(query, queryVisitor);
		query = queryVisitor.endVisit(query);
	}
	
	/**
	 * Recursively go through all the clauses of the query and call the visitor
	 * 
	 * @param query
	 * @param queryVisitor
	 * @return
	 */
	private Query accept(Query query, QueryVisitor queryVisitor) {
		
		Query returnQuery = query;
		
		if( query instanceof TermQuery ) {
			TermQuery termQuery = (TermQuery) query;
			Field field = Field.getByName(termQuery.getTerm().field());
			
			Query newQuery = queryVisitor.visit( termQuery, field );
			
			newQuery.setBoost( query.getBoost() );
			returnQuery = newQuery;
			
		} else if( query instanceof BooleanQuery ) {
			BooleanQuery boolQuery = (BooleanQuery) query;
			BooleanQuery newBoolQuery = queryVisitor.visit(boolQuery);
			
			for(BooleanClause boolClause: boolQuery.getClauses()) {
				if( ! queryVisitor.visit(boolClause) ) continue;
				Query newQuery = accept( boolClause.getQuery(), queryVisitor );
				boolClause.setQuery( newQuery );
			}
			
			newBoolQuery.setBoost( boolQuery.getBoost() );
			newBoolQuery.setMinimumNumberShouldMatch(boolQuery.getMinimumNumberShouldMatch());
			
			returnQuery = newBoolQuery;
		}
		else if( query instanceof FilteredQuery ) {
			FilteredQuery fq = (FilteredQuery) query;
			Query newQuery = accept(fq.getQuery(), queryVisitor);
			
			if( newQuery != fq.getQuery() )
			{
				FilteredQuery newFq = new FilteredQuery(fq.getQuery(), fq.getFilter());
				newFq.setBoost( fq.getBoost() );

				returnQuery = newFq;
			}
		}
		else if( query instanceof PhraseQuery ) {
			PhraseQuery phraseQuery = (PhraseQuery) query;
			
			returnQuery = queryVisitor.visit(phraseQuery);
			
		} else if( query instanceof WildcardQuery ) {
			WildcardQuery wildcardQuery = (WildcardQuery) query;
			Field field = Field.getByName(wildcardQuery.getTerm().field());
			
			Query newQuery = queryVisitor.visit( wildcardQuery, field );
			
			newQuery.setBoost( query.getBoost() );
			returnQuery = newQuery;
			
		} else if( query instanceof PrefixQuery ) {
			PrefixQuery prefixQuery = (PrefixQuery) query;
			Field field = Field.getByName(prefixQuery.getPrefix().field());
			
			Query newQuery = queryVisitor.visit( prefixQuery, field );
			
			newQuery.setBoost( query.getBoost() );
			returnQuery = newQuery;
		} 
		else {
			returnQuery = queryVisitor.visitQuery(query);
		}
		
		return returnQuery;
	}
	
	/**
	 * @return the resulting query after all visitors have changed it
	 */
	public Query getQuery() {
		return query;
	}
	
}