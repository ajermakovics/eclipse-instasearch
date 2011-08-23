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
import it.unibz.instasearch.ui.InstaSearchUI;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressConstants;

/**
 * A job that checks for new updates of the plugin
 * 
 */
public class CheckUpdatesJob extends WorkspaceJob
{
	// return code constants
	public static final int UPDATE_AVAILABLE_CODE = 1;
	public static final int NO_UPDATE_AVAILABLE_CODE = 2;
	
	private boolean updateAvailable;

	public CheckUpdatesJob()
	{
		super("InstaSearch Update Check");
		
		setProperty(IProgressConstants.ICON_PROPERTY, 
				InstaSearchPlugin.getImageDescriptor("update"));
		
		setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
		setProperty(IProgressConstants.KEEPONE_PROPERTY, Boolean.TRUE);
	}
	
	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor)
			throws CoreException
	{
		try
		{
			checkForUpdates(monitor);
			
			if( ! isSystem() )
				PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
					public void run() { 
						IAction action;

						if( isUpdateAvailable() )
							action = createUpdateNotificationAction();
						else 
							action = createNoUpdateNotificationAction();

						action.run();
					}
				});
			
			//setProperty(IProgressConstants.ACTION_PROPERTY, action);
			
		} catch (Exception e) {
			IAction action = new ShowExceptionAction(e, "Error Checking Updates");
			setProperty(IProgressConstants.ACTION_PROPERTY, action);
			
			if (!isSystem())
				setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
			
			if( ! isSystem() ) {
				InstaSearchPlugin.log(e);
				return new Status(IStatus.ERROR, InstaSearchPlugin.getPluginId(), 0, action.getText(), e);
			}
		}
		
		int returnCode = isUpdateAvailable() ? UPDATE_AVAILABLE_CODE : NO_UPDATE_AVAILABLE_CODE;
		return new Status(IStatus.OK, InstaSearchPlugin.getPluginId(), returnCode, "Done", null);
	}

	private boolean checkForUpdates(IProgressMonitor monitor) throws HttpException, IOException, URISyntaxException

	{
		updateAvailable = false;
		
		String versionCheckUrl = InstaSearchPlugin.getUpdateLocation();
		String v = InstaSearchPlugin.getVersion();
		
		HttpClient httpClient = new HttpClient();
		configureProxy(httpClient, versionCheckUrl);
		
		GetMethod getMethod = new GetMethod(versionCheckUrl + "?v="+v);
		
        int statusCode = httpClient.executeMethod(getMethod);
		
        if (statusCode != HttpStatus.SC_OK)
        	return updateAvailable;
        
        String response = getMethod.getResponseBodyAsString();
        getMethod.releaseConnection();
        
        if( "y".equals(response) )
        	updateAvailable = true;
        
		return updateAvailable;
	}

	/**
	 * @param httpClient
	 * @param versionCheckUrl
	 * @throws URISyntaxException 
	 */
	private void configureProxy(HttpClient httpClient, String versionCheckUrl) throws URISyntaxException 
	{
		IProxyService proxyService = InstaSearchPlugin.getDefault().getProxyService();
		
		if (proxyService != null && proxyService.isProxiesEnabled()) {
			URI uri = new URI(versionCheckUrl);
			final IProxyData[] proxiesData = proxyService.select(uri);
			
			IProxyData proxy = null;
			for(IProxyData proxyData: proxiesData)
			{
				if( proxyData.getType().equals(IProxyData.HTTP_PROXY_TYPE) )
				{
					proxy = proxyData;
					break;
				}
			}
			
			if( proxy == null )
				return;
			
			HostConfiguration config = httpClient.getHostConfiguration();
			config.setProxy(proxy.getHost(), proxy.getPort());
			
			if( proxy.isRequiresAuthentication() )
			{
				Credentials credentials = new UsernamePasswordCredentials(proxy.getUserId(), proxy.getPassword());
				AuthScope authScope = new AuthScope(proxy.getHost(), proxy.getPort());
				httpClient.getState().setProxyCredentials(authScope, credentials);
			}
		}
	}
	
	private IAction createNoUpdateNotificationAction()
	{
		String text = "No Update Available";
		
		IAction notificationAction = new Action(text)
		{
			public void run()
			{
				InstaSearchUI.showMessage("You have the latest version (" + InstaSearchPlugin.getVersion() + ")");
				setEnabled(false);
			}
		};
		notificationAction.setDescription(text);
		notificationAction.setToolTipText(text);
				
		return notificationAction;
	}

	/**
	 * Creates an action that offers to update the plugin
	 * @return IAction
	 */
	public static IAction createUpdateNotificationAction()
	{
		String text = "New Version Available";
		
		IAction notificationAction = new Action("Update")
		{
			public void run()
			{
				/*
				 MessageDialogWithToggle.openYesNoQuestion(shell, "Update", 
				    "There is a new version of InstaSearch available. Update?",
	  				"Update automatically", true,
	  				InstaSearchPlugin.getDefault().getPreferenceStore(), "AUTO_UPDATE");
				 */
				boolean update = MessageDialog.openQuestion(
							InstaSearchUI.getActiveShell(), 
							getDescription(), 
							"There is a new version available. Update?");
						
				if( update )
				{
					UpdatePluginJob updateJob = new UpdatePluginJob();
					updateJob.schedule();		
				}
				
			}
		};
		notificationAction.setDescription(text);
		notificationAction.setToolTipText(text);
		
		return notificationAction;
	}

	/**
	 * Is there an update available for this plugin 
	 * The value is set after the job has finished
	 * @return UpdateAvailable
	 */
	public boolean isUpdateAvailable()
	{
		return updateAvailable;
	}

	@Override
	public boolean belongsTo(Object family)
	{
		return family == CheckUpdatesJob.class;
	}
	

}