package edu.pitt.dbmi.ccd.cytoscape.tetrad;

import edu.cmu.tetrad.graph.EdgeTypeProbability;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.cytoscape.model.*;
import org.cytoscape.task.read.LoadVizmapFileTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

public class CreateNetworkTask extends AbstractTask {

    private final CyNetworkManager cyNetworkManager;
    private final CyNetworkFactory cyNetworkFactory;
    private final CyNetworkViewFactory cyNetworkViewFactory;
    private final CyNetworkViewManager cyNetworkViewManager;
    private final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory;
    private final VisualMappingManager visualMappingManager;

    private final String inputFileName;

    public CreateNetworkTask(final CyNetworkManager netMgr, final CyNetworkFactory cnf,
            final CyNetworkViewManager cyNetworkViewManager, final CyNetworkViewFactory cyNetworkViewFactory,
            final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory, final VisualMappingManager visualMappingManager,
            String inputFileName) {
        this.cyNetworkManager = netMgr;
        this.cyNetworkFactory = cnf;
        this.inputFileName = inputFileName;
        this.cyNetworkViewFactory = cyNetworkViewFactory;
        this.cyNetworkViewManager = cyNetworkViewManager;
        this.loadVizmapFileTaskFactory = loadVizmapFileTaskFactory;
        this.visualMappingManager = visualMappingManager;
    }

