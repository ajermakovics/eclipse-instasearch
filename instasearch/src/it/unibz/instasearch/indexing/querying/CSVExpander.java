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
 * Expands a list of comma separated values (CSV) into a boolean query. eg.
 * ext:xml,java,txt   becomes  (ext:xml OR ext:java OR ext:txt)
 */
public class CSVExpander extends QueryVisitor {

	@Override
	public Query visit(TermQuery termQuery, Field field) {
		
		Term term = termQuery.getTerm();

		if( field != Field.CONTENTS && term.text().contains(",") ) {
			BooleanQuery bq = new BooleanQuery();
			
			String vals[] = term.text().split(",");
			
			if( vals.length == 0 ) return termQuery;
			
			for(String val: vals) {
				if( "".equals(val) ) continue;
				TermQuery tq = new TermQuery(field.createTerm(val));
				bq.add(tq, Occur.SHOULD);
			}
			
			return bq;
		}
		
		return super.visit(termQuery, field);
	}
}
