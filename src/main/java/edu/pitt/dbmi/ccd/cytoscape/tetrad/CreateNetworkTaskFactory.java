package edu.pitt.dbmi.ccd.cytoscape.tetrad;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.task.read.LoadVizmapFileTaskFactory;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

public class CreateNetworkTaskFactory extends AbstractTaskFactory {

    private final CyApplicationManager cyApplicationManager;
    private final CyNetworkManager cyNetworkManager;
    private final CyNetworkFactory cyNetworkFactory;
    private final CyNetworkViewFactory cyNetworkViewFactory;
    private final CyNetworkViewManager cyNetworkViewManager;
    private final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory;
    private final VisualMappingManager visualMappingManager;
    private String inputFileName;

    public CreateNetworkTaskFactory(final CyApplicationManager cyApplicationManager,
            final CyNetworkManager cyNetworkManager,
            final CyNetworkFactory cyNetworkFactory,
            final CyNetworkViewFactory cyNetworkViewFactory,
            final CyNetworkViewManager cyNetworkViewManager,
            final LoadVizmapFileTaskFactory loadVizmapFileTaskFactory,
            final VisualMappingManager visualMappingManager) {

        this.cyApplicationManager = cyApplicationManager;
        this.cyNetworkManager = cyNetworkManager;
        this.cyNetworkFactory = cyNetworkFactory;
        this.cyNetworkViewFactory = cyNetworkViewFactory;
        this.cyNetworkViewManager = cyNetworkViewManager;
        this.loadVizmapFileTaskFactory = loadVizmapFileTaskFactory;
        this.visualMappingManager = visualMappingManager;
    }

    public void setInputFileName(String inputFileName) {
        this.inputFileName = inputFileName;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new CreateNetworkTask(cyApplicationManager,
                cyNetworkManager,
                cyNetworkFactory,
                cyNetworkViewManager,
                cyNetworkViewFactory,
                loadVizmapFileTaskFactory,
                visualMappingManager,
                inputFileName
        ));
    }
}
