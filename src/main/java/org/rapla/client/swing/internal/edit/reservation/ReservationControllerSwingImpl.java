package org.rapla.client.swing.internal.edit.reservation;

import java.awt.Component;
import java.awt.Point;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.client.ReservationController;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.internal.ReservationControllerImpl;
import org.rapla.client.internal.ReservationEditFactory;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.PopupContext;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of= ReservationController.class,context = InjectionContext.swing)
@Singleton
public class ReservationControllerSwingImpl extends ReservationControllerImpl
{
    private final InfoFactory<Component, DialogUI> infoFactory;
    private final RaplaContext context;
    private final RaplaGUIComponent wrapper;
    private final RaplaImages images;
    private final Set<Provider<EventCheck>> checkers;
    
    @Inject
    public ReservationControllerSwingImpl(RaplaContext context,ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n,
            AppointmentFormater appointmentFormater, Provider<ReservationEditFactory> editProvider, CalendarSelectionModel calendarModel, RaplaClipboard clipboard,Set<Provider<EventCheck>> checkers,InfoFactory<Component, DialogUI> infoFactory, RaplaImages images, PermissionController permissionController)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, editProvider, calendarModel, clipboard, permissionController);
        this.infoFactory = infoFactory;
        this.context = context;
        this.wrapper = new RaplaGUIComponent(context);
        this.images = images;
        this.checkers = checkers;
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

    @Override
    protected Set<Provider<EventCheck>> getEventChecks() throws RaplaException
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
        wrapper.showError(ex, sourceComponent);
    }


}
