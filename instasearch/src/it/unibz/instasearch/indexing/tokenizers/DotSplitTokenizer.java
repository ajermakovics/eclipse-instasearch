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
public class DotSplitTokenizer extends TermSplitTokenizer {

	/**
	 * @param in
	 */
	public DotSplitTokenizer(TokenStream in) {
		super(in);
	}

	@Override
	public String[] splitTerm(String term) {
		String[] parts = term.split("[.]");
		return parts;
	}
	
	@Override
	protected boolean returnOriginalTerm()
	{
		return true;
	}
}
