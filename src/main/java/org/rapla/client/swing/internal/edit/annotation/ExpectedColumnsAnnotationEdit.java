package org.rapla.client.swing.internal.edit.annotation;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditAttributeExtension;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides= AnnotationEditAttributeExtension.class, id="expectedcolums")
public class ExpectedColumnsAnnotationEdit extends ExpectedRowsAnnotationEdit implements AnnotationEditAttributeExtension
{
    
    @Inject
    public ExpectedColumnsAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface, LongFieldFactory longFieldFactory) {
        super(facade, i18n, raplaLocale, logger, ioInterface, longFieldFactory);
        annotationName = AttributeAnnotations.KEY_EXPECTED_COLUMNS;
        DEFAULT_VALUE = new Long(TextField.DEFAULT_LENGTH);
    }


}
