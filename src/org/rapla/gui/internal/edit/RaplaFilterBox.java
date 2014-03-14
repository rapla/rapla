package org.rapla.gui.internal.edit;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.components.calendar.RaplaComboBox;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class RaplaFilterBox extends RaplaComboBox {
	private static final long serialVersionUID = 1L;
	ClassifiableFilterEdit ui;
    public RaplaFilterBox(RaplaContext context, CalendarSelectionModel filterObj) throws RaplaException {
        super(new TestComponent());
        
        boolean isResourceOnly = true;
        ui = new ClassifiableFilterEdit( context, isResourceOnly);
        JPanel mainPanel = new JPanel();

        mainPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        ui.setFilter(filterObj);
    }

    @Override
    protected JComponent getPopupComponent() {
        return ui.getComponent();
    }

    static class TestComponent extends JPanel
    {

		private static final long serialVersionUID = 1L;
        
    }
}
