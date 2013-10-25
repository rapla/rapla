package org.rapla.gui.internal.edit;

import org.rapla.framework.PluginDescriptor;
import org.rapla.gui.OptionPanel;

public interface PluginOptionPanel extends OptionPanel {
	Class<? extends PluginDescriptor<?>> getPluginClass();
}
