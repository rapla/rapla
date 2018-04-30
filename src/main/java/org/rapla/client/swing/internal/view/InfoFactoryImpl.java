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

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.internal.AllocatableInfoUI;
import org.rapla.client.internal.AppointmentInfoUI;
import org.rapla.client.internal.CategoryInfoUI;
import org.rapla.client.internal.DynamicTypeInfoUI;
import org.rapla.client.internal.HTMLInfo;
import org.rapla.client.internal.PeriodInfoUI;
import org.rapla.client.internal.ReservationInfoUI;
import org.rapla.client.internal.UserInfoUI;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.HTMLView;
import org.rapla.components.iolayer.ComponentPrinter;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.HashMap;
import java.util.Map;

/** The factory can creatres an information-panel or dialog for
the entities of rapla.
@see ViewTable*/
@Singleton
@DefaultImplementation(of=InfoFactory.class, context = InjectionContext.swing)
public class InfoFactoryImpl extends RaplaGUIComponent implements InfoFactory

{
    Map<Class,HTMLInfo> views = new HashMap<>();
    private final IOInterface ioInterface;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public InfoFactoryImpl(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, AppointmentFormater appointmentFormater, IOInterface ioInterface,  DialogUiFactoryInterface dialogUiFactory) {
        super(clientFacade, i18n, raplaLocale, logger);
        RaplaFacade facade = clientFacade.getRaplaFacade();
        this.ioInterface = ioInterface;
        this.dialogUiFactory = dialogUiFactory;
        views.put( DynamicType.class, new DynamicTypeInfoUI(clientFacade, i18n, raplaLocale, logger) );
        views.put( Reservation.class, new ReservationInfoUI(i18n, raplaLocale, facade, logger, appointmentFormater)
                {
                    // Special usecase, because we want to use export name in all other names
                    @Override
                    protected String getAllocatableName(Allocatable allocatable)
                    {
                        final String exportName = NameFormatUtil.getName(allocatable, getRaplaLocale().getLocale());
                        return exportName;
                    }
                }
        );
        views.put( Appointment.class, new AppointmentInfoUI(i18n, raplaLocale, facade, logger, appointmentFormater) );
        views.put( Allocatable.class, new AllocatableInfoUI(clientFacade, i18n, raplaLocale, logger) );
        views.put( User.class, new UserInfoUI(clientFacade, i18n, raplaLocale, logger) );
        views.put( Period.class, new PeriodInfoUI(facade, i18n, raplaLocale, logger) );
        views.put( Category.class, new CategoryInfoUI(facade, i18n, raplaLocale, logger) );
    }

    /** this method is used by the viewtable to dynamicaly createInfoDialog an
     * appropriate HTMLInfo for the passed object
     */
    <T extends RaplaObject> HTMLInfo<T> createView( T object ) throws RaplaException {
        if ( object == null )
            throw new RaplaException( "Could not createInfoDialog view for null object" );

        @SuppressWarnings("unchecked")
		HTMLInfo<T> result =  views.get( object.getTypeClass() );
        if (result != null)
                return result;
        throw new RaplaException( "Could not createInfoDialog view for this object: " + object.getClass() );
    }

    @Override
    public String getToolTip(Object obj) {
        return getToolTip(obj,true);
    }

    @Override
    public String getToolTip(Object obj,boolean wrapHtml) {
        try {
            if ( !(obj instanceof RaplaObject))
            {
                return null;
            }
            RaplaObject o = (RaplaObject )obj;
            if ( !views.containsKey( o.getTypeClass()))
            {
                return null;
            }
            String text = createView( o ).getTooltip( o, getUser());
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
     * @see org.rapla.client.swing.gui.view.IInfoUIFactory#showInfoDialog(java.lang.Object, java.awt.ServerComponent, java.awt.Point)
     */
    @Override
    public <T> void showInfoDialog( T object, PopupContext popupContext )
            throws RaplaException
    {

        final ViewTable<T> viewTable = new ViewTable<>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), this, ioInterface, dialogUiFactory);
        final DialogInterface dlg = dialogUiFactory.createContentDialog(popupContext
                ,
                viewTable.getComponent()
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
        dlg.start( true );

        dlg.getAction(0).setRunnable( new Runnable() {
            private static final long serialVersionUID = 1L;

            public void run() {
                try {
                    DataFlavor.getTextPlainUnicodeFlavor();
                    viewTable.htmlView.selectAll();
                    String plainText = viewTable.htmlView.getSelectedText();
                    //String htmlText = viewTable.htmlView.getText();
                    //InfoSelection selection = new InfoSelection( htmlText, plainText );
                    StringSelection selection = new StringSelection( plainText );
                    ioInterface.setContents( selection, null);
                } catch (Exception ex) {
                    dialogUiFactory.showException(ex, new SwingPopupContext((Component)dlg, null));
                }
            }
        });
        dlg.getAction(1).setRunnable( new Runnable() {
            private static final long serialVersionUID = 1L;

            public void run() {
                try {
                    HTMLView htmlView = viewTable.htmlView;
                    ioInterface.print(
                            new ComponentPrinter(htmlView, htmlView.getPreferredSize())
                            , ioInterface.defaultPage()
                            ,true
                    );
                } catch (Exception ex) {
                    dialogUiFactory.showException(ex, new SwingPopupContext((Component)dlg, null));
                }
            }
        });
    }

    /*
    public <T> Component createInfoComponent( T object ) throws RaplaException {
        ViewTable<T> viewTable = new ViewTable<T>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), this, ioInterface, dialogUiFactory);
        viewTable.updateInfo( object );
        return viewTable.getComponent();
    }*/

}

