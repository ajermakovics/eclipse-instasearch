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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

/**
 *  Indexes documents of type IStorage 
 */
public class StorageIndexer 
{
	/** Maximum number of terms indexed per one document */
	private static final int MAX_TERMS_PER_DOC = 200000;
	/** */
	public static final String NO_VALUE = "<none>";
	/** */
	public static final int MIN_WORD_LENGTH = 1;
	protected static final FileAnalyzer fileAnalyzer = new FileAnalyzer(MIN_WORD_LENGTH);

	private IndexChangeListener changeListener = new NullIndexChangeListener();
	
	private static final Similarity similarity = new LengthNormSimilarity();
	private static final int MAX_RETRY_ATTEMPTS = 10;
	private Directory indexDir;

	/**
	 * @throws IOException 
	 * 
	 */
	public StorageIndexer() throws IOException 
	{
		checkLock();
	}

	private void checkLock() throws IOException 
	{
		Directory indexDir = getIndexDir();
		
		if( IndexWriter.isLocked(indexDir) ) // should not be locked at startup, unlock
			IndexWriter.unlock(indexDir);
	}

	protected Directory getIndexDir() throws IOException 
	{
		if( indexDir == null ) indexDir = new RAMDirectory();

		return indexDir;
	}

	/**
	 * @param create index
	 * @return IndexWriter
	 * @throws IOException 
	 */
	public IndexWriter createIndexWriter(boolean create) throws IOException 
	{
		IndexWriter indexWriter = new IndexWriter(getIndexDir(), fileAnalyzer, create, MaxFieldLength.UNLIMITED);

		indexWriter.setMergeFactor(2); // use less resources (although slower)
		indexWriter.setSimilarity(similarity);
		indexWriter.setMaxFieldLength(MAX_TERMS_PER_DOC);

		return indexWriter;
	}

	/**
	 * @return isIndexed
	 * @throws IOException 
	 */
	public boolean isIndexed() throws IOException 
	{
		return IndexReader.indexExists(getIndexDir());
	}

	/**
	 * Check if the index can be read
	 * 
	 * @return whether the index is readable
	 */
	public boolean isReadable(){

		try {
			IndexReader reader = IndexReader.open(getIndexDir(), true);
			reader.close();

		} catch (IOException readingException) {
			return false;
		}

		return true;
	}

	/**
	 * Delethe the whole index
	 * @throws Exception
	 */
	public void deleteIndex() throws Exception {

		RetryingRunnable runnable = new RetryingRunnable() 
		{	
			public void run() throws Exception 
			{
				IndexWriter w = createIndexWriter(true); // open for writing and close (make empty)
				w.deleteAll();
				w.commit();
				w.close(true);
				
				Directory dir = getIndexDir();
				for(String file: dir.listAll())
				{
					if( dir.fileExists(file) ) // still exits
					{
						dir.sync(file);
						dir.deleteFile(file);
					}
				}
				dir.close();
			}
			
			public boolean handleException(Throwable e) 
			{
				return true;
			}
		};
		
		changeListener.onIndexReset(); // close searcher because index is deleted
		
		runRetryingRunnable(runnable); // delete index with retry
	}

	/**
	 * @throws Exception
	 */
	public void optimizeIndex() throws Exception {
		if( ! isIndexed() )
			return;

		IndexWriter w = createIndexWriter(false);
		w.optimize();
		w.close();

		changeListener.onIndexUpdate();
	}

	/**
	 * @param changeListener the changeListener to set
	 */
	public void setIndexChangeListener(IndexChangeListener changeListener) {
		this.changeListener = changeListener;
	}

	/**
	 * @return the changeListener
	 */
	protected IndexChangeListener getIndexChangeListener() {
		return changeListener;
	}

	/**
	 * 
	 * @param indexWriter
	 * @param storage
	 * @param projectName
	 * @param modificationStamp 
	 * @param jar path to jar file containing this file or null
	 * @throws CoreException
	 * @throws IOException
	 */
	public void indexStorage(IndexWriter indexWriter, IStorage storage, String projectName,
			long modificationStamp, String jar) throws CoreException, IOException 
			{
		InputStream contents = storage.getContents();
		BufferedReader isReader = new BufferedReader(new InputStreamReader(contents));
		IPath fullPath = storage.getFullPath();
		String ext = fullPath.getFileExtension();
		if( ext == null ) ext = NO_VALUE;

		Document doc = new Document();

		doc.add(createLuceneField(Field.CONTENTS, 	isReader));
		doc.add(createLuceneField(Field.FILE, 		fullPath.toString()));
		doc.add(createLuceneField(Field.PROJ, 		projectName));
		doc.add(createLuceneField(Field.NAME, 		fullPath.lastSegment()));
		doc.add(createLuceneField(Field.EXT, 		ext.toLowerCase()));
		doc.add(createLuceneField(Field.MODIFIED, 	Long.toString(modificationStamp)));
		doc.add(createLuceneField(Field.JAR, 		(jar==null)?NO_VALUE:jar));

		indexWriter.addDocument(doc);
	}

