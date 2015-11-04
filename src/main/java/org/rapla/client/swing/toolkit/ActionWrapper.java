package org.rapla.client.swing.toolkit;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JMenuItem;

import org.rapla.client.swing.Action;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.action.SaveableToggleAction;
import org.rapla.components.xmlbundle.I18nBundle;

public class ActionWrapper implements javax.swing.Action
{
    private final Action delegate;
    private final I18nBundle locale;
    RaplaImages images;

    public ActionWrapper(Action delegate)
    {
        this(delegate, null, null);
    }

    public ActionWrapper(Action delegate, I18nBundle locale, RaplaImages images)
    {
        this.delegate = delegate;
        this.locale = locale;
        this.images = images;
    }

    @Override
    public void actionPerformed(ActionEvent event)
    {
        delegate.actionPerformed();
        if(delegate instanceof SaveableToggleAction)
        {
            if(event.getSource() instanceof JMenuItem)
            {
                final JMenuItem component = (JMenuItem) event.getSource();
                final boolean newSelected = !component.isSelected();
                component.setSelected(newSelected);
                javax.swing.ToolTipManager.sharedInstance().setEnabled(newSelected);
                component.setIcon(newSelected ? images.getIconFromKey("icon.checked") : images.getIconFromKey("icon.unchecked"));
            }
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        delegate.addPropertyChangeListener(listener);
    }

    @Override
    public Object getValue(String key)
    {
        return delegate.getValue(key);
    }

    @Override
    public boolean isEnabled()
    {
        return delegate.isEnabled();
    }

    @Override
    public void putValue(String key, Object value)
    {
        delegate.putValue(key, value);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        delegate.removePropertyChangeListener(listener);
    }

    @Override
    public void setEnabled(boolean b)
    {
        delegate.setEnabled(b);
    }

}
