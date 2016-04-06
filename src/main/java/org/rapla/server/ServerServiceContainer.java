package org.rapla.server;

import java.util.Collection;

import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.StorageOperator;

public interface ServerServiceContainer extends Disposable, HttpService
{
    Collection<ServletRequestPreprocessor> getServletRequestPreprocessors();

    StorageOperator getOperator();

    String getFirstAdmin() throws RaplaException;

    <T> T getMockService(final Class<T> test, final String accessToken);

}
