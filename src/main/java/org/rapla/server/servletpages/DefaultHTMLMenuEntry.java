package org.rapla.server.servletpages;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;

public class DefaultHTMLMenuEntry  implements RaplaMenuGenerator
{
    protected String name;
    protected String linkName;

    public DefaultHTMLMenuEntry(String name, String linkName)
    {
    	this.name = name;
        this.linkName = linkName;
    }
    
    public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLinkName() {
		return linkName;
	}

	public void setLinkName(String linkName) {
		this.linkName = linkName;
	}

    public void generatePage(  HttpServletRequest request, PrintWriter out ) 
    {
		// writing the html code line for a button
		// including the link to the appropriate servletpage
		out.println("<span class=\"button\"><a href=\"" + getLinkName() + "\">" + getName() + "</a></span>");
    }

}
