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

import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * 
 */
public class FilterSetter extends QueryVisitor {

	private Map<Field, Set<String>> filter;
	
	public FilterSetter() {
	}
	
	@Override
	public Query visit(TermQuery termQuery, Field field) {
		return addFilters(termQuery, field);
	}
		
	@Override
	public Query visit(PrefixQuery originalQuery, Field field) {
		return addFilters(originalQuery, field);
	}
	
	@Override
	public Query visit(WildcardQuery wildcardQuery, Field field) {
		return addFilters(wildcardQuery, field);
	}
	
	private Query getFilterQuery(Query originalQuery, Field field, Set<String> values) 
	{
		BooleanQuery boolQuery = new BooleanQuery();
		
		if( originalQuery != null )
		{
			boolQuery.add(originalQuery, Occur.SHOULD); // original term
			boolQuery.setBoost( originalQuery.getBoost() );
		}
		
		for(String value: values)
			boolQuery.add(new TermQuery(field.createTerm(value)), Occur.SHOULD);
			
		return boolQuery;
	}
	
	private Query addFilters(Query originalQuery, Field field)
	{
		Set<String> values = filter.get(field);
		if( values != null )
		{
			filter.remove(field);
			return getFilterQuery(originalQuery, field, values);
		}
		return originalQuery;
	}
	
	@Override
	public Query endVisit(Query query) 
	{
		if( filter.isEmpty() )
			return super.endVisit(query);
		
		// combine filters with main query using AND 
		BooleanQuery conjQuery = new BooleanQuery();
		conjQuery.add(query, Occur.MUST);
		
		for(Field field: filter.keySet())
		{
			Set<String> values = filter.get(field); // possible values
			
			if( values.size() == 1 )
			{
				String value = values.iterator().next();
				conjQuery.add( new TermQuery(field.createTerm(value)), Occur.MUST);
			}
			else // several values, any of them should match (using OR here)
			{
				conjQuery.add( getFilterQuery(null, field, values), Occur.MUST);
			}
		}
		
		return conjQuery;
	}
	
	/**
	 * @param filter
	 */
	public void setFilter(Map<Field, Set<String>> filter) {
		 this.filter = filter;
	}

}
