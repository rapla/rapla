package org.rapla.client.extensionpoints;

import org.rapla.client.swing.toolkit.IdentifiableMenuEntry;

public interface RaplaMenuExtension extends IdentifiableMenuEntry
{
    boolean isEnabled();
}
