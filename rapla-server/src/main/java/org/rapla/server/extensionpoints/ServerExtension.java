package org.rapla.server.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/**
 * a class implementing server extension is started automatically when the server is up and running and connected to a data store.
 */
@ExtensionPoint(context = InjectionContext.server,id="serverextension")
public interface ServerExtension {
    void start();
    void stop();
}
