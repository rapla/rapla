package org.rapla.gui;

import org.rapla.framework.PluginDescriptor;

public interface PluginOptionPanel extends OptionPanel {
	Class<? extends PluginDescriptor<?>> getPluginClass();
}
