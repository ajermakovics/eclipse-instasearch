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
import it.unibz.instasearch.actions.ShowExceptionAction;
import it.unibz.instasearch.indexing.Field;
import it.unibz.instasearch.indexing.SearchQuery;
import it.unibz.instasearch.indexing.SearchResultDoc;
import it.unibz.instasearch.prefs.PreferenceConstants;
import it.unibz.instasearch.ui.ResultContentProvider.MatchLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DecoratingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

/**
 * 
 */
public class InstaSearchView extends ViewPart implements ModifyListener, ILogListener, ITreeViewerListener, IPropertyChangeListener {

	/** The view ID */
	public static final String ID = InstaSearchView.class.getName(); // we have just one view
	
	/** Tree for results */
	private TreeViewer resultViewer;
	/** Textbox for search query */
	private StyledText searchText;
	
	private IAction openAction;
	private SearchJob searchJob;
	private ExpandCollapseJob expandCollapseJob;

	private ResultContentProvider contentProvider;
	private int lastIncrementalSearchPos = 0;
	
	// preferences
	private int maxResults;
	private int typingSearchDelay;
	private boolean incrementalSearchEnabled;
	
	private SearchViewControl searchViewControl;
	
	/**
	 * 
	 */
	public InstaSearchView() {
	}
	
	@Override
	public void init(IViewSite site) throws PartInitException 
	{
		super.init(site);
		
		InstaSearchPlugin.getDefault().getLog().addLogListener(this); // listen for exceptions
		
		initPrefs();
		
		InstaSearchPlugin.addPreferenceChangeListener(this);
	}

