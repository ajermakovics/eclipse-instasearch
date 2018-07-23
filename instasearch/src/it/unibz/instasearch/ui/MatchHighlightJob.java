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
import it.unibz.instasearch.indexing.SearchResultDoc;
import it.unibz.instasearch.ui.ResultContentProvider.MatchLine;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.MarkerUtilities;

class MatchHighlightJob extends Job implements ISchedulingRule, IPartListener 
{
	private SearchResultDoc doc;
	private MatchLine selectedLineMatches;
	private ResultContentProvider contentProvider;
	private ITextEditor editor;
	private Job searchJob;
	private IDocument document;
	private IWorkbenchPage workbenchPage;
	private IFile file;

	/**
	 * @param doc
	 * @param selectedLineMatches
	 * @param contentProvider 
	 * @param workbenchPage 
	 * @throws Exception 
	 */
	public MatchHighlightJob(SearchResultDoc doc, MatchLine selectedLineMatches, ResultContentProvider contentProvider, Job searchJob, IWorkbenchPage workbenchPage) throws Exception 
	{
		super("Highlight Matches");
		
		this.doc = doc;
		this.selectedLineMatches = selectedLineMatches;
		this.contentProvider = contentProvider;
		this.searchJob = searchJob;
		this.workbenchPage = workbenchPage;
		
		IEditorInput input = contentProvider.getEditorInput(doc);
		IEditorDescriptor editorDesc = IDE.getEditorDescriptor(doc.getFileName());
		
		IEditorPart editorPart = IDE.openEditor(workbenchPage, input, editorDesc.getId());
		this.editor = getTextEditor(input, editorPart);
		this.document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		
		IStorage storage = contentProvider.getStorage(doc);
		this.file = null;
		if( storage instanceof IFile )
			file = (IFile) storage;
	}
	
	@Override
	protected IStatus run(IProgressMonitor monitor) 
	{
		try {
			if( editor != null )
			{
				workbenchPage.addPartListener(this);
				
				findAndHighlightMatches(doc, monitor);
			}
		} catch (Exception e) {
			InstaSearchPlugin.log(e);
		} finally {
			workbenchPage.removePartListener(this);
		}

		return Status.OK_STATUS;
	}
	
	private void findAndHighlightMatches(SearchResultDoc doc, final IProgressMonitor monitor) 
		throws Exception, BadLocationException, CoreException 
	{
		if( file != null ) 
		{
			deleteMarkers();
			addMarkerRemover(file);
		}
		
		MatchHighliter highlighter = new MatchHighliter(monitor);
		
		if( selectedLineMatches != null )
			highlighter.highlightMatchLine(file, selectedLineMatches);
		
		contentProvider.getMatchLines(doc, false, highlighter);
		
		//highlightMatches(file, selectedLineMatches, lineMatchesList, monitor);
	}
	
	private class MatchHighliter implements ResultContentProvider.MatchFindCallback
	{
		private IProgressMonitor monitor;
		private boolean matchSelected = false;

		/**
		 * @param monitor
		 */
		public MatchHighliter(IProgressMonitor monitor)
		{
			this.monitor = monitor;
		}

		public void matchFound(MatchLine line)
		{
			try
			{
				highlightMatchLine(file, line);
			}
			catch (Exception e)
			{
				InstaSearchPlugin.log(e);
				monitor.setCanceled(true);
			}
		}

		public boolean isCanceled()
		{
			return monitor.isCanceled();
		}
		
