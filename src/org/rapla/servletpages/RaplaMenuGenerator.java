package org.rapla.servletpages;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

public interface RaplaMenuGenerator {
	public void generatePage(  HttpServletRequest request, PrintWriter out );
}
