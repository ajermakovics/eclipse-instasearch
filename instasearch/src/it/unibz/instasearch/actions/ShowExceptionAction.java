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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.Action;

public class ShowExceptionAction extends Action
{
	public static final String ID = "it.unibz.instasearch.ui.InstaSearchUI.ShowExceptionAction";
	
	private Throwable exception;
	private IStatus status;

	public ShowExceptionAction(Throwable exception)
	{
		this.exception = exception;
	
		setId(ID);
		setText("Exception");
		setDescription(exception.getMessage());
		setToolTipText(exception.getMessage());
	}
	
	public ShowExceptionAction(Exception exception, String text)
	{
		this(exception);
		
		setText(text);
	}
	
	public ShowExceptionAction(IStatus status)
	{
		this(status.getException());
		
		this.status = status;
	}
	
	@Override
	public void run()
	{
		if( status != null )
			InstaSearchUI.showError(status, getText());
		else
			InstaSearchUI.showError(exception, getText());
	}
}
