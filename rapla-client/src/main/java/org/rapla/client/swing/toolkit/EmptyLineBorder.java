/**
 * 
 */
package org.rapla.client.swing.toolkit;

import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

public class EmptyLineBorder implements Border {
    Insets insets = new Insets(0,0,0,0);
    Color COLOR = Color.LIGHT_GRAY;
    public void paintBorder( Component c, Graphics g, int x, int y, int width, int height )
    {
        g.setColor( COLOR );
        g.drawLine(30,8, c.getWidth(), 8);

    }

    public Insets getBorderInsets( Component c )
    {
        return insets;
    }

    public boolean isBorderOpaque()
    {
        return true;
    }
}