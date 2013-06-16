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

import it.unibz.instasearch.indexing.StorageIndexer.IndexChangeListener;
import it.unibz.instasearch.indexing.querying.CSVExpander;
import it.unibz.instasearch.indexing.querying.CurrentProjectSetter;
import it.unibz.instasearch.indexing.querying.FieldAliasConverter;
import it.unibz.instasearch.indexing.querying.FileNameSearcher;
import it.unibz.instasearch.indexing.querying.FilterSetter;
import it.unibz.instasearch.indexing.querying.FolderSearcher;
import it.unibz.instasearch.indexing.querying.LastTermQueryPrefixer;
import it.unibz.instasearch.indexing.querying.LowercaseConverter;
import it.unibz.instasearch.indexing.querying.ModifiedTimeConverter;
import it.unibz.instasearch.indexing.querying.QueryFuzzifier;
import it.unibz.instasearch.indexing.querying.UppercaseNameExpander;
import it.unibz.instasearch.indexing.querying.VisitableQuery;
import it.unibz.instasearch.indexing.querying.WorkingSetExpander;
import it.unibz.instasearch.prefs.PreferenceConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.QueryParser.Operator;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.highlight.QueryTermExtractor;
import org.apache.lucene.search.highlight.WeightedTerm;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

/**
 * Searcher for searching the index using SearchQuery
 */
@SuppressWarnings("deprecation")
public class Searcher implements PreferenceChangeListener, IndexChangeListener {

	/**  @see QueryParser#setPhraseSlop(int) 	*/
	private static final int DEFAULT_PHRASE_SLOP = 0;
	/** Minimum length of query in characters */
	public static final int MIN_QUERY_LENGTH = 2;
	/** Minimum number of characters to be considered a word in indexing */
	public static final int MIN_WORD_LENGTH = 1;
	/** Character that identifies the current project in search query */
	public static final String CURRENT_PROJECT_CHAR = ".";
	private static final Version LUCENE_VERSION = Version.LUCENE_29;
	
	private IndexSearcher indexSearcher;
	
	public static final LengthNormSimilarity SIMILARITY = new LengthNormSimilarity();  //TODO: share with WorkspaceIndexer
	private static final QueryAnalyzer queryAnalyzer = new QueryAnalyzer(MIN_WORD_LENGTH);
	
	// Query visitors that modify the search query
	private CSVExpander csvExpander = new CSVExpander();
	private WorkingSetExpander workingSetExpander = new WorkingSetExpander();
	private UppercaseNameExpander uppercaseNameExpander = new UppercaseNameExpander();
	private FieldAliasConverter fieldAliasConverter = new FieldAliasConverter();
	private ModifiedTimeConverter modifiedTimeConverter = new ModifiedTimeConverter();
	private LowercaseConverter lowercaseConverter = new LowercaseConverter();
	private FolderSearcher folderSearcher = new FolderSearcher();
	private FileNameSearcher fileNameSearcher = new FileNameSearcher();
	private CurrentProjectSetter currentProjectSetter = new CurrentProjectSetter();
	private QueryFuzzifier queryFuzzifier = new QueryFuzzifier();
	private LastTermQueryPrefixer lastTermQueryPrefixer = new LastTermQueryPrefixer(MIN_QUERY_LENGTH+1);
	private FilterSetter filterSetter = new FilterSetter();

	private boolean showMatchCounts = true;
	private boolean fuzzySearchAuto = true;
	private SearcherConfig config;

	public interface SearcherConfig
	{
		boolean getBoolPref(String pref);
		void log(Exception e);
		Directory getIndexDir() throws IOException;
	}
	
	/**
	 * Searcher
	 */
	public Searcher(SearcherConfig config) {
		this.config = config;
		initPrefs();
	}
	
	protected Directory getIndexDir() throws IOException {
		return config.getIndexDir(); 
	}
	
