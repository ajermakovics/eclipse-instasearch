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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;

public class ResourceCollector implements IResourceVisitor
{
	private Collection<IContainer> containers = new LinkedList<IContainer>();
	private List<Pattern> excludedPatterns = new ArrayList<Pattern>();
	
	public ResourceCollector() {
	}
	
	public boolean visit(IResource resource) throws CoreException {
		
		if( !(resource instanceof IContainer) )
			return false;
		
		if( isResourceExcluded(resource, excludedPatterns) )
			return false;
		
		switch(resource.getType()) 
		{
		case IResource.FOLDER:
			containers.add((IContainer)resource);
			break;
		case IResource.PROJECT:
			return visit((IProject) resource);
		}
		
		return true; 
	}
	
	
	/**
	 * @param resource
	 * @param excludedDirPatterns
	 * @return
	 */
	static boolean isResourceExcluded(IResource resource,
			List<Pattern> excludedDirPatterns)
	{
		String path = resource.getFullPath().toString();
		
		if( path.startsWith("/") ) path = path.substring(1);
		
		for(Pattern pattern: excludedDirPatterns)
		{
			if( pattern.matcher(path).matches() )
				return true;
		}
		
		return false;
	}

	public boolean visit(IProject project) {
		if( !project.isOpen()  ) // don't visit closed projects
			return false;
		
		containers.add(project);
		
		return true;
	}
	
	public Collection<IContainer> getContainers() {
		return containers;
	}
	
	/**
	 * 
	 */
	public void clear() {
		 containers.clear();
		 excludedPatterns.clear();
	}

	/**
	 * @param excludedDirRegExes 
	 */
	public void setExcludedDirRegExes(List<Pattern> excludedDirRegExes) {
		 this.excludedPatterns = new ArrayList<Pattern>(excludedDirRegExes);
	}

}