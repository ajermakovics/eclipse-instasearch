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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.IControlContentAdapter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE.SharedImages;

/**
 * 
 */
public class SearchViewControl implements DisposeListener {

	private static final String PROPOSAL_KEYSTROKE = "Ctrl+Space";
	private static final String SEARCH_TEXT_TIP = "search text " + Field.PROJ + ":MyProject " +  Field.EXT + ":xml,java";
	
	private TreeViewer resultViewer;
	private StyledText searchText;
	private ImageRegistry imageRegistry = new ImageRegistry();
	private ToolBar filterBar;
	private SearchContentProposalProvider proposalProvider;
	private Composite parent;
	private int itemInsertIndex = 0;
	private ModifyListener modifyListener; //TODO: new interface
	
	private Color searchTipColor =  getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
	private Color defaultSearchColor =  getDisplay().getSystemColor(SWT.COLOR_BLACK);
	/**
	 * @param parent
	 */
	public SearchViewControl(Composite parent, ModifyListener modifyListener) {
		
		this.parent = parent;
		this.modifyListener = modifyListener;
		
		createControl(parent);
		parent.addDisposeListener(this);
	}

	/**
	 * @return the searchText
	 */
	public StyledText getSearchText() {
		return searchText;
	}
	
	/**
	 * @return the resultViewer
	 */
	public TreeViewer getResultViewer() {
		return resultViewer;
	}
	
	/**
	 * @param parent
	 */
	private void createControl(Composite parent) {
						
		GridLayout parentLayout = new GridLayout();
		//parentLayout.marginTop = 2;
		parentLayout.marginLeft = 0;
		parentLayout.horizontalSpacing = 0;
		parentLayout.verticalSpacing = 0;
		parentLayout.marginWidth = 0;
		parentLayout.marginHeight = 2;
		parent.setLayout( parentLayout );
		
		Composite textComposite = new Composite(parent, SWT.NONE);
		GridData textCompositeGridData = new GridData(GridData.FILL, GridData.BEGINNING, true, false);
		textComposite.setLayoutData( textCompositeGridData );
		
		GridLayout layout = new GridLayout(2, false);
		layout.marginLeft = 2;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		textComposite.setLayout( layout );
								
		createSearchTextControl( textComposite );
		createFilterBar(textComposite);
		
		Composite viewComposite = new Composite(parent, SWT.NONE);
		viewComposite.setLayoutData( new GridData(GridData.FILL, GridData.FILL, true, true) );
		FillLayout fillLayout = new FillLayout();
		fillLayout.marginHeight = 0;
		fillLayout.marginWidth = 0;
		fillLayout.spacing = 0;
		viewComposite.setLayout( fillLayout );
		
		resultViewer = new TreeViewer(viewComposite, SWT.FULL_SELECTION);
	}

