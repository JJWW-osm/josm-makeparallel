// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.makeparallel;

import javax.swing.JMenuItem;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.makeparallel.actions.MakeParallelAction;

/**
 * This is the main class for the makeparallel plugin.
 *
 */
public class MakeParallelPlugin extends Plugin {

    JMenuItem MakeParallelTag;

    public MakeParallelPlugin(PluginInformation info) {
        super(info);
        MakeParallelTag = MainMenu.add(MainApplication.getMenu().moreToolsMenu, new MakeParallelAction());
    }

    /**
     * Called when the JOSM map frame is created or destroyed.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        boolean enabled = newFrame != null;
        MakeParallelTag.setEnabled(enabled);
    }
}


// EOF
