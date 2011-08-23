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

import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;

/**
 * @see org.eclipse.jface.fieldassist.TextContentAdapter
 */
public class StyledTextContentAdapter extends TextContentAdapter
{
	@Override
	public String getControlContents(Control control) {
		return ((StyledText) control).getText();
	}

	@Override
	public void setControlContents(Control control, String text,
			int cursorPosition) {
		((StyledText) control).setText(text);
		((StyledText) control).setSelection(cursorPosition, cursorPosition);
	}

	@Override
	public void insertControlContents(Control control, String text,
			int cursorPosition) {
		Point selection = ((StyledText) control).getSelection();
		((StyledText) control).insert(text);
		
		if (cursorPosition < text.length()) {
			((StyledText) control).setSelection(selection.x + cursorPosition,
					selection.x + cursorPosition);
		}
	}

	@Override
	public int getCursorPosition(Control control) {
		return ((StyledText) control).getCaretOffset();
	}

	@Override
	public Rectangle getInsertionBounds(Control control) {
		StyledText text = (StyledText) control;
		Point caretOrigin = text.getCaret().getLocation();
		
		return new Rectangle(caretOrigin.x + text.getClientArea().x,
				caretOrigin.y + text.getClientArea().y + 3, 1, text.getLineHeight());
	}

	@Override
	public void setCursorPosition(Control control, int position) {
		((StyledText) control).setSelection(new Point(position, position));
	}

	@Override
	public Point getSelection(Control control) {
		return ((StyledText) control).getSelection();
	}
	
	@Override
	public void setSelection(Control control, Point range) {
		((StyledText) control).setSelection(range);
	}
}
