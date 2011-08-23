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
package it.unibz.instasearch;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The activator class controls the plug-in life cycle
 */
public final class InstaSearchPlugin extends AbstractUIPlugin {
	
	/** **/
	public final static String PLUGIN_ID = "it.unibz.instasearch";
	private final static String INDEX_DIR_NAME = "searchindex";
	
	/** The shared plug-in instance */
	private static InstaSearchPlugin plugin;
	
	private String indexDirLocation;
	private InstaSearch instaSearch;
	private ServiceTracker<IProxyService,IProxyService> proxyServiceTracker;
	private boolean debug = false;
	
	/**
	 * The Plug-in instance
	 */
	public InstaSearchPlugin() {
		plugin = this;
	}
	
	public void start(BundleContext context) throws Exception {
		
		configureDebug();
		
		try {
			super.start(context);
		} catch(Exception e) {
			log(e);
			throw e;
		}
		
		plugin = this;
		instaSearch = new InstaSearch();
	}
	
	public void stop(BundleContext context) throws Exception {
		instaSearch.cancelJobs();
		
		plugin = null;
		super.stop(context);
		
		instaSearch = null;
	}

	/**
	 * @return location of the index directory
	 */
	public static File getIndexDirLocation()
	{
		File indexDir = null;
		
		try {
			indexDir = getStateDir(INDEX_DIR_NAME);
			plugin.indexDirLocation = indexDir.getAbsolutePath(); // save
		} catch(Exception e) {
			log(e);
			if( plugin.indexDirLocation != null )
				indexDir = new File(plugin.indexDirLocation);
		}
		
		return indexDir;
	}

	/**
	 * Returns a list of open project names
	 * @return ProjectNames
	 */
	public static List<String> getProjectNames() {
		
		ArrayList<String> projectNames = new ArrayList<String>();
		if( getWorkspaceRoot() == null )
			return projectNames;
		
		IProject[] projects = getWorkspaceRoot().getProjects();
		
		for(IProject project: projects) {
			if( project.exists() && project.isAccessible() && project.isOpen() )
				projectNames.add(project.getName());
		}
		
		Collections.sort(projectNames, String.CASE_INSENSITIVE_ORDER);
		
		return projectNames;
	}

	/**
	 * Array of working set names
	 * @return WorkingSets
	 */
	public static List<String> getWorkingSets() {
		ArrayList<String> workingSets = new ArrayList<String>();
		
		IWorkingSetManager wsManager = getDefault().getWorkbench().getWorkingSetManager();
		if( wsManager == null )
			return workingSets;
		
		for(IWorkingSet ws: wsManager.getWorkingSets()) {
			workingSets.add(ws.getName());
		}
		
		Collections.sort(workingSets, String.CASE_INSENSITIVE_ORDER);
		
		return workingSets;
	}
	
	/**
	 * Returns project names within working set
	 * @param workingSet 
	 * @return WorkingSetProjects
	 */
	public static String[] getWorkingSetProjects(String workingSet) {
		
		String emptyResults[] = new String[0];
		
		IWorkingSetManager wsManager = getDefault().getWorkbench().getWorkingSetManager();
		if( wsManager == null )
			return emptyResults;
		
		IWorkingSet ws = wsManager.getWorkingSet(workingSet);
		if( ws == null )
			return emptyResults;
		
		ArrayList<String> projects = new ArrayList<String>();
		IAdaptable elements[] = ws.getElements();
		
		for( IAdaptable elem: elements ) {
			if( elem instanceof IProject ) {
				IProject proj = (IProject) elem;
				projects.add( proj.getName() );
			} else {
				IProject proj = (IProject) elem.getAdapter(IProject.class);
				if( proj != null )
					projects.add( proj.getName() );
			}
		}
		
		return projects.toArray(emptyResults);
	}
		
	/**
	 * Returns the shared plugin instance
	 *
	 * @return the shared plugin instance
	 */
	public static InstaSearchPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 * 
	 * @param imageName the image name
	 * @return the image descriptor
	 * @see AbstractUIPlugin#imageDescriptorFromPlugin(String, String)
	 */
	public static ImageDescriptor getImageDescriptor(String imageName) {
		return imageDescriptorFromPlugin(getPluginId(), "icons" + File.separator + imageName + ".gif");
	}
	
