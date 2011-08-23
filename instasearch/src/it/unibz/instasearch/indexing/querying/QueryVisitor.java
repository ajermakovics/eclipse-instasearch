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
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

public abstract class QueryVisitor {

	/**
	 * @param query
	 * @return Query
	 */
	public Query visitQuery(Query query) {
		return query;
	}

	/**
	 * @param termQuery
	 * @param field or null if field is not recognized
	 * @return Query
	 */
	public Query visit(TermQuery termQuery, Field field) {
		return termQuery;
	}

	/**
	 * 
	 * @param boolQuery 
	 * @return the Query to replace boolQuery with (or itself).
	 *         If new query is returned, children of boolQuery are still visited
	 */
	public BooleanQuery visit(BooleanQuery boolQuery) {
		return boolQuery;
	}

	/**
	 * @param boolClause
	 * @return true if should visit this clause (and its children), false to skip it
	 */
	public boolean visit(BooleanClause boolClause) {
		return true;
	}

	/**
	 * @param phraseQuery
	 * @return Query
	 */
	public Query visit(PhraseQuery phraseQuery) {
		return phraseQuery;
	}

	/**
	 * @param wildcardQuery
	 * @param field
	 * @return Query
	 */
	public Query visit(WildcardQuery wildcardQuery, Field field) {
		return wildcardQuery;
	}

	/**
	 * @param prefixQuery
	 * @param field
	 * @return Query
	 */
	public Query visit(PrefixQuery prefixQuery, Field field) {
		return prefixQuery;
	}

	/**
	 * Initialize visitor 
	 * @param query
	 */
	public void init(Query query) {
		 
	}

	/**
	 * @param query
	 * @return modified query
	 */
	public Query endVisit(Query query) {
		return query;
	}
	
}