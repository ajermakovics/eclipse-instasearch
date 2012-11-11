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
package it.unibz.instasearch;

import it.unibz.instasearch.indexing.Searcher;
import it.unibz.instasearch.indexing.Searcher.SearcherConfig;
import it.unibz.instasearch.indexing.WorkspaceIndexer;
import it.unibz.instasearch.indexing.WorkspaceIndexerJDT;
import it.unibz.instasearch.jobs.DeleteIndexJob;
import it.unibz.instasearch.jobs.IndexUpdateJob;
import it.unibz.instasearch.jobs.IndexingJob;
import it.unibz.instasearch.prefs.PreferenceConstants;

import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.jobs.Job;

/**
 * InstaSearch class. Manages indexing jobs
 */
public class InstaSearch 
{
	private WorkspaceIndexer indexer;
	private Searcher searcher;
	
	private IndexingJob indexingJob;
	private IndexUpdateJob indexUpdateJob;
	
	InstaSearch() throws Exception
	{	
		try {
			indexer = new WorkspaceIndexerJDT(); // if have JDT installed, we can index jars
		} catch(Throwable ignored) { // NoClassDefFoundError
			// no jdt, nevermind
			InstaSearchPlugin.debug(ignored);
		}
		
		if( indexer == null ) 
			indexer = new WorkspaceIndexer(); // create default indexer

		searcher = new Searcher(getSearcherConfig());
		indexer.setIndexChangeListener(searcher);
		
		InstaSearchPlugin.addPreferenceChangeListener(indexer);
		InstaSearchPlugin.addPreferenceChangeListener(searcher);
		
		String lastVer = InstaSearchPlugin.getDefault().getPreferenceStore().getString("version");
		String curVer = InstaSearchPlugin.getVersion();
		InstaSearchPlugin.getDefault().getPreferenceStore().setValue("version", curVer);

		if( !indexer.isIndexed() || !indexer.isReadable() || !curVer.equals(lastVer) )
			createIndex();
		else
			searcher.onIndexUpdate();
		
		int indexUpdateInterval = InstaSearchPlugin.getIntPref(PreferenceConstants.P_INDEX_UPDATE_INTERVAL);
		boolean indexUpdateEnabled = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_INDEX_UPDATE_ENABLED);
		
		indexUpdateJob = new IndexUpdateJob(indexer, searcher);
		indexUpdateJob.setSystem(true);
		
		if ((indexUpdateEnabled) && (indexUpdateInterval > 0)){
			indexUpdateJob.schedule(indexUpdateInterval);
		}
		
		InstaSearchPlugin.addPreferenceChangeListener(indexUpdateJob);
	}
	
	public WorkspaceIndexer getIndexer() {
		return indexer;
	}
	
	public Searcher getSearcher() {
		return searcher;
	}
	
	public void rebuildIndex() {
		createIndex();
	}
	
	public void deleteIndex() {
		DeleteIndexJob deleteIndexJob = new DeleteIndexJob(indexer);
		deleteIndexJob.schedule();
	}
	
	public boolean isIndexing() {
		if( indexingJob == null )
			return false;
		
		if( indexingJob.getState() == WorkspaceJob.RUNNING  || indexingJob.getState() == WorkspaceJob.WAITING )
			return true;
		
		return false;
	}
	
	/**
	 * Starts the indexing job
	 * 
	 */
	public void createIndex() {
		if( indexingJob == null ) {
			indexingJob = new IndexingJob(indexer);
			indexingJob.setPriority(Job.LONG); // long running job
		} else if( isIndexing() )
			return;
		
		indexingJob.schedule();
	}
	
	void cancelJobs() {
		if( indexingJob != null )
			indexingJob.cancel();
		
		if( indexUpdateJob != null )
			indexUpdateJob.cancel();
		
	}

	/**
	 * Update the index with the latest changes
	 */
	public void updateIndex() {
		if( indexUpdateJob != null ) {
			indexUpdateJob.cancel();
			indexUpdateJob.schedule();
		}
	}
	
	public SearcherConfig getSearcherConfig()
	{
		return new SearcherConfig()
		{
			@SuppressWarnings("deprecation")
			@Override
			public Directory getIndexDir() throws IOException
			{
				return FSDirectory.getDirectory(InstaSearchPlugin.getIndexDirLocation(), false);
			}

			@Override
			public boolean getBoolPref(String pref)
			{
				return InstaSearchPlugin.getBoolPref(pref);
			}
			
			@Override
			public void log(Exception e)
			{
					InstaSearchPlugin.log(e);
			}
		};
	}
}
