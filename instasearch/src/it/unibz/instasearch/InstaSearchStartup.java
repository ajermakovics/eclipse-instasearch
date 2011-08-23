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

import it.unibz.instasearch.actions.ShowInstaSearchAction;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;

/**
 * This class forces the plugin to be started at eclipse startup (when this class is loaded)
 * We need an early startup to start collecting changed files (for indexing)
 */
public class InstaSearchStartup implements IStartup
{
	public void earlyStartup()
	{
		IPreferenceStore prefs = InstaSearchPlugin.getDefault().getPreferenceStore();
		boolean shownView = InstaSearchPlugin.getBoolPref("shownView"); // false first time
		
		if( ! shownView ) 
		{
			prefs.setValue("shownView", true); // do not show anymore on startup
			
			// Show view the first time after installation
			PlatformUI.getWorkbench().getDisplay().asyncExec( new ShowInstaSearchAction() );
		}
	}
	


}
