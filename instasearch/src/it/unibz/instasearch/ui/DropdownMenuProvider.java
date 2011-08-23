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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolItem;

/**
 * This class provides the "drop down" functionality for our dropdown tool items.
 */
class DropdownMenuProvider<T extends Action> extends SelectionAdapter 
{
	private MenuManager menuManager;
	private ToolItem toolItem;

	/**
	 * Constructs a DropdownMenuProvider
	 * 
	 * @param toolItem the dropdown this listener belongs to
	 */
	public DropdownMenuProvider(ToolItem toolItem) {

		toolItem.addSelectionListener(this);
		this.menuManager = new MenuManager();
		this.toolItem = toolItem;
	}
	
	/**
	 * @return the toolItem
	 */
	public ToolItem getToolItem() {
		return toolItem;
	}
		
	public T add(T action)
	{
		menuManager.add(action);
		
		return action;
	}
	
	public void addSeparator()
	{
		menuManager.add(new Separator());
	}
		
	/**
	 * Called when either the button itself or the dropdown arrow is clicked
	 * 
	 * @param event the event that trigged this call
	 */
	@Override
	public void widgetSelected(SelectionEvent event) {
		ToolItem item = (ToolItem) event.widget;
		
		if( item != toolItem ) 
			return;
		
		Rectangle rect = item.getBounds();
		Point pt = item.getParent().toDisplay(new Point(rect.x, rect.y));
		Menu menu = menuManager.createContextMenu(item.getParent());
		menu.setLocation(pt.x, pt.y + rect.height);
		menu.setVisible(true);
		
		if (event.detail != SWT.ARROW) {
			// They pushed the button; take appropriate action
			//System.out.println("button pushed");
		}
	}
}