package org.rapla.client.menu;

import jsinterop.annotations.JsType;
import org.rapla.client.RaplaWidget;

/** Adds an id to the standard Swing Menu ServerComponent as JSeperator, JMenuItem and JMenu*/
@JsType
public interface IdentifiableMenuEntry extends RaplaWidget
{
    String getId();

    IdentifiableMenuEntry[] EMPTY_ARRAY  = new IdentifiableMenuEntry[]{
    };
}
