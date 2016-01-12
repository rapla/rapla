package org.rapla.storage;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.framework.RaplaException;

@Deprecated
public class OldIdMapping {

    static public String getId(Class<? extends RaplaObject> type,String str) throws RaplaException {
    	if (str == null)
    		throw new RaplaException("Id string for " + type + " can't be null");
    	int index = str.lastIndexOf("_") + 1;
        if (index>str.length())
            throw new RaplaException("invalid rapla-id '" + str + "'");
        try {
        	return getId(type,Integer.parseInt(str.substring(index)));
        } catch (NumberFormatException ex) {
            throw new RaplaException("invalid rapla-id '" + str + "'");
        }
    }

    static public boolean isTextId( Class<? extends Entity> type,String content )
    {
        if ( content == null)
        {
            return false;
        }
        content = content.trim();
        if ( isNumeric( content))
        {
        	return true;
        }
        String KEY_START = RaplaType.getLocalName(type) + "_";
        boolean idContent = (content.indexOf( KEY_START ) >= 0  && content.length() > 0);
        return idContent;
    }

    static private boolean isNumeric(String text)
    {
    	int length = text.length();
    	if ( length == 0)
    	{
    		return false;
    	}
    	for ( int i=0;i<length;i++)
        {
        	char ch = text.charAt(i);
    		if (!Character.isDigit(ch))
        	{
        		return false;
        	}
        }
    	return true;
    }

    public static int parseId(String id) {
    	int indexOf = id.indexOf("_");
    	String keyPart = id.substring( indexOf + 1);
    	return Integer.parseInt(keyPart);
    }

    
    static public String getId(Class<? extends RaplaObject> type,int id)
    {
        return RaplaType.getLocalName(type) + "_" + id;
    }

}
