package org.rapla.framework;

public interface Configuration {
    String getName();
    Configuration getChild(String name);
    Configuration[] getChildren(String name);
    Configuration[] getChildren();
    String getValue() throws ConfigurationException;
    String getValue(String defaultValue);
    boolean getValueAsBoolean(boolean defaultValue);
    long getValueAsLong(int defaultValue);
    int getValueAsInteger(int defaultValue);
    String getAttribute(String name) throws ConfigurationException;
    String getAttribute(String name, String defaultValue);
    boolean getAttributeAsBoolean(String string, boolean defaultValue);
    String[] getAttributeNames();
    
    public Configuration find(  String localName);

    public Configuration find(  String attributeName, String attributeValue);

}
