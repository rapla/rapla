package org.rapla.client.swing.internal.edit.annotation;

import java.util.Collection;
import java.util.Collections;
import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditTypeExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.documents.DocumentsPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * PRD 111 D1 — edits the {@code documents} annotation: the comma-separated names of the stored
 * documents offered on entities of this type, in menu order (the name is the reference AND the
 * menu label). v1 is a free text field; a picker fed by {@code GET /api/documents} is the upgrade.
 *
 * <p>Hidden when the documents plugin is switched off at runtime (D6 gate 2), and on rapla-internal
 * types, which carry no user-facing entities.
 */
@Service
public class DocumentsAnnotationEdit extends RaplaGUIComponent implements AnnotationEditTypeExtension
{
    private final String annotationName = DynamicTypeAnnotations.KEY_DOCUMENTS;
    private final TextFieldFactory textFieldFactory;

    @Autowired
    public DocumentsAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale,
            TextFieldFactory textFieldFactory)
    {
        super(facade, i18n, raplaLocale);
        this.textFieldFactory = textFieldFactory;
    }

    @Override
    public int position() { return 1; }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable)
    {
        if (!(annotatable instanceof DynamicType dynamicType) || !pluginEnabled())
        {
            return Collections.emptyList();
        }
        String classificationType =
                dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE.equals(classificationType))
        {
            return Collections.emptyList();
        }
        TextField field = textFieldFactory.create("Dokumentvorlagen (Namen, kommagetrennt)");
        String annotation = annotatable.getAnnotation(annotationName);
        if (annotation != null)
        {
            field.setValue(annotation);
        }
        return Collections.singleton(field);
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException
    {
        if (field == null)
        {
            return;
        }
        String value = ((TextField) field).getValue();
        annotatable.setAnnotation(annotationName, value == null || value.isBlank() ? null : value.trim());
    }

    private boolean pluginEnabled()
    {
        try
        {
            return getClientFacade().getRaplaFacade().getSystemPreferences()
                    .getEntryAsBoolean(DocumentsPlugin.ENABLED, DocumentsPlugin.ENABLE_BY_DEFAULT);
        }
        catch (RaplaException e)
        {
            return DocumentsPlugin.ENABLE_BY_DEFAULT;
        }
    }
}
