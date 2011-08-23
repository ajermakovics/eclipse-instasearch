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

import it.unibz.instasearch.InstaSearch;
import it.unibz.instasearch.InstaSearchPlugin;
import it.unibz.instasearch.indexing.Field;
import it.unibz.instasearch.indexing.WorkspaceIndexer;
import it.unibz.instasearch.indexing.SearchQuery;
import it.unibz.instasearch.indexing.SearchResult;
import it.unibz.instasearch.indexing.SearchResultDoc;
import it.unibz.instasearch.indexing.Searcher;
import it.unibz.instasearch.indexing.StorageIndexer;
import it.unibz.instasearch.prefs.PreferenceConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IEditorInput;

class ResultContentProvider implements ITreeContentProvider {
	
	private static final String[] NO_INDEX_MESSAGE = new String[] {"Index is not built"};
	private static final String[] NO_FILE_MESSAGE = new String[] {"File missing"};
	private static final String NO_RESULTS_MESSAGE = "0 matches";
	
	public static final double MAX_LINE_SIMILARITY = 0.9; // if above that, only one of the similar lines is kept
	public static final int MAX_LINES_TO_PROCESS = 5000; // max nr of lines to read from files (prevent slowdown on huge files)
	
	private WorkspaceIndexer indexer;
	private Searcher searcher;
	
	/**
	 * Map of search terms to their boost score
	 */
	private SearchQuery currentSearchQuery;
	private Map<String, Float> searchTerms;
	
	private Object[] cachedResults;
	private SearchResultDoc cachedResultDoc;
	private Object[] cachedChildren;
	private int resultCount;
	
	
	public ResultContentProvider() {
		InstaSearch instaSearch = InstaSearchPlugin.getInstaSearch();
		
		this.indexer = instaSearch.getIndexer();
		this.searcher = instaSearch.getSearcher();
	}
	
	public void inputChanged(Viewer v, Object oldInput, Object newSearch) {
		if( newSearch==null || !(newSearch instanceof SearchQuery) ) {
			currentSearchQuery = null; // clear cache
			cachedResults = null;
		}	
	}
	
	public void dispose() {	
	}
	
	public Object[] getElements(Object searchQueryObj) 
	{	
		if( searchQueryObj==null || !(searchQueryObj instanceof SearchQuery) )
			return Collections.EMPTY_LIST.toArray(); // not searching
		
		SearchQuery searchQuery = (SearchQuery) searchQueryObj;
		String searchString = searchQuery.getSearchString();
		
		if( searchString==null || searchString.toString().length() < Searcher.MIN_QUERY_LENGTH )
			return Collections.EMPTY_LIST.toArray();
		
		try
		{
			if( ! indexer.isIndexed() )
				return NO_INDEX_MESSAGE;
		} catch(Exception e)
		{
			InstaSearchPlugin.log(e);
			return new Exception[]{e};
		}
		
		if( searchQuery.equals(currentSearchQuery) && cachedResults != null ) // same query
			return cachedResults;
		
		SearchResult result = null;
		cachedResults = null;
		resultCount = 0;
		Object[] resultArray = null;
		
		if(searchString != null) 
		{
			try {
				result = searcher.search(searchQuery); // do the search
				currentSearchQuery = searchQuery;
				
				if( result == null ) {
					if( !searchQuery.isFuzzy() ) {
						SearchQuery newQuery = new SearchQuery(searchQuery);
						newQuery.setExact(false);
						newQuery.setFuzzy(true);
						resultArray = new Object[]{NO_RESULTS_MESSAGE, newQuery}; // add fuzzy query
					} else {
						resultArray = new Object[]{NO_RESULTS_MESSAGE};
					}
					
					cachedResults = resultArray;
					
					return resultArray;
				}
				
				searchTerms = result.getSearchTerms();
				
			} catch (Exception e) {
				InstaSearchPlugin.log(e);
				return new Exception[]{e};
			}
		}
		
		List<SearchResultDoc> resultDocs = result.getResultDocs();
		this.resultCount = resultDocs.size();
		
		boolean addMoreResults = false, addFindSimilar = false;
		
		if( searchQuery.isLimited() && result.isFull() ) { // if only showing limited number of matches
			addMoreResults = true;
		}
		else
		{
			if( searchQuery.isExact() && !searchQuery.isFuzzy() ) // if query is exact, can try search for individual tokens
				addFindSimilar = true;
		}
		
		if( addMoreResults || addFindSimilar )
			resultArray = new Object[resultCount + 1];  // +1 because we append "More..." element (a SearchQuery object)
		else
			resultArray = new Object[resultCount];
		
		resultDocs.toArray(resultArray);
		
		if( addMoreResults ) { // if more results, create More result entry (return all elements)
			SearchQuery moreResultsQuery = new SearchQuery(currentSearchQuery);
			moreResultsQuery.setMaxResults(SearchQuery.UNLIMITED_RESULTS); // all results
			resultArray[resultCount] = moreResultsQuery;
		}
		else if( addFindSimilar )
		{
			SearchQuery findSimilarQuery = new SearchQuery(currentSearchQuery);
			findSimilarQuery.setExact(false);
			resultArray[resultCount] = findSimilarQuery;
		}
		
		cachedResults = resultArray;
		
		return resultArray;
	}
	
