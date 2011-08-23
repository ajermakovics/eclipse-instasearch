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

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LengthFilter;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;


public class FileAnalyzer extends Analyzer {

	private int minWordLength;
	
	public FileAnalyzer(int minWordLength){
		super();
		
		this.minWordLength = minWordLength;
	}
	
	public TokenStream tokenStream(Reader reader) {
		
		TokenStream result = new StandardTokenizer(reader); // splits at ". ", "-", etc.
		
		//result = new SysoFilter(result);

		result = new WordSplitTokenizer(result); 	// non-alphanumerics
		result = new DotSplitTokenizer(result); 	// all.package.names
		result = new CamelCaseTokenizer(result); 	// CamelCaseIdentifiers
		
	    result = new LengthFilter(result, minWordLength, 128);
	    result = new LowerCaseFilter(result);

	    return result;
	}

	@Override
	public TokenStream tokenStream(String fieldName, Reader reader) {
		return tokenStream(reader);
	}

	@SuppressWarnings("unused") // used when debugging
	private static class SysoFilter extends TokenFilter
	{
		private TermAttribute termAtt;
		
		public SysoFilter(TokenStream input)
		{
			super(input);
			termAtt = (TermAttribute) addAttribute(TermAttribute.class);
		}

		@Override
		public boolean incrementToken() throws IOException
		{
			if (input.incrementToken()) {
				System.out.println("TERM: " + termAtt.term());
				return true;
			}
			
			return false;
		}
	}
}
