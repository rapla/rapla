/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.gui.internal.print;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.print.PageFormat;

import javax.swing.SwingUtilities;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.RaplaAction;


public class PrintAction extends RaplaAction {
    CalendarSelectionModel model;
    PageFormat m_pageFormat;
    public PrintAction(RaplaContext sm) {
        super( sm);
        setEnabled(false);
        putValue(NAME,getString("print"));
        putValue(SMALL_ICON,getIcon("icon.print"));
    }

    public void setModel(CalendarSelectionModel settings) {
        this.model = settings;
        setEnabled(settings != null);
    }


    public void setPageFormat(PageFormat pageFormat) {
        m_pageFormat = pageFormat;
    }

    public void actionPerformed(ActionEvent evt) {
        Component parent = getMainComponent();
        try {
            boolean modal = true;
            final CalendarPrintDialog dialog = CalendarPrintDialog.create(getContext(),parent,modal, model, m_pageFormat);
            final Dimension dimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setSize(new Dimension(
                                        Math.min(dimension.width,900)
                                        ,Math.min(dimension.height-10,700)
                                        )
                          );
            
            SwingUtilities.invokeLater( new Runnable() {
                public void run()
                {
                    dialog.setSize(new Dimension(
                                             Math.min(dimension.width,900)
                                             ,Math.min(dimension.height-11,699)
                                             )
                               );
                }
                
            }
            );
            dialog.startNoPack();
            
        } catch (Exception ex) {
            showException(ex, parent);
        }
    }
}

