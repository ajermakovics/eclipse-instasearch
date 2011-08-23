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
package it.unibz.instasearch.indexing.tokenizers;


import org.apache.lucene.analysis.TokenStream;

/**
 * Splits words at non-alphanumeric characters
 */
public class WordSplitTokenizer extends TermSplitTokenizer {

	/**
	 * @param in
	 */
	public WordSplitTokenizer(TokenStream in) {
		super(in);
	}
	
	@Override
	public String[] splitTerm(String term) 
	{
		return term.split("[^A-Za-z0-9\u00E0-\u00FF_'.]"); //"\\W" or "[^A-Za-z0-9'\u00E0-\u00FF]"
	}
	
}
