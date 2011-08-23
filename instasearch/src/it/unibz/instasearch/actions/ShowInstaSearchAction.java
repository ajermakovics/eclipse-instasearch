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
package it.unibz.instasearch.actions;

import it.unibz.instasearch.InstaSearchPlugin;
import it.unibz.instasearch.prefs.PreferenceConstants;
import it.unibz.instasearch.ui.InstaSearchPage;
import it.unibz.instasearch.ui.InstaSearchUI;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public class ShowInstaSearchAction implements IWorkbenchWindowActionDelegate, Runnable {
	
	public ShowInstaSearchAction() {
	}
	
	public void run(IAction action) {
		run();		
	}

	public void run()
	{
		boolean dialogOnShortcut = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_DIALOG_ON_SHORTCUT);
		
		if( dialogOnShortcut )
			NewSearchUI.openSearchDialog(InstaSearchUI.getWorkbenchWindow(), InstaSearchPage.ID);
		else
			InstaSearchUI.showSearchView();
	}
	
	public void dispose()
	{
	}

	public void init(IWorkbenchWindow window)
	{
	}

	public void selectionChanged(IAction action, ISelection selection)
	{
	}

}