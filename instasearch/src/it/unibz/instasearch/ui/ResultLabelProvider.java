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
package it.unibz.instasearch.ui;

import it.unibz.instasearch.InstaSearchPlugin;
import it.unibz.instasearch.indexing.SearchQuery;
import it.unibz.instasearch.indexing.SearchResultDoc;
import it.unibz.instasearch.prefs.PreferenceConstants;
import it.unibz.instasearch.ui.ResultContentProvider.MatchLine;

import java.util.Collection;
import java.util.Locale;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.search.ui.text.Match;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.WorkbenchLabelProvider;

class ResultLabelProvider extends LabelProvider implements IStyledLabelProvider, IPropertyChangeListener {
	
	/**
	 * 
	 */
	private static final String HIGHLIGHT_COLOR_NAME = "it.unibz.instasearch.ui.HighlightColor";
	private static final String IMG_OBJ_TEXT_SEARCH_LINE = "org.eclipse.search.ui.line_match";
	private static final String MORE_RESULTS_LABEL = "More...";
	private static final String FIND_SIMILAR_LABEL = "Find similar...";
	private static final String DOWN_ARROW_IMG_KEY = "down_arrow";
	
	private ImageRegistry imageRegistry = new ImageRegistry();
	private Styler highlightStyle;
	
	private WorkbenchLabelProvider labelProvider;
	private ResultContentProvider contentProvider;
	
	private Image matchImage;
	private boolean showMatchCount;
	private boolean showFullPath;
	
