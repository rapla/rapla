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
package org.rapla.client.swing.internal.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.io.IOException;
import java.net.URL;

import javax.inject.Inject;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.rapla.client.RaplaWidget;
import org.rapla.components.util.IOUtil;
import org.rapla.framework.internal.ConfigTools;

public class LicenseUI 
    implements
        RaplaWidget
{
    JPanel panel = new JPanel();
    BorderLayout borderLayout1 = new BorderLayout();
    GridLayout gridLayout2 = new GridLayout();
    FlowLayout flowLayout1 = new FlowLayout();
    JTextPane license = new JTextPane();
    JScrollPane jScrollPane = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                                              JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    @Inject
    public LicenseUI() {
        panel.setOpaque(true);
        panel.setLayout(borderLayout1);
        panel.add(jScrollPane,BorderLayout.CENTER);
        license.setOpaque(false);
        license.setEditable(false);
        panel.setPreferredSize(new Dimension(640,400));
 
        try {
            String text = getLicense();
            license.setText(text);
        } catch (IOException ex) {
            license.setText(ex.getMessage());
        }
        license.revalidate();
 
    }

    public JComponent getComponent() {
        return panel;
    }

    private String getLicense() throws IOException {
        URL url= ConfigTools.class.getClassLoader().getResource("META-INF/license.txt");
        return new String(IOUtil.readBytes(url),"UTF-8");
    }

    public void showTop() {
        final JViewport viewport = new JViewport();
        viewport.setView(license);
        jScrollPane.setViewport(viewport);
        SwingUtilities.invokeLater( new Runnable() {
            
            @Override
            public void run() {
                viewport.setViewPosition(new Point(0,0));
                
            }
        });
    }

    public void showBottom() {
        JViewport viewport = new JViewport();
        viewport.setView(license);
        jScrollPane.setViewport(viewport);
        Dimension dim = viewport.getViewSize();
        viewport.setViewPosition(new Point(dim.width,dim.height));
    }


 }



