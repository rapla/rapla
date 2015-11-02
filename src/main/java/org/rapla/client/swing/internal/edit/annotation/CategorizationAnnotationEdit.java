package org.rapla.client.swing.internal.edit.annotation;

import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;

import org.rapla.client.extensionpoints.AnnotationEditAttributeExtension;
import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.swing.AnnotationEditExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.BooleanField;
import org.rapla.inject.Extension;

@Extension(provides = AnnotationEditAttributeExtension.class, id = "categorization")
public class CategorizationAnnotationEdit extends RaplaGUIComponent implements AnnotationEditExtension
{

    final String annotationName = AttributeAnnotations.KEY_CATEGORIZATION;

    @Inject
    public CategorizationAnnotationEdit(RaplaContext context)
    {
        super(context);
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable)
    {
        if (!(annotatable instanceof Attribute))
        {
            return Collections.emptyList();
        }
        Attribute attribute = (Attribute) annotatable;
        String annotation = annotatable.getAnnotation(annotationName);
        BooleanField field = new BooleanField(getContext(), getString(annotationName));
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
