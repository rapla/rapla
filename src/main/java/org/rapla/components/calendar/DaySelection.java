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

package org.rapla.components.calendar;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.UIManager;

import org.rapla.components.calendar.DateRenderer.RenderingInfo;
/** The graphical date-selection field
 *  @author Christopher Kohlhaas
 */

final class DaySelection extends JComponent implements DateChangeListener,MouseListener {
    private static final long serialVersionUID = 1L;

    // 7  rows and 7 columns
    static final int ROWS = 7; //including the header row
    static final int COLUMNS = 7;
    // 6 * 7 Fields for the Month + 7 Fields for the Header
    static final int FIELDS = (ROWS - 1) * COLUMNS;

    static final int HBORDER = 0; // horizontal BORDER of the component
    static final int VBORDER = 0; // vertical BORDER of the component
    static final int VGAP = 2; // vertical gap between the cells
    static final int HGAP = 2; // horizontal gap between the cells

    static final Color DARKBLUE = new Color(0x00, 0x00, 0x88);
    static final Color LIGHTGRAY = new Color(0xdf, 0xdf, 0xdf);


    static final Color FOCUSCOLOR = UIManager.getColor("Button.focus");
    static final Color DEFAULT_CELL_BACKGROUND = Color.white;
    static final Color HEADER = Color.white;
    static final Color HEADER_BACKGROUND = DARKBLUE;
    static final Color SELECTION = Color.white;
    static final Color SELECTION_BACKGROUND = DARKBLUE;
    static final Color SELECTION_BORDER = Color.red;
    static final Color SELECTABLE = Color.black;
    static final Color UNSELECTABLE = LIGHTGRAY;
    /** stores the weekdays order e.g so,mo,tu,.. or mo,tu.
     * Requirement: The week has seven days
     */
    private int[] weekday2slot = new int[8];
    /** stores the weekdayNames mon - sun.
     * Requirement: The week has seven days.
    */
    private String[] weekdayNames  = new String[7];


    /** stores the days 1 - 31 days[1] = 1
     * <br>maximum days per month 40
     */
    private String[] days = new String[40];



    private int selectedField = 10;
    private int focusedField = 10;
    private DateModel model;
    private DateModel focusModel;

    private int lastFocusedMonth; // performance improvement
    private int lastFocusedYear;  // performance improvement


    private PaintEnvironment e;
    private DateRenderer dateRenderer = null;

    public DaySelection(DateModel model,DateModel focusModel) {
        this.model = model;
        this.focusModel = focusModel;
        focusModel.addDateChangeListener(this);
        addMouseListener(this);
        createWeekdays(model.getLocale());
        createDays(model.getLocale());
        //      setFont(DEFAULT_FONT);
    }

    /** Implementation-specific. Should be private.*/
    public void mousePressed(MouseEvent me) {
        me.consume();
        selectField(me.getX(),me.getY());
    }
    /** Implementation-specific. Should be private.*/
    public void mouseReleased(MouseEvent me) {
        me.consume();
    }

    /** Implementation-specific. Should be private.*/
    public void mouseClicked(MouseEvent me) {
        me.consume();
    }

    /** Implementation-specific. Should be private.*/
    public void mouseEntered(MouseEvent me) {
    }

    /** You must call javax.swing.ToolTipManager.sharedInstance().registerComponent(thisComponent)
        to enable ToolTip text.
     */
    public String getToolTipText(MouseEvent me) {
        int day = calcDay(me.getX(),me.getY());
        if (day >=0 && dateRenderer != null)
            return dateRenderer.getRenderingInfo(focusModel.getWeekday(day), day, focusModel.getMonth(), focusModel.getYear()).getTooltipText();
        else
            return "";
    }

    /** Implementation-specific. Should be private.*/
    public void mouseExited(MouseEvent me) {
    }


    public void setDateRenderer(DateRenderer dateRenderer) {
        this.dateRenderer = dateRenderer;
    }

    // Recalculate the Components minimum and maximum sizes
    private void calculateSizes()  {
        if (e == null)
            e = new PaintEnvironment();
        int maxWidth =0;
        FontMetrics fm = getFontMetrics(getFont());
        e.fontHeight = fm.getHeight();
        e.cellHeight = fm.getHeight() + VGAP *2;

        for (int i=0;i<weekdayNames.length;i++) {
            int len = fm.stringWidth(weekdayNames[i]);
            if (len>maxWidth)
                maxWidth = len;
        }
        if (fm.stringWidth("33")> maxWidth)
            maxWidth = fm.stringWidth("33");

        e.fontWidth = maxWidth;
        e.cellWidth = maxWidth + HGAP * 2 ;
        int width = COLUMNS * e.cellWidth + HBORDER *2;
        setPreferredSize(new Dimension( width,(ROWS) * e.cellHeight  + 2*HBORDER));
        setMinimumSize(new Dimension( width,(ROWS) * e.cellHeight  + 2*VBORDER));
        invalidate();
    }

