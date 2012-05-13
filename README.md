InstaSearch - Eclipse plug-in for quick code search
--

InstaSearch is an  [Eclipse IDE](http://eclipse.org) plug-in for performing quick and advanced search of workspace files. It indexes files using [Lucene](http://lucene.apache.org/core/) and keeps the index up to date automatically. The search is performed instantly as-you-type and resulting files are displayed in an Eclipse view.

Each file then can be previewed using few most matching and relevant lines. A double-click on the match leads to the matching line in the file.

Download / Installation
--
In Eclipse Helios (3.6) or Indigo (3.7) please install using the **Eclipse Marketplace** from the *Help* menu ([how-to](http://marketplace.eclipse.org/updatesite/help?url=http://code.inf.unibz.it/instasearch/downloads/))

Alternatively you can install using the update site `http://code.inf.unibz.it/instasearch/downloads/`

Java 1.5 or newer is required

( [Add to favorites](http://marketplace.eclipse.org/content/instasearch) on Eclipse Marketplace.)

Main Features
--
* Instantly shows search results
* Shows a preview using relevant lines
* Periodically updates the index
* Matches partial words (e.g. case in CamelCase)
* Opens and highlights matches in files
* Searches JAR source attachments
* Supports filtering by extension/project/working set

[Screenshot](http://code.inf.unibz.it/instasearch/downloads/images/instasearch_new.jpg) 

Search Tips
---
Lucene [query syntax](http://lucene.apache.org/core/old_versioned_docs/versions/3_0_0/queryparsersyntax.html) can be used for searching. This includes:

* Wildcard searches
  * `app* initialize`
* Excluding words
  * `application -initialize`
* Fuzzy searches to find similar matches
   * `application init~`
* Limit by location - directory, projects or working set
   * `proj:MyProject,OtherProject  application  init `
   * `ws:MyWorkingSet  dir:src  init `
* Limit by filename, extension or modification time
   * `name:app*  ext:java,xml,txt  modified:yesterday  `
* Search by file name initials (e.g. FOS to find FileOutputStream.java)
   * `name:FOS`

To exclude some folders from search indexing, mark them as *Derived* in the folder's properties.
There are also useful [Eclipse Search Tips](https://github.com/ajermakovics/eclipse-instasearch/wiki/Eclipse-search-tips).

**Note**: Fuzzy search is started automatically if no exact matches are found

Bugs/Enhancement Requests
---
If you notice any issues with the plugin or have an idea for an enhancement, please open a new ticket

Authors/Contributors
---
Author:  [Andrejs Jermakovics](http://github.com/ajermakovics)

Contributors:  [Holger Voormann](http://eclipsehowl.wordpress.com/)

If you'd like to contribute feel free to get in touch or create a  pull request. 
