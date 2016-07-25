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
import it.unibz.instasearch.indexing.StorageIndexer;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.progress.IProgressConstants;

/**
 * 
 */
public class DeleteIndexJob extends WorkspaceJob
{
	private StorageIndexer indexer;

	/**
	 * @param indexer 
	 */
	public DeleteIndexJob(WorkspaceIndexer indexer)
	{
		super("Delete InstaSearch Index");
		
		this.indexer = indexer;
		setRule(indexer);
		
		setPriority(DECORATE);
		
		setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
		setProperty(IProgressConstants.ICON_PROPERTY, 
				InstaSearchPlugin.getImageDescriptor("delete"));
	}

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor)
			throws CoreException
	{
		if (!isSystem())
			setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
		
		try
		{
			indexer.deleteIndex();
		} catch (Exception e)
		{
			monitor.beginTask("Exception", 1);
			ShowExceptionAction action = new ShowExceptionAction(e, "Unable to delete index");
			setProperty(IProgressConstants.ACTION_PROPERTY, action);
			
			return Status.CANCEL_STATUS;
		}
		
		return Status.OK_STATUS;
	}

}
