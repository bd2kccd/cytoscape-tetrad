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
import static java.util.stream.Collectors.joining;
import org.cytoscape.application.CyApplicationManager;
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

    private static final String NETWORK_NAME = "Tetrad Output Network";
    private static final String CCD_ANNOTATIONS = "__CCD_Annotations";
    private static final String CCD_ANNOTATION_SET = "__CCD_Annotation_Set";

    public CreateNetworkTask(final CyApplicationManager appMgr,
            final CyNetworkManager netMgr,
            final CyNetworkFactory netFactory,
            final CyNetworkViewManager netViewMgr,
            final CyNetworkViewFactory netViewFactory,
            final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory,
            final VisualMappingManager vizMappingMgr,
            String fileName) {

        this.cyNetworkManager = netMgr;
        this.cyNetworkFactory = netFactory;
        this.cyNetworkViewManager = netViewMgr;
        this.cyNetworkViewFactory = netViewFactory;
        this.loadVizmapFileTaskFactory = loadVizmapFileTaskFactory;
        this.visualMappingManager = vizMappingMgr;
        this.inputFileName = fileName;
    }

    public Graph extractTetradGraphFromFile(final String fileName) {
        Graph tetradGraph = null;

        Path file = Paths.get(fileName);

        try {
            // Read Tetrad generated json file
            String contents = new String(Files.readAllBytes(file));

            // Parse to Tetrad graph
            tetradGraph = JsonUtils.parseJSONObjectToTetradGraph(contents);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tetradGraph;
    }

    public List<Edge> extractEdgesFromTetradGraph(Graph tetradGraph) {
        List<Edge> cytoEdges = new LinkedList<>();

        // Extract the edges, this is the tetrad graph edges.
        // We'll convert them into cyto Edge
        Set<edu.cmu.tetrad.graph.Edge> tetradGraphEdges = tetradGraph.getEdges();

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

        return cytoEdges;
    }

    @Override
    public void run(TaskMonitor monitor) {
        Graph tetradGraph = extractTetradGraphFromFile(inputFileName);

        // Get nodes from tetrad graph directly
        List<Node> tetradGraphNodes = tetradGraph.getNodes();
        // Need to convert the tetrad edges into cyto edge that can be used later
        List<Edge> cytoEdges = extractEdgesFromTetradGraph(tetradGraph);

        // Create the cytoscape network
        CyNetwork myNet = cyNetworkFactory.createNetwork();

        // Network Table
        CyTable myNetTable = myNet.getDefaultNetworkTable();

        // Set the name for network in Network Table
        myNet.getRow(myNet).set(CyNetwork.NAME, NETWORK_NAME);

        // Create "__CCD_Annotaions" column in Network Table
        myNetTable.createListColumn(CCD_ANNOTATIONS, String.class, true);

        // Column list
        List<String> __CCD_Annotations = new LinkedList<>();

        // Node Table
        CyTable myNodeTable = myNet.getDefaultNodeTable();

        // Create the "__CCD_Annotation_Set" column in Node Table
        myNodeTable.createListColumn(CCD_ANNOTATION_SET, String.class, true);

        // Add nodes
        HashMap<String, CyNode> nodeName2CyNodeMap = new HashMap<>();

        tetradGraphNodes.stream().forEach((Node tetradGraphNode) -> {
            CyNode myNode = myNet.addNode();

            CyRow myRow = myNodeTable.getRow(myNode.getSUID());

            // Set name for new node
            myRow.set(CyNetwork.NAME, tetradGraphNode.getName());

            // Empty list now since we don't have annotations tied to nodes
            myRow.set(CCD_ANNOTATION_SET, new LinkedList<>());

            // Add to the map
            nodeName2CyNodeMap.put(tetradGraphNode.getName(), myNode);
        });

        // Store all the unique edge types and their corresponding UUIDs for later reuse
        HashMap<String, String> edgeType2UUIDMap = new HashMap<>();

        // Edge Table
        CyTable myEdgeTable = myNet.getDefaultEdgeTable();

        // Create the "__CCD_Annotation_Set" column in Edge Table
        myEdgeTable.createListColumn(CCD_ANNOTATION_SET, String.class, true);

        // Add edges
        cytoEdges.stream().forEach((Edge edge) -> {
            // Column list
            List<String> __CCD_Annotation_Set = new LinkedList<>();

            // Set "interaction" and "name" in each row
            CyEdge myEdge = myNet.addEdge(nodeName2CyNodeMap.get(edge.getSource()), nodeName2CyNodeMap.get(edge.getTarget()), true);
            CyRow myRow = myEdgeTable.getRow(myEdge.getSUID());
            myRow.set(CyEdge.INTERACTION, edge.getType());
            myRow.set(CyNetwork.NAME, edge.getSource() + " (" + edge.getType() + ") " + edge.getTarget());

            // Get edgeTypeProbabilities
            List<EdgeTypeProbability> edgeTypeProbabilities = edge.getEdgeTypeProbabilities();

            // EdgeTypeProbability that has the max probablity value and EdgeType is not nil
            EdgeTypeProbability maxEdgeTypeProbability = null;

            // Find the max edge type probablity if generated by bootstraping
            if (!edgeTypeProbabilities.isEmpty()) {
                // Comparator
                final Comparator<EdgeTypeProbability> comparator = (etp1, etp2) -> Double.compare(etp1.getProbability(), etp2.getProbability());

                // Find the EdgeTypeProbability that has the max probablity value and EdgeType is not nil
                maxEdgeTypeProbability = edgeTypeProbabilities.stream()
                        .filter(edgeTypeProbability -> !edgeTypeProbability.getEdgeType().equals(EdgeTypeProbability.EdgeType.nil))
                        .max(comparator)
                        .get();
            }

            // Generate a new UUID for this edge type if not found
            String edgeTypeName = maxEdgeTypeProbability.getEdgeType().name();

            if (edgeType2UUIDMap.get(edgeTypeName) == null) {
                // Generate new UUID
                String a_id = UUID.randomUUID().toString();
                // Add to map for later reuse
                edgeType2UUIDMap.put(edgeTypeName, a_id);

                // Use LinkedHashMap to keeps the keys in the order they were inserted
                Map<String, String> maxEdgeTypeAnnoArgs = new LinkedHashMap<>();

                // A unique identifier for the annotation (separate from the Cytoscape generated annotation uuid)
                maxEdgeTypeAnnoArgs.put("uuid", a_id);
                // The naming information for the annotation (for example: tt, ta edge types)
                maxEdgeTypeAnnoArgs.put("name", edgeTypeName);
                // The type of the value (string, float, char, bool)
                maxEdgeTypeAnnoArgs.put("type", "float");
                // A description for the annotation name
                maxEdgeTypeAnnoArgs.put("description", "The edge type that has max probability value");

                // Convert args into "key1=val1|key2=val2" string format
                String formattedEdgeTypeAnnoArgs = maxEdgeTypeAnnoArgs.entrySet()
                        .stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(joining("|"));

                // Add to the "__CCD_Annotations" column in Network Table
                __CCD_Annotations.add(formattedEdgeTypeAnnoArgs);
            }

            // Each item in __CCD_Annotation_Set
            Map<String, String> annoSetArgs = new LinkedHashMap<>();
            // The CCD annotation uuid from __CCD_Annotations
            annoSetArgs.put("a_id", edgeType2UUIDMap.get(edgeTypeName));
            // The Cytoscape annotation uuid from __Annotations (leave this blank)
            annoSetArgs.put("cy_id", "");
            // The type of the value is provided by the type property of the CCD annotation mapped by a_id
            annoSetArgs.put("value", Double.toString(maxEdgeTypeProbability.getProbability()));

            // Convert args into "key1=val1|key2=val2" string format
            String formattedAnnoSetArgs = annoSetArgs.entrySet()
                    .stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(joining("|"));

            // Add to "__CCD_Annotation_Set" column in Edge Table
            __CCD_Annotation_Set.add(formattedAnnoSetArgs);
            myRow.set(CCD_ANNOTATION_SET, __CCD_Annotation_Set);
        });

        // Add all items to "__CCD_Annotations" column in the Network Table
        myNet.getRow(myNet).set(CCD_ANNOTATIONS, __CCD_Annotations);

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
