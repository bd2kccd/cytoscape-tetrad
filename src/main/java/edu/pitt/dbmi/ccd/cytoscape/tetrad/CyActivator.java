package edu.pitt.dbmi.ccd.cytoscape.tetrad;

import java.util.Properties;
import org.cytoscape.application.swing.CyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.task.read.LoadVizmapFileTaskFactory;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.swing.DialogTaskManager;
import org.osgi.framework.BundleContext;

/**
 * Each bundle app must have a class extending AbstractCyActivator that is used
 * as the execution entry point
 */
public class CyActivator extends AbstractCyActivator {

    public CyActivator() {
        super();
    }

    public void start(BundleContext bc) {

        CyNetworkManager cyNetworkManagerServiceRef = getService(bc, CyNetworkManager.class);
        CyNetworkFactory cyNetworkFactoryServiceRef = getService(bc, CyNetworkFactory.class);
        CySwingApplication cytoscapeDesktopService = getService(bc, CySwingApplication.class);
        DialogTaskManager dialogTaskManager = getService(bc, DialogTaskManager.class);
        CyNetworkViewFactory cyNetworkViewFactory = getService(bc, CyNetworkViewFactory.class);
        CyNetworkViewManager cyNetworkViewManager = getService(bc, CyNetworkViewManager.class);
        LoadVizmapFileTaskFactory loadVizmapFileTaskFactory = getService(bc, LoadVizmapFileTaskFactory.class);
        VisualMappingManager visualMappingManager = getService(bc, VisualMappingManager.class);

        CreateNetworkTaskFactory createNetworkTaskFactory = new CreateNetworkTaskFactory(cyNetworkManagerServiceRef,
                cyNetworkFactoryServiceRef, cyNetworkViewFactory, cyNetworkViewManager, loadVizmapFileTaskFactory, visualMappingManager);

        registerService(bc, createNetworkTaskFactory, TaskFactory.class, new Properties());

        ImportTetradFileAction importTetradFileAction = new ImportTetradFileAction(cytoscapeDesktopService, dialogTaskManager, createNetworkTaskFactory);

        registerService(bc, importTetradFileAction, CyAction.class, new Properties());

    }

}
