package org.rapla.gui.internal.edit.reservation;

import java.awt.Component;
import java.awt.Point;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.ImageIcon;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.EventCheck;
import org.rapla.gui.InfoFactory;
import org.rapla.gui.PopupContext;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.SwingPopupContext;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.gui.toolkit.DialogUI;

public class ReservationControllerSwingImpl extends ReservationControllerImpl
{
    Container container;
    InfoFactory infoFactory;    
    RaplaContext context;
    Wrapper wrapper;

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
    public ReservationControllerSwingImpl(RaplaContext context,ClientFacade facade, RaplaLocale raplaLocale, Logger logger, @Named(RaplaComponent.RaplaResourcesId) I18nBundle i18n,
            AppointmentFormater appointmentFormater, ReservationEditFactory editProvider, CalendarSelectionModel calendarModel, RaplaClipboard clipboard, Container container,InfoFactory infoFactory)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, editProvider, calendarModel, clipboard);
        this.infoFactory = infoFactory;
        this.container = container;
        this.context = context;
        this.wrapper = new Wrapper(context);
    }

    protected boolean showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException
    {
        DialogUI dlg =infoFactory.createDeleteDialog(deletables, context);
        dlg.start();
        int result = dlg.getSelectedIndex();
        return result == 0;
    }

    protected int showDialog(String action, PopupContext popupContext, List<String> optionList, List<ImageIcon> iconList, String title, String content, ImageIcon dialogIcon) throws RaplaException
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
        dialog.setIcon(dialogIcon);
        for ( int i=0;i< optionList.size();i++)
        {
            dialog.getButton(i).setIcon(iconList.get( i));
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
