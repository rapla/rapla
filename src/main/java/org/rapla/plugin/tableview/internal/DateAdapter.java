package org.rapla.plugin.tableview.internal;

import java.util.Date;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.rapla.components.util.SerializableDateTimeFormat;

public class DateAdapter extends XmlAdapter<String, Date>{

    private boolean cutDate; 
    public boolean isCutDate()
    {
        return cutDate;
    }

    public void setCutDate(boolean cutDate)
    {
        this.cutDate = cutDate;
    }

    @Override
    public Date unmarshal(String v) throws Exception
    {
        return SerializableDateTimeFormat.INSTANCE.parseDate(v, cutDate);
    }

    @Override
    public String marshal(Date v) throws Exception
    {
        return SerializableDateTimeFormat.INSTANCE.formatDate(v, cutDate);
    }
    
}