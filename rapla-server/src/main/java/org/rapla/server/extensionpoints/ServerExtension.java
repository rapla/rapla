package org.rapla.server.extensionpoints;


/**
 * a class implementing server extension is started automatically when the server is up and running and connected to a data store.
 */

public interface ServerExtension {
    void start();
    void stop();
}
