package org.rapla.client.swing.internal.edit.annotation;

import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditAttributeExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.LongField;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides= AnnotationEditAttributeExtension.class, id="expectedrows")
public class ExpectedRowsAnnotationEdit extends RaplaGUIComponent implements AnnotationEditAttributeExtension
{
    protected String annotationName = AttributeAnnotations.KEY_EXPECTED_ROWS;
    protected Long DEFAULT_VALUE = new Long(1);
    private final IOInterface service;
    private final LongFieldFactory longFieldFactory;
    
    @Inject
    public ExpectedRowsAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface service, LongFieldFactory longFieldFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.service = service;
        this.longFieldFactory = longFieldFactory;
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable) {
        if (!( annotatable instanceof Attribute))
        {
            return Collections.emptyList();
        }
        Attribute attribute = (Attribute)annotatable;
        AttributeType type = attribute.getType();
        if ( type!=AttributeType.STRING)
        {
            return Collections.emptyList();
        }
        String annotation = annotatable.getAnnotation(annotationName);
        LongField field = longFieldFactory.create(getString(annotationName));
        if ( annotation != null)
        {
            field.setValue( Integer.parseInt(annotation));
        }
        else
        {
            field.setValue( DEFAULT_VALUE);
        }
        addCopyPaste(field.getComponent(), getI18n(), getRaplaLocale(), service, getLogger());
        return Collections.singleton(field);
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException {
        if ( field != null)
        {
            Long value = ((LongField)field).getValue();
            if ( value != null && !value.equals(DEFAULT_VALUE))
            {
                annotatable.setAnnotation(annotationName, value.toString());
                return;
            }
        }
        annotatable.setAnnotation(annotationName, null);
        
    }

}
