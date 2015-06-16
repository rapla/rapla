package org.rapla.gui.toolkit;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JMenuItem;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.gui.Action;
import org.rapla.gui.internal.action.SaveableToggleAction;

public class ActionWrapper implements javax.swing.Action
{
    private final Action delegate;
    private final I18nBundle locale;

    public ActionWrapper(Action delegate)
    {
        this(delegate, null);
    }

    public ActionWrapper(Action delegate, I18nBundle locale)
    {
        this.delegate = delegate;
        this.locale = locale;
    }

    @Override
    public void actionPerformed(ActionEvent event)
    {
        delegate.actionPerformed();
        if(delegate instanceof SaveableToggleAction)
        {
            final JMenuItem component = (JMenuItem) event.getSource();
            final boolean newSelected = !component.isSelected();
            component.setSelected(newSelected);
            javax.swing.ToolTipManager.sharedInstance().setEnabled(newSelected);
            component.setIcon(newSelected ? locale.getIcon("icon.checked") : locale.getIcon("icon.unchecked"));
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
