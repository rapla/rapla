package org.rapla.server;

import org.rapla.framework.Disposable;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.StorageOperator;

import java.util.Collection;

public interface ServerServiceContainer extends Disposable, HttpService
{
    Collection<ServletRequestPreprocessor> getServletRequestPreprocessors();

    StorageOperator getOperator();

    String getFirstAdmin();

    <T> T getMockService(final Class<T> test, final String accessToken);

}