    public void setFont(Font font) {
        super.setFont(font);
        if (font == null)
            return;
        else
            // We need the font-size to calculate the component size
            calculateSizes();
    }

    public Dimension getPreferredSize() {
        if (getFont() != null && e==null)
            // We need the font-size to calculate the component size
            calculateSizes();
        return super.getPreferredSize();
    }

    public void dateChanged(DateChangeEvent evt) {
        if (e==null)
            // We need the font-size to calculate the component size
            if (getFont() != null )
                calculateSizes();
            else
                return;

        int lastSelectedField = selectedField;
        if (model.getMonth() == focusModel.getMonth() &&
            model.getYear() == focusModel.getYear())
            // #BUGFIX consider index shift.
            // weekdays 1..7 and days 1..31 but field 0..40
            selectedField = weekday2slot[model.firstWeekday()]+ (model.getDay() - 1) ;
        else
            selectedField = -1;

        int lastFocusedField = focusedField;
        // #BUGFIX consider index shift.
        // weekdays 1..7 and days 1..31 but field 0..40
        int newFocusedField = weekday2slot[focusModel.firstWeekday()] + (focusModel.getDay() - 1) ;

        if (lastSelectedField == selectedField
            && lastFocusedMonth == focusModel.getMonth()
            && lastFocusedYear == focusModel.getYear()
            )  {
            // Only repaint the focusedField (Performance improvement)
            focusedField = -1;
            calculateRectangle(lastFocusedField);
            repaint(e.r);
            focusedField = newFocusedField;
            calculateRectangle(focusedField);
            repaint(e.r);
        } else {
            // repaint everything
            focusedField = newFocusedField;
            repaint();
        }
        lastFocusedMonth = focusModel.getMonth();
        lastFocusedYear = focusModel.getYear();
    }

    /** calculate the day of month from the specified field. 1..31
     */
    private int calcDay(int field) {
        return (field + 1) - weekday2slot[focusModel.firstWeekday()] ;
    }

    /** calculate the day of month from the x and y coordinates.
        @return -1 if coordinates don't point to a day in the selected month
     */
    private int calcDay(int x,int y) {
        int col = (x - HBORDER) / e.cellWidth;
        int row = (y - VBORDER) / e.cellHeight;
        int field = 0;
        //        System.out.println(row + ":" + col);
        if ( row > 0 && col < COLUMNS && row < ROWS) {
            field =(row-1) * COLUMNS + col;
        }
        if (belongsToMonth(field)) {
            return calcDay(field);
        } else {
            return -1;
        }
    }


    private void createDays(Locale locale) {
        NumberFormat format = NumberFormat.getInstance(locale);
        // Try to simluate a right align of the numbers with a space
        for ( int i=0; i <10; i++)
            days[i] = " " + format.format(i);
        for ( int i=10; i < 40; i++)
            days[i] = format.format(i);
    }

    /** This method is necessary to shorten the names down to 2 characters.
        Some date-formats ignore the setting.
    */
    private String small(String string) {
        return string.substring(0,Math.min(string.length(),2));
    }

