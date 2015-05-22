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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.apache.lucene.index.IndexWriter;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.ui.IEditorInput;

/**
 * Workspace indexer which also indexes JAR source attachments
 */


@SuppressWarnings("restriction")
public class WorkspaceIndexerJDT extends WorkspaceIndexer {

	/**
	 * @throws Exception 
	 * @throws IOException
	 */
	public WorkspaceIndexerJDT() throws Exception {
		super();
		
		if( JavaCore.getJavaCore() == null ) // check that we have JDT. throws exception if we don't have JavaCore
			throw new RuntimeException("JDT not detected");
	}

	@Override
	protected void indexContainers(IndexWriter indexWriter, IWorkspaceRoot workspaceRoot, IProgressMonitor monitor) throws Exception {
		
		super.indexContainers(indexWriter, workspaceRoot, monitor);
		
		boolean indexArchives = InstaSearchPlugin.getBoolPref(PreferenceConstants.P_INDEX_ARCHIVES);
		if( ! indexArchives )
			return;
		
		try {
			IJavaModel javaModel = JavaCore.create(workspaceRoot);
			
			List<IPackageFragmentRoot> jars = getJars(javaModel);

			monitor.beginTask("Indexing JAR Source Attachements (" + jars.size() + ")", jars.size());

			for(IPackageFragmentRoot jar: jars) {
				monitor.worked(1);

				indexClassFiles(indexWriter, jar, monitor);
				indexNonJavaResources(indexWriter, jar, monitor);
				
				if( monitor.isCanceled() )
					break;
			}
		} catch(Exception e) {
			InstaSearchPlugin.log(e);
		}
		
		if( monitor.isCanceled() ) {
			// if user canceled, disable jar indexing in preferences. next time don't index
			InstaSearchPlugin.setBoolPref(PreferenceConstants.P_INDEX_ARCHIVES, false);
		}
		
		monitor.done();
	}
	
	/**
	 * Index non .class files 
	 * 
	 * @param indexWriter
	 * @param jar
	 * @param monitor
	 * @throws CoreException
	 * @throws IOException
	 */
	private void indexNonJavaResources(IndexWriter indexWriter, IPackageFragmentRoot jar, IProgressMonitor monitor) throws Exception {
		
		Object[] resources = jar.getNonJavaResources();
		
		if( resources == null || resources.length == 0 )
			return;
		
		IJarEntryResource[] jarEntries = new IJarEntryResource[resources.length];
		System.arraycopy(resources, 0, jarEntries, 0, resources.length);
		
		indexNonJavaResources(indexWriter, jar, jarEntries, monitor);
	}
	
	/**
	 * @param indexWriter
	 * @param jar 
	 * @param resources
	 * @param monitor
	 * @throws IOException 
	 * @throws CoreException 
	 */
	private void indexNonJavaResources(IndexWriter indexWriter, IPackageFragmentRoot jar, IJarEntryResource[] resources, IProgressMonitor monitor) throws Exception {
		
		String jarName = getJarName(jar);
		String projectPath = getProjectPath(jar);
		
		for(IJarEntryResource resource: resources)
		{
			if( monitor.isCanceled() )
				return;
			
			if( resource.isFile() ) 
			{
				if( isIndexable(resource) )
					indexStorageWithRetry(indexWriter, resource, projectPath, IResource.NULL_STAMP, jarName);
			} 
			else 
			{
				indexNonJavaResources(indexWriter, jar, resource.getChildren(), monitor);
			}
		}
		
	}

	/**
	 * @param jarRes
	 * @return
	 */
	private boolean isIndexable(IJarEntryResource jarRes) {
		String ext = jarRes.getFullPath().getFileExtension();
		
		return isIndexableExtension(ext);
	}

	/**
	 * @param jar
	 * @return
	 */
	private String getProjectPath(IPackageFragmentRoot jar) {
		return jar.getJavaProject().getElementName() + "/" + getJarName(jar);
	}

	/**
	 * @param jar
	 * @return
	 */
	private String getJarName(IPackageFragmentRoot jar) {
		return jar.getElementName();
	}

