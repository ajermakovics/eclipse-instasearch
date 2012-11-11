package it.unibz.instasearch.prefs;

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

public class PreferenceChangeDelegate implements IPropertyChangeListener
{
	private final PreferenceChangeListener listener;
	
	public PreferenceChangeDelegate(final PreferenceChangeListener listener)
	{
		this.listener = listener;
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt)
	{
		listener.preferenceChange(new PreferenceChangeEvent(null, evt.getProperty(), evt.getNewValue().toString()));
	}

}
