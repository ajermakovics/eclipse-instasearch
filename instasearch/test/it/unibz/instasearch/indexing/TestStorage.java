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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.junit.Ignore;

@Ignore
class TestStorage implements IStorage
{
	private IPath path;
	private InputStream contents;

	TestStorage(String fullPath, String contentsText)
	{
		this(fullPath, new ByteArrayInputStream(contentsText.getBytes()));
	}
	
	private TestStorage(String fullPath, InputStream contents)
	{
		this.path = new Path(fullPath);
		this.contents = contents;
	}
	
	public InputStream getContents() throws CoreException {
		return contents;
	}

	public IPath getFullPath() {
		return path;
	}

	public String getName() {
		return path.lastSegment();
	}

	public boolean isReadOnly() {
		return false;
	}
	
	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class adapter) {
		return null;
	}
}