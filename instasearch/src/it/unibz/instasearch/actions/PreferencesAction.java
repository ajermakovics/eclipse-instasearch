package it.unibz.instasearch.actions;

import it.unibz.instasearch.prefs.InstaSearchPreferencePage;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.PreferencesUtil;

public class PreferencesAction extends InstaSearchActionDelegate
{

	@Override
	public void run(IAction action)
	{
		Shell shell = view.getViewSite().getShell();
		PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(shell, InstaSearchPreferencePage.ID, null, null);
		dialog.open();
	}

}