	private static void runRetryingRunnable(RetryingRunnable runnable) throws Exception
	{
		Throwable lastException = null;
		
		for(int i = 1; i <= MAX_RETRY_ATTEMPTS; i++)
		{	
			try
			{
				runnable.run();
				lastException = null;
				break;
			} 
			catch(Throwable e) // exception during run occured
			{
				lastException = e;
				
				if( ! runnable.handleException(e) )
					break;
			}
			
			try {
				Thread.sleep( i*1000 ); // wait a bit longer each time for files to be freed
			} catch(Exception e) { 
				break;
			}
		}
		
		if( lastException != null )
		{
			if( lastException instanceof Exception )
				throw (Exception)lastException;
			else
				throw new Exception(lastException);
		}
	}
	
	/**
	 * Makes several attempts to index storage.
	 * Occasionally the index files get locked (by other processes) and are temporarily not writable.
	 * 
	 * @param indexWriter
	 * @param storage 
	 * @param projectName 
	 * @param modificationStamp 
	 * @param jar 
	 * @throws CoreException 
	 * @throws IOException 
	 */
	protected void indexStorageWithRetry(final IndexWriter indexWriter, final IStorage storage,
			final String projectName, final long modificationStamp, final String jar) throws Exception
	{
		RetryingRunnable runnable = new RetryingRunnable()
		{
			public void run() throws Exception 
			{
				indexStorage(indexWriter, storage, projectName, modificationStamp, jar);
			}
			
			public boolean handleException(Throwable e) 
			{
				if( e instanceof OutOfMemoryError )
				{
					if( indexWriter.getMaxFieldLength() > IndexWriter.DEFAULT_MAX_FIELD_LENGTH )
						indexWriter.setMaxFieldLength(IndexWriter.DEFAULT_MAX_FIELD_LENGTH); // use less memory
					else 
						return false;
				}
				else if( e instanceof IOException)
				{
					changeListener.onIndexReset(); // close searcher
				}
				
				return true; // keep retrying
			}
		};
		
		runRetryingRunnable(runnable);
	}
	
	public interface RetryingRunnable
	{
		public void run() throws Exception;
		
		/**
		 * If exception occurs during run()
		 * 
		 * @param e
		 * @return true if should run again, false if stop and re-throw exception
		 */
		public boolean handleException(Throwable e);
	}
	
	public void deleteStorage(IStorage storage) throws Exception 
	{	
		IndexReader reader = IndexReader.open(getIndexDir(), false);

		String filePath = storage.getFullPath().toString();

		Term term = Field.FILE.createTerm(filePath);
		reader.deleteDocuments(term);

		reader.close();
	}


	private static org.apache.lucene.document.Field createLuceneField(Field fieldName, String value) {
		return new org.apache.lucene.document.Field(fieldName.toString(), value, 
				Store.YES, org.apache.lucene.document.Field.Index.NOT_ANALYZED);
	}

	private static org.apache.lucene.document.Field createLuceneField(Field fieldName, Reader reader) {
		return new org.apache.lucene.document.Field(fieldName.toString(), 
				reader, TermVector.YES);
	}

	/**
	 * Extracts terms from text
	 * 
	 * @param text
	 * @return a map of terms to their offsets in text
	 * @throws IOException
	 */
	public static Map<String, List<Integer>> extractTextTerms(String text) throws IOException {
		Map<String, List<Integer>> terms = new HashMap<String, List<Integer>>();
		TokenStream tokenStream = fileAnalyzer.tokenStream(Field.CONTENTS.toString(), new StringReader(text));
		
		TermAttribute termAtt = (TermAttribute) tokenStream.addAttribute(TermAttribute.class);
		OffsetAttribute offsetAtt = (OffsetAttribute) tokenStream.addAttribute(OffsetAttribute.class);
		
		while(tokenStream.incrementToken())
		{
			String termText = termAtt.term().toLowerCase();// t.termText().toLowerCase();
			int offset = offsetAtt.startOffset();
			
			List<Integer> offsets = terms.get(termText);

			if( offsets == null ) {
				offsets = new LinkedList<Integer>();
				terms.put(termText, offsets);
			}

			offsets.add(offset);
		}
		tokenStream.close();

		return terms;
	}

	/**
	 * Listener that gets called when index has changed
	 */
	public interface IndexChangeListener
	{
		/** Index was updated with files or files were removed */
		public void onIndexUpdate();

		/** Index was reset - created or deleted */
		public void onIndexReset();
	}

	/** Empty implementation to void null checks (Null Object pattern) */
	private static class NullIndexChangeListener implements IndexChangeListener 
	{
		public void onIndexUpdate() {}
		public void onIndexReset() {}
	}

}
