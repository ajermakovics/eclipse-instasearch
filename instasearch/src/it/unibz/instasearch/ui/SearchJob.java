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

import it.unibz.instasearch.indexing.SearchQuery;
import it.unibz.instasearch.indexing.SearchResultDoc;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TreeItem;

/**
 * A search job that runs search in UI thread.
 * Is used to start search after user has stopped typing
 */
public class SearchJob extends Job {
	
	/**
	 * 
	 */
	private TreeViewer resultViewer = null;
	private SearchQuery searchQuery = null;
	private boolean selectLast = false;
	private InstaSearchView searchView;
	private IStatusLineManager statusLineManager;
	
	public SearchJob(InstaSearchView searchView)
	{
		super("Search");
		
		this.searchView = searchView;
		this.resultViewer = searchView.getResultViewer();
		this.statusLineManager = searchView.getViewSite().getActionBars().getStatusLineManager();
		
		this.addJobChangeListener(new JobChangeAdapter(){
			public void done(IJobChangeEvent event) {
				if( event.getResult() == Status.CANCEL_STATUS )
					cancelSearch();
			}
		});
		
		setPriority(Job.INTERACTIVE);
	}
	
	public void schedule(SearchQuery searchQuery, boolean selectLast, long delay) {
		cancelSearch();
		
		this.searchQuery = searchQuery;
		this.selectLast = selectLast;
		
		if( this.searchQuery != null ) {
			this.searchQuery.setCanceled(false);
			this.searchQuery.setCurrentProject( InstaSearchUI.getActiveProject() );
			
			schedule(delay);
		}
	}
	
	/**
	 * Cancel current search
	 */
	private void cancelSearch() {
		if( searchQuery != null )
			searchQuery.setCanceled(true);
	}
	
	protected IStatus run(IProgressMonitor monitor)
	{
		ResultContentProvider prov = (ResultContentProvider) resultViewer.getContentProvider();
		
		if(  prov == null || monitor.isCanceled() || searchQuery == null || searchQuery.isCanceled() ) return Status.CANCEL_STATUS;
		
		prov.getElements(searchQuery); // runs query and caches result. running in this job to avoid UI freeze
		
		if( monitor.isCanceled() || searchQuery.isCanceled() ) return Status.CANCEL_STATUS; // perhaps we cancelled while executing query
		
		Display display = resultViewer.getControl().getDisplay();
		
		display.syncExec(new Runnable() {	
			public void run() {
				search(); // run in UI
			}
		});
		
		return Status.OK_STATUS;
	}
	
	private void search() {
		int items = resultViewer.getTree().getItemCount();
		
		resultViewer.setInput(searchQuery); // run in UI, displays cached results immediately
		
		int newItems = resultViewer.getTree().getItemCount();
		
		if( selectLast && items != 0 && newItems >= items )
			resultViewer.getTree().setSelection(resultViewer.getTree().getItem(items-1)); // show previous selection
		
		int resultCount = countSearchResults(newItems);
		String resultsDesc = " " + (searchQuery.isExact() ? "exact" : "similar") + " results";
		
		if( searchQuery.isLimited() ) // limited
		{
			if( resultCount >= searchQuery.getMaxResults() )
				statusLineManager.setMessage(searchView.getTitleImage(), searchQuery.getMaxResults() + "+" + resultsDesc + " (Ctrl+Enter to see all)");
			else 
				statusLineManager.setMessage(searchView.getTitleImage(), resultCount + resultsDesc);
		}
		else { // not limited
			statusLineManager.setMessage(searchView.getTitleImage(), resultCount + resultsDesc);
		}
	}

	/**
	 * @param newItems
	 * @return
	 */
	private int countSearchResults(int newItems) {
		
		int i, allItems = resultViewer.getTree().getItemCount();
		
		for(i = allItems-1; i >= 0; i--)
		{
			TreeItem treeItem = resultViewer.getTree().getItem(i);
			
			if( treeItem == null || treeItem.getData() == null )
				continue;
			
			if( treeItem.getData() instanceof SearchResultDoc ) // from now on we have results
				return i+1;
		}
		
		return 0;
	}

	
}