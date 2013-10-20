package org.rapla.server.internal;

import java.util.Map;

import org.rapla.server.RemoteSession;


public interface RemoteServiceDispatcher {

    byte[] dispatch(RemoteSession remoteSession, String methodName, Map<String, String> parameterMap) throws Exception;

}