	private void initPrefs() {
		typingSearchDelay = InstaSearchPlugin.getIntPref(PreferenceConstants.P_TYPING_SEARCH_DELAY);
		maxResults = InstaSearchPlugin.getIntPref(PreferenceConstants.P_SHOWN_FILES_COUNT);
		incrementalSearchEnabled = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_INCREMENTAL_SEARCH);
	}
	
	@Override
	public void createPartControl(Composite parent) {
		
		this.searchViewControl = new SearchViewControl(parent, this);
		
		searchText = searchViewControl.getSearchText();
		resultViewer = searchViewControl.getResultViewer();
		
		searchText.addModifyListener(this);
		
		contentProvider = new ResultContentProvider();
		IStyledLabelProvider labelProvider = new ResultLabelProvider(contentProvider);
		IBaseLabelProvider decoratedLabelProvider = new DecoratingStyledCellLabelProvider(labelProvider, null, null);
		
		configureResultViewer(contentProvider, decoratedLabelProvider);
		searchViewControl.setContentProposalAdapter(new SearchContentProposalProvider(contentProvider));
		
		searchJob = new SearchJob(this);
		expandCollapseJob = new ExpandCollapseJob();
		
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
	}
	
	public void modifyText(ModifyEvent e)
	{
		searchJob.cancel();
		
		StyleRange[] styleRanges = createStyledSearchString(searchText.getText());
		searchText.setStyleRanges(styleRanges);
		
		if( searchViewControl.isShowingSearchTip() ) return; // showing search tip
		
		searchJob.schedule(getSearchQuery(), false, typingSearchDelay); // start with a delay since user might still be typing
		
		if( incrementalSearchEnabled )
			doIncrementalSearch();
	}

	private void doIncrementalSearch() 
	{
		IEditorPart editor = InstaSearchUI.getActiveEditor();
		
		if( editor != null )
		{
			IFindReplaceTarget target = (IFindReplaceTarget) editor.getAdapter(IFindReplaceTarget.class);
			
			if( target != null )
				lastIncrementalSearchPos = target.findAndSelect(lastIncrementalSearchPos, searchText.getText(), true, false, false) + searchText.getText().length();
		}
	}
	
	/**
	 * Highlight fields
	 * 
	 * @param text
	 * @return bold ranges 
	 */
	private static StyleRange[] createStyledSearchString(String text) 
	{
		//TODO: use parser
		ArrayList<StyleRange> styleRanges = new ArrayList<StyleRange>();
		String lcaseText = text.toLowerCase(Locale.ENGLISH);
		
		ArrayList<String> fieldsToHighlight = new ArrayList<String>(Field.values().length);
		for(Field field: Field.values()) // add all field names
			fieldsToHighlight.add(field.toString());
		
		for(String fieldName: fieldsToHighlight) 
		{
			int pos = lcaseText.indexOf(fieldName + ':'); // should use a parser here
			
			while( pos != -1 ) {
				styleRanges.add(new StyleRange(pos, fieldName.length(), null, null, SWT.BOLD));
				pos = lcaseText.indexOf(fieldName + ':', pos+fieldName.length()-1); // find next
			}
		}
		
		// ranges must be sorted by start position
		Collections.sort(styleRanges, new Comparator<StyleRange>() {
			public int compare(StyleRange sr1, StyleRange sr2) {
				return sr1.start - sr2.start;
			}
		});
		
		//TODO: highlight AND, OR 
		return styleRanges.toArray(new StyleRange[styleRanges.size()]);
	}

	void setSearchString(String searchString)
	{
		searchText.setText(searchString);
	}
	
	/**
	 * Starts the search by giving search string as input to the viewer
	 * 
	 * @param searchQuery 
	 * @param selectLast whether to select the item which is currently last
	 */
	void search(SearchQuery searchQuery, boolean selectLast) {
		searchJob.cancel(); // cancel previous search
		searchQuery.setFilter( searchViewControl.getFilter() );
		searchJob.schedule(searchQuery, selectLast, 0);
	}
	
	private SearchQuery getSearchQuery() 
	{
		SearchQuery sq = new SearchQuery(getSearchText(), maxResults );
		sq.setFilter( searchViewControl.getFilter() );
		return sq;
	}
	
	String getSearchText() {
		return searchText.getText().trim();
	}

	TreeViewer getResultViewer() {
		return resultViewer;
	}
	
	public void treeExpanded(TreeExpansionEvent event) {
		 
		 if( event.getElement() instanceof SearchQuery ) {
			 search((SearchQuery)event.getElement(), true);
		 }
	}
	
	public void treeCollapsed(TreeExpansionEvent event) {
	}
	
	private void configureResultViewer(ResultContentProvider contentProvider, IBaseLabelProvider decoratedLabelProvider) {
		
		resultViewer.setContentProvider(contentProvider);
		resultViewer.setLabelProvider(decoratedLabelProvider);
		resultViewer.setComparator(null);
		
		getViewSite().setSelectionProvider(resultViewer);
		resultViewer.addTreeListener(this);
		
		resultViewer.getControl().addMouseTrackListener(new MouseTrackAdapter() 
		{
			public void mouseHover(MouseEvent e) 
			{
				ViewerCell cell = resultViewer.getCell(new Point(e.x, e.y));
				
				if( cell != null && cell.getElement() instanceof SearchResultDoc ) 
				{
					SearchResultDoc doc = (SearchResultDoc) cell.getElement();
					resultViewer.getTree().setToolTipText(doc.getFilePath());
				}
				else
				{
					resultViewer.getTree().setToolTipText("");
				}
			}
		});
		
		KeyAdapter keyListener = new KeyAdapter() 
		{
			public void keyReleased(KeyEvent e) 
			{
				onSearchTextKeyPress(e);
			}
		};
		
		resultViewer.getControl().addKeyListener(keyListener);
		searchText.addKeyListener(keyListener);
	}
	
	private void hookContextMenu() 
	{
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});
		
		Menu menu = menuMgr.createContextMenu(resultViewer.getControl());
		resultViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, resultViewer);
	}

	private void onSearchTextKeyPress(KeyEvent e) 
	{
		if( e.keyCode == SWT.F5 ) 
		{ 
			refreshSearch();
		}
		if( e.keyCode == SWT.DEL ) 
		{
			deleteSelectedMatch();
		}
		if( e.keyCode == (int)'j' && (e.stateMask & SWT.CTRL)!=0 ) 
		{
			doIncrementalSearch();
		}
		else if( e.keyCode == SWT.TAB )
		{
			resultViewer.getTree().setFocus();
		}
		else if( e.keyCode == SWT.ESC ) 
		{
			if( expandCollapseJob.getState() == Job.RUNNING ) 
			{
				expandCollapseJob.cancel();
			}
			else 
			{
				if( searchText.getSelectionText().equals(searchText.getText()) )
				{
					searchText.setText("");
				}
				else
				{
					searchText.setFocus();
					searchText.selectAll();
				}
			}
		}
		else if( e.getSource() == searchText && e.keyCode == SWT.CR && (e.stateMask & SWT.CTRL)!=0 )
		{
			showAllResults();
		}
	}
	
	private void fillContextMenu(IMenuManager manager) 
	{
		boolean haveSelection = ! resultViewer.getSelection().isEmpty();
		SearchQuery sq = (SearchQuery) resultViewer.getInput();
		
		openAction.setEnabled( haveSelection  );
		manager.add(openAction);
		
		boolean showingItems = resultViewer.getTree().getItemCount() > 0;
		
		Action expandAll = new Action("Expand All", InstaSearchPlugin.getImageDescriptor("expandall")) {
			public void run() {
				expandAll();
			}
		};
		expandAll.setEnabled( showingItems  );
		manager.add(expandAll);
		
		Action collapseAll = new Action("Collapse All", InstaSearchPlugin.getImageDescriptor("collapseall")) {
			public void run() {
				collapseAll();
			}
		};
		collapseAll.setEnabled( showingItems  );
		manager.add(collapseAll);
		
		Action refresh = new Action("Refresh") {
			public void run() {
				refreshSearch();
			}
		};
		refresh.setAccelerator(SWT.F5);
		manager.add(refresh);
		
		Action delete = new Action("Delete Match") {
			public void run() {
				deleteSelectedMatch();
			}
		};
		delete.setAccelerator(SWT.DEL);
		manager.add(delete);
		
		Action moreResults = new Action("More Results...") {
			public void run() {
				showAllResults();
			}
		};
		moreResults.setEnabled( showingItems );
		manager.add(moreResults);
		
		if( sq == null || !sq.isLimited() )
			moreResults.setEnabled(false);
				
	}
	

	private void deleteSelectedMatch() {
		if( getResultViewer().getSelection() == null )
			return;
		IStructuredSelection selection = (IStructuredSelection)resultViewer.getSelection();
		getResultViewer().remove(selection.toArray());
	}
	
	/**
	 * 
	 */
	public void showAllResults() {
		SearchQuery sq = (SearchQuery)resultViewer.getInput();
		SearchQuery newSq = new SearchQuery(sq);
		newSq.setMaxResults(SearchQuery.UNLIMITED_RESULTS);
		search(newSq, false);
	}
	
	/**
	 * 
	 */
	public void expandAll() {
		expandCollapseJob.schedule(true);
	}
	
	/**
	 * 
	 */
	public void collapseAll() {
		expandCollapseJob.schedule(false);
	}
	
	private void openSelection() throws Exception {
		IStructuredSelection selection = (IStructuredSelection)resultViewer.getSelection();
		Object obj = selection.getFirstElement();
		
		SearchResultDoc doc = null;
		MatchLine selectedLineMatches = null;
		
		if(obj instanceof SearchResultDoc) {
			doc = (SearchResultDoc) obj;
		} else if(obj instanceof MatchLine) {
			selectedLineMatches = (MatchLine) obj;
			doc = selectedLineMatches.getResultDoc();
		} else if(obj instanceof Exception) {
			InstaSearchUI.showError((Exception)obj);
			return;
		} else if(obj instanceof SearchQuery ) {
			search( (SearchQuery) obj, true );
			return;
		} else
			return;
		
		
		new MatchHighlightJob(doc, selectedLineMatches, contentProvider, searchJob, getSite().getPage()).schedule();
	}

	
	
	private void hookDoubleClickAction() {
		resultViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				openAction.run();
			}
		});
	}
		
	public void setFocus() {
		searchText.setFocus();
		//searchText.selectAll();
	}
	
	private void makeActions() {
		openAction = new Action("Open") {
			public void run() {
				try {
					openSelection();
				} catch (Exception e) {
					InstaSearchPlugin.log(e);
				}
			}
		};
	}
	
	/**
	 * Logging an error in the plugin
	 * Create an action that allows reporting it
	 */
	public void logging(IStatus status, String plugin)
	{
		IMenuManager menuManager = getViewSite().getActionBars().getMenuManager();
		
		IContributionItem item = menuManager.find(ShowExceptionAction.ID);
		
		if( item != null )
			menuManager.remove(item);
		
		ShowExceptionAction action = new ShowExceptionAction(status);
		action.setText("Report Bug");
		
		menuManager.add(action);
	}
	
	@Override
	public void dispose()
	{		
		super.dispose();
		
		if( InstaSearchPlugin.getDefault() != null )
		{
			InstaSearchPlugin.getDefault().getLog().removeLogListener(this);
			InstaSearchPlugin.removePreferenceChangeListener(this);
		}
	}
	
	private void refreshSearch() 
	{
		InstaSearchPlugin.getInstaSearch().updateIndex();
		
		SearchQuery input = (SearchQuery) resultViewer.getInput();
		if( input == null ) return;
		resultViewer.setInput(null); // clear cached search results
		
		searchJob.cancel();
		searchJob.schedule(input, false, typingSearchDelay);
	}

	public void propertyChange(PropertyChangeEvent event) 
	{
		typingSearchDelay = InstaSearchPlugin.getIntPref(PreferenceConstants.P_TYPING_SEARCH_DELAY);
		maxResults = InstaSearchPlugin.getIntPref(PreferenceConstants.P_SHOWN_FILES_COUNT);
		incrementalSearchEnabled = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_INCREMENTAL_SEARCH);
	}
	
	/**
	 * Background job that expands/collapses all entries
	 */
	private class ExpandCollapseJob extends Job implements ISchedulingRule {
		
		/**
		 */
		public ExpandCollapseJob() {
			super("Expand All");
			
			setRule(this);
			//setUser(true);
			
			// listen to searchJob changes. stop expanding on new search
			searchJob.addJobChangeListener(new JobChangeAdapter() {
				public void scheduled(IJobChangeEvent event) {
					cancel(); // new search
				}
				public void done(IJobChangeEvent event) {
					cancel(); // canceled search
				}
			});
			
		}
	
		public void schedule(boolean expandAll) {
			
			this.cancel();
			
			if( !expandAll ) {
				resultViewer.collapseAll();
				return;
			}
			
			if( resultViewer.getTree().getItemCount() == 0 ) {
				return;
			}
			
			this.schedule();
		}
		
		protected IStatus run(IProgressMonitor monitor) {
			
			Display display = getViewSite().getShell().getDisplay();
			
			Object[] elements = contentProvider.getElements();
			monitor.beginTask("InstaSearch Expanding", elements.length);
			
			for(int i = 0; i < elements.length && !monitor.isCanceled(); i++) {
				final Object curDoc = elements[i];
				if( curDoc == null ) continue;
				if( !(curDoc instanceof SearchResultDoc) ) continue;
				
				contentProvider.getChildren(curDoc); // get lines from file (they become cached)
				
				Runnable expander = new Runnable() {
					public void run() {
						resultViewer.setExpandedState(curDoc, true);
					}
				};
				display.syncExec(expander); // expand in UI thread
				
				monitor.worked(1);
			}
			
			monitor.done();
			
			return Status.OK_STATUS;
		}

		public boolean contains(ISchedulingRule rule) {
			return rule.getClass() == this.getClass();
		}

		public boolean isConflicting(ISchedulingRule rule) {
			return rule.getClass() == this.getClass();
		}
		
	}

}
