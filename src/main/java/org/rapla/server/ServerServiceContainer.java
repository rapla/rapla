package org.rapla.server;

import org.rapla.framework.Disposable;
import org.rapla.jsonrpc.server.WebserviceCreator;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.StorageOperator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface ServerServiceContainer extends Disposable, HttpService
{
    Collection<ServletRequestPreprocessor> getServletRequestPreprocessors();

    StorageOperator getOperator();

    String getFirstAdmin();

    <T> T getMockService(final Class<T> test, final String accessToken);

}
