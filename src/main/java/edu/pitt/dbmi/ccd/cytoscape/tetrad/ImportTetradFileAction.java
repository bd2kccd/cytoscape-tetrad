package edu.pitt.dbmi.ccd.cytoscape.tetrad;

import java.awt.event.ActionEvent;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.cytoscape.application.swing.AbstractCyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.work.swing.DialogTaskManager;

public class ImportTetradFileAction extends AbstractCyAction {

    private final DialogTaskManager dialogTaskManager;
    private final CreateNetworkTaskFactory createNetworkTaskFactory;
    private static final long serialVersionUID = 1L;
    private final CySwingApplication desktopApp;
    private final CytoPanel cytoPanelWest;

    public ImportTetradFileAction(CySwingApplication desktopApp,
            final DialogTaskManager dialogTaskManager,
            final CreateNetworkTaskFactory createNetworkTaskFactory) {

        super("Tetrad");

        // File -> Import -> Network
        setPreferredMenu("File.Import.Network");

        this.desktopApp = desktopApp;
        this.cytoPanelWest = this.desktopApp.getCytoPanel(CytoPanelName.WEST);
        this.dialogTaskManager = dialogTaskManager;
        this.createNetworkTaskFactory = createNetworkTaskFactory;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        JFileChooser chooser = new JFileChooser();

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Tetrad Output File (.json)", "json");
        chooser.setFileFilter(filter);

        int returnVal = chooser.showOpenDialog(cytoPanelWest.getComponentAt(0));
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            System.out.println("You chose to open this file: " + chooser.getSelectedFile().getName());
        }

        createNetworkTaskFactory.setInputFileName(chooser.getSelectedFile().getAbsolutePath());

        dialogTaskManager.execute(createNetworkTaskFactory.createTaskIterator());
    }

}
