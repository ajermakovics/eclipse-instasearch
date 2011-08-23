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
package it.unibz.instasearch.prefs;

import it.unibz.instasearch.InstaSearchPlugin;
import it.unibz.instasearch.indexing.WorkspaceIndexer;
import it.unibz.instasearch.indexing.WorkspaceIndexerJDT;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.PathEditor;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.dialogs.TypeFilteringDialog;

public class InstaSearchPreferencePage
	extends FieldEditorPreferencePage
	implements IWorkbenchPreferencePage {

	public static final String ID = InstaSearchPreferencePage.class.getName();
	
	public InstaSearchPreferencePage() {
		super(GRID);
		setPreferenceStore(InstaSearchPlugin.getDefault().getPreferenceStore());
		setDescription("");
	}
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
	@Override
	public void createFieldEditors() {
		
		String[] fileValues = new String[]{"5", "10", "15", "25", "50"};
		addField(new ComboFieldEditor(PreferenceConstants.P_SHOWN_FILES_COUNT, "Shown results (initially)", getArrayOfPairs(fileValues), getFieldEditorParent()));
		
		String[] lineValues = new String[]{"3", "4", "5", "7", "10", "15", "25"};
		addField(new ComboFieldEditor(PreferenceConstants.P_SHOWN_LINES_COUNT, "Preview lines", getArrayOfPairs(lineValues), getFieldEditorParent()));
		
		addBoolField(PreferenceConstants.P_SHOW_MATCH_COUNT, "Show number of matches");
		
		addBoolField(PreferenceConstants.P_SHOW_FULL_PATH, "Show full file path in results");
		
		addBoolField(PreferenceConstants.P_DIALOG_ON_SHORTCUT, "Open search dialog with shortcut key (Ctrl+Alt+I)");
		
		StringButtonFieldEditor extensions = new ExtensionsFieldEditor(getFieldEditorParent());	
		extensions.setChangeButtonText("...");
		extensions.setEmptyStringAllowed(true);
		addField(extensions);
		
		addBoolField(PreferenceConstants.P_INDEX_EMPTY_EXTENSION, "Index files without extension");
		
		BooleanFieldEditor indexJars = new BooleanFieldEditor(PreferenceConstants.P_INDEX_ARCHIVES, "Index JAR Source Attachements (beta)", getFieldEditorParent());
		addField(indexJars);
		indexJars.setEnabled(false, getFieldEditorParent());
		
		if( InstaSearchPlugin.getInstaSearch() != null && InstaSearchPlugin.getInstaSearch().getIndexer() instanceof WorkspaceIndexerJDT )
			indexJars.setEnabled(true, getFieldEditorParent());
		
		addBoolField(PreferenceConstants.P_FUZZY_SEARCH_AUTO, "Find similar matches when no exact matches found");
		
		String version = InstaSearchPlugin.getVersion();
		addBoolField(PreferenceConstants.P_CHECK_UPDATES, "Notify about updates (ver. " + version + ")");
		
		addField(new WorkspacePathEditor(PreferenceConstants.P_EXCLUDE_DIRS, "Exclude folders from index", "Select folder to exclude from indexing", getFieldEditorParent()));
		//new Label(getFieldEditorParent(), SWT.NONE).setText("Note: Folders with Derived flag are excluded");
		
		Link annotationsLink = new Link(getFieldEditorParent(), SWT.NONE);
		annotationsLink.setText("See <a>Annotations</a> to set Search Results highlight color");
		annotationsLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e)
			{
				 PreferencesUtil.createPreferenceDialogOn(getShell(), "org.eclipse.ui.editors.preferencePages.Annotations", null, null).open();
			}
		});
	}

	@Override
	public boolean performOk() {
		return super.performOk();
	}
	
	@Override
	protected Control createContents(Composite parent) {
		Control contents = super.createContents(parent);
		
		return contents;
	}
	
	/**
	 * 
	 */
	private void addBoolField(String id, String label)
	{
		addField(new BooleanFieldEditor(id, label, getFieldEditorParent()));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent event)
	{
		super.propertyChange(event);
		
		if( event.getSource() instanceof FieldEditor ) 
		{
			FieldEditor field = (FieldEditor)event.getSource();
			
			if( PreferenceConstants.P_INDEXABLE_EXTENSIONS.equals(field.getPreferenceName()) 
					|| PreferenceConstants.P_EXCLUDE_DIRS.equals(field.getPreferenceName()) 
					|| PreferenceConstants.P_INDEX_EMPTY_EXTENSION.equals(field.getPreferenceName()))
				setMessage("Rebuilding of index is recommended", INFORMATION);
			else if( PreferenceConstants.P_INDEX_ARCHIVES.equals(field.getPreferenceName()) )
				setMessage("Rebuilding of index is required", INFORMATION);
		}
	}
	
	@Override
	protected void checkState()
	{
		super.checkState();
		
		setValid(true);	
	}
	
	private static String[][] getArrayOfPairs(String[] values)
	{
		String[][] pairs = new String[values.length][2];
		for(int i = 0; i < values.length; i++) {
			String val = values[i];
			pairs[i] = new String[]{val, val};
		}
		return pairs;
	}
	
	private static class WorkspacePathEditor extends PathEditor
	{
		public WorkspacePathEditor(String name, String labelText, String dirChooserLabelText, Composite parent) 
		{
			super(name, labelText, dirChooserLabelText, parent);
		}

		@Override
		protected String getNewInputObject() 
		{
			FilteredResourcesPatternDialog dlg = new FilteredResourcesPatternDialog(getShell());
			dlg.setTitle("Exclude folders");
			dlg.setMessage("Exclude folders matching pattern (? = any character, * = any string, ** = any subfolder)");
			dlg.setInitialPattern("**/bin");
			dlg.setHelpAvailable(false);
			dlg.open();
			
			if( dlg.getReturnCode() == Window.OK )
				return dlg.getLastPattern();
			
			return null;
		};
	}
	
	private static class FilteredResourcesPatternDialog extends FilteredResourcesSelectionDialog
	{
		private String lastPattern = null;
		private Pattern regexPattern;
		
		/**
		 */
		public FilteredResourcesPatternDialog(Shell shell)
		{
			super(shell, false, ResourcesPlugin.getWorkspace().getRoot(), IResource.FOLDER | IResource.PROJECT );
		}
		
		@Override
		protected ItemsFilter createFilter()
		{
			Text patternText = (Text) getPatternControl();
			String wcPattern = patternText.getText();
			if( wcPattern.startsWith("/") ) wcPattern = wcPattern.substring(1);
			
			try
			{
				this.regexPattern = WorkspaceIndexer.wildcardToRegex(wcPattern);
				
			} catch(Exception t)
			{
				InstaSearchPlugin.log(t);
				return null; // invalid pattern, no filter
			}
			
			lastPattern = wcPattern;
			
			ResourceFilter filter = new ResourceFilter()
			{
				@Override
				public boolean isSubFilter(ItemsFilter filter)
				{
					return false;
				}
				
				@Override
				public boolean matchItem(Object item)
				{
					if( !(item instanceof IResource) )
						return false;
					
					IResource resource = (IResource) item;
					
					if( resource.getType() != IResource.FOLDER 
							&& resource.getType() != IResource.PROJECT )
						return false;
					
					if( resource.isDerived() )
						return false;
					
					String path = resource.getFullPath().toString();
					if( path.startsWith("/") ) path = path.substring(1);
					
					boolean matches = false;
					
					try 
					{
						matches = regexPattern.matcher(path).matches();
					} catch(Exception t)
					{
						InstaSearchPlugin.log(t);
					}
					
					return matches;
				}
				
				@Override
				public boolean equalsFilter(ItemsFilter iFilter)
				{
					return false;
				}
			};
			
			return filter;
		}
		
		/**
		 * @return the lastPattern
		 */
		public String getLastPattern()
		{
			return lastPattern;
		}
	}
	
	private static class ExtensionsFieldEditor extends StringButtonFieldEditor {
		
		public ExtensionsFieldEditor(Composite parent) {
			super(PreferenceConstants.P_INDEXABLE_EXTENSIONS, "Extensions of files to index", parent);
		}
		
		@Override
		protected String changePressed() {
			String exts = this.getStringValue();
			
			TypeFilteringDialog dialog = new TypeFilteringDialog(getPage().getShell(), 
					Arrays.asList(exts.split(",")));
			
	        dialog.open();
	        
	        Object[] newSelectedTypes = dialog.getResult();
	        if( newSelectedTypes == null )
	        	return null;
	        
	        Arrays.sort(newSelectedTypes);
	        
	        return StringUtils.join(newSelectedTypes, ",");
		}
		
		@Override
		protected void doFillIntoGrid(Composite parent, int numColumns) {
			super.doFillIntoGrid(parent, numColumns);
			Text txt = getTextControl();
			GridData gd = (GridData) txt.getLayoutData();
			if( gd != null )
				gd.widthHint = 250;
		}
	};
}