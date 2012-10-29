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

/**
 * Constant definitions for plug-in preferences
 */
public class PreferenceConstants 
{
	// Indexing
	public static final String P_INDEX_ARCHIVES = "indexArchives";
	public static final String P_INDEXABLE_EXTENSIONS = "indexableExtensions";
	public static final String P_INDEX_EMPTY_EXTENSION = "indexEmptyExtension";
	public static final String P_EXCLUDE_DIRS = "excludedFolders";
	
	// Searching
	public static final String P_SEARCH_EXTENSIONS = "searchExtensions";
	public static final String P_FUZZY_SEARCH_AUTO = "fuzzySearchAuto";
	public static final String P_INCREMENTAL_SEARCH = "incrementalSearch";
	
	// Appearance
	public static final String P_SHOW_MATCH_COUNT = "showMatchCounts";
	public static final String P_SHOW_FULL_PATH = "showFullPath";
	
	public static final String P_SHOWN_FILES_COUNT = "shownFilesCount";
	public static final String P_SHOWN_LINES_COUNT = "shownLineCount";
	public static final String P_DIALOG_ON_SHORTCUT = "dialogOnShortcut";
	
	// General
	public static final String P_CHECK_UPDATES = "checkUpdates";
	
	// Internal 
	public static final String P_UPDATE_CHECK_DELAY = "updateCheckDelay";
	public static final String P_TYPING_SEARCH_DELAY = "typingSearchDelay";
	public static final String P_INDEX_UPDATE_INTERVAL = "indexUpdateInterval";
	public static final String P_INDEX_UPDATE_ENABLED = "indexUpdateEnabled";
}
