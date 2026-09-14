package org.rapla.client.extensionpoints;

import org.rapla.client.swing.OptionPanel;

/** You can add additional option panels for editing the user preference.
 * @see org.rapla.entities.configuration.Preferences
 * @see OptionPanel
 * */

public interface UserOptionPanel extends OptionPanel {

    String ID = "org.rapla.UserOptions";

    boolean isEnabled();
    
}

