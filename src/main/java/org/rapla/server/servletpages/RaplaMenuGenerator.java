package org.rapla.server.servletpages;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;

public interface RaplaMenuGenerator
{
	void generatePage(HttpServletRequest request, PrintWriter out);
	boolean isEnabled();
}
