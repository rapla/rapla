package org.rapla.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by Christopher on 23.11.2015.
 */
public interface HttpService
{
    void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
}
