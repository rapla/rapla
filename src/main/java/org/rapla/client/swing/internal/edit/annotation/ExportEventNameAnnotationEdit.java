package org.rapla.client.swing.internal.edit.annotation;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditTypeExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.Annotatable;
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


@Extension(provides= AnnotationEditTypeExtension.class, id=DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT)
public class ExportEventNameAnnotationEdit extends RaplaGUIComponent implements AnnotationEditTypeExtension
{
    protected String annotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT;
    protected String DEFAULT_VALUE = "";
    private final IOInterface ioInterface;
    private final TextFieldFactory textFieldFactory;
    
    @Inject
    public ExportEventNameAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface, TextFieldFactory textFieldFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.ioInterface = ioInterface;
        this.textFieldFactory = textFieldFactory;
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable) {
        if (!( annotatable instanceof DynamicType))
        {
            return Collections.emptyList();
        }
        DynamicType dynamicType = (DynamicType)annotatable;
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( classificationType == null )
        {
            return Collections.emptyList();
        }
        String annotation = annotatable.getAnnotation(annotationName);
        TextField field = textFieldFactory.create(getString(DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT));
        if ( annotation != null)
        {
            field.setValue( annotation);
        }
        else
        {
            field.setValue( DEFAULT_VALUE);
        }
        addCopyPaste(field.getComponent(), getI18n(), getRaplaLocale(), ioInterface, getLogger());
        return Collections.singleton(field);
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException {
        if ( field != null)
        {
            String value = ((TextField)field).getValue();
            if ( value != null && !value.equals(DEFAULT_VALUE))
            {
                annotatable.setAnnotation(annotationName, value.toString());
                return;
            }
        }
        annotatable.setAnnotation(annotationName, null);
        
    }

}
