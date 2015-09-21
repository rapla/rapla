package org.rapla.gui.internal.edit.reservation;

import java.awt.Component;
import java.awt.Point;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.*;
import org.rapla.gui.images.RaplaImages;
import org.rapla.gui.internal.SwingPopupContext;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of= ReservationController.class,context = InjectionContext.swing)
@Singleton
public class ReservationControllerSwingImpl extends ReservationControllerImpl
{
    Container container;
    InfoFactory infoFactory;    
    RaplaContext context;
    Wrapper wrapper;
    RaplaImages images;

    class Wrapper extends RaplaGUIComponent
    {
        public Wrapper(RaplaContext context)
        {
            super(context);
        }

        @Override
        public PopupContext createPopupContext(Component parent, Point p)
        {
            return super.createPopupContext(parent, p);
        }
    }
    
    @Inject
    public ReservationControllerSwingImpl(RaplaContext context,ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n,
            AppointmentFormater appointmentFormater, ReservationEditFactory editProvider, CalendarSelectionModel calendarModel, RaplaClipboard clipboard, Container container,InfoFactory infoFactory, RaplaImages images)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, editProvider, calendarModel, clipboard);
        this.infoFactory = infoFactory;
        this.container = container;
        this.context = context;
        this.wrapper = new Wrapper(context);
        this.images = images;
    }

    protected boolean showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException
    {
        DialogUI dlg =infoFactory.createDeleteDialog(deletables, context);
        dlg.start();
        int result = dlg.getSelectedIndex();
        return result == 0;
    }

    protected int showDialog(String action, PopupContext popupContext, List<String> optionList, List<String> iconList, String title, String content, String dialogIcon) throws RaplaException
    {
        Point point = null;
        Component parent = null;
        if ( popupContext instanceof SwingPopupContext)
        {
            SwingPopupContext casted = (SwingPopupContext)popupContext;
            point = casted.getPoint();
            parent = casted.getParent();
        }

        DialogUI dialog = DialogUI.create(
                context
                ,parent
                ,true
                ,title
                ,content
                ,optionList.toArray(new String[] {})
        );
        if ( dialogIcon != null)
        {
            dialog.setIcon(images.getIconFromKey(dialogIcon));
        }
        for ( int i=0;i< optionList.size();i++)
        {
            final String string = iconList.get( i);
            if ( string != null)
            {
                dialog.getButton(i).setIcon(images.getIconFromKey(string));
            }
        }
        
        dialog.start(point);
        int index = dialog.getSelectedIndex();
        return index;
    }
    
    protected Collection<EventCheck> getEventChecks() throws RaplaException
    {
        Collection<EventCheck> checkers = container.lookupServicesFor(EventCheck.class);
        return checkers;
    }

    @Override
    protected PopupContext getPopupContext()
    {
        return wrapper.createPopupContext(wrapper.getMainComponent(), null);
    }

    @Override
    protected void showException(Exception ex, PopupContext sourceComponent)
    {
        wrapper.showError(ex, sourceComponent);
    }


}
