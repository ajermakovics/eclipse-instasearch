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
package it.unibz.instasearch.indexing;

import it.unibz.instasearch.InstaSearchPlugin;
import it.unibz.instasearch.prefs.PreferenceConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;

/**
 * WorkspaceIndexer
 * Indexes the Eclipse workspace
 */
public class WorkspaceIndexer extends StorageIndexer implements ISchedulingRule, IPropertyChangeListener {
		
	private IContentType TEXT_CONTENT_TYPE;
	
	private static final ResourceCollector resourceCollector = new ResourceCollector();
	
	// Prefs
	private String fileExtensions[] = getIndexableFileExtensions();
	private List<Pattern> excludedDirRegExes = getExcludedDirsRegExes();
	private boolean indexEmptyExtension = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_INDEX_EMPTY_EXTENSION);
	
	/**
	 * @throws Exception
	 */
	public WorkspaceIndexer() throws Exception  
	{
    	super();
    	
    	if( Platform.getContentTypeManager() != null )
    		TEXT_CONTENT_TYPE = Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
	}
	
	@Override
	public Directory getIndexDir() throws IOException 
	{
		return FSDirectory.open(getIndexDirLocation()); // FSDirectory.getDirectory(getIndexDirLocation(), false); 
	}
	
	/**
	 * @param monitor
	 * @throws Exception
	 */
	public void createIndex(IWorkspaceRoot root, IProgressMonitor monitor) throws Exception {
		
		getIndexChangeListener().onIndexReset();
		
		deleteIndex();
		Directory indexDirectory = FSDirectory.open(getIndexDirLocation());
		
		IndexWriter indexWriter = createIndexWriter(true);
		
		indexContainers(indexWriter, root, monitor);
		
		monitor.setTaskName("Optimizing Index");
		indexWriter.optimize();
		
		indexWriter.close();
		indexDirectory.close();
		
		getIndexChangeListener().onIndexUpdate();
		
		monitor.done();
	}
	
	
	/**
	 * Index all containers in the workspace
	 * 
	 * @param indexWriter
	 * @param workspaceRoot
	 * @param monitor
	 * @throws Exception
	 */
	protected void indexContainers(IndexWriter indexWriter, IWorkspaceRoot workspaceRoot, IProgressMonitor monitor) throws Exception {
		
		resourceCollector.clear();
		resourceCollector.setExcludedDirRegExes( excludedDirRegExes );
		workspaceRoot.accept(resourceCollector); // use visitor to collect containers
		
		monitor.beginTask("File Indexing", resourceCollector.getContainers().size());
		
		for(IContainer container: resourceCollector.getContainers()) 
		{	
			if( monitor.isCanceled() ) 
				break;
			
			monitor.setTaskName("Indexing: " + container.getProject().getName() + " - " + container.getName());
			indexContainer(indexWriter, container, monitor);
			monitor.worked(1);
		}
		
		monitor.done();
	}
	
	private File getIndexDirLocation()
	{
		File location = InstaSearchPlugin.getIndexDirLocation();
		
		if( ! location.exists() )
			location.mkdirs();
		
		return location;
	}
	
	@Override
	public boolean isIndexed() throws IOException 
	{
		return IndexReader.indexExists(getIndexDir()) && super.isIndexed();
	}
	
	private static List<Pattern> getExcludedDirsRegExes() 
	{
		String excludeDirList = InstaSearchPlugin.getDefault().getPreferenceStore().getString(PreferenceConstants.P_EXCLUDE_DIRS);
		
		if( excludeDirList == null || "".equals(excludeDirList) ) 
			return Collections.emptyList();
		
		List<Pattern> excludeDirSet = new ArrayList<Pattern>();
		String[] excludedDirArr = excludeDirList.split(File.pathSeparator);
		
		for (String wildCardPattern : excludedDirArr)
		{
			Pattern pattern = null;
			if( wildCardPattern.startsWith("/") ) wildCardPattern = wildCardPattern.substring(1);
			
			try {
				pattern = wildcardToRegex(wildCardPattern);
				excludeDirSet.add(pattern);
			} catch(Throwable t) {
				InstaSearchPlugin.debug(t);
			}
		}
		
		return excludeDirSet;
	}
	
	private static String[] getIndexableFileExtensions() {
		
		String extensionList = InstaSearchPlugin.getDefault().getPreferenceStore().getString(PreferenceConstants.P_INDEXABLE_EXTENSIONS);
		if( extensionList == null || "".equals(extensionList) || "*".equals(extensionList) ) return null;
		
		extensionList = extensionList.replace(" ", "");
		
		String[] extensions = extensionList.split("[,|;:]");
		if( extensions.length == 0 ) return null;
		
		for(int i = 0; i<extensions.length; i++) {
			String ext = extensions[i].toLowerCase(Locale.ENGLISH).trim();
			if( ext.startsWith("*") ) ext = ext.substring(1);
			if( ext.startsWith(".") ) ext = ext.substring(1);
			extensions[i] = ext;
		}
		
		Arrays.sort(extensions);
		
		return extensions;
	}
	
	/**
	 * @param file
	 * @return isIndexable
	 * @throws CoreException 
	 */
	public boolean isIndexable(IFile file) throws CoreException {
		
		String ext = file.getFileExtension();
		
		return isIndexableExtension(ext) || isTextFile(file);
	}

	/**
	 * @param ext
	 * @return isIndexableExtension
	 */
	public boolean isIndexableExtension(String ext) {
		
		if( fileExtensions == null || fileExtensions.length == 0 )
			return true; // all files indexable
		
		if( ext == null || "".equals(ext) ) 
			return indexEmptyExtension;
		
		if( Arrays.binarySearch(fileExtensions, ext.toLowerCase(Locale.ENGLISH)) >= 0 )
			return true;
		
		return false;
	}
	
	private void indexFile(IndexWriter indexWriter, IFile file) throws Exception {
		
		if( ! file.isAccessible() )
			return;
		
		if( file.isDerived(IResource.CHECK_ANCESTORS) )
			return;
		
		if( ! file.isSynchronized(IResource.DEPTH_ZERO) )
			return;
		
		if( file.getRawLocation() == null ) // unknown location
			return;
		
		File f = file.getRawLocation().toFile();
		if( f == null || ! f.canRead() ) 
			return;
		
		indexStorageWithRetry(indexWriter, file, file.getProject().getName(), file.getLocalTimeStamp(), null);
	}
	
	/**
	 * @param file
	 * @throws Exception
	 */
	public void updateFile(IFile file) throws Exception {
		
		if( !isIndexed() )
			return;
		
		deleteStorage(file);
		
		if( !isIndexable(file) )
			return;
		
		if( file.isAccessible() && !file.isDerived(IResource.CHECK_ANCESTORS) ) 
		{
			IndexWriter w = createIndexWriter(false);
			indexFile(w, file);
			w.close();
		}

	}
	
	/**
	 * Deletes and re-indexes files in a folder
	 * 
	 * @param folder
	 * @param monitor 
	 * @throws Exception 
	 */
	public void updateFolder(IFolder folder, IProgressMonitor monitor) throws Exception {
		
		if( !isIndexed() )
			return;
		
		IndexReader reader = IndexReader.open(getIndexDir(), false);
		deleteFolder(reader, folder);
		reader.close();
		
		if( !folder.isAccessible() )	
			return;
		
		resourceCollector.clear();
		resourceCollector.setExcludedDirRegExes(excludedDirRegExes);
		folder.accept(resourceCollector); // get also subfolders
		
		IndexWriter w = createIndexWriter(false);
		
		for(IContainer container: resourceCollector.getContainers())
		{
			if( isExcluded(container) ) continue;
			indexContainer(w, container, monitor);
		}
		
		w.close();
	}

	/**
	 * @param container
	 * @return
	 */
	private boolean isExcluded(IContainer container) {
		
		if( container == null || excludedDirRegExes == null || excludedDirRegExes.isEmpty() )
			return false;
		
		return ResourceCollector.isResourceExcluded(container, excludedDirRegExes) 
				|| isExcluded(container.getParent());
	}

	/**
	 * @param project
	 * @param monitor
	 * @throws Exception
	 */
	public void updateProject(IProject project, IProgressMonitor monitor) throws Exception {
		
		if( !isIndexed() )
			return;
		
		deleteProject(project);
		
		if( project.exists() && project.isAccessible() && project.isOpen() ) {
			IndexWriter w = createIndexWriter(false);
			
			resourceCollector.clear();
			resourceCollector.setExcludedDirRegExes(excludedDirRegExes);
			project.accept(resourceCollector);
			
			for(IContainer container: resourceCollector.getContainers())
			{
				indexContainer(w, container, monitor);
			}
			
			w.close();
		}
		
	}
	
	/**
	 * @param w
	 * @param container
	 * @param monitor 
	 * @throws Exception 
	 */
	private void indexContainer(IndexWriter w, IContainer container, IProgressMonitor monitor) throws Exception
	{
		if( !container.isAccessible() || container.isDerived(IResource.CHECK_ANCESTORS) )
			return;
		
		IResource[] members = container.members(false);
		
		for(IResource member: members) {
			
			if( monitor.isCanceled() )
				return;
			
			if( member.getType() != IResource.FILE || !member.isAccessible() || member.isDerived() ) 
				continue;
			
			IFile file = (IFile) member;
			
			if( isIndexable(file) )
				indexFile(w, file);
		}
	}
	
	/**
	 * @param file
	 * @return
	 * @throws CoreException 
	 */
	private boolean isTextFile(IFile file) throws CoreException {
		
		if( !file.isSynchronized(IResource.DEPTH_ZERO) )
			return false;
		
		IContentType contentType = IDE.getContentType(file);
		if( contentType == null ) contentType = IDE.guessContentType(file);
		if( contentType == null ) return false;
		
		if( TEXT_CONTENT_TYPE != null && contentType.isKindOf(TEXT_CONTENT_TYPE) )
			return true;
		
		return false;
	}

	/**
	 * @throws IOException 
	 * 
	 */
	private void deleteFolder(IndexReader reader, IContainer container) throws Exception {
		
		IndexSearcher searcher = new IndexSearcher(reader);
		String path = container.getFullPath().addTrailingSeparator().toString();
		TopDocs topDocs = searcher.search(new PrefixQuery(Field.FILE.createTerm(path)), reader.numDocs());
		
		for(ScoreDoc doc: topDocs.scoreDocs)
		{
			int docNum = doc.doc;
			reader.deleteDocument(docNum);
		}
		
		searcher.close();
	}
	
	/**
	 * @param project
	 * @return deletedCount
	 * @throws Exception
	 */
	public int deleteProject(IProject project) throws Exception {
		IndexReader reader = IndexReader.open(getIndexDir(), false);
		String filePath = project.getFullPath().toString();
		
		Term term = Field.PROJ.createTerm(filePath);
		int deletedCount = reader.deleteDocuments(term);
		
		reader.close();
		
		return deletedCount;
	}
	
	public boolean isConflicting(ISchedulingRule rule)
	{
		return rule == this;  // prevent concurrent writing of the index from jobs
	}

	public boolean contains(ISchedulingRule rule)
	{
		return rule == this;
	}

	/**
	 * @param doc
	 * @return IEditorInput
	 * @throws Exception 
	 * @throws IOException 
	 */
	public IEditorInput getEditorInput(SearchResultDoc doc) throws Exception {
		
		return new FileEditorInput(doc.getFile());
	}

	/**
	 * @param doc
	 * @return IStorage
	 * @throws Exception 
	 * @throws IOException 
	 */
	public IStorage getStorage(SearchResultDoc doc) throws Exception {
		return doc.getFile(); 
	}
	
	public void propertyChange(PropertyChangeEvent event) {
		String prop = event.getProperty();
		
		if( PreferenceConstants.P_INDEXABLE_EXTENSIONS.equals(prop) )
			fileExtensions = getIndexableFileExtensions();
		else if( PreferenceConstants.P_EXCLUDE_DIRS.equals(prop) )
			excludedDirRegExes = getExcludedDirsRegExes();	
		else if( PreferenceConstants.P_INDEX_EMPTY_EXTENSION.equals(prop) )
			indexEmptyExtension = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_INDEX_EMPTY_EXTENSION);
		
	}
	
	/**
	 * Convert path matching wildcard pattern to regular expression.
	 * 
	 * 
	 * @param pathWildcardPattern
	 * @return regex pattern
	 */
	public static Pattern wildcardToRegex(String pathWildcardPattern)
	{
		String regex = pathWildcardPattern;
		
		regex = regex.replaceAll("\\*\\*", "<double-star>"); // escape initially
		regex = regex.replaceAll("\\.", "\\."); // escape .
		regex = regex.replaceAll("\\*", "[^/]*");
		regex = regex.replaceAll("\\?", ".");
		regex = regex.replaceAll("<double-star>", ".*");
		
		Pattern pattern = Pattern.compile(regex);
		
		return pattern;
	}
}
