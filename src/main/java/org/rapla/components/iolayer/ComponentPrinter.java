/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender                                     |
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
package org.rapla.components.iolayer;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;

import javax.swing.RepaintManager;

/** Use this to print an awt-Component on one page.
 */ 
public class ComponentPrinter implements Printable
{
  private Component component;
  protected Dimension scaleToFit;

  public ComponentPrinter(Component c, Dimension scaleToFit)
  {
    component= c;
    this.scaleToFit = scaleToFit;
  }


  protected boolean setPage( int pageNumber) 
  {
	  // default is one page
	  return pageNumber == 0;
  }
  
  public int print(Graphics g, PageFormat format, int pagenumber) throws PrinterException
  {
	
    if (!setPage( pagenumber)) { return Printable.NO_SUCH_PAGE; }
    Graphics2D g2 = (Graphics2D) g;
    if ( scaleToFit != null) {
        scaleToFit(g2, format, scaleToFit);
    }
    RepaintManager rm = RepaintManager.currentManager(component);
    boolean db= rm.isDoubleBufferingEnabled();
    try {
        rm.setDoubleBufferingEnabled(false);
        component.printAll(g2);
    } finally {
        rm.setDoubleBufferingEnabled(db);
    }
    return Printable.PAGE_EXISTS;
  }

  private static void scaleToFit(Graphics2D g, PageFormat format, Dimension dim)
  {
      scaleToFit(g, format, dim.getWidth(),dim.getHeight());
  }
  
  public static void scaleToFit(Graphics2D g, PageFormat format, double width, double height)
  {
	  g.translate(format.getImageableX(), format.getImageableY());
	  double sx = format.getImageableWidth() / width;
      double sy = format.getImageableHeight() / height;
      if (sx < sy) { sy = sx; } else { sx = sy; }
      g.scale(sx, sy);
  }
}






