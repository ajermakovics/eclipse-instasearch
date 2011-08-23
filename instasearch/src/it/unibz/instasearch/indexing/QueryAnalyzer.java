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

import it.unibz.instasearch.indexing.tokenizers.CamelCaseTokenizer;
import it.unibz.instasearch.indexing.tokenizers.DotSplitTokenizer;
import it.unibz.instasearch.indexing.tokenizers.WordSplitTokenizer;
import it.unibz.instasearch.indexing.tokenizers.standard.StandardTokenizer;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordTokenizer;
import org.apache.lucene.analysis.LengthFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * Analyzer for user entered search queries
 */
public class QueryAnalyzer extends Analyzer {

	private static final int MAX_WORD_LENGTH = 128;
	private int minWordLength;

	public QueryAnalyzer(int minWordLength) {
		super();
		
		this.minWordLength = minWordLength;
	}
	
	@Override
	public TokenStream tokenStream(String fieldName, Reader reader)
	{
		if( Field.CONTENTS.toString().equals(fieldName) ) {
			TokenStream result = new StandardTokenizer(reader); // splits at ". ", "-"
			
			result = new WordSplitTokenizer(result); 	// non-alphanumerics
			result = new DotSplitTokenizer(result); 	// com.package.names
			result = new CamelCaseTokenizer(result); 	// CamelCaseIdentifiers
			
			//result = new LowerCaseFilter(result);
			result = new LengthFilter(result, minWordLength, MAX_WORD_LENGTH);
			
			return result;
			
		} else { // PROJECT, EXT fields
			return new KeywordTokenizer(reader); // return whole stream contents as token
		}
	    
	}

}
