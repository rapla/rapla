package org.rapla.client.internal.check;

import org.rapla.client.RaplaTreeNode;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import javax.swing.JComponent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public interface HolidayCheckDialogView
{
    HolidayCheckPanel getConflictPanel(RaplaTreeNode root, boolean showCheckbox);

    class HolidayCheckPanel {
        public Object component;
        public Boolean checked = false;
        public Set selectedItems = Collections.emptySet();
    }
}
