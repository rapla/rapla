package org.rapla.plugin.copyurl;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.TreeSet;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.Tools;
import org.rapla.entities.Entity;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.MenuContext;
import org.rapla.gui.ObjectMenuFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.RaplaMenuItem;

public class CopyUrlMenuFactory extends RaplaGUIComponent implements ObjectMenuFactory
{

    public CopyUrlMenuFactory(RaplaContext context)
    {
        super(context);
    }

    public RaplaMenuItem[] create(final MenuContext menuContext, final RaplaObject focusedObject)
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
                RaplaType raplaType = ((RaplaObject) obj).getRaplaType();
                if (raplaType == Appointment.TYPE)
                {
                    Appointment appointment = (Appointment) obj;
                    ownable = appointment.getReservation();
                }
                else if (raplaType == Reservation.TYPE)
                {
                    ownable = (Reservation) obj;
                }
                else if (raplaType == Allocatable.TYPE)
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
                    showException(ex, menuContext.getComponent());
                }
            }

            private void copy_(String link) throws RaplaException
            {
                Transferable transferable = new StringSelection(link);
                try
                {
                    final IOInterface service = getIOService();
                    if (service != null)
                    {
                        service.setContents(transferable, null);
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

}