	/**
	 *  Count of search result docs from last search
	 * @return result count
	 *  
	 */
	public int getResultCount()
	{
		return resultCount; 
	}
	
	/**
	 * Returns last search result elements.
	 * Includes search result docs and additional action entries (eg More results)
	 * 
	 * @return  array of last search result elements
	 */
	public Object[] getElements() {
		return cachedResults;
	}
	
	public Object[] getChildren(Object parent) 
	{
		if( parent instanceof SearchResultDoc ) {
			
			SearchResultDoc doc = (SearchResultDoc) parent;
			
			if( cachedResultDoc != null && doc.equals(cachedResultDoc) )
				return cachedChildren; // cache results
			
			List<MatchLine> matches = null;
			Object[] children = null;
			
			try {
				matches = getMatchLines(doc, true, null);
				if( matches != null )
					children = matches.toArray();
			} catch (Exception e) {
				InstaSearchPlugin.log(e);
			}
			
			if( matches == null )
				children = NO_FILE_MESSAGE;
			
			cachedResultDoc = doc;
			cachedChildren = children;
			
			return children;
		}
		else if( parent instanceof Exception ) {
			Exception e = (Exception) parent;
			return e.getStackTrace();
		}
		
		return Collections.EMPTY_LIST.toArray();
	}
	
	public interface MatchFindCallback
	{
		void matchFound(MatchLine line);
		boolean isCanceled();
	}
	
	/**
	 * Returns matched lines
	 * @param doc
	 * @param limit
	 * @return
	 * @throws Exception
	 */
	List<MatchLine> getMatchLines(SearchResultDoc doc, boolean limit, MatchFindCallback callback) throws Exception {
		
		if( searchTerms == null || currentSearchQuery == null )
			return Collections.emptyList();
		
		int maxMatches = InstaSearchPlugin.getIntPref(PreferenceConstants.P_SHOWN_LINES_COUNT);
		List<MatchLine> matchedLines = new ArrayList<MatchLine>();
		int matchCount = doc.getMatchCount();
		String searchString = currentSearchQuery.getSearchString().toLowerCase();
		
		IStorage f = getStorage(doc);
		if( f == null ) {
			// index might be outdated (disabled updating)
			//TODO: remove file from index (update index)
			return null;
		}
		
		InputStream fileInputStream = null;
		
		if( f instanceof IFile ) {
			IFile file = (IFile) f;
			if( !file.exists() )
				return null;
			fileInputStream = file.getContents(true);
		} else {
			fileInputStream = f.getContents();
		}
		
		LineNumberReader lineReader = new LineNumberReader(new InputStreamReader(fileInputStream)); // is a buffered reader
		
		String line;
		
		// Read through file one line at a time 
		while ( (line = lineReader.readLine()) != null ) {
			
			if( callback != null && callback.isCanceled() ) break;
			//if( currentSearchQuery.isCanceled() ) break;
			
			if( "".equals(line) ) continue;
			
			Map<String, List<Integer>> lineTerms = StorageIndexer.extractTextTerms(line);
			if( lineTerms.isEmpty() ) continue;
			
			HashSet<String> matchedTerms = new HashSet<String>(searchTerms.keySet()); // search terms that appear on this line
			matchedTerms.retainAll(lineTerms.keySet());
			
			if( matchedTerms.isEmpty() && matchCount != 0 && limit ) // if have matches in general, but not on this line, then skip
				continue;
			
			float[] lineTermScoreVector = doc.getTermScoreVector(lineTerms.keySet());
			float[] matchedTermScoreVector = doc.getTermScoreVector(matchedTerms);
			
			MatchLine matchLine = new MatchLine(doc, line, lineReader.getLineNumber(), matchedTerms, lineTermScoreVector, matchedTermScoreVector);
			matchedLines.add(matchLine);
			
			addMatches(matchLine, lineTerms, matchedTerms, searchString);
			if( callback != null )
				callback.matchFound(matchLine);
			
			if( lineReader.getLineNumber() > MAX_LINES_TO_PROCESS )
				break;
			
			//TODO: break if all current matches have high score (eg >0.9)
		}
		
		lineReader.close();
		
		if(limit && matchedLines.size() > maxMatches) {	
			matchedLines = getTopMatchLines(maxMatches, matchedLines); // return TOP N lines
			return matchedLines;
			
		} else
			return matchedLines;
	}

