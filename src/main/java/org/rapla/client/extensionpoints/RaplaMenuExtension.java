package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;

public interface RaplaMenuExtension extends IdentifiableMenuEntry
{
    boolean isEnabled();
}
