package org.rapla.framework;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultConfiguration implements Configuration {

    Map<String,String> attributes = new LinkedHashMap<>();
    List<DefaultConfiguration> children = new ArrayList<>();
    // @JsonProperty rescues this field after the @JsonIgnore annotations below
    // mark the typed setValue(int)/setValue(boolean) overloads as ignored —
    // Jackson 2's "any-ignorals-propagate" rule would otherwise hide the
    // entire `value` property (including this field) from schema generation
    // because the property is reachable via ignored accessors. Annotating the
    // field explicitly tells Jackson "the field is a property regardless of
    // what the accessors say". Runtime serializer is field-based so no behavior
    // change; only swagger-core's schema generation cares.
    @com.fasterxml.jackson.annotation.JsonProperty
    String value;
    String name;
    
    public DefaultConfiguration()
    {
    }
    
    public DefaultConfiguration(String localName) {
        this.name = localName;
    }

    public DefaultConfiguration(String localName, String value) {
        this.name = localName;
        if ( value != null)
        {
        	this.value = value;
        }
    }

    public DefaultConfiguration(Configuration config) 
    {
        this.name = config.getName();
        for (Configuration conf: config.getChildren())
        {
            children.add( new DefaultConfiguration( conf));
        }
        this.value = ((DefaultConfiguration)config).value;
        attributes.putAll(((DefaultConfiguration)config).attributes);
    }

    public void addChild(Configuration configuration) {
        children.add( (DefaultConfiguration) configuration);
    }

    public void setAttribute(String name, boolean value) {
        attributes.put( name, value ? "true" : "false");
    }

    public void setAttribute(String name, String value) {
        if ( value == null)
        {
            attributes.remove( name);
            return;
        }
        attributes.put( name, value);
    }

    public void setValue(String value) {
        this.value = value;
    }

    /*
     * The two typed setValue(...) overloads below are framework-API conveniences
     * — they write into the same private `value` field that {@link #setValue(String)}
     * does, just after a primitive→String conversion. They are NEVER called by the
     * Jackson 3 runtime serializer (rapla configures it with SETTER=NONE +
     * FIELD=ANY in rapla-core/.../JacksonObjectMapperFactory.java; deserialization
     * goes straight to the field). But swagger-core 2.x's POJOPropertyBuilder
     * collects ALL public setters regardless of visibility config during property
     * discovery and then throws IllegalArgumentException because three overloads
     * claim the same property name "value". swagger-core's ModelResolver.ignore()
     * catches the exception, so the resulting OpenAPI schema is still correct
     * (single `value` property, derived from the field), but the throw leaves a
     * boot-time WARN log line:
     *
     *     IllegalArgumentException: Conflicting setter definitions for property
     *     "value": setValue(boolean) vs setValue(int)
     *
     * Annotating these two overloads with @JsonIgnore removes them from
     * swagger-core's property-discovery pass, eliminating the warning at the
     * source while preserving the framework API for non-Jackson callers. See
     * docs/architecture/rest-api.md §"OpenAPI / Swagger spec caveat" for the
     * broader Jackson 2 / Jackson 3 split that makes this annotation necessary
     * — once swagger-core 3.x ships with Jackson 3 (upstream #4991, ~12-24 months
     * out as of 2026-05-13), the underlying mismatch goes away and these
     * annotations can be removed.
     */
    @JsonIgnore
    public void setValue(int intValue) {
        this.value = Integer.toString( intValue);
    }

    @JsonIgnore
    public void setValue(boolean selected) {
        this.value = Boolean.toString( selected);
    }

    
    public DefaultConfiguration getMutableChild(String name) {
        return getMutableChild(name, true);
    }
    
    public DefaultConfiguration getMutableChild(String name, boolean create) {
        for (DefaultConfiguration child:children)
        {
            if ( child.getName().equals( name))
            {
                return child;
            }
        }
        if ( create )
        {
            DefaultConfiguration newConfig = new DefaultConfiguration( name);
            children.add( newConfig);
            return newConfig;
        }
        else
        {
            return null;
        }
    }

    
    public void removeChild(Configuration child) {
        children.remove( child);
    }

    public String getName()
    {
        return name;
    }

    public Configuration getChild(String name) 
    {
        for (DefaultConfiguration child:children)
        {
            if ( child.getName().equals( name))
            {
                return child;
            }
        }
        return new DefaultConfiguration( name);
    }

    public Configuration[] getChildren(String name) {
        List<Configuration> result = new ArrayList<>();
        for (DefaultConfiguration child:children)
        {
            if ( child.getName().equals( name))
            {
                result.add( child);
            }
        }
        return result.toArray( new Configuration[] {});
    }

    public Configuration[] getChildren() 
    {
        return children.toArray( new Configuration[] {});
    }

    public String getValue() throws ConfigurationException {
        if ( value == null)
        {
            throw new ConfigurationException("Value not set in configuration " + name);
        }
        return value;
    }

    public String getValue(String defaultValue) {
        if ( value == null)
        {
            return defaultValue;
        }
        return value;
    }

    public boolean getValueAsBoolean(boolean defaultValue) {
        if ( value == null)
        {
            return defaultValue;
        }
        if ( value.equalsIgnoreCase("yes"))
        {
            return true;
        }
        if ( value.equalsIgnoreCase("no"))
        {
            return false;
        }
        return Boolean.parseBoolean( value);
    }

    public long getValueAsLong(int defaultValue) {
        if ( value == null)
        {
            return defaultValue;
        }
        return Long.parseLong( value);
    }

    public int getValueAsInteger(int defaultValue) {
        if ( value == null)
        {
            return defaultValue;
        }
        return Integer.parseInt( value);
    }

    public String getAttribute(String name) throws ConfigurationException {
        String value = attributes.get(name);
        if ( value == null)
        {
            throw new ConfigurationException("Attribute " + name + " not found ");
        }
        return value;
    }

    public String getAttribute(String name, String defaultValue) {
        String value = attributes.get(name);
        if ( value == null)
        {
            return defaultValue;
        }
        return value;
    }

    public boolean getAttributeAsBoolean(String string, boolean defaultValue) {
        String value = getAttribute(string, defaultValue ? "true": "false");
        return Boolean.parseBoolean( value);
    }

    public String[] getAttributeNames() {
        String[] attributeNames = attributes.keySet().toArray( new String[] {});
        return attributeNames;
    }
    
    public Configuration find(  String localName) {
        Configuration[] childList= getChildren();
        for ( int i=0;i<childList.length;i++) {
            if (childList[i].getName().equals( localName)) {
                return childList[i];
            }
        }
        return null;
    }

    public Configuration find(  String attributeName, String attributeValue) {
        Configuration[] childList= getChildren();
        for ( int i=0;i<childList.length;i++) {
            String attribute = childList[i].getAttribute( attributeName,null);
            if (attributeValue.equals( attribute)) {
                return childList[i];
            }
        }
        return null;
    }

    
    public DefaultConfiguration replace(  Configuration newChild) throws ConfigurationException {
        Configuration find = find( newChild.getName());
        if ( find == null)
        {
            throw new ConfigurationException(" could not find " + newChild.getName());
        }
        return replace( find, newChild );
    }
        
    public DefaultConfiguration replace(  Configuration oldChild, Configuration newChild) {
        String localName = getName();
        DefaultConfiguration newConfig = newConfiguration(localName);
        boolean present = false;
        Configuration[] childList= getChildren();
        for ( int i=0;i<childList.length;i++) {
            if (childList[i] != oldChild) {
                newConfig.addChild( childList[i]);
            }  else {
                present = true;
                newConfig.addChild( newChild );
            }
        }
        if (!present) {
            newConfig.addChild( newChild );
        }
        return newConfig;
    }

    protected DefaultConfiguration newConfiguration(String localName) {
		return new DefaultConfiguration( localName);
	}

	public DefaultConfiguration add(  Configuration newChild)  {
        String localName = getName();
        DefaultConfiguration newConfig = newConfiguration(localName);
        boolean present = false;
        Configuration[] childList= getChildren();
        for ( int i=0;i<childList.length;i++) {
            if (childList[i] == newChild) {
                present = true;
                break;
            }
        }
        if (!present) {
            newConfig.addChild( newChild );
        }
        return newConfig;
    }

    /**
     * @param configuration
     */
    public DefaultConfiguration remove(Configuration configuration) {
        String localName = getName();
        DefaultConfiguration newConfig = newConfiguration(localName);
        Configuration[] childList= getChildren();
        for ( int i=0;i<childList.length;i++) {
            if (childList[i] != configuration) {
                newConfig.addChild( childList[i]);
            }
        }
        return newConfig;
    }

    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((attributes == null) ? 0 : attributes.hashCode());
        result = prime * result + ((children == null) ? 0 : children.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultConfiguration other = (DefaultConfiguration) obj;
        if (attributes == null) {
            if (other.attributes != null)
                return false;
        } else if (!attributes.equals(other.attributes))
            return false;
        if (children == null) {
            if (other.children != null)
                return false;
        } else if (!children.equals(other.children))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (value == null) {
            return other.value == null;
        } else return value.equals(other.value);
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append( name);
        if (attributes.size() > 0)
        {
            buf.append( "[");
            boolean first= true;
            for ( Map.Entry<String, String> entry: attributes.entrySet())
            {
                if (!first)
                {
                    buf.append( ", ");
                }
                else
                {
                	first = false;
                }
                buf.append(entry.getKey());
                buf.append( "='");
                buf.append(entry.getValue());
                buf.append( "'");
            }
            buf.append( "]");
        }
        buf.append( "{");
        boolean first= true;
        for ( Configuration child:children)
        {
            if (first)
            {
                buf.append("\n");
                first  =false;
            }
            buf.append( child.toString());
            buf.append("\n");
        }
        if ( value != null)
        {
            buf.append( value);
        }
        buf.append( "}");
        return buf.toString();
    }
    
    
}
