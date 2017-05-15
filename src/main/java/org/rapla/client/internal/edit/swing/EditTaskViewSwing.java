package org.rapla.client.internal.edit.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.AllocatableMergeEditUI;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.framework.RaplaException;
import org.rapla.function.Consumer;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@DefaultImplementation(of = EditTaskPresenter.EditTaskView.class, context = InjectionContext.swing)
public class EditTaskViewSwing implements EditTaskPresenter.EditTaskView
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
    public <T extends Entity> RaplaWidget doSomething(Collection<T> toEdit,String titleI18n, Consumer<Collection<T>> save, Runnable close, boolean isMerge) throws RaplaException
    {
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
        ui.setObjects(new ArrayList<T>(toEdit));
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new BorderLayout());
        jPanel.add(ui.getComponent(), BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel();
        final String confirmText = isMerge ? i18n.getString("merge"): i18n.getString("save");
        RaplaButton saveButton = new RaplaButton(confirmText, RaplaButton.DEFAULT);
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
                dialogUiFactory.showWarning(ex.getMessage(), new SwingPopupContext((Component) jPanel, null));
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext((Component) jPanel, null));
            }
        });
        RaplaButton cancelButton = new RaplaButton(i18n.getString("cancel"), RaplaButton.DEFAULT);
        cancelButton.addActionListener((evt) ->
        {
            close.run();
        });

        saveButton.setDefaultCapable(true);
        buttonsPanel.add(saveButton);
        buttonsPanel.add(cancelButton);
        jPanel.add(buttonsPanel, BorderLayout.SOUTH);
        JLabel header = new JLabel();
        jPanel.add(header, BorderLayout.NORTH);
        header.setText(titleI18n);
        return () -> jPanel;
    }

}
