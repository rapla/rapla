package org.rapla.entities.domain;

import java.util.Comparator;


public class AppointmentBlockStartComparator implements Comparator<AppointmentBlock>
{
    /**
     * This method is used to compare two appointment blocks by their start dates
     */
    public int compare(AppointmentBlock a1, AppointmentBlock a2)
    {
      
        
        // Otherwise the comparison between two appointment blocks is needed
        if ( a1 == a2)
        {
            return 0;
        }
        // a1 before a2
        if (a1.getStart() <a2.getStart())
            return -1;
        // a1 after a2<
        if (a1.getStart() > a2.getStart())
            return 1;
        // a1 before a2
        if (a1.getEnd() < a2.getEnd())
            return -1;
        // a1 after a2
        if (a1.getEnd() > a2.getEnd())
            return 1;
        // If a1 and a2 have equal start and end dates, sort by hash code
        return (a1.hashCode() < a2.hashCode()) ? -1 : 1;
    }
    
}