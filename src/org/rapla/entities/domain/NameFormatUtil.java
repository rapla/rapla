package org.rapla.entities.domain;

import java.util.Locale;

import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.EntityResolver;

public class NameFormatUtil
{

    public static String getExportName(AppointmentBlock appointmentBlock, Locale locale)
    {
        final Reservation reservation = appointmentBlock.getAppointment().getReservation();
        if ( reservation.getClassification().getType().getAnnotation( DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT) != null)
        {
            return reservation.format( locale, DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT, appointmentBlock);
        }
        else
        {
            return getName(appointmentBlock, locale);
        }
    
    }

    public static String getExportName(Appointment appointment, Locale locale)
    {
        final Reservation reservation = appointment.getReservation();
        String eventDescription;
        if ( reservation.getClassification().getType().getAnnotation( DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT) != null)
        {
            eventDescription = reservation.format(locale, DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT, appointment);
        }
        else
        {
            return getName(appointment, locale);
        }
        return eventDescription;
    }

    public static String getExportName(Reservation reservation, Locale locale)
    {
        String eventDescription;
        if ( reservation.getClassification().getType().getAnnotation( DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT) != null)
        {
            eventDescription = reservation.format(locale, DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT);
        }
        else
        {
            return getName(reservation, locale);
        }
        return eventDescription;
    }

    public static String getName(AppointmentBlock appointmentBlock, Locale locale)
    {
        String annotiationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
        final Reservation reservation = appointmentBlock.getAppointment().getReservation();
        return reservation.format( locale, annotiationName, appointmentBlock);
    }
    
    public static String getName(Appointment appointment, Locale locale)
    {
        String annotiationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
        final Reservation reservation = appointment.getReservation();
        return reservation.format( locale, annotiationName, appointment);
    }

    public static String getName(Reservation reservation, Locale locale)
    {
        String annotiationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
        return reservation.format( locale, annotiationName);
    }

    
}
