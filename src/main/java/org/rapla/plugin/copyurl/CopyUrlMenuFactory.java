package org.rapla.plugin.copyurl;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.security.AccessControlException;
import java.util.Collection;
import java.util.HashSet;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.swing.SwingMenuContext;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.ErrorDialog;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.Tools;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Singleton
@Extension(provides = ObjectMenuFactory.class, id="copyurl")
public class CopyUrlMenuFactory extends RaplaGUIComponent implements ObjectMenuFactory
{

    private final IOInterface ioInterface;
    private final Provider<ErrorDialog> errorDialogProvider;

    @Inject
    public CopyUrlMenuFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface,
            Provider<ErrorDialog> errorDialogProvider)
    {
        super(facade, i18n, raplaLocale, logger);
        this.ioInterface = ioInterface;
        this.errorDialogProvider = errorDialogProvider;
    }

    public RaplaMenuItem[] create(final SwingMenuContext menuContext, final RaplaObject focusedObject)
    {
        if (!isAdmin())
        {
            return RaplaMenuItem.EMPTY_ARRAY;
        }

        Collection<Object> selectedObjects = new HashSet<Object>();
        Collection<?> selected = menuContext.getSelectedObjects();
        if (selected.size() != 0)
        {
            selectedObjects.addAll(selected);
        }
        if (focusedObject != null)
        {
            selectedObjects.add(focusedObject);
        }

        URL link = null;
        for (Object obj : selectedObjects)
        {
            final Classifiable ownable;
            if (obj instanceof AppointmentBlock)
            {
                ownable = ((AppointmentBlock) obj).getAppointment().getReservation();
            }
            else if (obj instanceof Entity)
            {

                Class<? extends Entity> raplaType = ((RaplaObject) obj).getTypeClass();
                if (raplaType == Appointment.class)
                {
                    Appointment appointment = (Appointment) obj;
                    ownable = appointment.getReservation();
                }
                else if (raplaType == Reservation.class)
                {
                    ownable = (Reservation) obj;
                }
                else if (raplaType == Allocatable.class)
                {
                    ownable = (Allocatable) obj;
                }
                else
                {
                    ownable = null;
                }
            }
            else
            {
                ownable = null;
            }
            if (ownable != null)
            {
                final Classification classification = ownable.getClassification();
                if ( classification != null)
                {
                    final Attribute[] attributes = classification.getAttributes();
                    for ( Attribute attribute: attributes)
                    {
                        if ( attribute.getType() == AttributeType.STRING)
                        {
                            final Object value = classification.getValue( attribute );
                            if ( value != null)
                            {
                                final URL url = Tools.getUrl( value.toString());
                                if ( url != null)
                                {
                                    link = url;
                                }
                            }
                        }
                    }
                            
                }
            }
        }

        if (link == null)
        {
            return RaplaMenuItem.EMPTY_ARRAY;
        }
        final String url = link.toExternalForm();
        // create the menu entry
        final RaplaMenuItem setOwnerItem = new RaplaMenuItem("copylink");
        setOwnerItem.setText("Copy Link");
        setOwnerItem.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    copy_(url);
                }
                catch (RaplaException ex)
                {
                    ErrorDialog dialog;
                    dialog = errorDialogProvider.get();
                    final SwingPopupContext popupContext = (SwingPopupContext) menuContext.getPopupContext();
                    dialog.showExceptionDialog(ex, popupContext.getParent());
                }
            }

            private void copy_(String link) throws RaplaException
            {
                Transferable transferable = new StringSelection(link);
                try
                {
                    if (ioInterface != null)
                    {
                        ioInterface.setContents(transferable, null);
                    }
                }
                catch (AccessControlException ex)
                {
                    //   clipboard.set( transferable);
                }

            }
        });

        return new RaplaMenuItem[] { setOwnerItem };
    }

    public void showException(Exception ex) {

    }
}
