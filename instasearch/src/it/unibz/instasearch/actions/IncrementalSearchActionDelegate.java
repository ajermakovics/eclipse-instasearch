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

import org.eclipse.jface.action.IAction;

public class IncrementalSearchActionDelegate extends InstaSearchActionDelegate {
	
	@Override
	public void run(IAction action) 
	{
		InstaSearchPlugin.setBoolPref(PreferenceConstants.P_INCREMENTAL_SEARCH, action.isChecked());
	}

}
