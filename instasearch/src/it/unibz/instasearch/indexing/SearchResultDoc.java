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

import it.unibz.instasearch.InstaSearchPlugin;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.Directory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;


public class SearchResultDoc {

	private Document doc;
	private int docId;
	private TermFreqVector termFreqVector;
	private float[] termScoreVector;
	private float score;
	private int matchCount;
	private Directory indexDir;

	public SearchResultDoc(Directory dir, Document doc, int docId, float score) {
		this.indexDir = dir;
		this.docId = docId;
		this.doc = doc;
		this.score = score;
		
		matchCount = 0;
	}

	private String getFieldValue(Field field) {
		return doc.get(field.toString());
	}
	
	public String getFilePath() {
		return getFieldValue(Field.FILE);
	}
	
	public String getFileName() {
		return getFieldValue(Field.NAME);
	}
	
	public String getFileExtension() {
		return getFieldValue(Field.EXT);
	}
	
	public boolean isInJar() {
		if( doc.getField(Field.JAR.toString())==null )
			return false;
		
		String jarField = getFieldValue(Field.JAR);
		
		if( StorageIndexer.NO_VALUE.equals(jarField) )
			return false;
		
		if( jarField.toLowerCase(Locale.ENGLISH).endsWith(".jar") )
			return true;
		
		return false;
	}
	
	public String getJarName() {
		
		if( isInJar() )
			return getFieldValue(Field.JAR);
		
		return null;
	}
	
	public IPath getProject() {
		return new Path(getFieldValue(Field.PROJ));
	}
	
	public String getProjectName() {
		return getProject().lastSegment();
	}
	
	/**
	 * 
	 * @return
	 * @throws IOException
	 */
	private float[] getTermScoreVector()  throws IOException
	{
		if( termScoreVector == null ) {
			IndexReader reader = IndexReader.open(indexDir, true);

			if( termFreqVector == null ) 
				createFreqVect(reader);

			termScoreVector = createTermScoreVector(termFreqVector, reader);
			reader.close();
		}

		return termScoreVector;
	}

	private TermFreqVector getTermFreqVector() throws IOException
	{
		if( termFreqVector == null ) {
			IndexReader reader = IndexReader.open(indexDir, true);
			createFreqVect(reader);
			reader.close();
		}

		return termFreqVector;
	}

	private void createFreqVect(IndexReader reader) throws IOException
	{
		termFreqVector = reader.getTermFreqVector(docId, Field.CONTENTS.toString()); // obtain only when requested
	}

	/**
	 * Returns a vector of given term scores (tf-idf). 
	 * The size of the vector is the number of terms in this document
	 * The term positions in the vector are the same as in the term frequency vector
	 * 
	 * @param terms
	 * @return TermScoreVector
	 * @throws IOException
	 */
	public float[] getTermScoreVector(Collection<String> terms) throws IOException
	{
		float[] allTermScoreVect = getTermScoreVector();
		float[] termScoreVect = new float[allTermScoreVect.length]; 
		TermFreqVector freqVector = getTermFreqVector();

		for(String term: terms){
			int idx = freqVector.indexOf(term); // does a binary search
			if( idx == -1 ) continue;
			termScoreVect[idx] = allTermScoreVect[idx];
		}

		return termScoreVect;
	}

	public double getTermScore(String term) throws IOException
	{
		float[] allTermScoreVect = getTermScoreVector();
		TermFreqVector freqVector = getTermFreqVector();

		if( freqVector == null ) return 0;
		
		int idx = freqVector.indexOf(term); // does a binary search
		if( idx == -1 ) return 0;
		return allTermScoreVect[idx];
	}

	private float[] createTermScoreVector(TermFreqVector vect, IndexReader reader) throws IOException
	{		
		if( vect == null )
			return new float[0];

		int[] termFrequencies = vect.getTermFrequencies();
		String[] terms = vect.getTerms();
		float[] scores = new float[terms.length];

		int numDocs = reader.maxDoc();
		Similarity sim = Searcher.SIMILARITY;
		
		for(int i = 0; i < terms.length; i++) {
			String termText = terms[i];
			Term term = new Term(Field.CONTENTS.toString(), termText);
			
			float termFreq = sim.tf( termFrequencies[i] );

			int docFreq = reader.docFreq(term);
			float idf = sim.idf(docFreq, numDocs); 

			float tfIdf = termFreq * idf;

			scores[i] = tfIdf;
		}

		return scores; 
	}

	public IFile getFile() 
	{
		if( isInJar() ) return null;
		
		Path path = new Path(getFilePath());
		IWorkspaceRoot workspaceRoot = InstaSearchPlugin.getWorkspaceRoot();
		IFile file = workspaceRoot.getFile(path);
		
		if( file == null || file.getRawLocation() == null )
			file = workspaceRoot.getFileForLocation(path);
		
		return file;
	}
	
	/**
	 * @return the score
	 */
	public float getScore()
	{
		return score;
	}

	/**
	 * @return the doc
	 */
	public Document getDoc()
	{
		return doc;
	}

	/**
	 * @return the docId
	 */
	public int getDocId()
	{
		return docId;
	}

	/**
	 * @return the matchCount
	 */
	public int getMatchCount()
	{
		return matchCount;
	}

	/**
	 * Computes match count as SUM( tf ) of all query terms in the document
	 * Accesses the index thus affects performance
	 * 
	 * @param reader
	 * @param queryTerms
	 * @throws IOException
	 */
	public void computeMatchCount(IndexReader reader, Collection<String> queryTerms) throws IOException 
	{
		if( termFreqVector == null )
			createFreqVect(reader);

		if( termFreqVector == null )
			return;
		
		int freqs[] = termFreqVector.getTermFrequencies();
		int freqSum = 0;

		for(String term: queryTerms){
			int idx = termFreqVector.indexOf(term); // does a binary search
			if( idx == -1 ) continue;
			freqSum += freqs[idx]; 
		}

		matchCount = freqSum;
	}
	
	@Override
	public String toString() {
		return getFilePath();
	}
}


