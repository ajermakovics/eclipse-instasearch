package it.unibz.instasearch.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IViewActionDelegate;

public class CollapseAllActionDelegate extends InstaSearchActionDelegate
		implements IViewActionDelegate {

	@Override
	public void run(IAction action) {
		view.collapseAll();
	}

}
