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

import it.unibz.instasearch.ui.InstaSearchUI;

import org.eclipse.jface.action.IAction;

public class VisitHomePageActionDelegate extends InstaSearchActionDelegate
{
	@Override
	public void run(IAction action)
	{
		try
		{
			InstaSearchUI.showHomePage();	
		} catch (Exception e)
		{
			InstaSearchUI.showError(e, "Error Opening Page");
		}
	}

}
