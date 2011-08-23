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
import it.unibz.instasearch.indexing.Field;
import it.unibz.instasearch.indexing.WorkspaceIndexerJDT;
import it.unibz.instasearch.indexing.SearchQuery;
import it.unibz.instasearch.prefs.PreferenceConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.search.ui.ISearchPage;
import org.eclipse.search.ui.ISearchPageContainer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.dialogs.TypeFilteringDialog;

public class InstaSearchPage extends DialogPage implements ISearchPage
{
	public static final String ID = "it.unibz.instasearch.ui.InstaSearchPage";
	/**
	 * 
	 */
	private ISearchPageContainer container;
	private Text searchText;
	private Button similarSearch;
	private StringButtonFieldEditor extensionEditor;
	//private Button jarSearch;

	public boolean performAction()
	{
		HashMap<Field, Set<String>> filter = new HashMap<Field, Set<String>>();
		
		if( container.getSelectedScope() == ISearchPageContainer.SELECTED_PROJECTS_SCOPE 
				&& container.getSelectedProjectNames().length != 0 ) // projects
		{
			TreeSet<String> selectedProjects = new TreeSet<String>();
			Collections.addAll(selectedProjects, container.getSelectedProjectNames());
			filter.put(Field.PROJ, selectedProjects);
			
		} else if( container.getSelectedScope() == ISearchPageContainer.WORKING_SET_SCOPE 
				&& container.getSelectedWorkingSets().length != 0 ) { // working sets
			
			filter.put(Field.WS, getWorkingSetSearchString());
			
		} else if( container.getSelectedScope() == ISearchPageContainer.SELECTION_SCOPE 
				&& !container.getSelection().isEmpty() )
		{
			getSelectedResources(filter);
		}
		
		filter.put(Field.EXT, getSelectedExtensions());
		extensionEditor.store();
		
		String searchString = searchText.getText();
		
		InstaSearchView searchView = InstaSearchUI.showSearchView();
		
		if( searchView != null ) {
			int maxResults = SearchQuery.UNLIMITED_RESULTS;
			searchString += convertFilterToString(filter);
			SearchQuery searchQuery = new SearchQuery(searchString, maxResults);
			searchQuery.setExact( similarSearch.getSelection() );
			searchQuery.setFilter(null); // put filters in search string instead
			
			searchView.setSearchString(searchString); // to display
			searchView.search(searchQuery, false);
		}
		
		return true;
	}

	private void getSelectedResources(HashMap<Field, Set<String>> filter) 
	{	
		if( container.getSelection() instanceof IStructuredSelection ) {
			
			IStructuredSelection sel = (IStructuredSelection) container.getSelection();
			
			for(Object elem: sel.toArray()) 
			{
				if( elem instanceof IAdaptable ) {
					
					IResource res = null;
					
					if( elem instanceof IResource)
						res = (IResource) elem;
					else {
						IAdaptable adaptable = (IAdaptable) elem;
						res = (IResource) adaptable.getAdapter(IResource.class);
					}
					
					if( res == null ) continue;
					
					switch(res.getType())
					{
					case IResource.PROJECT:
						Set<String> projects = filter.containsKey(Field.PROJ)?filter.get(Field.PROJ):new TreeSet<String>();
						projects.add( ((IProject)res).getName() );
						filter.put(Field.PROJ, projects);
						break;
					case IResource.FILE:
						Set<String> files = filter.containsKey(Field.FILE)?filter.get(Field.FILE):new TreeSet<String>();
						files.add( ((IFile)res).getFullPath().toString() );
						filter.put(Field.FILE, files);
						break;
					case IResource.FOLDER:
						Set<String> folders = filter.containsKey(Field.DIR)?filter.get(Field.DIR):new TreeSet<String>();
						folders.add( res.getFullPath().toString() );
						filter.put(Field.DIR, folders);
						break;
					}
					
				}
			}
		}
	}

	/**
	 * @param filter
	 * @return
	 */
	private static String convertFilterToString(HashMap<Field, Set<String>> filter) {
		
		String filterString = "";
		
		for(Entry<Field, Set<String>> entry: filter.entrySet()) {
			
			String fieldFilterString = getFieldFilterString(entry.getKey(), entry.getValue());
			
			if( !"".equals(fieldFilterString) )
				filterString += " " + fieldFilterString;
		}
		
		return filterString;
	}

	private Set<String> getSelectedExtensions() {
		
		TreeSet<String> extSet = new TreeSet<String>();
		
		String exts = extensionEditor.getStringValue();
		
		if( exts != null && ! "".equals(exts) && ! "*".equals(exts)) {
			exts = exts.replace(" ", "");
			exts = exts.replace("*.", "");
			Collections.addAll(extSet, exts.split(","));
		}
		
		return extSet;
	}

