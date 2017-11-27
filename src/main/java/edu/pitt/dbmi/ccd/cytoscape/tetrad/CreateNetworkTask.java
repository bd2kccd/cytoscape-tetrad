package edu.pitt.dbmi.ccd.cytoscape.tetrad;

import edu.cmu.tetrad.graph.EdgeTypeProbability;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
        List<Edge> cytoEdges = new LinkedList<>();

        Path file = Paths.get(fileName);

        try {
            // Read Tetrad generated json file
            String contents = new String(Files.readAllBytes(file));

            // Parse to Tetrad graph
            Graph graph = JsonUtils.parseJSONObjectToTetradGraph(contents);

            // Extract the edges
            Set<edu.cmu.tetrad.graph.Edge> tetradGraphEdges = graph.getEdges();

            // For each edge determine the types of endpoints to figure out edge type.
            // Basically convert to these '-->', 'o-o', or 'o->' strings
            for (edu.cmu.tetrad.graph.Edge tetradGraphEdge : tetradGraphEdges) {
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
                // Add to the list
                cytoEdges.add(cytoEdge);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return cytoEdges;
    }

    public void run(TaskMonitor monitor) {

        List<Edge> edgesFromFile = extractEdgesFromFile(inputFileName);

        Set<String> inputNodes = new HashSet<>();
        for (Edge edge : edgesFromFile) {
            inputNodes.add(edge.getSource());
            inputNodes.add(edge.getTarget());
        }

        CyNetwork myNet = cyNetworkFactory.createNetwork();
        myNet.getRow(myNet).set(CyNetwork.NAME, cyNetworkNaming.getSuggestedNetworkTitle(inputFileName));

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

            // Specific to Mark's work, placeholder UUID
            List<String> anno_set = Arrays.asList("11a6e1a3-da96-498f-8d79-1af6dab80158");
            myRow.set("__CCD_Annotation_Set", anno_set);
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
