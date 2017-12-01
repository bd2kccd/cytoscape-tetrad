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
import org.cytoscape.view.presentation.annotations.AnnotationFactory;
import org.cytoscape.view.presentation.annotations.AnnotationManager;
import org.cytoscape.view.presentation.annotations.TextAnnotation;
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
    private final AnnotationFactory<TextAnnotation> textAnnotationFactory;
    private final AnnotationManager annotationManager;

    private final String inputFileName;

    private static final String ANNOTATIONS = "__Annotations";
    private static final String CCD_ANNOTATIONS = "__CCD_Annotations";
    private static final String CCD_ANNOTATION_SET = "__CCD_Annotation_Set";
    private static final String CCD_EXTENDED_ATTRIBUTES = "__CCD_Extended_Attributes";
    private static final String CCD_EXTENDED_ATTRIBUTES_VALUES = "__CCD_Extended_Attribute_Values";

    public CreateNetworkTask(final CyApplicationManager appMgr,
            final CyNetworkManager netMgr,
            final CyNetworkFactory netFactory,
            final CyNetworkViewManager netViewMgr,
            final CyNetworkViewFactory netViewFactory,
            final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory,
            final VisualMappingManager vizMappingMgr,
            final AnnotationFactory textAnnoFactory,
            final AnnotationManager annoMgr,
            String fileName) {

        this.cyNetworkManager = netMgr;
        this.cyNetworkFactory = netFactory;
        this.cyNetworkViewManager = netViewMgr;
        this.cyNetworkViewFactory = netViewFactory;
        this.loadVizmapFileTaskFactory = loadVizmapFileTaskFactory;
        this.visualMappingManager = vizMappingMgr;
        this.textAnnotationFactory = textAnnoFactory;
        this.annotationManager = annoMgr;
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

        CyTable myNetTable = myNet.getDefaultNetworkTable();

        // Create CCD annotaion columns in Network Table
        myNetTable.createListColumn(ANNOTATIONS, String.class, true);
        myNetTable.createListColumn(CCD_ANNOTATIONS, String.class, true);
        myNetTable.createListColumn(CCD_EXTENDED_ATTRIBUTES, String.class, true);

        // Set the name for network in Network Table
        myNet.getRow(myNet).set(CyNetwork.NAME, "Tetrad Output Network");

        // Column lists
        List<String> __Annotations = new LinkedList<>();
        List<String> __CCD_Annotations = new LinkedList<>();
        List<String> __CCD_Extended_Attributes = new LinkedList<>();

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

            // Specific to Mark's work, empty list now
            myRow.set(CCD_ANNOTATION_SET, new LinkedList<>());
            // Add to the map
            nodeName2CyNodeMap.put(tetradGraphNode.getName(), myNode);
        });

        // Edge Table
        CyTable myEdgeTable = myNet.getDefaultEdgeTable();

        // Create the "__CCD_Annotation_Set" column in Edge Table
        myEdgeTable.createListColumn(CCD_ANNOTATION_SET, String.class, true);
        // Create the "__CCD_Extended_Attribute_Values" column in Edge Table
        myEdgeTable.createListColumn(CCD_EXTENDED_ATTRIBUTES_VALUES, String.class, true);

        // Add edges
        cytoEdges.stream().forEach((Edge edge) -> {
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

            // Annotation on edge type
            Map<String, String> edgeTypeAnnoArgs = new HashMap<>();
            // Fields inherited from interface org.cytoscape.view.presentation.annotations.Annotation
            // BACKGROUND, CANVAS, FOREGROUND, X, Y, Z, ZOOM
            edgeTypeAnnoArgs.put("FONTFAMILY", "Arial");
            edgeTypeAnnoArgs.put("COLOR", String.valueOf(-16777216));
            edgeTypeAnnoArgs.put("TEXT", maxEdgeTypeProbability.getEdgeType().name());
            edgeTypeAnnoArgs.put("UUID", UUID.randomUUID().toString());

            // Annotation on edge type probablility
            Map<String, String> edgeTypeProbablityAnnoArgs = new HashMap<>();
            edgeTypeProbablityAnnoArgs.put("FONTFAMILY", "Arial");
            edgeTypeProbablityAnnoArgs.put("COLOR", String.valueOf(-16777216));
            edgeTypeProbablityAnnoArgs.put("TEXT", Double.toString(maxEdgeTypeProbability.getProbability()));
            edgeTypeProbablityAnnoArgs.put("UUID", UUID.randomUUID().toString());

            // Convert args into "key1=val1|key2=val2" string format
            String formattedEdgeTypeAnnoArgs = edgeTypeAnnoArgs.entrySet()
                    .stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(joining("|"));

            String formattedEdgeTypeProbablityAnnoArgs = edgeTypeProbablityAnnoArgs.entrySet()
                    .stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(joining("|"));

            System.out.println("formattedEdgeTypeAnnoArgs: " + formattedEdgeTypeAnnoArgs);
            System.out.println("formattedEdgeTypeProbablityAnnoArgs: " + formattedEdgeTypeProbablityAnnoArgs);

            myRow.set(CCD_ANNOTATION_SET, new LinkedList<>());
            myRow.set(CCD_EXTENDED_ATTRIBUTES_VALUES, new LinkedList<>());

            // Specific to Mark's work, bar separated values
            __Annotations.add(formattedEdgeTypeAnnoArgs);
            __Annotations.add(formattedEdgeTypeProbablityAnnoArgs);

            // Add UUID of edgeTypeAnno and edgeTypeProbablityAnno
            __CCD_Annotations.add(edgeTypeAnnoArgs.get("UUID"));
            __CCD_Annotations.add(edgeTypeProbablityAnnoArgs.get("UUID"));

            // Pending, placeholders with dummy data
            __CCD_Extended_Attributes.add("id=1|name=edge type|type=string|description=Type of edge");
            __CCD_Extended_Attributes.add("id=2|name=edge type probability|type=float|description=Probability of certainty for edge type");
        });

        // Add all value specified columns in Network Table
        myNet.getRow(myNet).set(ANNOTATIONS, __Annotations);
        myNet.getRow(myNet).set(CCD_ANNOTATIONS, __CCD_Annotations);
        myNet.getRow(myNet).set(CCD_EXTENDED_ATTRIBUTES, __CCD_Extended_Attributes);

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
