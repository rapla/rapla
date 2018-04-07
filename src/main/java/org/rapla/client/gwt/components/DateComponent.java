package org.rapla.client.gwt.components;

import org.gwtbootstrap3.client.ui.html.Div;
import org.gwtbootstrap3.extras.datepicker.client.ui.DatePicker;
import org.gwtbootstrap3.extras.datepicker.client.ui.base.constants.DatePickerLanguage;
import org.gwtbootstrap3.extras.datepicker.client.ui.base.events.ChangeDateEvent;
import org.gwtbootstrap3.extras.datepicker.client.ui.base.events.ChangeDateHandler;
import org.rapla.client.gwt.components.util.GWTDateUtils;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.DateTools;

import java.util.Date;

public class DateComponent extends Div
{
    public interface DateValueChanged
    {
        void valueChanged(Date newValue);
    }

    private final DatePicker datePicker;
    private boolean updateInProgress = false;

    public DateComponent(Date initDate, final DateValueChanged changeHandler, BundleManager bundleManager)
    {
        super();
        addStyleName("datePicker");
        datePicker = new DatePicker();
        final String language = DateTools.getLang(bundleManager.getLocale());
        DatePickerLanguage lang = null;
        for (DatePickerLanguage l : DatePickerLanguage.values())
        {
            if (l.name().equalsIgnoreCase(language))
            {
                lang = l;
            }
        }
        if ( lang != null)
        {
            datePicker.setLanguage(lang);
        }
        datePicker.setFormat(bundleManager.getFormats().getFormatDateShort().toLowerCase());
        datePicker.setShowTodayButton(true);
        datePicker.setForceParse(true);
        add(datePicker);
        datePicker.setAutoClose(true);
        datePicker.addChangeDateHandler(evt -> {
            if(!updateInProgress)
            {
                Date newDate = datePicker.getValue();
                final Date raplaDate = GWTDateUtils.gwtDateToRapla(newDate);
                changeHandler.valueChanged(raplaDate);
            }
        });
        if (initDate != null)
        {
            setDate(initDate);
        }
    }

    public void setDate(Date date)
    {
        try
        {
            updateInProgress = true;
            final Date gwtDate = GWTDateUtils.raplaToGwtDate(date);
            datePicker.setValue(gwtDate, false);
        }
        finally {
            updateInProgress  =false;
        }
    }
}
