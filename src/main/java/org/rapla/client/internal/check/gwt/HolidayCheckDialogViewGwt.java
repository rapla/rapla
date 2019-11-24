package org.rapla.client.internal.check.gwt;

import org.rapla.client.RaplaTreeNode;
import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.dialog.gwt.components.VueTree;
import org.rapla.client.dialog.gwt.components.VueTreeNode;
import org.rapla.client.internal.check.ConflictDialogView;
import org.rapla.client.internal.check.HolidayCheckDialogView;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(of= HolidayCheckDialogView.class,context = InjectionContext.gwt)
public class HolidayCheckDialogViewGwt implements HolidayCheckDialogView {

    @Override
    public HolidayCheckPanel getConflictPanel(RaplaTreeNode root, boolean showCheckbox)
    {
        HolidayCheckPanel panel = new HolidayCheckPanel();
        final VueTree vueTree = new VueTree((VueTreeNode)root);
        panel.component = vueTree;
        return panel;
    }
}
