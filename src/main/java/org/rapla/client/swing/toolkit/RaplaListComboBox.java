package org.rapla.client.swing.toolkit;

import javax.swing.JComboBox;

import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.entities.Named;
import org.rapla.framework.RaplaLocale;

public final class RaplaListComboBox extends JComboBox {
    private static final long serialVersionUID = 1L;
    // copied the coe from tree table
    String cachedSearchKey = "";
    
    public RaplaListComboBox(RaplaLocale raplaLocale)  {
        init(raplaLocale);
    }
    
    @SuppressWarnings("unchecked")
    public RaplaListComboBox(RaplaLocale raplaLocale,Object[] named)  {
        super(named);
        init(raplaLocale);
    }

    @SuppressWarnings("unchecked")
    public void init(RaplaLocale raplaLocale) {
        setRenderer(new NamedListCellRenderer(raplaLocale.getLocale()));
    }

    protected boolean processKeyBinding(javax.swing.KeyStroke ks, java.awt.event.KeyEvent e, int condition, boolean pressed) {
         // live search in current parent node
        if ((Character.isLetterOrDigit(e.getKeyChar())) && ks.isOnKeyRelease()) {
            char keyChar = e.getKeyChar();

            // search term
            String search = ("" + keyChar).toLowerCase();

            // try to find node with matching searchterm plus the search before
            int nextIndexMatching = getNextIndexMatching(cachedSearchKey + search);

            // if we did not find anything, try to find search term only: restart!
            if (nextIndexMatching <0 ) {
                nextIndexMatching = getNextIndexMatching(search);
                cachedSearchKey = "";
            }
            // if we found a node, select it, make it visible and return true
            if (nextIndexMatching >=0 ) {

                // store found treepath
                cachedSearchKey = cachedSearchKey + search;
                setSelectedIndex(nextIndexMatching);
                return true;
            }
            cachedSearchKey = "";
            return true;
        }
        return super.processKeyBinding(ks,e,condition,pressed);
    }

    private int getNextIndexMatching(String string) 
    {
        int i = 0;
        while ( i< getItemCount())
        {
            Object item = getItemAt( i );
            String toString;
            if  ( item instanceof Named)
            {
                toString = ((Named) item).getName( getLocale());
            }
            else if ( item != null)
            {
                toString = item.toString();
            }
            else
            {
                toString = null; 
            }
            if ( toString != null && toString.toLowerCase().startsWith( string.toLowerCase()))
            {
                return i;
            }
            i++;
        }
        
        return -1;
    }
}