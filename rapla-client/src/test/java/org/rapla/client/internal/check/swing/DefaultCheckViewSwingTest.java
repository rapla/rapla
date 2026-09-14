package org.rapla.client.internal.check.swing;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCheckViewSwingTest
{
    @Test
    void addWarningMakesHasMessagesTrue()
    {
        DefaultCheckViewSwing view = new DefaultCheckViewSwing();
        assertFalse(view.hasMessages());
        view.addWarning("hello");
        assertTrue(view.hasMessages());
    }

    @Test
    void clearRemovesAllPreviouslyAddedWarnings()
    {
        DefaultCheckViewSwing view = new DefaultCheckViewSwing();
        view.addWarning("first");
        view.addWarning("second");
        view.clear();
        assertFalse(view.hasMessages(), "clear() must drop accumulated warnings");
        assertEquals(0, ((JPanel) view.getComponent()).getComponentCount());
    }

    @Test
    void warningsDoNotLeakAcrossUses()
    {
        DefaultCheckViewSwing view = new DefaultCheckViewSwing();
        view.addWarning("from previous check");
        view.clear();
        view.addWarning("only from this check");
        assertEquals(1, ((JPanel) view.getComponent()).getComponentCount(),
                "after clear(), panel must contain only warnings added since clear");
    }
}
