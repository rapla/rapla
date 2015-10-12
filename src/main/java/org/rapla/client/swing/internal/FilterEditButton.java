package org.rapla.client.swing.internal;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.event.ChangeListener;

import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.framework.RaplaContext;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.ClassifiableFilterEdit;
import org.rapla.client.swing.toolkit.DialogUI;

public class FilterEditButton extends RaplaGUIComponent
{
    protected RaplaArrowButton filterButton;
    JWindow popup;
    ClassifiableFilterEdit ui;
        
    public FilterEditButton(final RaplaContext context,final ClassifiableFilter filter, final ChangeListener listener, final boolean  isResourceSelection) 
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
                    if ( ui != null && listener != null)
                    {
                        ui.removeChangeListener( listener);
                    }
                    ui = new ClassifiableFilterEdit( context, isResourceSelection);
                    if ( listener != null)
                    {
                    	ui.addChangeListener(listener);
                    }
                    ui.setFilter( filter);
                    final Point locationOnScreen = filterButton.getLocationOnScreen();
                    final int y = locationOnScreen.y + 18;
                    final int x = locationOnScreen.x;
                    if ( popup == null)
                    {
                    	Component ownerWindow = DialogUI.getOwnerWindow(filterButton);
                    	if ( ownerWindow instanceof Frame)
                    	{
                    		popup = new JWindow((Frame)ownerWindow);
                    	}
                    	else if ( ownerWindow instanceof Dialog)
                    	{
                    		popup = new JWindow((Dialog)ownerWindow);
                    	}
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