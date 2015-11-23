package org.rapla.server;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Christopher on 23.11.2015.
 */
public interface HttpService
{
    void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
}
