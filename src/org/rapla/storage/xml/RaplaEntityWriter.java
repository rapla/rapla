/*--------------------------------------------------------------------------*
  | Copyright (C) 2006 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org         |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/
package org.rapla.storage.xml;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.dbrm.EntityList;

/** Stores the data from the local cache in XML-format to a print-writer.*/
public class RaplaEntityWriter extends RaplaXMLWriter
{
    protected final static String OUTPUT_FILE_VERSION="1.0";
	String encoding = "utf-8";

	/**
     * @param sm
     * @throws RaplaException
     */
    public RaplaEntityWriter(RaplaContext sm) throws RaplaException {
        super(sm);
    }
    
    public void setWriter( Appendable writer ) {
        super.setWriter( writer );
        for ( RaplaXMLWriter xmlWriter: writerMap.values()) {
            xmlWriter.setWriter( writer );
        }
    }
    
    
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
    
    private void printHeader(long repositoryVersion, TimeInterval invalidateInterval, boolean resourcesRefresh) throws IOException
    {
        println("<?xml version=\"1.0\" encoding=\"" + encoding + "\"?><!--*- coding: " + encoding + " -*-->");
        openTag("rapla:data");
        for (int i=0;i<NAMESPACE_ARRAY.length;i++) {
            String prefix = NAMESPACE_ARRAY[i][1];
            String uri = NAMESPACE_ARRAY[i][0];
            if ( prefix == null) {
                att("xmlns", uri);
            } else {
                att("xmlns:" + prefix, uri);
            }
            println();
        }
        att("version", RaplaEntityWriter.OUTPUT_FILE_VERSION);
        if ( repositoryVersion > 0)
        {
            att("repositoryVersion", String.valueOf(repositoryVersion));
        }
        if ( resourcesRefresh)
        {
            att("resourcesRefresh", "true");
        }
        if ( invalidateInterval != null)
        {
            Date startDate = invalidateInterval.getStart();
            Date endDate = invalidateInterval.getEnd(); 
			String start;
			if ( startDate == null)
			{
				startDate = new Date(0);
			}
			start = dateTimeFormat.formatDate( startDate);
			att("startDate", start);
        	if ( endDate != null)
        	{
        		String end = dateTimeFormat.formatDate( endDate);
            	att("endDate", end);
        	}
        }
        closeTag();
    }

//    public void printList(UpdateEvent evt ) throws RaplaException, IOException
//    {
//       long repositoryVersion = evt.getRepositoryVersion();
//       TimeInterval invalidateInterval = evt.getInvalidateInterval();
//	   boolean resourcesRefresh = evt.isNeedResourcesRefresh();
//	   printHeader( repositoryVersion, invalidateInterval, resourcesRefresh);
//       
//       Collection<Entity>remove = evt.getRemoveObjects();
//       Collection<Entity>store = evt.getStoreObjects();
//       Collection<Entity>all = evt.getAllObjects();
//       Collection<Entity>reference = evt.getReferenceObjects();
//       
//       printListPrivate( all);
//       openElement("rapla:store");
//       for ( Entity object:store)
//       {
//           openTag("rapla:" + object.getRaplaType().getLocalName());
//           String id = getId( object);
//           att("idref", id);
//           closeElementTag();
//       }
//       closeElement("rapla:store");
//       openElement("rapla:remove");
//       for ( Entity object:remove)
//       {
//           openTag("rapla:" + object.getRaplaType().getLocalName());
//           att("idref", getId( object));
//           closeElementTag();
//       }
//       closeElement("rapla:remove");
//       openElement("rapla:reference");
//       for ( Entity object:reference)
//       {
//           openTag("rapla:" + object.getRaplaType().getLocalName());
//           att("idref", getId( object));
//           closeElementTag();
//       }
//       closeElement("rapla:reference");
//       closeElement("rapla:data");
//    }
//    
//	public void printList(EntityList storeList) throws RaplaException, IOException
//	{
//		long repositoryVersion = storeList.getRepositoryVersion();
//		TimeInterval invalidateInterval = null;
//		boolean resourcesRefresh = false;
//		printHeader( repositoryVersion, invalidateInterval, resourcesRefresh);
//		printListPrivate( storeList);
//	    closeElement("rapla:data");
//	}
//    
//    protected void printListPrivate( Collection<? extends RaplaObject> resources ) throws RaplaException, IOException
//    {
//       HashSet<RaplaObject> hashSet = new HashSet<RaplaObject>(resources);
//       HashMap<User,Preferences> hashMap = new HashMap<User,Preferences>();
//       SortedSet<RaplaObject> set = new TreeSet<RaplaObject>(new RaplaEntityComparator());
//       for ( RaplaObject object:hashSet)
//       {
//    	   RaplaType type = object.getRaplaType();
//           if ( type == Preferences.TYPE)
//           {
//        	   Preferences preferences = (Preferences)object;
//        	   User user = preferences.getOwner();
//        	   if ( user != null )
//        	   {
//        		   if (!hashSet.contains( user))
//        		   {
//        			   set.add( user);
//        		   }
//        		   hashMap.put( user, preferences);
//        	   }
//           }
//       }
//       set.addAll(resources );
//       for ( Iterator<RaplaObject> it = set.iterator();it.hasNext();)
//       {
//           RaplaObject object =  it.next();
//           
//           RaplaType type = object.getRaplaType();
//           RaplaXMLWriter writer;
//           if ( type == Attribute.TYPE || type == Appointment.TYPE)
//           {
//               continue;
//           }
//           try
//           {
//              writer = getWriterFor(type);
//           }
//           catch (RaplaException e)
//           {
//               System.err.println( e.getMessage());
//               continue;
//           }
//           if ( type == Preferences.TYPE)
//           {
//               Preferences preferences= (Preferences)object;
//               User owner = preferences.getOwner();
//               if ( owner != null )
//               {
//                   continue;
//               }
//               writer.writeObject(object);
//           }
//           else if ( type == User.TYPE)
//           {
//               User user = (User)object;
//               Preferences preferences = hashMap.get( user);
//               ((UserWriter)writer).printUser(user, null, preferences );
//           }
//           else if ( type == Category.TYPE)
//           {
//               Category category = (Category)object;
//               if (category.getParent() != null && hashSet.contains(category.getParent()))
//               {
//                   continue;
//               }
//               ((CategoryWriter) writer).printCategory( category, true);
//           }
//           else
//           {
//               writer.writeObject(object);
//           }
//            
//       }
//        
//    }

    

    




}



