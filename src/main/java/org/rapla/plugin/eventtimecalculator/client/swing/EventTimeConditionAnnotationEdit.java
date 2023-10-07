package org.rapla.plugin.eventtimecalculator.client.swing;

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
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorResources;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;


@Extension(provides= AnnotationEditTypeExtension.class, id=EventTimeCalculatorPlugin.EVENTIME_CONDITION_ANNOTATION_NAME)
public class EventTimeConditionAnnotationEdit extends RaplaGUIComponent implements AnnotationEditTypeExtension
{
    protected String annotationName = EventTimeCalculatorPlugin.EVENTIME_CONDITION_ANNOTATION_NAME;
    protected String DEFAULT_VALUE = "";
    private final IOInterface service;
    private final TextFieldFactory textFieldFactory;
    EventTimeCalculatorResources eventTimei18n;

    @Inject
    public EventTimeConditionAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface service, TextFieldFactory textFieldFactory, EventTimeCalculatorResources eventTimei18n) {
        super(facade, i18n, raplaLocale, logger);
        this.service = service;
        this.textFieldFactory = textFieldFactory;
        this.eventTimei18n = eventTimei18n;
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable) {
        if (!( annotatable instanceof DynamicType))
        {
            return Collections.emptyList();
        }
        DynamicType dynamicType = (DynamicType)annotatable;
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( classificationType == null || !(classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION) ))
        {
            return Collections.emptyList();
        }
        String annotation = annotatable.getAnnotation(annotationName);
        TextField field = textFieldFactory.create(eventTimei18n.getString("compare_condition"));
        if ( annotation != null)
        {
            field.setValue( annotation);
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
            String value = ((TextField)field).getValue();
            if ( value != null && !value.equals(DEFAULT_VALUE))
            {
                annotatable.setAnnotation(annotationName, value);
                return;
            }
        }
        annotatable.setAnnotation(annotationName, null);
        
    }

}