	private void createFilterBar(Composite parent) {
		
		filterBar = new ToolBar(parent, SWT.FLAT | SWT.RIGHT);
		GridData gridData = new GridData(GridData.END, GridData.CENTER, false, false);
		filterBar.setLayoutData(gridData);
		
		ToolItem clearItem = new ToolItem(filterBar, SWT.NONE);
		clearItem.setImage( getPluginImage("close") );
		clearItem.setToolTipText("Clear search text");
		clearItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				searchText.setText("");
				searchText.setFocus();
			}
		});
		
		new ToolItem(filterBar, SWT.SEPARATOR);
		itemInsertIndex = filterBar.getItemCount(); // so we insert after separator
		
		ToolItem filterItem = new ToolItem(filterBar, SWT.DROP_DOWN);
		filterItem.setToolTipText("Filter by...");
		filterItem.setImage( getPluginImage("filter") );
		
		DropdownMenuProvider<FilterChangeAction> dropdown = new DropdownMenuProvider<FilterChangeAction>(filterItem);
		dropdown.add( new ProjFilterAction(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(SharedImages.IMG_OBJ_PROJECT)) );
		dropdown.add( new ExtensionFilterAction(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_FILE)) );
		dropdown.add( new FieldFilterAction(Field.WS, InstaSearchPlugin.getImageDescriptor("workingsets")) );
		dropdown.add( new FieldFilterAction(Field.MODIFIED, InstaSearchPlugin.getImageDescriptor("dates")) );
		
		filterBar.update();
	}
	
	private abstract class FilterChangeAction extends Action
	{
		public FilterChangeAction(String proposal, ImageDescriptor imageDescriptor) {
			super(proposal, imageDescriptor);
		}
	
		public FilterChangeAction(String string) {
			super(string);
		}
	
		@Override
		final public void run() {
			runAction();
			updateSearch();
		}
		
		abstract protected void runAction();
	}

	private class FieldFilterAction extends FilterChangeAction 
	{
		private Field field;

		public FieldFilterAction(Field field, ImageDescriptor img) {
			super(field.getTitle(), img);
			
			this.field = field;
		}
		
		protected Image getImage(String proposal)
		{
			String key = field + ":" + proposal;
			Image img = imageRegistry.get(key);
			
			if( img == null ) img = imageRegistry.get(field + ":");
			
			if( img == null )
			{
				imageRegistry.put(key, getImageDescriptor());
				img = imageRegistry.get(key);
			}
			
			return img;
		}
		
		@Override
		public void runAction() {
			
			final ToolItem fieldItem = new ToolItem(filterBar, SWT.DROP_DOWN, itemInsertIndex);
			fieldItem.setToolTipText("Filter by " + field.getTitle());
			fieldItem.setImage( getImage("") );
			fieldItem.setData(field);
			
			DropdownMenuProvider<FilterChangeAction> dropdown = new DropdownMenuProvider<FilterChangeAction>(fieldItem);
			addRemoveAction(fieldItem, dropdown);
			dropdown.addSeparator();
			
			List<String> proposals = proposalProvider.getFieldProposals(field, "");
			FilterChangeAction firstAction = null;
			
			for(String proposal: proposals)
			{
				FilterChangeAction action = addAction(dropdown, proposal);
				
				if( firstAction == null ) firstAction = action;
			}
			
			if( "".equals(fieldItem.getText()) && firstAction != null )
				firstAction.run();
			
			parent.layout(true, true);
		}

		private void addRemoveAction(final ToolItem fieldItem,
				DropdownMenuProvider<FilterChangeAction> dropdown) 
		{
			FilterChangeAction removeAction = new FilterChangeAction("Remove") {
				@Override
				public void runAction() {
					fieldItem.dispose();
					parent.layout(true, true);
				}
			};
			removeAction.setImageDescriptor(InstaSearchPlugin.getImageDescriptor("close"));
			dropdown.add(removeAction);
		}
		
		/**
		 * @param dropdown 
		 * 
		 */
		protected FilterChangeAction addAction(final DropdownMenuProvider<FilterChangeAction> dropdown, final String proposal) 
		{
			FilterChangeAction action = new FilterChangeAction(proposal, getImageDescriptor())
			{
				@Override
				public void runAction() 
				{
					dropdown.getToolItem().setText(getText());
					dropdown.getToolItem().setImage(getImage(proposal));
					parent.layout(true, true);
				}
			};
			
			dropdown.add(action);
			return action;
		}
	}
	
	private class ProjFilterAction extends FieldFilterAction
	{
		public ProjFilterAction(ImageDescriptor img) {
			super(Field.PROJ, img);
		}
		
		@Override
		protected FilterChangeAction addAction(
				DropdownMenuProvider<FilterChangeAction> dropdown,
				String proposal) {
			
			FilterChangeAction action = super.addAction(dropdown, proposal);
			
			if( proposal.equals(InstaSearchUI.getActiveProject()) ) // active project
				action.run(); // add it
			
			return action;
		}
	}
	
	private class ExtensionFilterAction extends FieldFilterAction
	{
		public ExtensionFilterAction(ImageDescriptor img) {
			super(Field.EXT, img);
		}
		
		@Override
		protected Image getImage(String proposal) {
			
			getExtImageDescriptor(proposal);
			
			return imageRegistry.get(Field.EXT + ":" + proposal);
		}
		
		@Override
		protected FilterChangeAction addAction(DropdownMenuProvider<FilterChangeAction> dropdown, String proposal) {
			FilterChangeAction action = super.addAction(dropdown, proposal); 
			ImageDescriptor imgDesc = getExtImageDescriptor(proposal);
	
			if( imgDesc != null )
				action.setImageDescriptor(imgDesc);
			
			IEditorPart editor = InstaSearchUI.getActiveEditor();
			
			if( editor != null )
			{
				IAdaptable adaptable = editor.getEditorInput();
				
				if( adaptable != null ) 
				{
					// get current edited file
					IResource res = (IResource) adaptable.getAdapter(IResource.class);
					
					if( res != null && proposal.equals(res.getFileExtension())) 
						action.run(); // set as active extension
				}
			}
			
			return action;
		}
	}
	
	private void updateSearch()
	{
		modifyListener.modifyText(null); // notify view
	}

	private void createSearchTextControl(Composite textComposite) {
		searchText = new StyledText(textComposite, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.CANCEL);
		
		textComposite.setTabList(new Control[]{searchText});
		
		GridData searchTextGridData = new GridData(GridData.FILL, GridData.CENTER, true, false);
		searchText.setLayoutData( searchTextGridData );

		FocusListener focusListener = new FocusListener() { // to set search tip
			
			public void focusGained(FocusEvent e) 
			{
				if (searchText.isDisposed()) 
					return;

				if( isShowingSearchTip() )
				{
					searchText.setForeground( defaultSearchColor );
					
					if( SEARCH_TEXT_TIP.equals(searchText.getText()) )
						searchText.setText("");
				}
			}
			
			public void focusLost(FocusEvent e) 
			{
				if( searchText.getText().length() == 0 )
				{
					searchText.setForeground( searchTipColor );
					searchText.setText(SEARCH_TEXT_TIP);
				}
			}
		};
		
		searchText.addFocusListener(focusListener);
		
		searchText.setToolTipText(SEARCH_TEXT_TIP + "\n(" + PROPOSAL_KEYSTROKE + " for autocompletion)");
	}
	
	public boolean isShowingSearchTip()
	{
		return searchText.getForeground().equals( searchTipColor );
	}
	
	/**
	 * @return display
	 * 
	 */
	private Display getDisplay() {
		return Display.getCurrent();
	}
	
	public void setContentProposalAdapter(SearchContentProposalProvider provider)
	{
		this.proposalProvider = provider;
		KeyStroke keyStroke = null;
		
		try {
			keyStroke = KeyStroke.getInstance(PROPOSAL_KEYSTROKE);
		} catch(Exception e) { 
			InstaSearchPlugin.log(e);
		}
		
		char[] autoActivationCharacters = new char[]{':', ','};
		
		ContentProposalAdapter adapter = new SearchContentProposalAdapter(
				searchText, new StyledTextContentAdapter(),
				provider, keyStroke, autoActivationCharacters );
		
		adapter.setPopupSize(new Point(300, 150));
		adapter.setPropagateKeys(true);
		adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
	}
	
	private Image getPluginImage(String name)
	{
		Image img = imageRegistry.get(name);
		if( img == null )
		{
			imageRegistry.put(name, InstaSearchPlugin.getImageDescriptor(name));
			img = imageRegistry.get(name);
		}
		
		return img;
	}
	
	private ImageDescriptor getExtImageDescriptor(String ext) 
	{
		String key = Field.EXT + ":" + ext;
		ImageDescriptor fileImage = imageRegistry.getDescriptor(key);
		
		if( fileImage == null ) {
			IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
			fileImage = registry.getImageDescriptor("*." + ext);
			imageRegistry.put(key, fileImage); // cache created images for extensions
		}
		
		return fileImage;
	}
	
	public void widgetDisposed(DisposeEvent e) {
		imageRegistry.dispose();
	}
	
	private class SearchContentProposalAdapter extends ContentProposalAdapter 
	{
		/**
		 * @param control
		 * @param controlContentAdapter
		 * @param proposalProvider
		 * @param keyStroke
		 * @param autoActivationCharacters
		 */
		public SearchContentProposalAdapter(Control control,
				IControlContentAdapter controlContentAdapter,
				IContentProposalProvider proposalProvider, KeyStroke keyStroke,
				char[] autoActivationCharacters) 
		{
			super(control, controlContentAdapter, proposalProvider, keyStroke,
					autoActivationCharacters);
		}
		
		@Override
		protected void closeProposalPopup() { // workaround to keep popup open
			String content = getControlContentAdapter().getControlContents(getControl());
			int pos = getControlContentAdapter().getCursorPosition(getControl());
			
			if( content.length() == 0 || pos == 0 ) {
				super.closeProposalPopup();
				return;
			}
			
			String prefix = content.substring(0, pos);
			if( prefix.length() == 0 || prefix.endsWith(" ") ) {
				super.closeProposalPopup();
				return;
			}
			
		}
		
		@Override
		public int getProposalAcceptanceStyle() { // workaround to have the popup at insert positon
			return ContentProposalAdapter.PROPOSAL_INSERT;
		}
	}

	public Map<Field, Set<String>> getFilter() 
	{
		HashMap<Field, Set<String>> filters = new HashMap<Field, Set<String>>();
		
		for(ToolItem item: filterBar.getItems())
		{
			Object data = item.getData();
			if( data== null ) continue;
			if( data instanceof Field )
			{
				Field field = (Field) data;
				String value = item.getText();
				
				if( "".equals(value) ) continue;
				
				Set<String> values = filters.get(field);
				if( values == null ) {
					values = new TreeSet<String>();
					filters.put(field, values);
				}
				values.add(value);
			}
		}
		
		return filters;
	}

	/**
	 * @param filter
	 */
	public void setFilter(Map<Field, Set<String>> filter) 
	{
		clearFilter();
		
		
	}
	
	private void clearFilter()
	{
		for(ToolItem item: filterBar.getItems())
		{
			Object data = item.getData();
			if( data== null ) continue;
			if( data instanceof Field )
				item.dispose();
		}
	}
}
