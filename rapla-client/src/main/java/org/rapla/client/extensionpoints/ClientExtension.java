package org.rapla.client.extensionpoints;

/** classes implementing ClientExtension are started automaticaly when a user has successfully login into the Rapla system. A class added as service doesn't need to implement a specific interface and is instanciated automaticaly after client login.
 * Generally you don't need to start a client service. It is better to provide functionality through the RaplaClientExtensionPoints  
 */

public interface ClientExtension {
     String ID = "startafterlogin";
     void start();
}
