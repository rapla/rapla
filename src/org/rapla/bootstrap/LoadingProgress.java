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
package org.rapla.bootstrap;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.ImageObserver;
import java.net.URL;

import javax.swing.JProgressBar;

final public class LoadingProgress {
	static LoadingProgress instance;
	JProgressBar progressBar;
    Frame frame;
    ImageObserver observer;
    Image image;
    Component canvas;

    int maxValue;

    static public LoadingProgress getInstance()
    {
    	if ( instance == null)
    	{
    		instance = new LoadingProgress();
    	}
    	return instance;
   }
    
    public boolean isStarted()
    {
    	return frame != null;
    }
    
	/** this method creates the progress bar */
	public void start(int startValue, int maxValue)
	{
		this.maxValue = maxValue;
		frame = new Frame()
		{
			private static final long	serialVersionUID	= 1L;
			
			public void paint(Graphics g)
			{
				super.paint(g);
				g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			}
		};
		observer = new ImageObserver()
		{
			public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h)
			{
				if ((flags & ALLBITS) != 0)
				{
					canvas.repaint();
				}
				return (flags & (ALLBITS | ABORT | ERROR)) == 0;
			}
		};
		frame.setBackground(new Color(255, 255, 204));
		canvas = new Component()
		{
			private static final long	serialVersionUID	= 1L;
			
			public void paint(Graphics g)
			{
				g.drawImage(image, 0, 0, observer);
			}
		};
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		URL url = LoadingProgress.class.getResource("/org/rapla/bootstrap/tafel.png");
		
		// Variables (integer pixels) for...
		// ... the width of the border around the picture
		int borderwidth = 4;
		// ... the height of the border around the picture
		int borderheight = 4;
		// ... the width of the picture within the frame
		int picturewidth = 356;
		// ... the height of the picture within the frame
		int pictureheight = 182;
		// ... calculating the frame width
		int framewidth = borderwidth + borderheight + picturewidth;
		// ... calculating the frame height
		int frameheight = borderwidth + borderheight + pictureheight;
		// ... width of the loading progress bar
		int progresswidth = 150;
		// ... height of the loading progress bar
		int progressheight = 15;
		
		image = toolkit.createImage(url);
		frame.setResizable(false);
		frame.setLayout(null);
		
		progressBar = new JProgressBar(0, maxValue);
		progressBar.setValue(startValue);
		// set the bounds to position the progressbar and set width and height
		progressBar.setBounds(158, 130, progresswidth, progressheight);
		progressBar.repaint();
		
		// we have to add the progressbar first to get it infront of the picture
		frame.add(progressBar);
		frame.add(canvas);
		
		// width and height of the canvas equal to those of the picture inside
		// but here we need the borderwidth as X and the borderheight as Y value
		canvas.setBounds(borderwidth, borderheight, picturewidth, pictureheight);
		try
		{
			// If run on jdk < 1.4 this will throw a MethodNotFoundException
			// frame.setUndecorated(true);
			Frame.class.getMethod("setUndecorated", new Class[] { boolean.class }).invoke(frame, new Object[] { new Boolean(true) });
		}
		catch (Exception ex)
		{
		}
		// we grab the actual size of the screen
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		// to make sure the starting dialog is positioned in the middle of
		// the screen and has the width and height we specified for the frame
		frame.setBounds((screenSize.width / 2) - (picturewidth / 2), (screenSize.height / 2) - (pictureheight / 2), framewidth, frameheight);
		frame.validate();
		frame.setVisible(true);
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		MediaTracker mt = new MediaTracker(frame);
		mt.addImage(image, 0);
		try
		{
			mt.waitForID(0);
		}
		catch (InterruptedException e)
		{
		}
	}

    public void advance() {
    	if ( frame == null)
    	{
    		return;
    	}
        int oldValue = progressBar.getValue();
        if (oldValue < maxValue)
            progressBar.setValue(oldValue + 1);
        progressBar.repaint();
    }

    public void close() {
    	if ( frame != null)
    	{
    		frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    		frame.dispose();
    		frame = null;
    	}
    }
}