	private SearchResult searchIndex(SearchQuery searchQuery) throws Exception {
		
		IndexSearcher indexSearcher = getIndexSearcher();
		IndexReader reader = indexSearcher.getIndexReader();
		boolean exact = searchQuery.isExact();
		
		Query query = null;
		
		try {
			query = parseSearchQuery(searchQuery, reader, exact, true);
			
		} catch(TooManyClauses e) { // too many, try without prefix search
			query = parseSearchQuery(searchQuery, reader, exact, false);
			
		} catch(ParseException e) {
			
			// remove special query characters
			String newSearchString = searchQuery.getSearchString().replaceAll("[\\(\\)\"\\[\\]'\\{\\}]", " ");
			
			try {
				searchQuery.setSearchString(newSearchString);
				query = parseSearchQuery(searchQuery, reader, exact, true);
				
			} catch(ParseException ignored) {
				// can have error while typing query, just ignore
				//debug(newSearchString, " - ", ignored.getMessage());
				return null; 
			}
		}
		
		SearchResult searchResut = collectSearchResults(searchQuery, indexSearcher, reader, query);
		
		return searchResut;
	}

	private SearchResult collectSearchResults(SearchQuery searchQuery,
			IndexSearcher indexSearcher, IndexReader reader, Query query)
			throws IOException
	{
		int maxResults = reader.numDocs(); // all documents
		
		if( searchQuery.isLimited() )
			maxResults = searchQuery.getMaxResults();
		
		Map<String, Float> searchTerms = extractTerms(query);
		TopDocCollector collector = new TopDocCollector( maxResults );
		
		if( searchQuery.isCanceled() ) 
			return null;
		
		indexSearcher.search(query, collector); // do the actual search
		
		if( collector.getTotalHits() == 0 )
			return null;
		
		ScoreDoc[] hits = collector.topDocs().scoreDocs;
		ArrayList<SearchResultDoc> resultDocs = new ArrayList<SearchResultDoc>(hits.length);
		
		for (int i = 0; i < hits.length && !searchQuery.isCanceled(); i++) 
		{
			int docId = hits[i].doc;
			float score = hits[i].score;
			
			Document doc = reader.document(docId);
			
			SearchResultDoc resultDoc = new SearchResultDoc(getIndexDir(), doc, docId, score);
			
			if( showMatchCounts )
				resultDoc.computeMatchCount(reader, searchTerms.keySet());
			
			resultDocs.add(resultDoc);
		}
		
		return new SearchResult(searchQuery, resultDocs, searchTerms);
	}

	/**
	 * Performs a search using the search query
	 * @param searchQuery
	 * 
	 * @return SearchResult or null if nothing is found
	 * @throws Exception
	 */
	public SearchResult search(SearchQuery searchQuery) throws Exception {
		
		String searchString = searchQuery.getSearchString();
		
		if( searchString == null || searchString.length() < MIN_QUERY_LENGTH ) 
			return null;
		
		SearchResult result = null;
		
		if( searchQuery.isFuzzy() )
		{
			searchQuery.setExact(false);
			result = searchIndex(searchQuery); // search fuzzy
		}
		else
		{
			// 1. search exact text
			if( searchQuery.isExact() )
				result = searchIndex(searchQuery);
			
			// 2. split search text into tokens and search (non-exact)
			if( (result==null || result.isEmpty()) && !searchQuery.isCanceled() )
			{
				searchQuery.setExact(false);
				result = searchIndex(searchQuery);
			}
			
			// 3. search wildcarded and fuzzy matches
			if( (result==null || result.isEmpty()) && fuzzySearchAuto && !searchQuery.isCanceled() ){ // if fuzzy search enabled
				searchQuery.setFuzzy(true);
				searchQuery.setExact(false);
				
				result = searchIndex(searchQuery);
			}
		}
		
		return result;
	}

	/**
	 * @return the indexSearcher
	 * @throws IOException 
	 */
	private IndexSearcher getIndexSearcher() throws IOException
	{
		if( indexSearcher == null ) 
		{
			indexSearcher = new IndexSearcher( getIndexDir(), true );
			indexSearcher.setSimilarity( SIMILARITY );
		}
		
		// indexSearcher.getIndexReader().isCurrent()
		
		return indexSearcher;
	}
		
