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
package it.unibz.instasearch.indexing;

import java.util.List;
import java.util.Map;

/**
 * Container for search results
 */
public class SearchResult {
	
	private List<SearchResultDoc> resultDocs;
	private Map<String, Float> searchTerms;
	private SearchQuery query;
	
	public SearchResult(SearchQuery searchQuery, List<SearchResultDoc> resultDocs, Map<String, Float> searchTerms)
	{
		this.query = searchQuery;
		this.resultDocs = resultDocs;
		this.searchTerms = searchTerms;
	}

	public boolean isEmpty()
	{
		return resultDocs.isEmpty();
	}
	
	/**
	 * @return the query
	 */
	public SearchQuery getQuery()
	{
		return query;
	}
	
	public List<SearchResultDoc> getResultDocs()
	{
		return resultDocs;
	}
	
	public Map<String, Float> getSearchTerms()
	{
		return searchTerms;
	}
	
	public boolean isFull() {
		return resultDocs.size() == query.getMaxResults();
	}
}