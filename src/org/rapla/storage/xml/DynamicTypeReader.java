/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.storage.xml;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.rapla.components.util.Assert;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class DynamicTypeReader extends RaplaXMLReader
{
    DynamicTypeImpl dynamicType;
    MultiLanguageName currentName = null;
    String currentLang = null;
    String constraintKey = null;
    AttributeImpl attribute = null;
    String annotationKey = null;
    boolean isAttributeActive = false;
    boolean isDynamictypeActive = false;
    HashMap<String,String> typeAnnotations = new LinkedHashMap<String,String>();
    HashMap<String,String> attributeAnnotations = new LinkedHashMap<String,String>();

    public DynamicTypeReader( RaplaContext context ) throws RaplaException
    {
        super( context );
    }

    public void processElement(
        String namespaceURI,
        String localName,
        String qName,
        Attributes atts ) throws SAXException
    {

        if (localName.equals( "element" ))
        {
            String qname = getString( atts, "name" );
            String name = qname.substring( qname.indexOf( ":" ) + 1 );
            Assert.notNull( name );
            //System.out.println("NAME: " + qname + " Level " + level + " Entry " + entryLevel);

            if (!isDynamictypeActive)
            {
                isDynamictypeActive = true;
                typeAnnotations.clear();
                dynamicType = new DynamicTypeImpl();
                 if (atts.getValue( "id" )!=null)
                {
                    setId( dynamicType, atts );
                }
                else
                {
                    setNewId( dynamicType );
                }

                currentName = dynamicType.getName();
                dynamicType.setElementKey( name );
                setVersionIfThere( dynamicType, atts);

            }
            else
            {
                isAttributeActive = true;
                attribute = new AttributeImpl();
                currentName = attribute.getName();
                attribute.setKey( name );
                Assert.notNull( name, "key attribute cannot be null" );
                if (atts.getValue("id") != null)
                {
                    setId( attribute, atts );
                }
                else
                {
                    setNewId( attribute );
                }
                setVersionIfThere( attribute, atts);
                attributeAnnotations.clear();
            }
        }

        if (localName.equals( "constraint" ) && namespaceURI.equals( RAPLA_NS ))
        {
            constraintKey = atts.getValue( "name" );
            startContent();
        }
        
        if (localName.equals( "default" ))
        {
            startContent();
        }

        // if no attribute type is set
        if (localName.equals( "data" ) && namespaceURI.equals( RELAXNG_NS ) && attribute.getType().equals(
            AttributeImpl.DEFAULT_TYPE ))
        {
            String typeName = atts.getValue( "type" );
            if (typeName == null)
                throw createSAXParseException( "element relax:data is requiered!" );
            AttributeType type = AttributeType.findForString( typeName );
            if (type == null)
                throw createSAXParseException( "AttributeType '" + typeName + "' not found." );
            attribute.setType( type );
        }

        if (localName.equals( "annotation" ) && namespaceURI.equals( RAPLA_NS ))
        {
            annotationKey = atts.getValue( "key" );
            Assert.notNull( annotationKey, "key attribute cannot be null" );
            startContent();
        }

        if (localName.equals( "name" ) && namespaceURI.equals( ANNOTATION_NS ))
        {
            startContent();
            currentLang = atts.getValue( "lang" );
            Assert.notNull( currentLang );
        }
    }

    private void addAnnotations( Annotatable annotatable, Map<String,String> annotations )
    {
        for (Iterator<Map.Entry<String,String>> it = annotations.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<String,String> entry = it.next();
            String key =  entry.getKey();
            String annotation =  entry.getValue();
            try
            {
                annotatable.setAnnotation( key, annotation );
            }
            catch (IllegalAnnotationException e)
            {
                getLogger().error("Can't parse annotation " + e.getMessage(), e);
            	//throw createSAXParseException( e.getMessage() );
            }
        }

    }

    public void processEnd( String namespaceURI, String localName, String qName )
        throws SAXException
    {
        if (localName.equals( "element" ))
        {
            if (!isAttributeActive)
            {
                addAnnotations( dynamicType, typeAnnotations );
                setCurrentTranslations(dynamicType.getName());
                add( dynamicType );
                // We ensure the dynamic type is not modified anymore
                dynamicType.setReadOnly( true );
                isDynamictypeActive = false;
            }
            else
            {
                addAnnotations( attribute, attributeAnnotations );
                //System.out.println("Adding attribute " + attribute + " to " + dynamicType);
                setCurrentTranslations(attribute.getName());
                dynamicType.addAttribute( attribute );
                add( attribute );
                isAttributeActive = false;
            }
        }
        else if (localName.equals( "annotation" ) && namespaceURI.equals( RAPLA_NS ))
        {
            if (isAttributeActive)
            {
                attributeAnnotations.put( annotationKey, readContent() );
            }
            else
            {
                typeAnnotations.put( annotationKey, readContent() );
            }
        }
        else if (localName.equals( "optional" ) && namespaceURI.equals( RELAXNG_NS ))
        {
            attribute.setOptional( true );
        }
        else if (localName.equals( "name" ) && namespaceURI.equals( ANNOTATION_NS ))
        {
            Assert.notNull( currentName );
            currentName.setName( currentLang, readContent() );
        }
        else if (localName.equals( "constraint" ) && namespaceURI.equals( RAPLA_NS ))
        {
            String content = readContent().trim();
            Object constraint = null;
            if (attribute.getConstraintClass( constraintKey ) == Category.class)
            {
                boolean idContent = isContentCategoryId( content );
                if (idContent)
                {
                    constraint = resolve( Category.TYPE, content );
                }
                else
                {
                    constraint = getCategoryFromPath( content );
                }
            }
            else if (attribute.getConstraintClass( constraintKey ) == Integer.class)
            {
                constraint = parseLong( content );
            }
            else
            {
                constraint = content;
            }
            attribute.setConstraint( constraintKey, constraint );
        }
        
        if (localName.equals( "default" ) && namespaceURI.equals( RAPLA_NS ))
        {
            String content = readContent().trim();
            final Object defaultValue;
            final AttributeType type = attribute.getType();
            if (type == AttributeType.CATEGORY)
            {
                boolean idContent = isContentCategoryId( content );
                if (idContent)
                {
                    defaultValue = resolve( Category.TYPE, content );
                }
                else
                {
                    defaultValue = getCategoryFromPath( content );
                }
            }
            else 
            {
                Object value;
                try 
                {
                    value = AttributeImpl.parseAttributeValue(attribute, content, resolver);
                } 
                catch (ParseException e) 
                {
                    value = null;
                }
                defaultValue = value;
            }
            attribute.setDefaultValue(defaultValue );
        }
    }

    

   
}