	private void indexClassFiles(IndexWriter indexWriter, IPackageFragmentRoot jar, IProgressMonitor monitor) throws Exception {
		
		String jarName = getJarName(jar);
		String projectPath = getProjectPath(jar);
		
		for(IJavaElement pkgRootChild: jar.getChildren()) {
			
			IPackageFragment pkg = (IPackageFragment) pkgRootChild;
			monitor.setTaskName("Indexing JAR Source Attachements: " + jar.getElementName() + " - " + pkg.getElementName());
			
			for(IClassFile classFile: pkg.getClassFiles()) {
				if( classFile.getElementName().contains("$") ) continue; // not type root
				
				try {
					ClassFileSourceStorage classFileSourceStorage = new ClassFileSourceStorage(classFile);

					if (classFileSourceStorage.hasSource()) indexStorageWithRetry(indexWriter, classFileSourceStorage, projectPath, IResource.NULL_STAMP, jarName);
				} catch (Exception e) {
					//Issue #69. Avoid Ex where Eclipse has trouble loading a source file.
				}
				
				if( monitor.isCanceled() ) return;
			}
			
		}

	}
	
	private List<IPackageFragmentRoot> getJars(IJavaModel javaModel) throws JavaModelException {
		
		IJavaProject[] projects = javaModel.getJavaProjects();
		TreeSet<String> jarNames = new TreeSet<String>();
		LinkedList<IPackageFragmentRoot> jars = new LinkedList<IPackageFragmentRoot>();
		
		for(IJavaProject javaProj: projects) {
			
			IPackageFragmentRoot[] roots = javaProj.getPackageFragmentRoots();
			
			for(IPackageFragmentRoot root: roots) {
				if( root.isArchive() && root.getSourceAttachmentPath() != null ) {
					
					String name = root.getElementName();
					
					if( !jarNames.contains(name) ) {
						jarNames.add(name);
						jars.add(root);
					}
				}
			}
		}
		
		return jars;
	}

	@Override
	public IEditorInput getEditorInput(SearchResultDoc doc) throws Exception {
		
		if( ! doc.isInJar() )
			return super.getEditorInput(doc);
	
		if( "class".equals(doc.getFileExtension()) ) {
			IClassFile classFile = getClassFile(doc);

			if( classFile == null )
				return null;

			return EditorUtility.getEditorInput(classFile);
		}
		
		IStorage storage = getNonJavaResource(doc);
		
		if( storage == null )
			return null;
		
		return EditorUtility.getEditorInput(storage);
	}

	@Override
	public IStorage getStorage(SearchResultDoc doc) throws Exception {
		if( !doc.isInJar() )
			return super.getStorage(doc); // return file
		
		if( "class".equals(doc.getFileExtension())) {
			IClassFile classFile = getClassFile(doc);

			if( classFile == null )
				return null;

			ClassFileSourceStorage storage = new ClassFileSourceStorage(classFile);

			return storage;
		}
		
		return getNonJavaResource(doc);
	}
	
	/**
	 * @param doc
	 * @return
	 * @throws JavaModelException 
	 */
	private IStorage getNonJavaResource(SearchResultDoc doc) throws JavaModelException {
		
		IWorkspaceRoot workspaceRoot = InstaSearchPlugin.getWorkspaceRoot();
		IJavaModel javaModel = JavaCore.create(workspaceRoot);
		
		String javaProjectName = doc.getProject().segment(0);
		IJavaProject javaProj = javaModel.getJavaProject(javaProjectName);
		
		if( !javaProj.isOpen() )
			javaProj.open(new NullProgressMonitor());
		
		javaModel.refreshExternalArchives(new IJavaElement[]{javaProj}, new NullProgressMonitor());
		
		String jarName = doc.getJarName();
		
		IPackageFragmentRoot[] roots = javaProj.getPackageFragmentRoots();
		IPackageFragmentRoot jar = null;
		
		for(IPackageFragmentRoot root: roots) {
			if( root.isArchive() && root.getSourceAttachmentPath() != null ) {
				
				String name = root.getElementName();
				if( name.equals(jarName)) {
					jar = root;
					break;
				}
			}
		}
		
		if( jar == null )
			return null;
		
		String filePath = doc.getFilePath();
		IPath path = new Path(filePath);
		
		IJarEntryResource res  = null;
		for(String segment: path.segments())
		{
			if( res == null )
				res = findJarEntry(jar, segment);
			else
				res = findJarEntry(res.getChildren(), segment);
		}
		
		return res; 
	}

