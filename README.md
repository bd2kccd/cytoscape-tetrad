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

Put a graph box on the Tetrad workspace and select the graph type “graph”. Double click on the graph box to display the graph in Tetrad. Within the graph display box, click on File --> Save JSON. 

In Cytoscape, select the File --> Import --> Network --> Tetrad option and select the file that you saved previously from Tetrad. 

Apply a layout in Cytoscape. By default Cytoscape doesn't apply a layout so the initial rendering will look like a single node. Apply a layout by selecting Layout in the top menu and then choosing a layout to see your graph (e.g., Layouts --> Prefuse Force Directed Layout). 
