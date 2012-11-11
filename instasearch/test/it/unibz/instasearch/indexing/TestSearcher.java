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
public class TestSearcher extends Searcher
{
	public TestSearcher(final Directory dir) {
		super(new TestConfig(dir));
	}
	
	@Override
	protected void initPrefs() {	
	}
	
	private static class TestConfig implements SearcherConfig
	{
		private Directory dir;
		
		public TestConfig(Directory d)
		{
			this.dir = d;
		}

		@Override
		public void log(Exception e)
		{
			throw new RuntimeException(e);
		}
		
		@Override
		public Directory getIndexDir() throws IOException
		{
			return dir;
		}
		
		@Override
		public boolean getBoolPref(String pref)
		{
			return false;
		}
	}
}