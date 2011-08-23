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

import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.junit.Ignore;

@Ignore
class TestSearcher extends Searcher
{
	private Directory dir;

	public TestSearcher(Directory dir) {
		this.dir = dir;
	}
	
	@Override
	protected Directory getIndexDir() throws IOException {
		return dir;
	}
	
	@Override
	protected void initPrefs() {	
	}
}