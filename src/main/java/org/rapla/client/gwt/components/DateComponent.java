package org.rapla.client.gwt.components;

import java.util.Date;

import org.gwtbootstrap3.extras.datepicker.client.ui.DatePicker;
import org.gwtbootstrap3.extras.datepicker.client.ui.base.constants.DatePickerLanguage;
import org.rapla.client.gwt.components.util.GWTDateUtils;
import org.rapla.components.i18n.BundleManager;
import org.rapla.framework.RaplaLocale;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.SimplePanel;

public class DateComponent extends SimplePanel
{
    public interface DateValueChanged
    {
        void valueChanged(Date newValue);
    }

    private final DatePicker datePicker;

    public DateComponent(Date initDate, final DateValueChanged changeHandler, BundleManager bundleManager)
    {
        super();
        addStyleName("datePicker");
        datePicker = new DatePicker();
        final DatePickerLanguage lang = DatePickerLanguage.valueOf(bundleManager.getLocale().getLanguage().toUpperCase());
        datePicker.setLanguage(lang);
        datePicker.setFormat(bundleManager.getFormats().getFormatDateShort().toLowerCase());
        datePicker.setShowTodayButton(true);
        datePicker.setForceParse(true);
        add(datePicker);
        datePicker.addValueChangeHandler(new ValueChangeHandler<Date>()
        {
            @Override
            public void onValueChange(ValueChangeEvent<Date> event)
            {
                final Date newDate = event.getValue();
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
        final Date gwtDate = GWTDateUtils.raplaToGwtDate(date);
        datePicker.setValue(gwtDate, false);
    }
}
