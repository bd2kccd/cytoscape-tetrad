package edu.pitt.dbmi.ccd.cytoscape.tetrad;

import org.cytoscape.model.*;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.read.LoadVizmapFileTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class CreateNetworkTask extends AbstractTask {

    private final CyNetworkManager cyNetworkManager;
    private final CyNetworkFactory cyNetworkFactory;
    private final CyNetworkNaming cyNetworkNaming;
    private final CyNetworkViewFactory cyNetworkViewFactory;
    private final CyNetworkViewManager cyNetworkViewManager;
    private final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory;
    private final VisualMappingManager visualMappingManager;

    private String inputFileName;

    public CreateNetworkTask(final CyNetworkManager netMgr, final CyNetworkNaming namingUtil, final CyNetworkFactory cnf,
                             final CyNetworkViewManager cyNetworkViewManager, final CyNetworkViewFactory cyNetworkViewFactory,
                             final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory, final VisualMappingManager visualMappingManager,
                             String inputFileName) {
        this.cyNetworkManager = netMgr;
        this.cyNetworkFactory = cnf;
        this.cyNetworkNaming = namingUtil;
        this.inputFileName = inputFileName;
        this.cyNetworkViewFactory = cyNetworkViewFactory;
        this.cyNetworkViewManager = cyNetworkViewManager;
        this.loadVizmapFileTaskFactory = loadVizmapFileTaskFactory;
        this.visualMappingManager = visualMappingManager;

    }


    public List<Edge> extractEdgesFromFile(final String fileName) {
        List<Edge> nodes = new LinkedList<>();


        Path file = Paths.get(fileName);
        try (BufferedReader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
            Pattern space = Pattern.compile("\\s+");
            boolean isData = false;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                line = line.trim();
                if (isData) {
                    String[] data = space.split(line, 2);
                    if (data.length == 2) {
                        String value = data[1].trim();
                        String[] edgeValues = space.split(value);
                        if (edgeValues.length == 3) {
                            CharSequence source = edgeValues[0].trim();
                            CharSequence target = edgeValues[2].trim();
                            CharSequence type = edgeValues[1].trim();
                            nodes.add(new Edge(source.toString(), target.toString(), type.toString()));
                        }
                    }
                } else if ("Graph Edges:".equals(line)) {
                    isData = true;
                }
            }
        } catch (IOException exception) {
            // TODO: use cytoscape logger
            System.err.println(String.format("Unable to read file '%s'. \n%s", fileName, exception));
        }

        return nodes;
    }


    public void run(TaskMonitor monitor) {

        List<Edge> edgesFromFile = extractEdgesFromFile(inputFileName);

        Set<String> inputNodes = new HashSet<>();
        for (Edge edge : edgesFromFile) {
            inputNodes.add(edge.getSource());
            inputNodes.add(edge.getTarget());
        }


        CyNetwork myNet = cyNetworkFactory.createNetwork();
        myNet.getRow(myNet).set(CyNetwork.NAME,
                cyNetworkNaming.getSuggestedNetworkTitle(inputFileName));

        // create nodes
        Hashtable<String, CyNode> nodeNameNodeMap = new Hashtable<>();
        for (String nodeName : inputNodes) {
            CyNode myNode = myNet.addNode();
            nodeNameNodeMap.put(nodeName, myNode);
            myNet.getDefaultNodeTable().getRow(myNode.getSUID()).set("name", nodeName);
        }

        // create edges
        CyTable edgeTable = myNet.getDefaultEdgeTable();
        for (Edge edge : edgesFromFile) {
            //String[] inputEdgeColumns = inputEdge.split(" ");
            CyEdge myEdge = myNet.addEdge(nodeNameNodeMap.get(edge.getSource()), nodeNameNodeMap.get(edge.getTarget()), true);
            CyRow myRow = edgeTable.getRow(myEdge.getSUID());
            myRow.set("interaction", edge.getType());
            myRow.set("name", edge.getSource() + " (" + edge.getType() + ") " + edge.getTarget());
        }


        cyNetworkManager.addNetwork(myNet);

        // create the view
        CyNetworkView myView = cyNetworkViewFactory.createNetworkView(myNet);
        cyNetworkViewManager.addNetworkView(myView);


        // perform statistical analysis

        // do organic layour

        // use the tetrad style
        InputStream stream = getClass().getResourceAsStream("/tetrad.xml");
        if (stream != null) {
            Set<VisualStyle> visualStyles = loadVizmapFileTaskFactory.loadStyles(stream);
            VisualStyle vs = (VisualStyle) visualStyles.toArray()[0];
            visualMappingManager.addVisualStyle(vs);
            vs.apply(myView);
            myView.updateView();

        } else {
            // TODO: log this properly
            System.err.println("Could not load style - null");
        }


    }
}
