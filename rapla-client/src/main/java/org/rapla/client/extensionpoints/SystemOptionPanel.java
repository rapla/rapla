package org.rapla.client.extensionpoints;

import org.rapla.client.swing.OptionPanel;

/** You can add additional option panels for the editing the system preferences
 * @see org.rapla.entities.configuration.Preferences
 * @see OptionPanel
 * */

public interface SystemOptionPanel extends OptionPanel {
    String ID = "org.rapla.SystemOptions";
}

