/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.toolkit;

import javax.swing.JTextPane;
import java.net.URL;

final public class HTMLView extends JTextPane  {
    private static final long serialVersionUID = 1L;
    
    public HTMLView() {
        setOpaque(false);
        setEditable(false);
        setContentType("text/html");
        setDefaultDocBase();
    }

    public static String DEFAULT_STYLE =
        "body {font-family:SansSerif;font-size:12;}\n"
        + ".infotable{padding:0px;margin:0px;}\n"
        + ".label {vertical-align:top;}\n"
        + ".value {vertical-align:top;}\n"
        ;
    private static URL base;
    private static Exception error = null;

    /** will only work for resources inside the same jar as org/rapla/client/swing/gui/images/repeating.png */
    private void setDefaultDocBase() {
        if (base == null && error == null) {
            try {
                String marker = "org/rapla/gui/images/repeating.png";
                URL url= HTMLView.class.getClassLoader().getResource(marker);
                if (url == null) {
                    System.err.println("Marker not found " + marker);
                    return;
                }
                //System.out.println("resource:" + url);
                String urlPath = url.toString();
                base = new URL(urlPath.substring(0,urlPath.lastIndexOf(marker)));
                //System.out.println("document-base:" + base);
            } catch (Exception ex) {
                error = ex;
                System.err.println("Can't get document-base: " + ex + " in class: " + HTMLView.class.getName());
            }
        }

        if (error == null)
            ((javax.swing.text.html.HTMLDocument)getDocument()).setBase(base);
    }

    /** calls setText(createHTMLPage(body)) */
    public void setBody(String body) {
        try {
            setText(createHTMLPage(body));
        } catch (Exception ex) {
            setText(body);
        }
    }
    static public String createHTMLPage(String body,String styles) {
        String buf = "<html>" +
                "<head>" +
                "<style type=\"text/css\">" +
                styles +
                "</style>" +
                "</head>" +
                "<body>" +
                body +
                "</body>" +
                "</html>";
        return buf;
    }

    static public String createHTMLPage(String body) {
        return createHTMLPage(body,DEFAULT_STYLE);
    }

    public void setText( String message, boolean packText )
    {
        if (packText) {
            JEditorPaneWorkaround.packText(this, message ,600);
        } else {
            setText( message);
        }
    }

}
