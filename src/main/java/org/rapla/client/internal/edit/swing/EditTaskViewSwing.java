package org.rapla.client.internal.edit.swing;

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.internal.edit.EditTaskViewFactory;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.internal.edit.AllocatableMergeEditUI;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@DefaultImplementation(of = EditTaskViewFactory.class, context = InjectionContext.swing)
public class EditTaskViewSwing implements EditTaskViewFactory<Component>
{
    protected final Map<String, Provider<EditComponent>> editUiProvider;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaResources i18n;
    final Provider<AllocatableMergeEditUI> mergeUiProvider;

    @Inject
    public EditTaskViewSwing(Map<String, Provider<EditComponent>> editUiProvider, DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n,
            Provider<AllocatableMergeEditUI> mergeUiProvider)
    {
        this.editUiProvider = editUiProvider;
        this.dialogUiFactory = dialogUiFactory;
        this.i18n = i18n;
        this.mergeUiProvider = mergeUiProvider;
    }

    @Override
    public <T extends Entity> EditTaskPresenter.EditTaskView<T,Component> create(Map<T,T> editMap, boolean isMerge) throws RaplaException {
        final Collection<T> toEdit = editMap.values();
        final EditComponent<T, JComponent> ui;
        {
            T obj = toEdit.iterator().next();
            final Class typeClass = obj.getTypeClass();
            final String id = typeClass.getName();
            if ( isMerge )
            {
                ui = (EditComponent<T, JComponent>) mergeUiProvider.get();
            }
            else
            {
                final Provider<EditComponent> editComponentProvider = editUiProvider.get(id);
                if (editComponentProvider != null)
                {
                    ui = (EditComponent<T, JComponent>) editComponentProvider.get();
                }
                else
                {
                    throw new RuntimeException("Can't edit objects of type " + typeClass.toString());
                }
            }
        }
        ui.setObjects(new ArrayList<>(toEdit));
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new BorderLayout());
        jPanel.add(ui.getComponent(), BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel();
        final String confirmText = isMerge ? i18n.getString("merge"): i18n.getString("save");
        RaplaButton saveButton = new RaplaButton(confirmText, RaplaButton.DEFAULT);
        final RaplaWidget raplaWidget = () -> jPanel;
        final PopupContext popupContext = dialogUiFactory.createPopupContext(raplaWidget);
        RaplaButton cancelButton = new RaplaButton(i18n.getString("cancel"), RaplaButton.DEFAULT);
        saveButton.setDefaultCapable(true);
        buttonsPanel.add(saveButton);
        buttonsPanel.add(cancelButton);
        jPanel.add(buttonsPanel, BorderLayout.SOUTH);

        return new EditTaskPresenter.EditTaskView<T,Component>() {
            @Override
            public Component getComponent() {
                return (Component) raplaWidget.getComponent();
            }

            @Override
            public void start(Consumer<Collection<T>> save, Runnable close, Runnable delete) {

                saveButton.addActionListener((evt) ->
                {
                    try
                    {
                        ui.mapToObjects();
                        List<T> saveObjects = ui.getObjects();
                        save.accept( saveObjects);
                    }
                    catch (IllegalAnnotationException ex)
                    {
                        dialogUiFactory.showWarning(ex.getMessage(), popupContext);
                    }
                    catch (Exception ex)
                    {
                        dialogUiFactory.showException(ex, popupContext);
                    }
                });
                cancelButton.addActionListener((evt) ->
                {
                    close.run();
                });
            }

            @Override
            public Map<T,T> getEditMap()
            {
                return editMap;
            }

            @Override
            public boolean hasChanged() {
                return true;
            }
        };
    }

}
