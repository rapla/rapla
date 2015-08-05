package org.rapla.client.edit.reservation.sample.gwt.subviews;

import java.util.HashMap;
import java.util.Map;

import org.rapla.entities.domain.Appointment;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

public class TerminList extends FlowPanel
{
    public interface DateSelected
    {
        void selectDate(Appointment appointment);
    }

    private RaplaDate selected = null;

    public TerminList(final DateSelected selectionHandler, Appointment... appointments)
    {
        setStyleName("dateList");
        final Map<Element, RaplaDate> elementToRaplaDate = new HashMap<Element, RaplaDate>();
        addHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                final Element target = DOM.eventGetTarget(DOM.eventGetCurrentEvent());
                final RaplaDate raplaDate = tryResolve(elementToRaplaDate, target);
                if (raplaDate != null)
                {
                    if (selected != null)
                    {
                        selected.removeStyleName("selected");
                    }
                    selected = raplaDate;
                    selected.addStyleName("selected");
                    final Appointment appointment = raplaDate.getAppointment();
                    selectionHandler.selectDate(appointment);
                }
            }

            private RaplaDate tryResolve(final Map<Element, RaplaDate> elementToRaplaDate, final Element target)
            {
                if (target == TerminList.this.getElement())
                {
                    return null;
                }
                final RaplaDate raplaDate = elementToRaplaDate.get(target);
                if (raplaDate != null)
                {
                    return raplaDate;
                }
                return tryResolve(elementToRaplaDate, target.getParentElement());
            }
        }, ClickEvent.getType());
        if (appointments == null || appointments.length == 0)
        {
            //initial explanation text if no date is created yet
            final FlowPanel firstDateListWidget = new FlowPanel();
            firstDateListWidget.setStyleName("wildcardPanel");
            final Label explainer = new Label("Durch das Dr\u00FCcken des Plus-Buttons bei ausgef\u00FCllten Termindaten, wird ein Termin hinzugef\u00FCgt");
            explainer.setStyleName("wildcard");
            firstDateListWidget.add(explainer);
            add(firstDateListWidget);
        }
        else
        {
            for (Appointment appointment : appointments)
            {
                final RaplaDate date = new RaplaDate(appointment);
                elementToRaplaDate.put(date.getElement(), date);
                add(date);
            }
        }
        this.addDomHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
            }
        }, ClickEvent.getType());
    }

    public Appointment getSelectedAppointment()
    {
        return selected != null ? selected.getAppointment() : null;
    }
}
