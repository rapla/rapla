package org.rapla.client.swing.internal.edit.reservation;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.internal.ReservationControllerImpl;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of= ReservationController.class,context = InjectionContext.swing)
@Singleton
public class ReservationControllerSwingImpl extends ReservationControllerImpl
{
    private final InfoFactory infoFactory;
    private final RaplaGUIComponent wrapper;
    private final RaplaImages images;
    private final Provider<Set<EventCheck>> checkers;
    private final DialogUiFactoryInterface dialogUiFactory;
    
    @Inject
    public ReservationControllerSwingImpl(ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n,
            AppointmentFormater appointmentFormater, CalendarSelectionModel calendarModel, RaplaClipboard clipboard,Provider<Set<EventCheck>> checkers,InfoFactory infoFactory, RaplaImages images,DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, calendarModel, clipboard);
        this.infoFactory = infoFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.wrapper = new RaplaGUIComponent(facade, i18n, raplaLocale, logger);
        this.images = images;
        this.checkers = checkers;
    }

    protected boolean showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException
    {
        DialogInterface dlg =infoFactory.createDeleteDialog(deletables, context);
        dlg.start(true);
        int result = dlg.getSelectedIndex();
        return result == 0;
    }

    protected int showDialog(String action, PopupContext popupContext, List<String> optionList, List<String> iconList, String title, String content, String dialogIcon) throws RaplaException
    {
        Point point = null;
        if ( popupContext instanceof SwingPopupContext)
        {
            SwingPopupContext casted = (SwingPopupContext)popupContext;
            point = casted.getPoint();
        }

        DialogInterface dialog = dialogUiFactory.create(
                popupContext
                ,true
                ,title
                ,content
                ,optionList.toArray(new String[] {})
        );
        if ( dialogIcon != null)
        {
            dialog.setIcon(dialogIcon);
        }
        for ( int i=0;i< optionList.size();i++)
        {
            final String string = iconList.get( i);
            if ( string != null)
            {
                dialog.getAction(i).setIcon(string);
            }
        }
        dialog.setPosition(point.getX(), point.getY());
        dialog.start(true);
        int index = dialog.getSelectedIndex();
        return index;
    }

    @Override
    protected Provider<Set<EventCheck>> getEventChecks()
    {
        return checkers;
    }

    @Override
    protected PopupContext getPopupContext()
    {
        return new SwingPopupContext(wrapper.getMainComponent(), null);
    }

    @Override
    protected void showException(Exception ex, PopupContext sourceComponent)
    {
        dialogUiFactory.showError(ex, sourceComponent);
    }


}