	private Set<String> getWorkingSetSearchString() {
		
		Set<String> wsNames = new TreeSet<String>();
		
		for(IWorkingSet ws: container.getSelectedWorkingSets()) {
			String workingSet = ws.getName();
			wsNames.add(workingSet);
		}
		
		return wsNames;
	}
	
	private static String getFieldFilterString(Field field, Set<String> values)
	{
		String filterString = null;
	
		if( values.size() == 0 )
			return "";
		
		for (String val : values) {
			if( filterString == null ) filterString = "";
			else filterString += ",";
			filterString += val;
		}
		
		if( filterString.contains(" ") )
			filterString = '"' + filterString + '"';
		
		return field.toString() + ':' + filterString;
	}
	
	
	
	public void setContainer(ISearchPageContainer container)
	{
		this.container = container;
	}

	public void createControl(Composite parent)
	{
		Composite pageComposite = new Composite(parent, SWT.FILL);
		
		GridLayout pageLayout = new GridLayout();
		pageLayout.numColumns = 1;
		
		pageComposite.setLayout(pageLayout);
		pageComposite.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true,false,1,1));
		
		Label label = new Label(pageComposite, SWT.LEFT);
		label.setText("Search:");
		label.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false,false,1,1));
		
		searchText = new Text(pageComposite, SWT.SINGLE | SWT.BORDER | SWT.FILL);
		searchText.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true,false,1,1));

		if (container.getSelection() instanceof TextSelection) {
			String selection = ((TextSelection) container.getSelection()).getText();
			searchText.setText(selection);
		}
		
		createShowViewLink(pageComposite);
		
		similarSearch = new Button(pageComposite, SWT.CHECK);
		similarSearch.setText("Find similar matches");
		similarSearch.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true,false,1,1));
		
		if( InstaSearchPlugin.getInstaSearch() != null && InstaSearchPlugin.getInstaSearch().getIndexer() instanceof WorkspaceIndexerJDT )
		{
//			jarSearch = new Button(pageComposite, SWT.CHECK);
//			jarSearch.setText("Exclude JAR files");
//			jarSearch.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true,false,1,1));
		}
		
		createExtensionEditor(pageComposite);
		
		
		setControl( searchText );
	}

	private void createExtensionEditor(Composite pageComposite) {
		
		GridLayout pageLayout = new GridLayout();
		pageLayout.numColumns = 1;
		pageLayout.marginWidth = 1;
		
		Composite labelComposite = new Composite(pageComposite, SWT.FILL);
		labelComposite.setLayout(pageLayout);
		Label label = new Label(labelComposite, SWT.NONE );
		label.setText("File types:");
		labelComposite.setLayoutData(new GridData(GridData.BEGINNING,GridData.CENTER,true,false,1,1));
		
		Composite extensionsComposite = new Composite(pageComposite, SWT.FILL);
		extensionsComposite.setLayout(pageLayout);
		extensionsComposite.setLayoutData(new GridData(GridData.FILL,GridData.CENTER,true,false,2,1));
		
		extensionEditor = new StringButtonFieldEditor(PreferenceConstants.P_SEARCH_EXTENSIONS, "", extensionsComposite) {
			protected String changePressed() {
				String exts = this.getStringValue();
				exts = exts.replace(" ", "");
				
				List<String> extList = Arrays.asList(exts.split(","));
				TypeFilteringDialog dialog = new TypeFilteringDialog(getShell(), extList);
		        dialog.open();
		        
		        Object[] newSelectedTypes = dialog.getResult();
		        
		        return StringUtils.join(newSelectedTypes, ",");
		     }
			
		};
		extensionEditor.setChangeButtonText("Choose...");
		extensionEditor.setEmptyStringAllowed(true);
		extensionEditor.setPreferenceStore(InstaSearchPlugin.getDefault().getPreferenceStore());
		extensionEditor.load();
		
		Text txt = extensionEditor.getTextControl(extensionsComposite);
		txt.setToolTipText("E.g:\njava, xml");
	}

	private void createShowViewLink(Composite pageComposite) 
	{	
		Composite linkComposite = new Composite(pageComposite, SWT.FILL);
		GridLayout pageLayout = new GridLayout();
		pageLayout.numColumns = 2;
		pageLayout.marginWidth = 1;
		
		linkComposite.setLayout(pageLayout);
		linkComposite.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false,1,1));
		
		Link openViewLink = new Link(linkComposite, SWT.NONE);
		openViewLink.setText("You can search directly from the <a>search view</a>");
		openViewLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				InstaSearchUI.showSearchView();
			}
		});
		openViewLink.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false,false,1,1));
	}

	@Override
	public void setVisible(boolean visible)
	{
		super.setVisible(visible);
		searchText.setFocus();
		container.setPerformActionEnabled(true);
	}
	
	
}
