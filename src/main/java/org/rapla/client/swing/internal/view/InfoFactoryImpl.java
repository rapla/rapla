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

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.swing.AbstractAction;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.internal.AllocatableInfoUI;
import org.rapla.client.internal.AppointmentInfoUI;
import org.rapla.client.internal.CategoryInfoUI;
import org.rapla.client.internal.DeleteInfoUI;
import org.rapla.client.internal.DynamicTypeInfoUI;
import org.rapla.client.internal.HTMLInfo;
import org.rapla.client.internal.PeriodInfoUI;
import org.rapla.client.internal.ReservationInfoUI;
import org.rapla.client.internal.UserInfoUI;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.HTMLView;
import org.rapla.components.iolayer.ComponentPrinter;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

/** The factory can creatres an information-panel or dialog for
the entities of rapla.
@see ViewTable*/
@DefaultImplementation(of=InfoFactory.class, context = InjectionContext.swing)
public class InfoFactoryImpl extends RaplaGUIComponent implements InfoFactory<Component, DialogUI>

{
    Map<RaplaType,HTMLInfo> views = new HashMap<RaplaType,HTMLInfo>();
    private final IOInterface ioInterface;
    private final RaplaImages raplaImages;
    private final DialogUiFactory dialogUiFactory;

    @Inject
    public InfoFactoryImpl(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, AppointmentFormater appointmentFormater, IOInterface ioInterface, PermissionController permissionController, RaplaImages raplaImages, DialogUiFactory dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.ioInterface = ioInterface;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        views.put( DynamicType.TYPE, new DynamicTypeInfoUI(facade, i18n, raplaLocale, logger) );
        views.put( Reservation.TYPE, new ReservationInfoUI(i18n, raplaLocale, facade, logger, appointmentFormater, permissionController) );
        views.put( Appointment.TYPE, new AppointmentInfoUI(i18n, raplaLocale, facade, logger, appointmentFormater, permissionController) );
        views.put( Allocatable.TYPE, new AllocatableInfoUI(facade, i18n, raplaLocale, logger) );
        views.put( User.TYPE, new UserInfoUI(facade, i18n, raplaLocale, logger) );
        views.put( Period.TYPE, new PeriodInfoUI(facade, i18n, raplaLocale, logger) );
        views.put( Category.TYPE, new CategoryInfoUI(facade, i18n, raplaLocale, logger) );
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

    public <T> Component createInfoComponent( T object ) throws RaplaException {
        ViewTable<T> viewTable = new ViewTable<T>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), this, ioInterface, dialogUiFactory);
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
     * @see org.rapla.client.swing.gui.view.IInfoUIFactory#showInfoDialog(java.lang.Object, java.awt.Component, java.awt.Point)
     */
    public <T> void showInfoDialog( T object, PopupContext popupContext )
        throws RaplaException
    {
       
        final ViewTable<T> viewTable = new ViewTable<T>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), this, ioInterface, dialogUiFactory);
        final DialogUI dlg = dialogUiFactory.create(popupContext
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
        dlg.start( SwingPopupContext.extractPoint(popupContext) );

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
                	ioInterface.setContents( selection, null);
                } catch (Exception ex) {
                    showException(ex, dlg, dialogUiFactory);
                }
            }
        });
        dlg.getButton(1).setAction( new AbstractAction() {
            private static final long serialVersionUID = 1L;
            
            public void actionPerformed(ActionEvent e) {
                try {
                    HTMLView htmlView = viewTable.htmlView;
                    ioInterface.print(
                            new ComponentPrinter(htmlView, htmlView.getPreferredSize())
                            , ioInterface.defaultPage()
                            ,true
                    );
                } catch (Exception ex) {
                    showException(ex, dlg, dialogUiFactory);
                }
            }
        });
    }

    /* (non-Javadoc)
     * @see org.rapla.client.swing.gui.view.IInfoUIFactory#createDeleteDialog(java.lang.Object[], java.awt.Component)
     */
    public DialogUI createDeleteDialog( Object[] deletables, PopupContext popupContext ) throws RaplaException {
        ViewTable<Object[]> viewTable = new ViewTable<Object[]>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), this, ioInterface, dialogUiFactory);
        DeleteInfoUI deleteView = new DeleteInfoUI(getI18n(), getRaplaLocale(), getClientFacade(), getLogger());
        DialogUI dlg = dialogUiFactory.create(popupContext
                                       ,true
                                       ,viewTable.getComponent()
                                       ,new String[] {
                                           getString( "delete.ok" )
                                           ,getString( "delete.abort" )
                                       });
        dlg.setIcon( raplaImages.getIconFromKey("icon.warning") );
        dlg.getButton( 0).setIcon(raplaImages.getIconFromKey("icon.delete") );
        dlg.getButton( 1).setIcon(raplaImages.getIconFromKey("icon.abort") );
        dlg.setDefault(1);
        viewTable.updateInfo( deletables, deleteView );
        dlg.setTitle( viewTable.getDialogTitle() );
        return dlg;
    }
}

