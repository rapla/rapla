package org.rapla.client.gwt;

import org.rapla.inject.client.gwt.GwtComponentMarker;

public interface GwtStarter extends GwtComponentMarker
{
    void startApplication();
    void registerJavascriptApi();
}
