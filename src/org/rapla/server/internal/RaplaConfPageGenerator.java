package org.rapla.server.internal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.RaplaMainContainer;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.servletpages.RaplaPageGenerator;

public class RaplaConfPageGenerator extends RaplaComponent implements RaplaPageGenerator{

    public RaplaConfPageGenerator(RaplaContext context) {
        super(context);
    }
 
    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response ) throws IOException {
        java.io.PrintWriter out = response.getWriter();
        response.setContentType("application/xml;charset=utf-8");
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        
        out.println("<rapla-config>");
        Configuration conf = getService(RaplaMainContainer.RAPLA_MAIN_CONFIGURATION);
        //<!-- Use this to customize the rapla resources
        //<default-bundle>org.rapla.MyResources</default-bundle>
        //-->
        Configuration localeConf = conf.getChild("locale");
        if ( localeConf != null)
        {
            printConfiguration( out, localeConf);
        }
        Configuration[] bundles = conf.getChildren("default-bundle");
        for ( Configuration bundle: bundles)
        {
            printConfiguration( out, bundle);
        }
        
        String remoteId = null;
        Configuration[] clients = conf.getChildren("rapla-client");
        for ( Configuration client: clients)
        {
            if ( client.getAttribute("id", "").equals( "client"))
            {
                remoteId = client.getChild("facade").getChild("store").getValue( "remote");
                printConfiguration( out, client);
                break;
            }
        }
        if ( remoteId != null)
        {
            Configuration[] storages = conf.getChildren("remote-storage");
            for ( Configuration storage: storages)
            {
                if ( storage.getAttribute("id", "").equals( remoteId))
                {
                    printConfiguration( out, storage);
                }
            }
        }
        else
        {
            // Config not found use default
            out.println("<rapla-client id=\"client\">");
            out.println(" <facade id=\"facade\">");
            out.println("   <store>remote</store>");
            out.println(" </facade>");
            out.println("</rapla-client>");
            out.println(" ");
            out.println("<remote-storage id=\"remote\">");
            out.println("  <server>${download-url}</server>");
            out.println("</remote-storage>");
        }
        out.println(" ");
        out.println("</rapla-config>");
        out.close();
     }

    private void printConfiguration(PrintWriter out, Configuration conf) throws IOException {
        ConfigurationWriter configurationWriter = new ConfigurationWriter();
        BufferedWriter writer = new BufferedWriter( out);
        configurationWriter.setWriter( writer);
        configurationWriter.printConfiguration( conf);
        writer.flush();
    }

    class ConfigurationWriter extends org.rapla.components.util.xml.XMLWriter
    {
        private void printConfiguration(Configuration element) throws IOException {
            LinkedHashMap<String, String> attr = new LinkedHashMap<String, String>();
            String[] attrNames = element.getAttributeNames();
    
            if( null != attrNames )
            {
                for( int i = 0; i < attrNames.length; i++ )
                {
                    String key = attrNames[ i ];
                    String value = element.getAttribute( attrNames[ i ], "" );
                    attr.put(key,value);
                }
            }
    
            String qName = element.getName();
            openTag(qName);
            att(attr);
            Configuration[] children = element.getChildren();
            if (children.length > 0)
            {
                closeTag();
                for( int i = 0; i < children.length; i++ )
                {
                    printConfiguration( children[ i ] );
                }
                closeElement(qName);
            }
            else
            {
                String value = element.getValue( null );
                if (null == value)
                {
                    closeElementTag();
                }
                else
                {
                    closeTagOnLine();
                    print(value);
                    closeElementOnLine(qName);
                    println();
                }
            }
        }
            
    }

    
    
}
