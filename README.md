InstaSearch - Eclipse plug-in for quick code search
--

<a href="http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=1093" title="Drag and drop into a running Eclipse menu area to install InstaSearch">
  <img src="https://marketplace.eclipse.org/sites/all/modules/custom/marketplace/images/installbutton.png"/>
</a>

InstaSearch is an  [Eclipse IDE](http://eclipse.org) plug-in for performing quick and advanced search of workspace files. It indexes files using [Lucene](http://lucene.apache.org/core/) and keeps the index up to date automatically. The search is performed instantly as-you-type and resulting files are displayed in an Eclipse view.

Each file then can be previewed using few most matching and relevant lines. A double-click on the match leads to the matching line in the file.

Download / Installation
--
In Eclipse please install using the **Eclipse Marketplace** from the *Help* menu ([how-to](http://marketplace.eclipse.org/marketplace-client-intro))

Alternatively you can install using the update site `http://dl.bintray.com/ajermakovics/InstaSearch/`

Java 1.7 or newer is required

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

![Screenshot](https://raw.githubusercontent.com/ajermakovics/eclipse-instasearch/gh-pages/images/instasearch_new.jpg) 

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

Building and Developing
---

Build using Maven from project root:

    mvn install

To develop you will need Eclipse (3.7+) with Plug-In Development Environment (PDE) installed:
* Use File -> Import -> Existing Projects to import all instasearch projects
* To run or debug right click on 'instasearch' project and select Run As -> Eclipse Application

To use the plug-in in existing Eclipse installation do:
* Right click on instasearch project, Export -> Deployable plug-ins
* Choose an existing Eclipse installation directory. Restart

Authors/Contributors
---
Author:  [Andrejs Jermakovics](http://github.com/ajermakovics)

Contributors:  [Holger Voormann](http://eclipsehowl.wordpress.com/), [solganik](https://github.com/solganik), [on github](https://github.com/ajermakovics/eclipse-instasearch/graphs/contributors)

Contributions are very welcome so feel free to get in touch or create a pull request. 

Acknowledgements
---

YourKit is supporting InstaSearch open source project with its full-featured Java Profiler.  
YourKit, LLC is the creator of innovative and intelligent tools for profiling  
Java and .NET applications. Take a look at YourKit's leading software products:  
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.  