    private void createWeekdays(Locale locale ) {
        Calendar calendar = Calendar.getInstance(locale);
        SimpleDateFormat format = new SimpleDateFormat("EE",locale);
        calendar.set(Calendar.DAY_OF_WEEK,calendar.getFirstDayOfWeek());
        for (int i=0;i<7;i++) {
            weekday2slot[calendar.get(Calendar.DAY_OF_WEEK)] = i;
            weekdayNames[i] = small(format.format(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_WEEK,1);
        }
    }

    // calculates the rectangle for a given field
    private void calculateRectangle(int field) {
        int row = (field / COLUMNS) + 1;
        int col = (field % COLUMNS);
        calculateRectangle(row,col);
    }


    // calculates the rectangle for a given row and col
    private void calculateRectangle(int row,int col) {
        Rectangle r = e.r;
        r.x = e.cellWidth * col + HBORDER;
        r.y = e.cellHeight * row + VBORDER;
        r.width = e.cellWidth;
        r.height = e.cellHeight;
    }

    private void calculateTextPos(int field) {
        int row = (field / COLUMNS) + 1;
        int col = (field % COLUMNS);
        calculateTextPos(row,col);
    }

    // calculates the text-anchor for a giver field
    private void calculateTextPos(int row,int col) {
        Point p = e.p;
        p.x = e.cellWidth * col + HBORDER + (e.cellWidth - e.fontWidth)/2;
        p.y = e.cellHeight * row  + VBORDER + e.fontHeight - 1;
    }

    private boolean belongsToMonth(int field) {
        return (calcDay(field) > 0 && calcDay(field) <= focusModel.daysMonth());
    }

    private void selectField(int x,int y) {
        int day = calcDay(x,y);

        if ( day >=0 ) {
            model.setDate(day, focusModel.getMonth(), focusModel.getYear());
        } // end of if ()
    }


    private Color getBackgroundColor(RenderingInfo renderingInfo) {
        Color color = null;
        if ( renderingInfo != null)
        {
        	color = renderingInfo.getBackgroundColor();
        }
        if (color != null)
            return color;
    
        return DEFAULT_CELL_BACKGROUND;
    }

	protected RenderingInfo getRenderingInfo(int field) {
		RenderingInfo renderingInfo = null;
    	int day = calcDay(field);
        if (belongsToMonth(field) && dateRenderer != null) {
        	renderingInfo = dateRenderer.getRenderingInfo(focusModel.getWeekday(day), day, focusModel.getMonth(), focusModel.getYear());
        }
		return renderingInfo;
	}

    // return the Daytext, that should be displayed in the specified field
    private String getText(int field) {
        int index = 0;

        if ( calcDay(field) < 1)
            index = focusModel.daysLastMonth() + calcDay(field) ;

        if (belongsToMonth(field))
            index = calcDay(field);

        if ( calcDay(field) >  focusModel.daysMonth() )
            index = calcDay(field) - focusModel.daysMonth();

        return days[index];
    }

    static char[] test = {'1'};
    private void drawField(int field,boolean highlight) {
        if (field <0)
            return;
        Graphics g = e.g;
        Rectangle r = e.r;
        Point p = e.p;

        calculateRectangle(field);
        RenderingInfo renderingInfo = getRenderingInfo( field);
        if ( field == selectedField ) {
            g.setColor(SELECTION_BACKGROUND);
            g.fillRect(r.x +2 ,r.y +2 ,r.width-4,r.height-4);
            g.setColor(SELECTION_BORDER);
            //g.drawRect(r.x,r.y,r.width-1,r.height-1);
            g.drawRect(r.x+1,r.y+1,r.width-3,r.height-3);
            if ( field == focusedField )
            {
                g.setColor(FOCUSCOLOR);
            }
            else
            {
                g.setColor(getBackgroundColor(renderingInfo));
            }
            g.drawRect(r.x,r.y,r.width-1,r.height-1);
            g.setColor(SELECTION);
        } else if ( field == focusedField ) {
            g.setColor(getBackgroundColor(renderingInfo));
            g.fillRect(r.x,r.y,r.width -1,r.height -1);
            g.setColor(FOCUSCOLOR);
            g.drawRect(r.x,r.y,r.width-1,r.height-1);
            if ( highlight) {
                g.setColor(SELECTABLE);
            } else {
                g.setColor(UNSELECTABLE);
            } // end of else
        } else {
            g.setColor(getBackgroundColor(renderingInfo));
            g.fillRect(r.x ,r.y ,r.width ,r.height);
            if ( highlight) {
                g.setColor(SELECTABLE);
            } else {
                g.setColor(UNSELECTABLE);
            } // end of else
        }
        calculateTextPos(field);
        if  ( field!= selectedField)
        {
        	if ( renderingInfo != null)
        	{
        		Color foregroundColor = renderingInfo.getForegroundColor();
        		if ( foregroundColor != null)
        		{
        			g.setColor( foregroundColor);
        		}
        	}
        }
        g.drawString(getText(field)
                     , p.x , p.y);
    }

    private void drawHeader() {
        Graphics g = e.g;
        Point p = e.p;

        g.setColor(HEADER_BACKGROUND);
        g.fillRect(HBORDER,VBORDER, getSize().width - 2 * HBORDER, e.cellHeight);
        g.setColor(HEADER);
        for ( int i=0; i < COLUMNS; i++) {
            calculateTextPos(0,i);
            g.drawString(weekdayNames[i], p.x,p.y);
        }
    }

    private void paintComplete() {
        drawHeader();
        int field = 0;
        int start = weekday2slot[focusModel.firstWeekday()];
        int end= start + focusModel.daysMonth() -1 ;
        for ( int i=0; i< start; i++) {
            drawField(field ++,false);
        }

        for ( int i= start; i <= end ; i++) {
            drawField(field ++,true);
        }

        for ( int i= end + 1; i< FIELDS ; i++) {
            drawField(field ++,false);
        }
    }

    private void updateEnvironment(Graphics g) {
        e.cellWidth = (getSize().width - HBORDER * 2) / COLUMNS;
        e.g = g;
    }

    public void paint(Graphics g) {
        super.paint(g);
        if (e==null)
            if (getFont() != null )
                // We need the font-size to calculate the component size
                calculateSizes();
            else
                return;
        updateEnvironment(g);
        paintComplete();
        drawField(selectedField,true);
        drawField(focusedField,true);
    }

}

// Environment stores the values that would normaly be stored on the stack.
// This is to speedup performance, e.g. new Point and new Rectangle are only called once.
class PaintEnvironment {
    Graphics g;
    int cellWidth;
    int cellHeight;
    int fontWidth;
    int fontHeight;
    Point p = new Point();
    Rectangle r = new Rectangle();
}




