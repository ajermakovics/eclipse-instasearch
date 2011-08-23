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
 * Splits words at camel case, underscore and dot
 */
public class CamelCaseTokenizer extends TermSplitTokenizer {

	public CamelCaseTokenizer(TokenStream in) {
	    super(in);
	}
	
	@Override
	public String[] splitTerm(String term) {
		
		String newWord = term.replaceAll("([A-Z][a-z])", "_$1"); // not ideal, but short
		newWord = newWord.replaceAll("([a-z])([A-Z])", "$1_$2"); 
		
		return newWord.split("[_]"); // will also split CONSTANT_NAMES 
	}

	@Override
	protected boolean returnOriginalTerm() {
		return true;
	}
}
