package edu.pitt.dbmi.ccd.cytoscape.tetrad;

import edu.cmu.tetrad.graph.EdgeTypeProbability;
import java.util.List;

/**
 * Class for storing edges parsed from tetrad output file
 *
 * Author : Jeremy Espino MD Created 7/1/16 11:19 AM
 *
 *
 */
public class Edge {

    private String source;

    private String target;

    private String type;

    private List<EdgeTypeProbability> edgeTypeProbabilities;

    public Edge() {
    }

    public Edge(String source, String target, String type, List<EdgeTypeProbability> edgeTypeProbabilities) {
        this.source = source;
        this.target = target;
        this.type = type;
        this.edgeTypeProbabilities = edgeTypeProbabilities;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<EdgeTypeProbability> getEdgeTypeProbabilities() {
        return edgeTypeProbabilities;
    }

    public void setEdgeTypeProbabilities(List<EdgeTypeProbability> edgeTypeProbabilities) {
        this.edgeTypeProbabilities = edgeTypeProbabilities;
    }

}
