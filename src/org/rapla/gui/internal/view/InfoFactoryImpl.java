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
package org.rapla.gui.internal.view;

import java.awt.Component;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JComponent;

import org.rapla.components.iolayer.ComponentPrinter;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.InfoFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.HTMLView;
/** The factory can creatres an information-panel or dialog for
the entities of rapla.
@see ViewTable*/
public class InfoFactoryImpl extends RaplaGUIComponent implements InfoFactory

{
    Map<RaplaType,HTMLInfo> views = new HashMap<RaplaType,HTMLInfo>();

    public InfoFactoryImpl(RaplaContext sm) {
        super( sm);
        views.put( DynamicType.TYPE, new DynamicTypeInfoUI(sm) );
        views.put( Reservation.TYPE, new ReservationInfoUI(sm) );
        views.put( Appointment.TYPE, new AppointmentInfoUI(sm) );
        views.put( Allocatable.TYPE, new AllocatableInfoUI(sm) );
        views.put( User.TYPE, new UserInfoUI(sm) );
        views.put( Period.TYPE, new PeriodInfoUI(sm) );
        views.put( Category.TYPE, new CategoryInfoUI(sm) );
    }

    /** this method is used by the viewtable to dynamicaly create an
     * appropriate HTMLInfo for the passed object
     */
    <T extends RaplaObject> HTMLInfo<T> createView( T object ) throws RaplaException {
        if ( object == null )
            throw new RaplaException( "Could not create view for null object" );

        @SuppressWarnings("unchecked")
		HTMLInfo<T> result =  views.get( object.getRaplaType() );
        if (result != null)
                return result;
        throw new RaplaException( "Could not create view for this object: " + object.getClass() );
    }

    public <T> JComponent createInfoComponent( T object ) throws RaplaException {
        ViewTable<T> viewTable = new ViewTable<T>(getContext());
        viewTable.updateInfo( object );
        return viewTable.getComponent();
    }

    public String getToolTip(Object obj) {
        return getToolTip(obj,true);
    }

    public String getToolTip(Object obj,boolean wrapHtml) {
        try {
            if ( !(obj instanceof RaplaObject)) 
            {
                return null;
            }
            RaplaObject o = (RaplaObject )obj;
            if ( !views.containsKey( o.getRaplaType()))
            {
            	return null;
            }
            String text = createView( o ).getTooltip( o);
            if (wrapHtml && text != null)
                return HTMLView.createHTMLPage( text );
            else
                return text;
        } catch(RaplaException ex) {
            getLogger().error( ex.getMessage(), ex );
        }
        if (obj instanceof Named)
            return ((Named) obj).getName(getI18n().getLocale());
        return null;
    }

    /* (non-Javadoc)
     * @see org.rapla.gui.view.IInfoUIFactory#showInfoDialog(java.lang.Object, java.awt.Component)
     */
    public void showInfoDialog( Object object, Component owner )
    throws RaplaException
    {
        showInfoDialog( object, owner, null);
    }

    /* (non-Javadoc)
     * @see org.rapla.gui.view.IInfoUIFactory#showInfoDialog(java.lang.Object, java.awt.Component, java.awt.Point)
     */
    public <T> void showInfoDialog( T object, Component owner, Point point )
        throws RaplaException
    {
       
        final ViewTable<T> viewTable = new ViewTable<T>(getContext());
        final DialogUI dlg = DialogUI.create(getContext(),owner
                                       ,false
                                       ,viewTable.getComponent()
                                       ,new String[] {
        						getString( "copy_to_clipboard" )
								,getString( "print" )
								,getString( "back" )
            });

        if ( !(object instanceof RaplaObject)) {
            viewTable.updateInfoHtml( object.toString());
        }
        else
        {
            @SuppressWarnings("unchecked")
			HTMLInfo<RaplaObject<T>> createView = createView((RaplaObject<T>)object);
			@SuppressWarnings("unchecked")
			final HTMLInfo<T> view = (HTMLInfo<T>) createView;
			viewTable.updateInfo( object, view );
        }
        dlg.setTitle( viewTable.getDialogTitle() );
        dlg.setDefault(2);
        dlg.start( point );

        dlg.getButton(0).setAction( new AbstractAction() {
            private static final long serialVersionUID = 1L;
            
            public void actionPerformed(ActionEvent e) {
                try {
                	DataFlavor.getTextPlainUnicodeFlavor();
                	viewTable.htmlView.selectAll();
                	String plainText = viewTable.htmlView.getSelectedText();
                	//String htmlText = viewTable.htmlView.getText();
                	//InfoSelection selection = new InfoSelection( htmlText, plainText );
                	StringSelection selection = new StringSelection( plainText );
                	IOInterface printTool = getService(IOInterface.class);
                    printTool.setContents( selection, null);
                } catch (Exception ex) {
                    showException(ex, dlg);
                }
            }
        });
        dlg.getButton(1).setAction( new AbstractAction() {
            private static final long serialVersionUID = 1L;
            
            public void actionPerformed(ActionEvent e) {
                try {
                    IOInterface printTool = getService(IOInterface.class);
                    HTMLView htmlView = viewTable.htmlView;
					printTool.print(
                            new ComponentPrinter(htmlView, htmlView.getPreferredSize())
                            , printTool.defaultPage()
                            ,true
                    );
                } catch (Exception ex) {
                    showException(ex, dlg);
                }
            }
        });
    }

    /* (non-Javadoc)
     * @see org.rapla.gui.view.IInfoUIFactory#createDeleteDialog(java.lang.Object[], java.awt.Component)
     */
    public DialogUI createDeleteDialog( Object[] deletables, Component owner ) throws RaplaException {
        ViewTable<Object[]> viewTable = new ViewTable<Object[]>(getContext());
        DeleteInfoUI deleteView = new DeleteInfoUI(getContext());
        DialogUI dlg = DialogUI.create(getContext(),owner
                                       ,true
                                       ,viewTable.getComponent()
                                       ,new String[] {
                                           getString( "delete.ok" )
                                           ,getString( "delete.abort" )
                                       });
        dlg.setIcon( getIcon("icon.warning") );
        dlg.getButton( 0).setIcon(getIcon("icon.delete") );
        dlg.getButton( 1).setIcon(getIcon("icon.abort") );
        dlg.setDefault(1);
        viewTable.updateInfo( deletables, deleteView );
        dlg.setTitle( viewTable.getDialogTitle() );
        return dlg;
    }
}