	private List<MatchLine> getTopMatchLines(int maxMatchLines, List<MatchLine> matchedLines) {
		Collections.sort(matchedLines); // sort by match count, score, line
		removeSimilarLines(matchedLines, maxMatchLines);
		matchedLines = matchedLines.subList(0, maxMatchLines); // top N results
		
		Collections.sort(matchedLines, new Comparator<MatchLine>() { // sort by line number for display
			public int compare(MatchLine l1, MatchLine l2) {
				return l1.getLineNumber() - l2.getLineNumber();
			}
		});
		
		return matchedLines;
	}

	/**
	 * Find matches on the line
	 * 
	 * @param matchLine
	 * @param terms
	 * @param matchedTerms
	 * @param searchString
	 * @return
	 */
	private float addMatches(MatchLine matchLine, Map<String, List<Integer>> terms, 
			Set<String> matchedTerms, String searchString) {
		
		String lcaseLine = matchLine.getLine().toLowerCase();
		
		if( !matchedTerms.contains(searchString) && !currentSearchQuery.isFuzzy() ) { // check for exact match on the line
			
			int pos = lcaseLine.indexOf(searchString);
			
			while( pos != -1 ) {
				Match m = new Match(searchString, pos, searchString.length());
				matchLine.add(m, true);
				pos = lcaseLine.indexOf(searchString, pos + searchString.length() - 1);
			}
		}
		
		float matchedTermBoost = 0;
		
		for(String term: matchedTerms) {							
			List<Integer> offsets = terms.get(term);
			
			for(int offset: offsets) {
				int pos = lcaseLine.indexOf(term, offset);
				if( pos == -1 ) continue;
				Match m = new Match(term, pos, term.length());
				matchLine.add(m);
			}
			
			float boost = searchTerms.get(term);
			
			matchedTermBoost += boost;
		}
		
		matchLine.setMatchedTermBoost(matchedTermBoost);
		
		return matchedTermBoost;
	}
	
	/**
	 * Removes similar lines from line matches.
	 * Even if they are high scored, we don not want to see the same lines again
	 * Line similarity is based on Cosine between their corresponding term vectors
	 * 
	 * @param matchedLines
	 * @param maxMatches
	 */
	private void removeSimilarLines(List<MatchLine> matchedLines, int maxMatches)
	{
		MatchLine curMatchLine = null;
		
		int lineNr = 0;
		for (Iterator<MatchLine> iterator = matchedLines.iterator(); 
					iterator.hasNext() && matchedLines.size()>maxMatches; )
		{
			MatchLine matchLine = iterator.next();
			
			if( curMatchLine == null ) {
				curMatchLine = matchLine;
				lineNr++;
				continue;
			}
			
			double similarity = getLineSimilarity(curMatchLine, matchLine);
			
			if( similarity > MAX_LINE_SIMILARITY )
				iterator.remove(); // since lines are sorted by score, lowest score line will be removed
			else {
				curMatchLine = matchLine;
				lineNr++;	
			}
			
			if( lineNr == maxMatches )
				break;
		}
	}

	/**
	 * Calculates similarity based on the Cosine angle between score vectors of each line.
	 * 
	 * @param lineMatches1
	 * @param lineMatches2
	 * @return
	 */
	private double getLineSimilarity(MatchLine lineMatches1, MatchLine lineMatches2) 
	{
		float[] vect1 = lineMatches1.getScoreVector();
		float[] vect2 = lineMatches2.getScoreVector();
		
		double dotProduct = 0.0;
		double magnitude1 = 0.0;
		double magnitude2 = 0.0;
		for (int i = 0; i < vect1.length ; i++) {
		    double val1 = vect1[i];
		    double val2 = vect2[i];
		    magnitude1 += val1 * val1;
		    magnitude2 += val2 * val2;
		    dotProduct += val1 * val2;
		}
		magnitude1 = Math.sqrt(magnitude1);
		magnitude2 = Math.sqrt(magnitude2);
		return (magnitude1 == 0 || magnitude2 == 0)
		    ? 0
		    : dotProduct / (magnitude1 * magnitude2);
		
	}

