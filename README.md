# Cytoscape Tetrad App

A Cytoscape 3 app for importing the Tetrad generated JSON output file as a Network.

## Prerequisites - Tools needed to build/install this App

* Oracle Java 1.8 - (http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* Maven 3.x -(https://maven.apache.org/download.cgi)
* Cytoscape 3.x - (http://www.cytoscape.org)

## Installing this App

Clone and build this repo with Maven.

````
git clone https://github.com/bd2kccd/cytoscape-tetrad.git
cd cytoscape-tetrad
mvn clean package
````

To install this app within Cytoscape, go to the menu bar and choose **Apps** -> **App Manager**. At the top of the App Manager window, make sure you have the **Install App** tab selected. Then click **Install from File...** button on the bottom-left. Then select the cytoscape-tetrad jar file.

## How to use this App

This app currently does the following:

Provides a new **File** -> **Import** -> **Network** -> **Tetrad** option that reads in a Tetrad .json output file, renders a network that utilizes a Cytoscape style that displays causal edges including those used in partial ancestral graphs.

Once the network is imported, you can apply a preferred layout to view and analyze it.