package org.rapla.client.extensionpoints;

import org.rapla.client.swing.OptionPanel;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** You can add additional option panels for editing the user preference.
 * @see org.rapla.entities.configuration.Preferences
 * @see OptionPanel
 * */
@ExtensionPoint(context = InjectionContext.swing,id = "org.rapla.UserOptions")
public interface UserOptionPanel extends OptionPanel {
    
    boolean isEnabled();
    
}

