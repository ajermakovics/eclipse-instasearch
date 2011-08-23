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
package it.unibz.instasearch.jobs;

import it.unibz.instasearch.InstaSearchPlugin;
import it.unibz.instasearch.actions.ShowExceptionAction;
import it.unibz.instasearch.indexing.WorkspaceIndexer;
import it.unibz.instasearch.indexing.StorageIndexer.IndexChangeListener;
import it.unibz.instasearch.prefs.PreferenceConstants;
import it.unibz.instasearch.ui.InstaSearchUI;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressConstants;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;

/**
 * Workspace job that periodically updates the index
 */
public class IndexUpdateJob extends WorkspaceJob implements SynchronousBundleListener, IResourceChangeListener, IPropertyChangeListener {
	
	private WorkspaceIndexer indexer;
	private IndexChangeListener indexChangeListener;
	
	private Map<IResource, Integer> changedResources = new ConcurrentHashMap<IResource, Integer>(); // changed by several threads
	private boolean searchViewVisible = false;

	/**
	 * @param indexer
	 * @param indexChangeListener
	 */
	public IndexUpdateJob(WorkspaceIndexer indexer, IndexChangeListener indexChangeListener) {
		super("InstaSearch Index Update");
		this.indexer = indexer;
		this.indexChangeListener = indexChangeListener;
		
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE); // listen to file changes
		
		if( InstaSearchPlugin.getDefault() != null )
			InstaSearchPlugin.getDefault().getBundle().getBundleContext().addBundleListener(this); // listen to plugin stopping
		
		setRule(indexer);
		
		setProperty(IProgressConstants.ICON_PROPERTY, InstaSearchPlugin.getImageDescriptor("syncdb")); 
		setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
	}
	
	public void resourceChanged(IResourceChangeEvent event)
	{
		if( event.getType() == IResourceChangeEvent.PRE_BUILD
				|| event.getType() == IResourceChangeEvent.POST_BUILD )
			return; // not change events
		
		try 
		{
			if( indexer.isIndexed() ) 
			{
				if( event.getType() == IResourceChangeEvent.POST_CHANGE )
				{
					IResourceDelta delta = event.getDelta();
					
					delta.accept(createResourceVisitor(), false);
				}
				//else if( event.getResource().getType() == IResource.PROJECT ) // CLOSE,DELETE,REFRESH events for project
				//changedResources.put( event.getResource(), 0 ); 
			}
		} catch (Exception e) {
			InstaSearchPlugin.log(e);
		}
	}
	
	private IResourceDeltaVisitor createResourceVisitor()
	{
		return new IResourceDeltaVisitor() 
		{
			public boolean visit(IResourceDelta delta) throws CoreException
			{
				IResource resource = delta.getResource();

				if( resource.getType() == IResource.FILE  )
				{
					IFile file = (IFile) resource;

					if( indexer.isIndexable(file) ) {
						changedResources.put( file, file.getType());
					}

				}
				else if( resource.getType() == IResource.FOLDER ) {
					if( delta.getFlags() == IResourceDelta.DERIVED_CHANGED ) { // we must skip derived resources
						changedResources.put( resource, resource.getType() );
					}
				}

				//else if(resource.getType() == IResource.PROJECT )
				//	System.out.println("Project change " + resource.toString());

				return true;
			}
		};
	}
	
	public void bundleChanged(BundleEvent event)
	{
		if( event.getType() == BundleEvent.STOPPING || event.getType() == BundleEvent.STOPPED ) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
			this.cancel();
			
			if( event.getBundle() != null && event.getBundle().getBundleContext() != null )
				event.getBundle().getBundleContext().removeBundleListener(this);
		}
	}
	
	@Override
	protected void canceling() {
		//ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}
	
	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor)
			throws CoreException
	{	
		if( InstaSearchPlugin.getDefault() == null || monitor.isCanceled() ) // no plugin (stopped)
			return Status.CANCEL_STATUS;
		
		int indexUpdateInterval = InstaSearchPlugin.getIntPref(PreferenceConstants.P_INDEX_UPDATE_INTERVAL);
		
		boolean indexed = false;
		
		try {
			indexed = indexer.isIndexed();
		} catch (IOException e1) {
			InstaSearchPlugin.log(e1);
		}
		
		if( !indexed || changedResources.isEmpty() ) { // not indexed or still indexing
			schedule(indexUpdateInterval); // check later
			return Status.CANCEL_STATUS;
		}
		
		monitor.beginTask("Updating Search Index", changedResources.size());
		IStatus returnStatus = Status.OK_STATUS;
		
		try
		{
			for (Iterator<IResource> iterator = changedResources.keySet().iterator(); 
									iterator.hasNext() && !monitor.isCanceled();)
			{
				IResource resource = iterator.next();
				monitor.subTask(resource.getName());
				
				if( resource.getType() == IResource.FILE )
					indexer.updateFile( (IFile)resource );
				else if( resource.getType() == IResource.FOLDER ) {
					indexer.updateFolder( (IFolder)resource, monitor );
				}
				//else if( resource.getType() == IResource.PROJECT )
				//	indexer.updateProject((IProject)resource);
				
				iterator.remove();
				
				monitor.worked(1);
			}
			
			indexChangeListener.onIndexUpdate();
			
			PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
				public void run() { 
					searchViewVisible = InstaSearchUI.isSearchViewVisible(); // must run in UI thread, but this job isn't
				}
			});
			
			if( ! searchViewVisible ) { // don't optimize when might be searching
				indexer.optimizeIndex(); 
			}
			
			monitor.done();
			
		} catch (Exception e) {
			monitor.beginTask("Exception", 1);
			setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
			setProperty(IProgressConstants.ACTION_PROPERTY, new ShowExceptionAction(e, "Error Updating Index"));
			InstaSearchPlugin.log(e);
			
			returnStatus = Status.CANCEL_STATUS;
		}
		
		if( ! monitor.isCanceled() )
			schedule(indexUpdateInterval);
		
		return returnStatus;
	}

	public void propertyChange(PropertyChangeEvent event) {
		String prop = event.getProperty();
		
		if( PreferenceConstants.P_EXCLUDE_DIRS.equals(prop) )
		{
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			String excludedDirStr = "";
			
			if( event.getOldValue() != null )
				excludedDirStr += event.getOldValue().toString() + File.pathSeparator;
			
			if( event.getNewValue() != null )
				excludedDirStr += event.getNewValue().toString();
			
			String[] excludedDirArr = excludedDirStr.split(File.pathSeparator);
			
			for (String excludedDir : excludedDirArr) 
			{
				if( excludedDir.length() == 0 ) continue;
				Path path = new Path(excludedDir);
				if( !root.exists(path) ) continue;
				
				IResource res = root.findMember(path);
				if( res != null )
					changedResources.put(res, res.getType());// mark excluded as changed for re-indexing
			}
		}
	}
		
}