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

import java.io.IOException;
import java.util.LinkedList;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 * Splits terms. Returns the original term and its split parts 
 */
public abstract class TermSplitTokenizer extends TokenFilter {

	private LinkedList<Token> tokens = new LinkedList<Token>();
	
	private TermAttribute termAtt;
	private OffsetAttribute offsetAtt;
	private PositionIncrementAttribute posAtt;

	public TermSplitTokenizer(TokenStream in) {
	    super(in);
	    
	    assert( in.hasAttribute(TermAttribute.class) );
	    assert( in.hasAttribute(OffsetAttribute.class) );
	    assert( in.hasAttribute(PositionIncrementAttribute.class) );
	    
	    termAtt = (TermAttribute) addAttribute(TermAttribute.class);
		offsetAtt = (OffsetAttribute) addAttribute(OffsetAttribute.class);
		posAtt = (PositionIncrementAttribute) addAttribute(PositionIncrementAttribute.class);
	}
	
	@Override
	public boolean incrementToken() throws IOException
	{
		if( !tokens.isEmpty() )
		{
			applyToken(tokens.removeFirst());
		}
		else if( input.incrementToken() )
		{
			splitIntoTokens();
			
			if( !tokens.isEmpty() )
			{
				if( ! returnOriginalTerm() )
					applyToken( tokens.removeFirst() );
			}
		}
		else
		{
			return false; // does not have any more tokens
		}
		
		return true;
	}

	private void splitIntoTokens()
	{
		String term = termAtt.term();
		String[] termParts = splitTerm(term);

		if(termParts.length > 1)
		{
			int termPos = offsetAtt.startOffset();
			
			for (int i = 0; i < termParts.length; i++) 
			{
				String termPart = termParts[i];
				int termPartPos = termPos + term.indexOf(termPart);
				int termPartEndPos = termPartPos + termPart.length();

				Token newToken = new Token(termPart, termPartPos, termPartEndPos);
				newToken.setPositionIncrement(0); // in the same position

				tokens.add( newToken );
			}
		}
	}

	private void applyToken(Token token)
	{
		termAtt.setTermBuffer(token.termBuffer(), 0, token.termLength());
		posAtt.setPositionIncrement(token.getPositionIncrement());
		offsetAtt.setOffset(token.startOffset(), token.endOffset());
	}

	/**
	 * Return original term together with the parts
	 * @return returnOriginalTerm
	 */
	protected boolean returnOriginalTerm()
	{
		return false;
	}
	
	/**
	 * Split term into an array of terms
	 * 
	 * @param term
	 * @return split term
	 */
	public abstract String[] splitTerm(String term);
}