	/**
	 * Get all terms that start with prefixText
	 * @param prefixText
	 * @param prefixField
	 * 
	 * @return proposed terms
	 * @throws IOException
	 */
	public List<String> getProposals(String prefixText, Field prefixField) throws IOException 
	{
	    ArrayList<String> proposals = new ArrayList<String>();
	    IndexReader reader = getIndexSearcher().getIndexReader();
	    Term prefix = prefixField.createTerm(prefixText);
	    TermEnum enumerator = reader.terms(prefix);
	    prefixText = prefixText.toLowerCase(Locale.ENGLISH);
	    
	    try {
	      do {
	        Term term = enumerator.term();
	        
	        if (term != null &&
	            term.text().toLowerCase(Locale.ENGLISH).startsWith(prefixText) &&
	            term.field().equalsIgnoreCase(prefixField.toString())) {
	        	
	        	proposals.add(term.text());
	        	
	        } else {
	          break;
	        }
	      } while (enumerator.next());
	    } finally {
	      enumerator.close();
	    }
	    
	    return proposals;
	  }

	/**
	 * Get names of all projects that are in the index
	 * 
	 * @return list of indexed projects
	 * @throws IOException
	 */
	public List<String> getIndexProjects() throws IOException
	{
		return getProposals("", Field.PROJ);
	}
	
	/**
	 * Need to reset searcher when index has been updated
	 * Otherwise changes won't appear in the results
	 * It is recommended to do a warmup search after the searcher was reset
	 * 
	 */
	
	public void onIndexReset() {
		resetSearcher();
	}
	
	private void resetSearcher()
	{
		if( indexSearcher != null ) {
			try {
				indexSearcher.close();
			} catch (IOException e) {
				config.log(e);
			} finally {
				indexSearcher = null;
			}	
		}
	}
	
	/**
	 * Warmup search. Searches after this will be faster
	 * 
	 * @throws Exception
	 */
	private void warmup() throws Exception {
		search(new SearchQuery("<warmup search>", 1));
	}

	/**
	 * Closes and re-opens the index for searching
	 * Should be called after the index is updated
	 * 
	 */
	public void onIndexUpdate() {
		
		resetSearcher();
		
		try {
			warmup();
		} catch (Exception e) {
			config.log(e);
		}
	}

	private Query parseSearchQuery(SearchQuery searchQuery, IndexReader reader, boolean exact, boolean prefix) throws ParseException, IOException
	{
		String searchString = searchQuery.getSearchString();
		
		BooleanQuery.setMaxClauseCount(5000); // so we don't get TooManyClauses exceptions
		
		Query exactQuery = createExactQuery(searchQuery);
		Query returnQuery;
		
		if( exact ) // want exact search, use KeywordAnalyzer
		{
			returnQuery = exactQuery;
		}
		else
		{
			Query query = parserSearchString(searchString, queryAnalyzer);
			exactQuery.setBoost(query.getBoost() * 2f); // exact query more important
			returnQuery = combineQueries(query, exactQuery);
		}
		
		returnQuery = rewriteQuery(searchQuery, prefix, returnQuery);
		
		returnQuery = returnQuery.rewrite(reader); // lucene's rewrite (ie expand prefix queries)
		//System.out.println("q: " + returnQuery + " - exact " + exact);
		
		return returnQuery;
	}

	/**
	 * @param searchQuery
	 * @return
	 * @throws ParseException 
	 */
	private Query createExactQuery(SearchQuery searchQuery) throws ParseException
	{
		Query query = null;
		String searchString = searchQuery.getSearchString();
		
		if( searchString.contains(" ") )
		{
			query = parserSearchString(searchString, new StandardAnalyzer(LUCENE_VERSION));
			query = convertToPhraseQuery(query);
		}
		else
		{
			query = parserSearchString(searchString, new KeywordAnalyzer()); // searchstring is one term
		}
		
		VisitableQuery visitableQuery = new VisitableQuery(query);
		
		visitableQuery.accept(uppercaseNameExpander);
		visitableQuery.accept(fileNameSearcher);
		
		query = visitableQuery.getQuery();
		
		return query;
	}

