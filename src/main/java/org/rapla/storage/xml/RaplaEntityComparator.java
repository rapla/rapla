package org.rapla.storage.xml;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.rapla.entities.Category;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;

public class RaplaEntityComparator implements Comparator<RaplaObject>
{
    Map<RaplaType,Integer> ordering = new HashMap<RaplaType,Integer>();
    public RaplaEntityComparator()
    {
        int i=0;
        ordering.put( Category.TYPE,new Integer(i++));
        ordering.put( DynamicType.TYPE, new Integer(i++));
        ordering.put( User.TYPE,new Integer(i++));
        ordering.put( Allocatable.TYPE, new Integer(i++));
        ordering.put( Preferences.TYPE,new Integer(i++) );
        ordering.put( Period.TYPE, new Integer(i++) );
        ordering.put( Reservation.TYPE,new Integer(i++));
    }
    
    public int compare( RaplaObject o1, RaplaObject o2)
    {
        RaplaObject r1 =  o1;
        RaplaObject r2 = o2;
        RaplaType t1 = r1.getRaplaType();
        RaplaType t2 = r2.getRaplaType();
        Integer ord1 = ordering.get( t1);
        Integer ord2 = ordering.get( t2);
        if ( o1 == o2) 
        {
            return 0;
        }
        
        if ( ord1 != null && ord2 != null)
        {            
            if (ord1.intValue()>ord2.intValue())
            {
                return 1;
            }
            if (ord1.intValue()<ord2.intValue())
                
            {
                return -1;
            }
        }
        if ( ord1 != null && ord2 == null)
        {            
            return -1;
        }
        if ( ord2 != null && ord1 == null)
        {            
            return 1;
        }
        if ( o1.hashCode() > o2.hashCode())
        {
            return 1;
        }
        else
        {
            return -1;
        }
    }

}
