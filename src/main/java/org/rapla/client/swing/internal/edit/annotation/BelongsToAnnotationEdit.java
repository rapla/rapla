package org.rapla.client.swing.internal.edit.annotation;

/*
@Extension(provides= AnnotationEditAttributeExtension.class, id=AttributeAnnotations.KEY_BELONGSTO)
public class BelongsToAnnotationEdit extends RaplaGUIComponent implements AnnotationEditAttributeExtension
{

    static private final String annotationName = AttributeAnnotations.KEY_BELONGSTO;
    private final BooleanField.BooleanFieldFactory booleanFieldFactory;
    String NOTHING_SELECTED = "nothing_selected";

    @Inject
    public BelongsToAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, BooleanField.BooleanFieldFactory booleanFieldFactory)
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
        if (attribute.getRefType() != Allocatable.class)
        {
            return Collections.emptyList();
        }
        DynamicType dynamicType = attribute.getDynamicType();
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( classificationType == null || !(classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON ) || classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)))
        {
            return Collections.emptyList();
        }
        if ( annotatable.getAnnotation())
        String annotation = annotatable.getAnnotation(annotationName);
        final String string = getString(AttributeAnnotations.KEY_BELONGSTO);
        BooleanField field = booleanFieldFactory.createInfoDialog(string);
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
*/