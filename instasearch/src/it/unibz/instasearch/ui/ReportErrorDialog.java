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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Constants;

public class ReportErrorDialog extends ErrorDialog
{
	private static final int REPORT_BUTTON_ID = IDialogConstants.CLIENT_ID + 1;
	private IStatus status;
	private Link newTicketLink;
	
	/**
	 * @param parentShell
	 * @param dialogTitle
	 * @param message
	 * @param status
	 * @param displayMask
	 */
	public ReportErrorDialog(Shell parentShell, String dialogTitle,
			String message, IStatus status, int displayMask)
	{
		super(parentShell, dialogTitle, message, status, displayMask);
		
		this.status = status;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.ErrorDialog#createButtonsForButtonBar(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected void createButtonsForButtonBar(Composite parent)
	{
		super.createButtonsForButtonBar(parent);
		
		createButton(parent, REPORT_BUTTON_ID, "Report", true);
	}
	
	@Override
	protected List createDropDownList(Composite parent)
	{
		List list = super.createDropDownList(parent);
		
		if( newTicketLink == null ) {
			newTicketLink = new Link(parent, SWT.NONE);
			newTicketLink.setText("You can also open a <a>new ticket</a>");
			newTicketLink.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e)
				{
					try
					{
						InstaSearchUI.showHomePage();
					} catch (Exception ex)
					{
						InstaSearchPlugin.log(ex);
						newTicketLink.setEnabled(false);
					}
				}
			});
			
			GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL
					| GridData.GRAB_HORIZONTAL | GridData.VERTICAL_ALIGN_FILL
					| GridData.GRAB_VERTICAL ); //
			data.horizontalSpan = 2;
			newTicketLink.setLayoutData( data );
		}
		
		list.addDisposeListener(new DisposeListener(){ 
			public void widgetDisposed(DisposeEvent e)
			{
				newTicketLink.dispose();
				newTicketLink = null;
			}
		});
		
		return list;
	}
	
	public static void openReportError(Shell parentShell, String title,
			String message, IStatus status) {
				
		int displayMask = IStatus.OK | IStatus.INFO | IStatus.WARNING | IStatus.ERROR;
		final ReportErrorDialog dialog = new ReportErrorDialog(parentShell, title, message, status, displayMask);
		
		Display display = PlatformUI.getWorkbench().getDisplay();
		if(display == null || display.isDisposed() )
			return;
		
		display.asyncExec(new Runnable() {
			public void run()
			{
				dialog.open();
			}
		});
		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.ErrorDialog#buttonPressed(int)
	 */
	@Override
	protected void buttonPressed(int id)
	{
		if( id == REPORT_BUTTON_ID ) {
			try {
				reportError();
				close();
			} catch (Exception e) {
				e.printStackTrace();
				InstaSearchPlugin.log(e);
				InstaSearchUI.showMessage("Unable to send. Please open a ticket in project's site");
			}
		} else
			super.buttonPressed(id);
	}


	/**
	 * @throws IOException 
	 * @throws HttpException 
	 * 
	 */
	private void reportError() throws HttpException, IOException
	{
		HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(InstaSearchPlugin.getErrorReportURL());
        
        postMethod.addParameter("subj", message);
        postMethod.addParameter("attn", "Exception");
        postMethod.addParameter("notes", getStatusContent(status));
        postMethod.addParameter("visitor", "InstaSearch Plugin " + InstaSearchPlugin.getVersion());
        
        httpClient.executeMethod(postMethod);
        
        postMethod.releaseConnection();
	}
	
	private String getStatusContent(IStatus status) {
		
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		
		addProperty(printWriter, "version", InstaSearchPlugin.getVersion());
		
		addSystemProperty(printWriter, "osgi.framework.version");
		addSystemProperty(printWriter, "eclipse.buildId");
		addSystemProperty(printWriter, "java.version");
		
		addBundleProperty(printWriter, Constants.FRAMEWORK_VERSION);
		addBundleProperty(printWriter, Constants.FRAMEWORK_OS_NAME);
		addBundleProperty(printWriter, Constants.FRAMEWORK_OS_VERSION);
		
		addStatusContent(printWriter, status);
		
		for(IStatus st: status.getChildren())
			addStatusContent(printWriter, st);
		
		return stringWriter.toString();
	}
	
	private void addBundleProperty(PrintWriter b, String prop) {
		addProperty(b, prop, InstaSearchPlugin.getDefault().getBundle().getBundleContext().getProperty(prop));
	}
	
	private void addSystemProperty(PrintWriter b, String prop) {
		addProperty(b, prop, System.getProperty(prop));
	}
	
	private void addProperty(PrintWriter b, String prop, String val) {
		b.print(prop);
		b.print(": ");
		b.println(val);
	}
	
	private void addStatusContent(PrintWriter b, IStatus status) {
		b.print("Status: ");
		b.println(status.getMessage());
		status.getException().printStackTrace(b);
		b.println();
	}
}