    public List<Node> extractNodesFromFile(final String fileName) {
        List<Node> tetradGraphNodes = new LinkedList<>();

        Path file = Paths.get(fileName);

        try {
            // Read Tetrad generated json file
            String contents = new String(Files.readAllBytes(file));

            // Parse to Tetrad graph
            Graph graph = JsonUtils.parseJSONObjectToTetradGraph(contents);

            // Extract the nodes
            tetradGraphNodes = graph.getNodes();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tetradGraphNodes;
    }

    public List<Edge> extractEdgesFromFile(final String fileName) {
        List<Edge> cytoEdges = new LinkedList<>();

        Path file = Paths.get(fileName);

        try {
            // Read Tetrad generated json file
            String contents = new String(Files.readAllBytes(file));

            // Parse to Tetrad graph
            Graph graph = JsonUtils.parseJSONObjectToTetradGraph(contents);

            // Extract the edges, this is the tetrad graph edges.
            // We'll convert them into cyto Edge
            Set<edu.cmu.tetrad.graph.Edge> tetradGraphEdges = graph.getEdges();

            // For each edge determine the types of endpoints to figure out edge type.
            // Basically convert to these '-->', 'o-o', or 'o->' strings
            tetradGraphEdges.stream().map((tetradGraphEdge) -> {
                String edgeType = "";
                Endpoint endpoint1 = tetradGraphEdge.getEndpoint1();
                Endpoint endpoint2 = tetradGraphEdge.getEndpoint2();

                String endpoint1Str = "";
                if (endpoint1 == Endpoint.TAIL) {
                    endpoint1Str = "-";
                } else if (endpoint1 == Endpoint.ARROW) {
                    endpoint1Str = "<";
                } else if (endpoint1 == Endpoint.CIRCLE) {
                    endpoint1Str = "o";
                }

                String endpoint2Str = "";
                if (endpoint2 == Endpoint.TAIL) {
                    endpoint2Str = "-";
                } else if (endpoint2 == Endpoint.ARROW) {
                    endpoint2Str = ">";
                } else if (endpoint2 == Endpoint.CIRCLE) {
                    endpoint2Str = "o";
                }
                // Produce a string representation of the edge
                edgeType = endpoint1Str + "-" + endpoint2Str;

                // Extract the probability of an edge - find out what column name in cytoscape Mark needs is in
                // Will need to use the latest release of Tetrad to have this feature available
                List<EdgeTypeProbability> edgeTypeProbabilities = tetradGraphEdge.getEdgeTypeProbabilities();

                // Create a new Edge(String source, String target, String type, List<EdgeTypeProbability> edgeTypeProbabilities)
                // This is the cyto edge object in this package, not the edu.cmu.tetrad.graph.Edge
                Edge cytoEdge = new Edge(tetradGraphEdge.getNode1().getName(), tetradGraphEdge.getNode2().getName(), edgeType, edgeTypeProbabilities);

                return cytoEdge;
            }).forEach((cytoEdge) -> {
                // Add to the list
                cytoEdges.add(cytoEdge);
            });

        } catch (IOException e) {
            e.printStackTrace();
        }

        return cytoEdges;
    }

    @Override
    public void run(TaskMonitor monitor) {
        List<Node> tetradGraphNodes = extractNodesFromFile(inputFileName);
        List<Edge> cytoEdges = extractEdgesFromFile(inputFileName);

        // Create the cytoscape network
        CyNetwork myNet = cyNetworkFactory.createNetwork();

        // Set the name for network in Network Table
        myNet.getRow(myNet).set(CyNetwork.NAME, "Tetrad Output Network");

        // Add nodes
        HashMap<String, CyNode> nodeName2CyNodeMap = new HashMap<>();

        tetradGraphNodes.stream().forEach((tetradGraphNode) -> {
            CyNode myNode = myNet.addNode();

            // Set name for new node
            myNet.getRow(myNode).set(CyNetwork.NAME, tetradGraphNode.getName());

            // Add to the map
            nodeName2CyNodeMap.put(tetradGraphNode.getName(), myNode);
        });

        // Edge Table
        CyTable myEdgeTable = myNet.getDefaultEdgeTable();

        // Create the "__CCD_Annotation_Set" column
        myEdgeTable.createColumn("__CCD_Annotation_Set", String.class, true);

        // Add edges
        cytoEdges.stream().forEach((edge) -> {
            CyEdge myEdge = myNet.addEdge(nodeName2CyNodeMap.get(edge.getSource()), nodeName2CyNodeMap.get(edge.getTarget()), true);
            CyRow myRow = myEdgeTable.getRow(myEdge.getSUID());
            myRow.set("interaction", edge.getType());
            myRow.set(CyNetwork.NAME, edge.getSource() + " (" + edge.getType() + ") " + edge.getTarget());

            // Get edgeTypeProbabilities
            List<EdgeTypeProbability> edgeTypeProbabilities = edge.getEdgeTypeProbabilities();

            String maxEdgeTypeProbablityTypeAndValue = "";

            // Find the max edge type probablity if generated by bootstraping
            if (!edgeTypeProbabilities.isEmpty()) {
                // Comparator
                final Comparator<EdgeTypeProbability> comparator = (etp1, etp2) -> Double.compare(etp1.getProbability(), etp2.getProbability());

                // Find the EdgeTypeProbability that has the max probablity value and EdgeType is not nil
                EdgeTypeProbability maxEdgeTypeProbability = edgeTypeProbabilities.stream()
                        .filter(edgeTypeProbability -> !edgeTypeProbability.getEdgeType().equals(EdgeTypeProbability.EdgeType.nil))
                        .max(comparator)
                        .get();

                // Create the string of "edge type: probablity value"
                maxEdgeTypeProbablityTypeAndValue = maxEdgeTypeProbability.getEdgeType().name() + ": " + maxEdgeTypeProbability.getProbability();
            }

            // Specific to Mark's work, placeholder
            myRow.set("__CCD_Annotation_Set", maxEdgeTypeProbablityTypeAndValue);
        });

        // Add the network to Cytoscape
        cyNetworkManager.addNetwork(myNet);

        // Create a new network view
        CyNetworkView myView = cyNetworkViewFactory.createNetworkView(myNet);

        // Add view to Cytoscape
        cyNetworkViewManager.addNetworkView(myView);

        // perform statistical analysis
        // do organic layout
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
