package org.rapla.components.xmlbundle.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;

public class PropertyResourceBundleWrapper extends ResourceBundle {

    private Map<String,Object> lookup;
    String name;

    static Charset charset = Charset.forName("UTF-8");
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public PropertyResourceBundleWrapper(InputStream stream,String name) throws IOException {
	    Properties properties = new Properties();
	    properties.load(new InputStreamReader(stream, charset));
        lookup = new LinkedHashMap(properties);
        this.name = name;
	}
	
	public String getName()
	{
		return name;
	}

	// We make the setParent method public, so that we can use it in I18nImpl
	public void setParent(ResourceBundle parent) {
		super.setParent(parent);
	}
	
	// Implements java.util.ResourceBundle.handleGetObject; inherits javadoc specification.
    public Object handleGetObject(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return lookup.get(key);
    }

    /**
     * Returns an <code>Enumeration</code> of the keys contained in
     * this <code>ResourceBundle</code> and its parent bundles.
     *
     * @return an <code>Enumeration</code> of the keys contained in
     *         this <code>ResourceBundle</code> and its parent bundles.
     * @see #keySet()
     */
    public Enumeration<String> getKeys() {
        ResourceBundle parent = this.parent;
        Set<String> set = new LinkedHashSet<String>(lookup.keySet());
        if ( parent != null)
        {
        	set.addAll( parent.keySet());
        }
        Vector<String> vector = new Vector<String>(set);
		Enumeration<String> enum1 = vector.elements();
        return enum1;
    }

    /**
     * Returns a <code>Set</code> of the keys contained
     * <em>only</em> in this <code>ResourceBundle</code>.
     *
     * @return a <code>Set</code> of the keys contained only in this
     *         <code>ResourceBundle</code>
     * @since 1.6
     * @see #keySet()
     */
    protected Set<String> handleKeySet() {
        return lookup.keySet();
    }

    // ==================privates====================


}
