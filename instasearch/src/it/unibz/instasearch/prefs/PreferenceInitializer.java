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

import java.util.Arrays;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IFileEditorMapping;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.registry.EditorRegistry;

/**
 * Class used to initialize default preference values.
 */
@SuppressWarnings("restriction")
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	private static final String DEFAULT_EXTENSIONS = "java,xml,xsd,txt,jsp,css,c,cpp,h,hpp,mf,properties,php,php4,php5,js,inc,ini,sql,sqlj,pl,sh,bat,cmd,htm,html";
	
	/**
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() 
	{
		IPreferenceStore store = InstaSearchPlugin.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.P_CHECK_UPDATES, true);
		store.setDefault(PreferenceConstants.P_SHOW_MATCH_COUNT, true);
		store.setDefault(PreferenceConstants.P_INDEX_ARCHIVES, false);
		store.setDefault(PreferenceConstants.P_INDEX_EMPTY_EXTENSION, false);
		store.setDefault(PreferenceConstants.P_FUZZY_SEARCH_AUTO, true);
		store.setDefault(PreferenceConstants.P_SHOW_FULL_PATH, true);
		store.setDefault(PreferenceConstants.P_DIALOG_ON_SHORTCUT, false);
		store.setDefault(PreferenceConstants.P_INCREMENTAL_SEARCH, true);
		
		store.setDefault(PreferenceConstants.P_SHOWN_FILES_COUNT, 25);
		store.setDefault(PreferenceConstants.P_SHOWN_LINES_COUNT, 4);
		
		store.setDefault(PreferenceConstants.P_UPDATE_CHECK_DELAY, 10000);
		store.setDefault(PreferenceConstants.P_TYPING_SEARCH_DELAY, 200);
		store.setDefault(PreferenceConstants.P_INDEX_UPDATE_INTERVAL, 60000);
		store.setDefault(PreferenceConstants.P_INDEX_UPDATE_ENABLED, true);
		
		String extensions = getIndexableExtensions();
		store.setDefault(PreferenceConstants.P_INDEXABLE_EXTENSIONS, extensions);
		store.setDefault(PreferenceConstants.P_SEARCH_EXTENSIONS, "");
		store.setDefault(PreferenceConstants.P_EXCLUDE_DIRS, "");
	}

	/**
	 * Get extensions that Eclipse knows of and the default ones
	 * @return comma separated string of extensions
	 */
	public static String getIndexableExtensions()
	{
		String defaultExtArray[] = DEFAULT_EXTENSIONS.split(",");
		
		TreeSet<String> extensions = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		extensions.addAll(Arrays.asList(defaultExtArray));
		
		IFileEditorMapping[] allMappings = ((EditorRegistry)PlatformUI.getWorkbench()
                .getEditorRegistry()).getUnifiedMappings();
		
		IContentType text = Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
		
        for (int i = 0; i < allMappings.length; i++) {
            if (allMappings[i].getName().equals("*")) {
            	String ext = allMappings[i].getExtension();
            	IContentType type = Platform.getContentTypeManager().findContentTypeFor("." + ext);
            	
            	if( type != null && type.isKindOf(text))
            		extensions.add( ext );
            }
        }
        
        IContentType[] types = Platform.getContentTypeManager().getAllContentTypes();
        for(IContentType type: types) {
        	if( type.isKindOf(text)) {
        		String exts[] = type.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
        		extensions.addAll(Arrays.asList(exts));
        	}
        }
        
        return StringUtils.join(extensions.toArray(), ",");
	}

}
