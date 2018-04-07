package org.rapla.client.swing.internal.edit.annotation;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditAttributeExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.BooleanField;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;

@Extension(provides= AnnotationEditAttributeExtension.class, id=AttributeAnnotations.KEY_COLOR)
public class ColorAnnotationEdit extends RaplaGUIComponent implements AnnotationEditAttributeExtension
{

    private final String annotationName = AttributeAnnotations.KEY_COLOR;
    private final BooleanFieldFactory booleanFieldFactory;

    @Inject
    public ColorAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, BooleanFieldFactory booleanFieldFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.booleanFieldFactory = booleanFieldFactory;
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable) {
        if (!( annotatable instanceof Attribute))
        {
            return Collections.emptyList();
        }
        Attribute attribute = (Attribute)annotatable;
        AttributeType type = attribute.getType();
        if ( type!=AttributeType.CATEGORY && type!= AttributeType.STRING)
        {
            return Collections.emptyList();
        }
        String annotation = annotatable.getAnnotation(annotationName);
        BooleanField field = booleanFieldFactory.create(getString(AttributeAnnotations.KEY_COLOR));
        if ( annotation != null)
        {
            field.setValue( annotation.equalsIgnoreCase("true"));
        }
        return Collections.singleton(field);
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException 
    {
        if ( field != null)
        {
            Boolean value = ((BooleanField)field).getValue();
            if ( value != null && value)
            {
                annotatable.setAnnotation(annotationName, Boolean.TRUE.toString());
                return;
            }
        }
        annotatable.setAnnotation(annotationName, null);
    }

}