	/**
	 * @param jar
	 * @param filePath
	 * @return
	 * @throws JavaModelException 
	 */
	private IJarEntryResource findJarEntry(IPackageFragmentRoot jar, String filePath) throws JavaModelException {
		
		Object[] resources = jar.getNonJavaResources();
		
		if( resources == null || resources.length == 0 )
			return null;
		
		IJarEntryResource[] jarEntries = new IJarEntryResource[resources.length];
		System.arraycopy(resources, 0, jarEntries, 0, resources.length);
		
		return findJarEntry(jarEntries, filePath);
	}

	/**
	 * @param jarEntries
	 * @param filePath
	 * @return
	 */
	private IJarEntryResource findJarEntry(IJarEntryResource[] jarEntries,
			String filePath) {
		
		for(IJarEntryResource entry: jarEntries)
		{
			if( filePath.equals(entry.getName()) )
				return entry;
		}
		
		return null; 
	}

	/**
	 * @param doc
	 * @return
	 * @throws JavaModelException 
	 */
	private IClassFile getClassFile(SearchResultDoc doc) throws Exception {
		
		IWorkspaceRoot workspaceRoot = InstaSearchPlugin.getWorkspaceRoot();
		IJavaModel javaModel = JavaCore.create(workspaceRoot);
		
		String javaProjectName = doc.getProject().segment(0);
		IJavaProject proj = javaModel.getJavaProject(javaProjectName);
		
		if( proj == null )
			throw new Exception("Project " + javaProjectName + " not found");
		
		if( !proj.isOpen() )
			proj.open(new NullProgressMonitor());
		
		javaModel.refreshExternalArchives(new IJavaElement[]{proj}, new NullProgressMonitor());
		
		IPath filePath = new Path(doc.getFilePath());
		String fileName = filePath.lastSegment();
		
		IPath jarPath = filePath.removeLastSegments(2); // remove pkg and filename
		IPackageFragmentRoot jar = null;
		IResource jarFile = workspaceRoot.findMember(jarPath);
		
		if( jarFile != null )
			jar = proj.getPackageFragmentRoot(jarFile);
		else
			jar = proj.getPackageFragmentRoot(jarPath.toString()); // external archive
		
		if( jar == null )
			throw new Exception("Jar " + jarPath + " not found in project " + doc.getProjectName());
		
		IPath pkgPath = filePath.removeLastSegments(1); // remove filename
		String pkgName = pkgPath.lastSegment();
		
		IPackageFragment pkg = jar.getPackageFragment(pkgName);
		
		if( pkg == null )
			throw new Exception("Package " + pkgName + " not found  in " + doc.getProjectName());
		
		IClassFile classFile = pkg.getClassFile(fileName);
		
		return classFile;
	}
	
	/**
	 * Stores the attached source of a .class file
	 */
	private class ClassFileSourceStorage implements IStorage {

		private IClassFile classFile;
		private String source;

		/**
		 * @param classFileWithSource 
		 * @throws JavaModelException 
		 * 
		 */
		public ClassFileSourceStorage(IClassFile classFileWithSource) throws JavaModelException {
			this.classFile = classFileWithSource;
			this.source = classFile.getSource();
		}
		
		public InputStream getContents() throws CoreException {
			if( !hasSource() ) return null;
			
			return new ByteArrayInputStream(source.getBytes());
		}

		public boolean hasSource() {
			return source != null;
		}
		
		/**
		 * <jar path>/<package name>/<file name>
		 */
		public IPath getFullPath() {
			IPackageFragment pkg = (IPackageFragment)classFile.getParent();

			IPackageFragmentRoot jar = (IPackageFragmentRoot)pkg.getParent();
			
			String pkgName = pkg.getElementName();
			IPath jarPath = jar.getPath();
			
			IPath filePath = jarPath.append(pkgName).append(getName());
			
			return filePath;
		}

		public String getName() {
			return classFile.getElementName(); // ClassName.class
		}

		public boolean isReadOnly() {
			return true; 
		}

		@SuppressWarnings("rawtypes")
		public Object getAdapter(Class adapter) {
			return classFile.getAdapter(adapter);
		}
		
	}
}
