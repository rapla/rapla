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
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;

@Extension(provides = AnnotationEditAttributeExtension.class, id = AttributeAnnotations.KEY_CATEGORIZATION)
public class CategorizationAnnotationEdit extends RaplaGUIComponent implements AnnotationEditAttributeExtension
{

    final String annotationName = AttributeAnnotations.KEY_CATEGORIZATION;
    private final BooleanFieldFactory booleanFieldFactory;

    @Inject
    public CategorizationAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, BooleanFieldFactory booleanFieldFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.booleanFieldFactory = booleanFieldFactory;
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable)
    {
        if (!(annotatable instanceof Attribute))
        {
            return Collections.emptyList();
        }
        Attribute attribute = (Attribute) annotatable;
        DynamicType dynamicType = attribute.getDynamicType();
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( classificationType == null || !(classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON ) || classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)))
        {
            return Collections.emptyList();
        }
        String annotation = annotatable.getAnnotation(annotationName);

        BooleanField field = booleanFieldFactory.create(getString(annotationName));
        if (annotation != null)
        {
            field.setValue(annotation.equalsIgnoreCase("true"));
        }
        else
        {
            if (attribute.getKey().equalsIgnoreCase(annotationName))
            {
                field.setValue(true);
            }
        }
        return Collections.singleton(field);
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException
    {
        if (field != null)
        {
            Boolean value = ((BooleanField) field).getValue();
            if (value != null)
            {
                if (value)
                {
                    annotatable.setAnnotation(annotationName, Boolean.TRUE.toString());
                    return;
                }
                else if (((Attribute) annotatable).getKey().equals(annotationName))
                {
                    annotatable.setAnnotation(annotationName, Boolean.FALSE.toString());
                    return;
                }

            }
        }
        annotatable.setAnnotation(annotationName, null);
    }

}
