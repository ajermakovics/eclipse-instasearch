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


import org.apache.lucene.search.DefaultSimilarity;

/**
 * 
 */
public class LengthNormSimilarity extends DefaultSimilarity
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public float lengthNorm(String fieldName, int numTerms)
	{
		
		if( fieldName.equals(Field.CONTENTS.toString()) )
			return 1.0f; // don't decrease relevance for long documents 
		else
			return super.lengthNorm(fieldName, numTerms);
		
		//Alternative - Sweetspot similarity:
		//	1/sqrt( steepness * (abs(numTerms-min) + abs(numTerms-max) - (max-min)) + 1 ) .
		//	      - steepnes = 0.5, for [min, max] term docs returns 1
	}
}
