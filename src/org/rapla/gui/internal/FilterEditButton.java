package org.rapla.gui.internal;

import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.event.ChangeListener;

import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.ClassifiableFilterEdit;
import org.rapla.gui.toolkit.DialogUI;

public class FilterEditButton extends RaplaGUIComponent
{
    protected RaplaArrowButton filterButton;
    JWindow popup;
    ClassifiableFilterEdit ui;
        
    public FilterEditButton(final RaplaContext context,final CalendarSelectionModel model, final ChangeListener listener, final boolean  isResourceSelection) 
    {
        super(context);
        filterButton = new RaplaArrowButton('v');
        filterButton.setText(getString("filter"));
        filterButton.setSize(80,18);
        filterButton.addActionListener( new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
                
                if ( popup != null)
                {
                    popup.setVisible(false);
                    popup= null;
                    filterButton.setChar('v');
                    return;
                }
                try {
                    if ( ui != null)
                    {
                        ui.removeChangeListener( listener);
                    }
                    ui = new ClassifiableFilterEdit( context, isResourceSelection);
                    ui.addChangeListener(listener);
                    ui.setFilter( model);
                    final Point locationOnScreen = filterButton.getLocationOnScreen();
                    final int y = locationOnScreen.y + 18;
                    final int x = locationOnScreen.x;
                    if ( popup == null)
                    {
                    	popup = new JWindow((Frame)DialogUI.getOwnerWindow(filterButton));
                    }
                    JComponent content = ui.getComponent();
					popup.setContentPane(content );
                    popup.setSize( content.getPreferredSize());
                    popup.setLocation( x, y);
                    //.getSharedInstance().getPopup( filterButton, ui.getComponent(), x, y);
                    popup.setVisible(true);
                    filterButton.setChar('^');
                } catch (Exception ex) {
                    showException(ex, getMainComponent());
                }
            }
            
        });
        
    }
    
    public ClassifiableFilterEdit getFilterUI()
    {
    	return ui;
    }
    
    public RaplaArrowButton getButton()
    {
        return filterButton;
    }
    
}