	public ResultLabelProvider(ResultContentProvider contentProvider) {
		JFaceResources.getColorRegistry().put(HIGHLIGHT_COLOR_NAME, new RGB(206, 204, 247));
		
		this.labelProvider = new WorkbenchLabelProvider();
		this.highlightStyle = StyledString.createColorRegistryStyler(null, HIGHLIGHT_COLOR_NAME);
		this.contentProvider = contentProvider;
		this.matchImage = InstaSearchUI.getSharedImage(IMG_OBJ_TEXT_SEARCH_LINE);
		
		imageRegistry.put(DOWN_ARROW_IMG_KEY, InstaSearchPlugin.getImageDescriptor(DOWN_ARROW_IMG_KEY));
		
		this.showMatchCount = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_SHOW_MATCH_COUNT);
		this.showFullPath = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_SHOW_FULL_PATH);
		
		InstaSearchPlugin.addPreferenceChangeListener(this);
	}
	
	public StyledString getStyledText(Object element) {
				
		if(element instanceof SearchResultDoc) {
			SearchResultDoc doc = (SearchResultDoc) element;
			
			return decorateFile(doc);
			
		} else if(element instanceof MatchLine) {
			MatchLine lineMatches = (MatchLine) element;
			return decorateLineMatches(lineMatches);
			
		} else if( element instanceof SearchQuery ) {
			// we have search query in 2 cases:
			SearchQuery query = (SearchQuery) element;
			
			if( !query.isLimited() )
				return new StyledString(MORE_RESULTS_LABEL);
			else
				return new StyledString(FIND_SIMILAR_LABEL);	
		}
		
		return new StyledString(element.toString());
	}
	
	private StyledString decorateFile(SearchResultDoc doc) {
		
		String fileName = doc.getFileName();
		StyledString str = new StyledString(fileName);
		
		Collection<String> searchTerms = contentProvider.getSearchTerms();
		
		for(String searchTerm: searchTerms) // highlight matches 
		{
			int termPos = fileName.toLowerCase(Locale.ENGLISH).indexOf(searchTerm);
			
			if( termPos != -1 )
				str.setStyle(termPos, searchTerm.length(), highlightStyle);
		}
		
		int matchCount = doc.getMatchCount();
		
		if( matchCount > 1 && showMatchCount ) 
		{
			str.append(" (", StyledString.COUNTER_STYLER);
			str.append(Integer.toString(matchCount), StyledString.COUNTER_STYLER);
			str.append(" matches)", StyledString.COUNTER_STYLER);
		}
		
		str.append(" - ", StyledString.QUALIFIER_STYLER);
		str.append(doc.getProjectName(), StyledString.QUALIFIER_STYLER);
		
		if( showFullPath ) 
		{
			String filePath = getCompactFilePath(doc, fileName);
			
			if( !"".equals(filePath) ) 
			{
				str.append(" - ", StyledString.QUALIFIER_STYLER);
				str.append(filePath, StyledString.QUALIFIER_STYLER);
			}
		}
		
		if( InstaSearchPlugin.isDebug() )
		{
			str.append(" - [", StyledString.DECORATIONS_STYLER);
			str.append(String.format("%.03f", doc.getScore()), StyledString.DECORATIONS_STYLER);
			str.append("]", StyledString.DECORATIONS_STYLER);
		}
		
		return str;
	}

	/**
	 * File path without the project name and without the filename
	 * 
	 * @param doc
	 * @param fileName
	 * @return
	 */
	private String getCompactFilePath(SearchResultDoc doc, String fileName) {
		
		String projectName = doc.getProjectName();
		String filePath = doc.getFilePath();
		
		int end = filePath.length()-1, start = filePath.indexOf(projectName);
		if( start == -1 ) 
			start = 0;
		else 
			start = start + projectName.length() + 1;
		
		if( filePath.endsWith(fileName) )
			end = filePath.length() - fileName.length() - 1;
		
		if( start > end )
			return "";
			
		filePath = filePath.substring(start, end);
		
		return filePath;
	}
	
	private StyledString decorateLineMatches(MatchLine lineMatches) {
		
		String line = lineMatches.getLine();
		StyledString str = new StyledString(line);
		
		for(Match m: lineMatches.getMatches()) 
		{
			str.setStyle(m.getOffset(), m.getLength(), highlightStyle);
		}
		
		StyledString ret = new StyledString();
		ret.append(lineMatches.getLineNumber() + ": ", StyledString.QUALIFIER_STYLER);
		ret.append(str);
		
		return ret;
	}
	
	public Image getImage(Object element) {
		try {
			if(element instanceof SearchResultDoc) {
				SearchResultDoc doc = (SearchResultDoc) element;
				
				Image image = null;
				
				if( !doc.isInJar() )
					image = labelProvider.getImage(doc.getFile());
				
				if( image == null )
					image = getFileImage(doc);
				
				if( image == null )
					image = InstaSearchUI.getSharedImage(ISharedImages.IMG_OBJ_FILE);
				
				return image;
				
			} else if( element instanceof MatchLine )
				return matchImage ;
			else if( element instanceof Exception )
				return InstaSearchUI.getSharedImage(ISharedImages.IMG_OBJS_WARN_TSK);
			else if( element instanceof SearchQuery )
				return imageRegistry.get(DOWN_ARROW_IMG_KEY);
			
		} catch(Exception e) {
			InstaSearchPlugin.log(e);
			return InstaSearchUI.getSharedImage(ISharedImages.IMG_OBJS_WARN_TSK);
		}
		
		return InstaSearchUI.getSharedImage(ISharedImages.IMG_OBJS_INFO_TSK); // no results
	}

	private Image getFileImage(SearchResultDoc doc) 
	{
		String ext = doc.getFileExtension();
		Image fileImage = imageRegistry.get(ext);
		
		if( fileImage == null ) {
			ImageDescriptor imageDescriptor = PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(doc.getFileName());
			fileImage = imageDescriptor.createImage(true); 
			imageRegistry.put(ext, fileImage); // cache created images for extensions
		}
		
		return fileImage;
	}
	
	public void propertyChange(PropertyChangeEvent event) {
		// update preferences
		if( PreferenceConstants.P_SHOW_MATCH_COUNT.equals(event.getProperty()))
			showMatchCount = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_SHOW_MATCH_COUNT);
		else if( PreferenceConstants.P_SHOW_FULL_PATH.equals(event.getProperty()))
			showFullPath = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_SHOW_FULL_PATH);
	}
	
	@Override
	public void dispose()
	{
		super.dispose();
		imageRegistry.dispose();
		InstaSearchPlugin.removePreferenceChangeListener(this);
	}
}