package org.rapla.plugin.copyurl;

import io.reactivex.functions.Consumer;
import org.rapla.client.PopupContext;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.SelectionMenuContext;
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
import org.rapla.facade.client.ClientFacade;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashSet;

@Singleton
@Extension(provides = ObjectMenuFactory.class, id="copyurl")
public class CopyUrlMenuFactory implements ObjectMenuFactory
{

    private final MenuItemFactory menuItemFactory;
    private final ClientFacade clientFacade;
    private final URLCopyService copyService;

    @Inject
    public CopyUrlMenuFactory(ClientFacade clientFacade, MenuItemFactory menuItemFactory, URLCopyService copyService)
    {
        this.clientFacade = clientFacade;
        this.menuItemFactory = menuItemFactory;
        this.copyService = copyService;
    }

    public IdentifiableMenuEntry[] create(final SelectionMenuContext menuContext, final RaplaObject focusedObject)
    {
        if (!clientFacade.isAdmin())
        {
            return IdentifiableMenuEntry.EMPTY_ARRAY;
        }

        Collection<Object> selectedObjects = new HashSet<>();
        Collection<?> selected = menuContext.getSelectedObjects();
        if (selected.size() != 0)
        {
            selectedObjects.addAll(selected);
        }
        if (focusedObject != null)
        {
            selectedObjects.add(focusedObject);
        }

        String link = null;
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
                            final Object value = classification.getValueForAttribute( attribute );
                            if ( value != null)
                            {
                                final String url = Tools.getUrl( value.toString());
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
            return IdentifiableMenuEntry.EMPTY_ARRAY;
        }
        final String url = link;
        Consumer<PopupContext> action = (popupContext)-> copyService.copy(url);
        IdentifiableMenuEntry item = menuItemFactory.createMenuItem("Copy Link", null, action);
        return new IdentifiableMenuEntry[] {item};
    }

    public void showException(Exception ex) {

    }
}