	private static Query convertToPhraseQuery(Query query)
	{
		PhraseQuery phraseQuery = new PhraseQuery();
		
		Set<Term> terms = new LinkedHashSet<Term>();
		
		try
		{
			query.extractTerms(terms);

			for(Term term: terms)
			{
				Field field = Field.fromTerm(term);

				if( Field.CONTENTS == field  )
					phraseQuery.add(term);
				else
					return query;
			}
		}
		catch(UnsupportedOperationException ignored) // not all queries support it 
		{
			return query;
		}
		
		return phraseQuery;
	}

	private Query rewriteQuery(SearchQuery searchQuery, boolean prefix, Query query) 
	{
		VisitableQuery visitableQuery = new VisitableQuery(query);
		
		visitableQuery.accept(uppercaseNameExpander);
		
		visitableQuery.accept(lowercaseConverter);
		
		if( searchQuery.isFuzzy() )
			visitableQuery.accept(queryFuzzifier);
		
		visitableQuery.accept(csvExpander);
		visitableQuery.accept(fieldAliasConverter);
		visitableQuery.accept(modifiedTimeConverter);
		visitableQuery.accept(folderSearcher);
		
		if( prefix && !searchQuery.isFuzzy() ) // prefix last term query for substring search while typing
			visitableQuery.accept(lastTermQueryPrefixer);
		
		if( searchQuery.getFilter() != null )
		{
			filterSetter.setFilter( searchQuery.getFilter() );
			visitableQuery.accept(filterSetter);
		}
		
		visitableQuery.accept(workingSetExpander);
		
		if( searchQuery.getCurrentProject() != null )
		{
			currentProjectSetter.setCurrentProject(searchQuery.getCurrentProject());
			visitableQuery.accept(currentProjectSetter);
		}
		
		return visitableQuery.getQuery();  // the modified query after all visitors
	}

	private BooleanQuery combineQueries(Query query, Query exactQuery)
	{
		BooleanQuery topQuery = new BooleanQuery();
		topQuery.add(exactQuery, Occur.SHOULD);
		topQuery.add(query, Occur.SHOULD);
		return topQuery;
	}

	private Query parserSearchString(String searchString, Analyzer analyzer) throws ParseException 
	{
		QueryParser queryParser = new QueryParser(LUCENE_VERSION, Field.CONTENTS.toString(), analyzer);
		queryParser.setDefaultOperator(Operator.AND); // all fields required
		queryParser.setLowercaseExpandedTerms(false);
		queryParser.setPhraseSlop(DEFAULT_PHRASE_SLOP);

		/*
		 * Allow words in the query to begin with *
		 * see http://lucene.apache.org/java/2_3_1/api/org/apache/lucene/queryParser/QueryParser.html#setAllowLeadingWildcard%28boolean%29
		 */
		queryParser.setAllowLeadingWildcard(true);

		Query parsedQuery = queryParser.parse(searchString);
		
		return parsedQuery;
	}
	
	/**
	 * Extracts search terms and their weights
	 * @param query
	 * @return
	 */
	private static Map<String, Float> extractTerms(Query query)
	{
		WeightedTerm[] weightedTerms = QueryTermExtractor.getTerms(query, false, Field.CONTENTS.toString());
		
		Map<String,Float> terms = new HashMap<String,Float>(weightedTerms.length);
		
		for(WeightedTerm weightedTerm: weightedTerms)
			terms.put( weightedTerm.getTerm(), weightedTerm.getWeight() );
		
		return terms;
	}

	protected void initPrefs() 
	{
		fuzzySearchAuto = config.getBoolPref(PreferenceConstants.P_FUZZY_SEARCH_AUTO);
		showMatchCounts = config.getBoolPref(PreferenceConstants.P_SHOW_MATCH_COUNT);
	}

	public void preferenceChange(PreferenceChangeEvent evt)
	{
		String prop = evt.getKey();

		if( PreferenceConstants.P_SHOW_MATCH_COUNT.equals(prop) )
			showMatchCounts = config.getBoolPref(PreferenceConstants.P_SHOW_MATCH_COUNT);
		else if( PreferenceConstants.P_FUZZY_SEARCH_AUTO.equals(prop) )
			fuzzySearchAuto = config.getBoolPref(PreferenceConstants.P_FUZZY_SEARCH_AUTO);
	}

}
