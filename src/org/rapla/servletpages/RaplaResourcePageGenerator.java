/**
 * 
 */
package org.rapla.servletpages;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.components.util.IOUtil;
/** 
 * @deprecated place resources in war folder instead 
 *
 */
@Deprecated
public class RaplaResourcePageGenerator implements RaplaPageGenerator{
    Map<String,Resource> resourceMap = new HashMap<String,Resource>();
    
    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response ) throws IOException {
        String resourcename = request.getParameter("name");
        if ( resourcename == null)
        {
        	response.getWriter().println("No name parameter specified");
        	return;
        }
        Resource res =  resourceMap.get( resourcename );
        if ( res == null)
        {
        	response.getWriter().println("Can't find resource with the name '" + resourcename + "'");
        	return;
        }
        response.setContentType( res.mimetyp );
        InputStream in = res.resourceURL.openStream();
        IOUtil.copyStreams( in, response.getOutputStream());
        in.close();
    }
    
    public void registerResource( String resourcename, String mimetype, URL resourceUrl) {
        resourceMap.put( resourcename, new Resource( resourcename, mimetype, resourceUrl));
    }

    static class Resource {
        String resourcename;
        String mimetyp;
        URL resourceURL;
        
        Resource(String resourcename, String mimetyp,  URL resourceURL ) 
        {
        	this.resourcename = resourcename;
        	this.mimetyp  = mimetyp;
        	this.resourceURL = resourceURL;
        }
    }
}