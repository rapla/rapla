package org.rapla.client.swing.toolkit;

import org.rapla.RaplaResources;
import org.rapla.client.swing.Action;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.action.SaveableToggleAction;

import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;

public class ActionWrapper implements javax.swing.Action
{
    private final Action delegate;
    private final RaplaResources i18n;

    public ActionWrapper(Action delegate)
    {
        this(delegate, null);
    }

    public ActionWrapper(Action delegate, RaplaResources i18n)
    {
        this.delegate = delegate;
        this.i18n = i18n;
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
                component.setIcon(RaplaImages.getIcon(newSelected ? i18n.getIcon("icon.checked") : i18n.getIcon("icon.unchecked")));
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
