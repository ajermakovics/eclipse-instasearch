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

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.progress.IProgressConstants;

/**
 * Workspace Job that updates the plugin
 */
public class UpdatePluginJob extends WorkspaceJob
{

	/**
	 * 
	 */
	private static final String UPDATE_MANAGER_COMMAND = "org.eclipse.equinox.p2.ui.sdk.update";

	public UpdatePluginJob()
	{
		super("InstaSearch Update");
		
		setPriority(DECORATE);
		
		setProperty(IProgressConstants.ICON_PROPERTY, InstaSearchPlugin.getImageDescriptor("update"));
		setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
	}

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor)
			throws CoreException
	{
		if( !isSystem() )
			setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
		
		showUpdateManager();
		
		return new Status(IStatus.OK, InstaSearchPlugin.getPluginId(), 0, "Update Manager Launched", null);
	}

	private void showUpdateManager() 
	{
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
			
			public void run()
			{
				IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);

				if( handlerService != null ) {
					try {
						handlerService.executeCommand(UPDATE_MANAGER_COMMAND, new Event()); // show software updates dialog
					} catch(Exception e) {
						InstaSearchPlugin.log(e);
						setProperty(IProgressConstants.ACTION_PROPERTY, new ShowExceptionAction(e, "Error Launching Update Manager"));
					}
				}
			}
		});
	}
	
}
