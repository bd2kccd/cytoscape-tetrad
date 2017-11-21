package edu.pitt.dbmi.ccd.cytoscape.tetrad;

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

            // read json file
            String contents = new String(Files.readAllBytes(file));

            // parse to graph
            Graph graph = JsonUtils.parseJSONObjectToTetradGraph(contents);

            // extract the edges
            Set<edu.cmu.tetrad.graph.Edge> edges = graph.getEdges();

            // for each edge determine the types of endpoints so you can figure out edge type
            // basically convert to these -->  o-o o->
            for (edu.cmu.tetrad.graph.Edge edge : edges) {
                String edgeType = "";

                Endpoint endpoint1 = edge.getEndpoint1();
                Endpoint endpoint2 = edge.getEndpoint2();

                if (endpoint1 == Endpoint.ARROW || endpoint1 == Endpoint.CIRCLE) {
                    continue;
                }
//                switch (endpoint1) {
//                    case Endpoint.ARROW:
//                        continue;
//                    case Endpoint.CIRCLE:
//                        continue;
//
//                }

                cytoEdges.add(new Edge(edge.getNode1().getName(), edge.getNode2().getName(), edgeType));

                // extract the probability of an edge - find out what column name in cytoscape Mark needs is in
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

//        try (BufferedReader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
//            Pattern space = Pattern.compile("\\s+");
//            boolean isData = false;
//            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
//                line = line.trim();
//                if (isData) {
//                    String[] data = space.split(line, 2);
//                    if (data.length == 2) {
//                        String value = data[1].trim();
//                        String[] edgeValues = space.split(value);
//                        if (edgeValues.length == 3) {
//                            CharSequence source = edgeValues[0].trim();
//                            CharSequence target = edgeValues[2].trim();
//                            CharSequence type = edgeValues[1].trim();
//                            nodes.add(new Edge(source.toString(), target.toString(), type.toString()));
//                        }
//                    }
//                } else if ("Graph Edges:".equals(line)) {
//                    isData = true;
//                }
//            }
//        } catch (IOException exception) {
//            // TODO: use cytoscape logger
//            System.err.println(String.format("Unable to read file '%s'. \n%s", fileName, exception));
//        }
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
