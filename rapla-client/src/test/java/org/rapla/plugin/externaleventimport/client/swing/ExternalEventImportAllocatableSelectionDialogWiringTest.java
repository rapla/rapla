package org.rapla.plugin.externaleventimport.client.swing;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import static org.junit.Assert.assertTrue;

/**
 * {@link ExternalEventImportDialogImpl} injects a {@code Supplier<ExternalEventImportAllocatableSelectionDialog>};
 * the SupplierAutoWrapperBeanFactoryPostProcessor resolves that supplier by looking up a Spring bean of the
 * dialog's type when {@code .get()} is called. If the dialog class carries no stereotype annotation it is never
 * registered as a bean and {@code .get()} throws NoSuchBeanDefinitionException at import time.
 */
@RunWith(JUnit4.class)
public class ExternalEventImportAllocatableSelectionDialogWiringTest
{
    @Test
    public void dialogIsASpringManagedComponentSoTheSupplierCanResolveIt()
    {
        assertTrue("ExternalEventImportAllocatableSelectionDialog must be a Spring stereotype component "
                        + "(@Service/@Component) so the injected Supplier can resolve it",
                AnnotatedElementUtils.hasAnnotation(ExternalEventImportAllocatableSelectionDialog.class, Component.class));
    }
}
