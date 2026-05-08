package org.rapla.client.swing.internal.edit.annotation;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditAttributeExtension;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;

@Service

public class ExpectedColumnsAnnotationEdit extends ExpectedRowsAnnotationEdit implements AnnotationEditAttributeExtension
{
    
    @Autowired
    public ExpectedColumnsAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface, LongFieldFactory longFieldFactory) {
        super(facade, i18n, raplaLocale, logger, ioInterface, longFieldFactory);
        annotationName = AttributeAnnotations.KEY_EXPECTED_COLUMNS;
        DEFAULT_VALUE = Long.valueOf(TextField.DEFAULT_LENGTH);
    }


}
