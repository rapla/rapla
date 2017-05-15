package org.rapla.client.extensionpoints;

import org.rapla.client.swing.OptionPanel;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** You can add a specific configuration panel for your plugin.
 * Note if you add a pluginOptionPanel you need to provide the PluginClass as hint.
 * @see org.rapla.entities.configuration.Preferences
 * @see OptionPanel
 * */
@ExtensionPoint(context = InjectionContext.swing,id = PluginOptionPanel.ID)
public interface PluginOptionPanel extends OptionPanel {
    String ID ="org.rapla.plugin.Option";
}