	/**
	 * Returns a directory in the plugin's state directory
	 * 
	 * @param dirPath
	 * @return StateDir
	 */
	public static File getStateDir(String dirPath) 
	{
		IPath stateLocation = plugin.getStateLocation();
		IPath path = stateLocation.append(dirPath);
		
		return path.toFile();
	}

	public static String getHomePageLocation() {
		return getBundleHeader(Constants.BUNDLE_DOCURL);
	}
	
	public static String getUpdateLocation() {
		
		return getBundleHeader(Constants.BUNDLE_UPDATELOCATION);
	}
	
	public static String getErrorReportURL() {
		return getBundleHeader("ErrorReportURL");
	}
	
	public static String getPluginName() {
		return getBundleHeader(Constants.BUNDLE_NAME);
	}
	
	public static String getPluginId() {
		return PLUGIN_ID;
	}
	
	public static String getFeatureId() {
		return getPluginId() + ".feature";
	}
	
	public static String getVersion() {
		return getBundleHeader(Constants.BUNDLE_VERSION);
	}
	
	public static String getBundleHeader(String header) {
		return (String) plugin.getBundle().getHeaders().get(header);
	}
	
	public static void log(IStatus status) {
		if( plugin != null )
			plugin.getLog().log(status);
		else
			ResourcesPlugin.getPlugin().getLog().log(status);
		
		if( status.getException() != null && isDebug() )
			status.getException().printStackTrace();
	}
	
	/**
	 * 
	 * @return true if eclipse is running in debug mode
	 */
	public static boolean isDebug()
	{
		if( plugin == null ) return true;
		return plugin.debug;
	}
	
	/**
	 * Print messages if running in eclipse debug mode
	 * @param msgs
	 */
	public static void debug(Object... msgs)
	{
		if( ! isDebug() ) return;
		
		String msg = StringUtils.join(msgs, ", ");
		
		System.out.println(msg);
	}
	
	public static void debug(Throwable t)
	{
		if( ! isDebug() ) return;
		
		t.printStackTrace();
	}
	
	public static void log(Exception e) {
		Status status = new Status(IStatus.ERROR, getPluginId(), e.getMessage(), e);
		log(status);
	}
	
	public static InstaSearch getInstaSearch() 
	{
		return plugin.instaSearch;
	}
	
	public static boolean getBoolPref(String pref) {
		return plugin.getPreferenceStore().getBoolean(pref);
	}
	
	public static void setBoolPref(String pref, boolean value) {
		plugin.getPreferenceStore().setValue(pref, value);
	}
	
	public static int getIntPref(String pref) {
		return plugin.getPreferenceStore().getInt(pref);
	}
	
	public static IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}
	
	public static void addPreferenceChangeListener(IPropertyChangeListener listener)
	{
		if( plugin != null )
			plugin.getPreferenceStore().addPropertyChangeListener(listener);
	}
	
	public static void removePreferenceChangeListener(IPropertyChangeListener listener)
	{
		if( plugin != null )
			plugin.getPreferenceStore().removePropertyChangeListener(listener);
	}
	
	public IProxyService getProxyService() {
		try {
			if (proxyServiceTracker == null) {
				proxyServiceTracker = new ServiceTracker<IProxyService,IProxyService>(getBundle().getBundleContext(), IProxyService.class, null);
				proxyServiceTracker.open();
			}
			return proxyServiceTracker.getService();
		} catch (Exception e) {
			InstaSearchPlugin.log(e);
		}
		
		return null;
	}
	
	private void configureDebug() 
	{
		try {
			// check if running in debug mode in Eclipse
			
			debug = debug || java.lang.management.ManagementFactory.getRuntimeMXBean().
									getInputArguments().toString().contains("-agentlib:jdwp");
			
		} catch(Exception e) {
			debug = true;
			log(e);
		}
	}
}