	public Object getParent(Object element) {
		return null;
	}
	
	public boolean hasChildren(Object element) {
		return ( element instanceof SearchResultDoc ) || ( element instanceof SearchQuery );
	}
	
	public Collection<String> getSearchTerms() {
		return searchTerms.keySet();
	}

	public IEditorInput getEditorInput(SearchResultDoc doc) throws Exception {
		return indexer.getEditorInput(doc);
	}
	
	public IStorage getStorage(SearchResultDoc doc) throws Exception {
		return indexer.getStorage(doc);
	}
	
	public List<String> getProposals(String prefix, Field field) throws IOException
	{
		List<String> ucaseProposals = searcher.getProposals(prefix.toUpperCase(), field);
		
		if( prefix.toUpperCase().equals(prefix.toLowerCase()))
			return ucaseProposals;
		
		List<String> lcaseProposals = searcher.getProposals(prefix.toLowerCase(), field);
		
		ucaseProposals.addAll(lcaseProposals);
		Collections.sort(ucaseProposals, String.CASE_INSENSITIVE_ORDER);
		
		return ucaseProposals;
	}
	
	/**
	 * A class representing a line in a document and containing some keyword matches
	 */
	class MatchLine implements Comparable<MatchLine> {
		
		private String lineText;
		private List<Match> matches = new LinkedList<Match>();
		private SearchResultDoc doc;
		private int lineNumber;
	
		private double termScore;
		private double matchedTermScore;
		private float[] scoreVector;
		private float matchedTermBoost;
		private int exactMatches;
		private int matchedTermCount;
		
		private MatchLine(SearchResultDoc doc, String lineText, int lineNumber, Set<String> matchedTerms, float[] termScoreVector, float[] matchedTermScoreVector) throws IOException {
			this.doc = doc;
			this.lineText = lineText;
			this.lineNumber = lineNumber;
			this.scoreVector = termScoreVector;
			
			termScore = getMagnitude(termScoreVector);
			matchedTermScore = getMagnitude(matchedTermScoreVector);
			matchedTermCount = matchedTerms.size();
		}

		public void setMatchedTermBoost(float matchedTermBoost)
		{
			this.matchedTermBoost = matchedTermBoost;
		}

		public float getMatchedTermBoost()
		{
			return matchedTermBoost;
		}
		
		public void add(Match m) {
			matches.add(m);
		}
		
		public void add(Match m, boolean isExactMatch) {
			matches.add(m);
			if( isExactMatch )
				this.exactMatches++;
		}
		
		public List<Match> getMatches() {
			return matches;
		}

		public String getLine() {
			return lineText;
		}

		public int getLineNumber() {
			return lineNumber;
		}
				
		public double getTermScore()
		{
			return termScore;
		}
		
		public float[] getScoreVector()
		{
			return scoreVector;
		}
		
		public double getMatchedTermScore()
		{
			return matchedTermScore;
		}
		
		public int compareTo(MatchLine lineMatches) { // to sort by match count and then by line number
			int diff = lineMatches.exactMatches - exactMatches;
			if( diff == 0 )
				diff = lineMatches.matchedTermCount - matchedTermCount;
			if( diff == 0 )
				Double.compare(lineMatches.matchedTermBoost, matchedTermBoost);
			if(diff == 0)
				diff = Double.compare(lineMatches.termScore, termScore);
			if(diff == 0)
				return getLineNumber() - lineMatches.lineNumber; // smaller to bigger
			return diff;
		}
		
		public SearchResultDoc getResultDoc()
		{
			return doc;
		}
	
		/**
		 * Vector magnitude 
		 * 
		 * @param vect
		 * @return
		 */
		private double getMagnitude(float[] vect)
		{
			double magnitude = 0;
			
			for(float value: vect)
				magnitude+=value*value;
			
			magnitude = Math.sqrt(magnitude);
			
			return magnitude;
		}

		@Override
		public String toString()
		{
			return "Line " + lineNumber + ": (" + matchedTermCount + ")" + lineText;
		}
	}
}