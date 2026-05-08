package org.rapla.client.extensionpoints;

import org.rapla.client.swing.OptionPanel;

/** You can add a specific configuration panel for your plugin.
 * Note if you add a pluginOptionPanel you need to provide the PluginClass as hint.
 * @see org.rapla.entities.configuration.Preferences
 * @see OptionPanel
 * */

public interface PluginOptionPanel extends OptionPanel {
    String ID ="org.rapla.plugin.Option";
}

