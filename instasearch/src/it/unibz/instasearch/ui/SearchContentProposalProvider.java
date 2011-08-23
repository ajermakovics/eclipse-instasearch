/* Copyright (c) 2009 Andrejs Jermakovics.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrejs Jermakovics - initial implementation
 */
package it.unibz.instasearch.ui;

import it.unibz.instasearch.InstaSearchPlugin;
import it.unibz.instasearch.indexing.Field;
import it.unibz.instasearch.indexing.Searcher;
import it.unibz.instasearch.indexing.querying.ModifiedTimeConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;

class SearchContentProposalProvider extends SimpleContentProposalProvider
{
	private final IContentProposal[] EMPTY_PROPOSALS = new IContentProposal[]{};
	private ResultContentProvider contentProvider;
	
	public SearchContentProposalProvider(ResultContentProvider contentProvider)
	{
		super(new String[]{});
		this.contentProvider = contentProvider;
	}
	
	@Override
	public IContentProposal[] getProposals(String contents, int position)
	{
		String curText = contents.substring(0, position);
		String rest = contents.substring(position);
		
		if( curText.endsWith(" ") )
			return EMPTY_PROPOSALS;
		
		//TODO: use parser
		int colIdx = curText.lastIndexOf(':');
		int spaceIdx = curText.lastIndexOf(' ');
		
		if( colIdx == -1 || spaceIdx > colIdx )
			return getPrefixProposals(curText, Field.CONTENTS, rest);
		
		String beforeCol = curText.substring(0, colIdx);
		String fieldName = beforeCol;
		if( spaceIdx != -1 )
			fieldName = beforeCol.substring(spaceIdx+1);
		
		fieldName = fieldName.toLowerCase();
		List<String> proposalNames = null;
		
		int commaIdx = curText.lastIndexOf(',');
		String prevProposal = "";
		
		if( commaIdx > colIdx && commaIdx < position ) {
			prevProposal = curText.substring(colIdx+1, commaIdx+1);
			colIdx = commaIdx;
		}
		
		String prefix = curText.substring(colIdx+1).toLowerCase(); // filtering text
		Field field = Field.getByName(fieldName);
		
		if( field == null ) 
			return EMPTY_PROPOSALS;
		
		proposalNames = getFieldProposals(field, prefix);
		if( proposalNames.size() == 0 )
			return EMPTY_PROPOSALS;
		
		ArrayList<IContentProposal> proposals = new ArrayList<IContentProposal>();
		
		if( "".equals(prefix) && field == Field.PROJ )
			addCurrentProjectProposal(rest, beforeCol, prevProposal, proposals);
		
		for(String proposalName: proposalNames) {
			if( proposalName.toLowerCase().startsWith(prefix) ) {
				String label = proposalName;
				proposalName = prevProposal + proposalName;
				
				if( proposalName.contains(" ") && !proposalName.contains("\"") ) 
					proposalName = "\"" + proposalName + "\"";
				
				String proposalContent = beforeCol + ':' + proposalName + rest;
				int pos = beforeCol.length() + 1 + proposalName.length();
				
				proposals.add( makeContentProposal(proposalContent, label, pos) );
			}
		}
		
		return proposals.toArray(new IContentProposal[proposals.size()]);
	}

	public List<String> getFieldProposals(Field field, String prefix) {
		
		List<String> proposalNames;
		
		switch(field)
		{
		case PROJ: 		proposalNames = InstaSearchPlugin.getProjectNames(); break;
		case WS: 		proposalNames = InstaSearchPlugin.getWorkingSets(); break;
		case MODIFIED: 	proposalNames = ModifiedTimeConverter.getDurationNames(); break;
		case FILE:  	return Collections.emptyList();
		case DIR:  		return Collections.emptyList();
		default: 		proposalNames = getIndexedProposals(prefix, field); break;
		}

		return proposalNames;
	}

	private void addCurrentProjectProposal(String rest, String beforeCol,
			String prevProposal, ArrayList<IContentProposal> proposals) {
		if( InstaSearchUI.getActiveProject() != null ) {
			String content = beforeCol + ":" + prevProposal + Searcher.CURRENT_PROJECT_CHAR + rest;
			proposals.add( makeContentProposal(content, 
					Searcher.CURRENT_PROJECT_CHAR + " (Current Project)", beforeCol.length()+2) );
		}
	}
	
	/**
	 * @param rest 
	 * @param curText
	 * @return
	 */
	private IContentProposal[] getPrefixProposals(String text, Field field, String rest) {
		
		if( "".equals(text.trim()))
			return EMPTY_PROPOSALS;
		
		String before, prefix;
		
		int spaceIdx = text.lastIndexOf(' ');
		
		if( spaceIdx != -1 ) {
			prefix = text.substring(spaceIdx+1);
			before = text.substring(0, spaceIdx+1);
		} else { // whole prefix
			prefix = text;
			before = "";
		}
		
		ArrayList<IContentProposal> contentProposals = new ArrayList<IContentProposal>();
		
		List<String> proposals = getIndexedProposals(prefix, field);
		if( proposals == null )
			return EMPTY_PROPOSALS;
		
		for(String proposal: proposals) {
			if( proposal.toLowerCase().startsWith(prefix) ) {
				String label = proposal;
				
				String proposalContent = before + proposal + rest;
				int pos = before.length() + proposal.length();
				
				contentProposals.add( makeContentProposal(proposalContent, label, pos) );
			}
		}
		
		return contentProposals.toArray(new IContentProposal[contentProposals.size()]);
	}

	private List<String> getIndexedProposals(String prefix, Field field) {
		List<String> proposals = null;
		try {
			proposals = contentProvider.getProposals(prefix, field);
		} catch (IOException e) {
			return null;
		}
		return proposals;
	}

	private IContentProposal makeContentProposal(final String proposalContent, final String label, final int position) {
		return new IContentProposal() {
			public String getContent() {
				return proposalContent;
			}

			public String getDescription() {
				return null;
			}

			public String getLabel() {
				return label;
			}

			public int getCursorPosition() {
				return position;
			}
		};
	}
}