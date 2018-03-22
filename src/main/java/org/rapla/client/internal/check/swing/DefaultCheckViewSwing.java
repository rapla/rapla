package org.rapla.client.internal.check.swing;

import org.rapla.client.RaplaWidget;
import org.rapla.client.internal.check.CheckView;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

@DefaultImplementation(of=CheckView.class,context = InjectionContext.swing)
public class DefaultCheckViewSwing implements CheckView, RaplaWidget
{
    JPanel warningPanel = new JPanel();

    @Inject
    public DefaultCheckViewSwing()
    {
        warningPanel.setLayout( new BoxLayout( warningPanel, BoxLayout.Y_AXIS));
    }

    @Override
    public void addWarning(String warning) {
        JLabel warningLabel = new JLabel();
        warningLabel.setForeground(java.awt.Color.red);
        warningLabel.setText(warning);
        warningPanel.add( warningLabel);
    }

    @Override
    public boolean hasMessages() {
        return warningPanel.getComponentCount() > 0;
    }

    @Override
    public Object getComponent()
    {
        return warningPanel;
    }
}