		/**
		 * @param file
		 * @throws CoreException 
		 * @throws BadLocationException 
		 */
		protected void highlightMatchLine(IFile file, MatchLine lineMatches) throws CoreException, BadLocationException
		{
			int lineNum = lineMatches.getLineNumber();
		
			if( document.getNumberOfLines() < lineNum ) 
				return;
		
			if( selectedLineMatches == null ) selectedLineMatches = lineMatches; // pick first
			
			int pos = document.getLineOffset(lineNum-1);
		
			for(Match match: lineMatches.getMatches()) 
			{
				if( monitor.isCanceled() ) break;
				
				editor.setHighlightRange(pos+match.getOffset(), match.getLength(), false);
				
				IMarker marker = createMarker(pos, lineNum, match, file, editor);
				
				if(selectedLineMatches != null && lineNum == selectedLineMatches.getLineNumber()) 
				{
					if( marker != null && ! matchSelected )
					{
						gotoEditorMarker(marker);
						matchSelected = true;
					}
					
					if( !matchSelected ) {
						int selectedOffset = pos+match.getOffset();
						int selectedLength = match.getLength();
						editor.setHighlightRange(selectedOffset, selectedLength, true);
						matchSelected = true;
					}
				}
			}
		}

		private IMarker createMarker(int pos, int lineNumber, Match match, IFile file, ITextEditor textEditor) throws CoreException {
			
			if( file == null )
				return null;
			
			IMarker marker = null;	
			
			marker = file.createMarker(NewSearchUI.SEARCH_MARKER);
			marker.setAttribute(IMarker.TRANSIENT, true);
			marker.setAttribute(IMarker.MESSAGE, match.getElement());
			
			MarkerUtilities.setLineNumber(marker, lineNumber);
			MarkerUtilities.setCharStart(marker, pos + match.getOffset());
			MarkerUtilities.setCharEnd(marker, pos + match.getOffset() + match.getLength());
			
			return marker;
		}

		private void gotoEditorMarker(final IMarker selectedMarker) 
		{
			if( selectedMarker == null )
				return;
			
			Runnable runnable = new Runnable() 
			{
				public void run() 
				{
					IDE.gotoMarker(editor, selectedMarker);
				}
			};
			
			editor.getSite().getShell().getDisplay().asyncExec(runnable);	
		}

	}
	
	private ITextEditor getTextEditor(IEditorInput input, IEditorPart editor) 
	{
		if( editor instanceof MultiPageEditorPart ) 
		{
			MultiPageEditorPart multiPageEditor = (MultiPageEditorPart) editor;
			IEditorPart[] editors = multiPageEditor.findEditors(input);
			
			for(IEditorPart ed: editors) 
			{
				if( ed instanceof ITextEditor ) 
				{
					multiPageEditor.setActiveEditor(ed);
					return (ITextEditor) ed;
				}
			}
		}
		else if( editor instanceof ITextEditor )
		{
			return (ITextEditor) editor;
		}
		
		return null;
	}

	/**
	 * Remove markers when new search is made
	 * @param file
	 */
	private void addMarkerRemover(final IFile file) 
	{
		 searchJob.addJobChangeListener(new JobChangeAdapter(){
			public void done(IJobChangeEvent event) {
				try {
					cancel();
					deleteMarkers();
				} finally {
					searchJob.removeJobChangeListener(this);
				}
			}
		 });
	}
	
	public boolean contains(ISchedulingRule rule) {
		return rule.getClass() == this.getClass();
	}

	public boolean isConflicting(ISchedulingRule rule) {
		return rule.getClass() == this.getClass();
	}


	public void partClosed(IWorkbenchPart part)
	{
		if( part == editor ) // if closing editor
		{
			this.cancel(); // cancel highlight job
			deleteMarkers();
		}
	}

	private void deleteMarkers()
	{
		try
		{
			if( file != null )
				file.deleteMarkers(NewSearchUI.SEARCH_MARKER, true, IResource.DEPTH_INFINITE);
		}
		catch (CoreException e)
		{
			InstaSearchPlugin.log(e);
		}
	}

	public void partDeactivated(IWorkbenchPart part)
	{
	}

	public void partOpened(IWorkbenchPart part)
	{
	}
	
	public void partActivated(IWorkbenchPart part)
	{
	}

	public void partBroughtToTop(IWorkbenchPart part)
	{
	}

}