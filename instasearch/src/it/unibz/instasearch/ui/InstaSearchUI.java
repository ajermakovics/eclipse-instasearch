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

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * Contains UI helper methods
 */
public class InstaSearchUI
{

	public static Shell getActiveShell() {
		if( PlatformUI.getWorkbench().getDisplay() != null )
			return PlatformUI.getWorkbench().getDisplay().getActiveShell();
		
		return null;
	}

	public static void showMessage(String message) {
		
		MessageDialog.openInformation(
					getActiveShell(),
					InstaSearchPlugin.getPluginName(),
					message);
	}

	public static void showError(Exception ex) {
		String message = ex.getMessage();
		
		if( message == null || "".equals(message) )
			message = ex.getClass().getName();
		
		showError(ex, message);
	}

	public static void showError(Throwable exception, String message) {
		Status status = new Status(IStatus.ERROR, InstaSearchPlugin.getPluginId(), message, exception);
		
		showError(status, message);
	}
	
	public static void showError(IStatus status, String message) {
		
		//status.getException().printStackTrace();
		
		ReportErrorDialog.openReportError(getActiveShell(), InstaSearchPlugin.getPluginName(), message, status);
				
		InstaSearchPlugin.log(status);
	}

	/**
	 * Opens and shows the search view
	 * Note: This method must be run from the UI thread
	 * @return InstaSearchView
	 */
	public static InstaSearchView showSearchView() 
	{
		IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		
		if( win != null && win.getActivePage() != null )
		{
			IViewPart view = null;
			
			try {
				view = win.getActivePage().showView(InstaSearchView.ID);
			} catch(PartInitException e) {
				InstaSearchPlugin.log(e);
				return null;
			}
			
			if( view == null ) 
				return null;
			
			if( view instanceof InstaSearchView ) 
				return (InstaSearchView)view;
		}
		
		return null;
	}
	
	/**
	 * Checks weather the search view is visible
	 * Note: This method must be run from the UI thread
	 * @return InstaSearchView
	 */
	public static boolean isSearchViewVisible()
	{
		IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		
		if( win != null && win.getActivePage() != null )
		{	
			IViewPart view = win.getActivePage().findView(InstaSearchView.ID);
			
			return win.getActivePage().isPartVisible(view);
		}
		
		return false;
	}
	
	public static void showHomePage() throws MalformedURLException, PartInitException  {
		URL homePageUrl = new URL(InstaSearchPlugin.getHomePageLocation());
		PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(homePageUrl);
	}

	public static IWorkbenchWindow getWorkbenchWindow() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	}

	public static String getActiveProject() {
		
		IEditorPart editor = getActiveEditor();
		
		if( editor == null )
			return null;
		
		IAdaptable adaptable = editor.getEditorInput();
		
		IProject project = (IProject) adaptable.getAdapter(IProject.class);
		
		if (project == null) {
			IResource resource = (IResource) adaptable.getAdapter(IResource.class);
			
			if (resource != null)
				project = resource.getProject();
		}
		
		if (project != null && project.isAccessible())
			return project.getName();
		
		return null;
	}
	
	/**
	 * Gets active editor
	 * Must be run from UI thread
	 * 
	 * @return IEditorPart
	 */
	public static IEditorPart getActiveEditor() {
		if( getWorkbenchWindow() == null )
			return null;
		
		IWorkbenchPage activePage= getWorkbenchWindow().getActivePage();
		if (activePage != null) {
			IEditorPart activeEditor= activePage.getActiveEditor();
			return activeEditor;
		}
		
		return null;
	}

	public static Image getSharedImage(String img) {
		return PlatformUI.getWorkbench().getSharedImages().getImage(img);
	}
}
