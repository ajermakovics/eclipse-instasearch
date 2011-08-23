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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Replaces named modified field values with millisecond range queries
 */
public class ModifiedTimeConverter extends QueryVisitor {

	private static ArrayList<String> intervalNames = new ArrayList<String>();
	private static Calendar cal = Calendar.getInstance();
	
	static {
		for(Interval interval: Interval.values())
			intervalNames.add(interval.toString().toLowerCase());
		
		intervalNames.add("3 days"); // as an example, any number can be specified
	}
	
	private enum Interval
	{
		TODAY,
		YESTERDAY,
		HOUR(TimeUnit.HOURS.toMillis(1)),
		DAY(TimeUnit.DAYS.toMillis(1)),
		WEEK(TimeUnit.DAYS.toMillis(7)),
		MONTH(TimeUnit.DAYS.toMillis(30))
		;
		
		private long millis;
		
		Interval() {  };
		
		Interval(long millis) 
		{
			this.millis = millis;
		}
	}
	
	public static List<String> getDurationNames()
	{
		return intervalNames;
	}
	
	/**
	 * 
	 */
	public ModifiedTimeConverter() {
	}
	
	@Override
	public Query visit(TermQuery termQuery, Field termField) {
		
		if( termField != Field.MODIFIED )
			return super.visit(termQuery, termField);
		
		Term t = termQuery.getTerm();
		String intervalName = t.text();
		int multiplier = 1;
		
		if( intervalName.matches("^[0-9]+.*$") ) // e.g. "3 days"
		{
			String multiplierString = intervalName.replaceAll("[^0-9]+", ""); // remove non-digits
			multiplier = NumberUtils.toInt(multiplierString.trim(), 1);
			
			intervalName = intervalName.replaceAll("[0-9 ]+", "").trim(); // remove digits
		}
		
		if( intervalName.endsWith("s") )
			intervalName = intervalName.substring(0, intervalName.length() - 1 );
		
		Interval interval = getIntervalByName(intervalName);

		if( interval == null )
			return super.visit(termQuery, termField);

		long start = 0, end = System.currentTimeMillis();
		cal.setTimeInMillis(end);
		
		switch(interval)
		{
		case TODAY:
			cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DATE), 0, 0, 0);
			start = cal.getTimeInMillis();
			break;
		case YESTERDAY:
			cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DATE), 0, 0, 0);
			end = cal.getTimeInMillis();
			cal.add(Calendar.DATE, -1);
			start = cal.getTimeInMillis();
			break;
		default: 
			start = end - multiplier * interval.millis;
		}
		
		String field = Field.MODIFIED.name().toLowerCase();
		NumericRangeQuery rangeQuery = NumericRangeQuery.newLongRange(field, start, end, true, true);
		
		return rangeQuery;
	}
	
	private static Interval getIntervalByName(String intervalName)
	{
		try {
			return Interval.valueOf(intervalName.toUpperCase());
		} 
		catch(Throwable ignored)
		{
			return null;
		}
	}
}
