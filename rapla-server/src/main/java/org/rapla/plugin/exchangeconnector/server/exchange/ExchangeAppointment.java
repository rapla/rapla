package org.rapla.plugin.exchangeconnector.server.exchange;

import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.Objects;

public class ExchangeAppointment {
    final ReferenceInfo<Appointment> raplaAppointmentId;
    final String exchangeAppointmentId;
    final ItemId itemId;
    final microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment;
    final String raplaAppointmentLastChanged;
    boolean foreign;


    public ExchangeAppointment(ReferenceInfo<Appointment> raplaAppointmentId, String exchangeAppointmentId, ItemId itemId, microsoft.exchange.webservices.data.core.service.item.Appointment exchangeAppointment, String raplaAppointmentLastChanged) {
        this.raplaAppointmentId = raplaAppointmentId;
        this.exchangeAppointmentId = exchangeAppointmentId;
        this.itemId = itemId;
        this.exchangeAppointment = exchangeAppointment;
        this.raplaAppointmentLastChanged = raplaAppointmentLastChanged;
    }

    /** true = the mailbox owner's item (an Outlook copy or marked private): rapla can neither update nor delete it (PRD 114 hunks 7/10/11) */
    public boolean isForeign() {
        return foreign;
    }

    public microsoft.exchange.webservices.data.core.service.item.Appointment getExchangeAppointment() {
        return exchangeAppointment;
    }

    public ItemId getItemId() {
        return itemId;
    }

    public String getExchangeAppointmentId() {
        return exchangeAppointmentId;
    }

    public ReferenceInfo<Appointment> getRaplaAppointmentId() {
        return raplaAppointmentId;
    }

    public String getRaplaAppointmentLastChanged() {
        return raplaAppointmentLastChanged;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeAppointment that = (ExchangeAppointment) o;
        return Objects.equals(raplaAppointmentId, that.raplaAppointmentId) && Objects.equals(exchangeAppointmentId, that.exchangeAppointmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raplaAppointmentId, exchangeAppointmentId);
    }

    @Override
    public String toString() {
        return "ExchangeAppointment{" +
                "raplaAppointmentId=" + raplaAppointmentId +
                ", exchangeAppointmentId='" + exchangeAppointmentId + '\'' +
                ", lastChanged='" + raplaAppointmentLastChanged + '\'' +
                '}';
    }
}
