/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.internal;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.internal.LanguageChooser;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.menu.PasswordChangeAction;
import org.rapla.client.swing.toolkit.ActionWrapper;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.rest.SettingsService;
import org.rapla.rest.dto.UserSettings;
import org.rapla.storage.dbrm.RemoteStorage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Scope("prototype")

public class UserOption extends RaplaGUIComponent implements UserOptionPanel
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UserOption.class);
    JPanel superPanel = new JPanel();

    JLabel emailLabel = new JLabel();
    JLabel nameLabel = new JLabel();
    JLabel usernameLabel = new JLabel();

    LanguageChooser languageChooser;

    Preferences preferences;


    private final DialogUiFactoryInterface dialogUiFactory;

    private final IOInterface ioInterface;
    private final Supplier<PasswordChangeAction> passwordChangeAction;
    private final SettingsService settings;
    private final RemoteStorage remoteStorage;

    @Autowired
    public UserOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale,
            DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface,Supplier<PasswordChangeAction> passwordChangeAction,
            SettingsService settings, RemoteStorage remoteStorage)
    {
        super(facade, i18n, raplaLocale);
        this.passwordChangeAction = passwordChangeAction;
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
        this.settings = settings;
        this.remoteStorage = remoteStorage;
    }

    @Override
    public boolean isEnabled()
    {
        return true;
    }

    private void create() throws RaplaException
    {
        superPanel.removeAll();
        TableLayout tableLayout = new TableLayout(
                new double[][] { { TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED },
                        { TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5,
                                TableLayout.PREFERRED, 5, TableLayout.PREFERRED } });
        languageChooser = new LanguageChooser(getI18n(), getRaplaLocale());
        RaplaButton changeNameButton = new RaplaButton();
        RaplaButton changeEmailButton = new RaplaButton();
        RaplaButton changePasswordButton = new RaplaButton();

        superPanel.setLayout(tableLayout);
        superPanel.add(new JLabel(getString("language") + ": "), "0,0");
        superPanel.add(languageChooser.getComponent(), "2,0");
        superPanel.add(new JLabel(getString("username") + ": "), "0,2");
        superPanel.add(usernameLabel, "2,2");
        superPanel.add(new JLabel(), "4,2");
        superPanel.add(new JLabel(getString("name") + ": "), "0,4");
        superPanel.add(nameLabel, "2,4");
        superPanel.add(changeNameButton, "4,4");
        superPanel.add(new JLabel(getString("email") + ": "), "0,6");
        superPanel.add(emailLabel, "2,6");
        superPanel.add(changeEmailButton, "4,6");
        changeNameButton.setText(getString("change"));
        changeNameButton.addActionListener(new MyActionListener());
        nameLabel.setText(this.getUser().getName());
        emailLabel.setText(this.getUser().getEmail());
        changeEmailButton.setText(getString("change"));
        changeEmailButton.addActionListener(new MyActionListener2());
        superPanel.add(new JLabel(getString("password") + ":"), "0,8");
        superPanel.add(new JLabel("****"), "2,8");
        superPanel.add(changePasswordButton, "4,8");
        PopupContext popupContext = createPopupContext(getComponent(), null);
        PasswordChangeAction passwordChangeAction = this.passwordChangeAction.get().setPopupContext( popupContext);
        User user = getUser();
        passwordChangeAction.changeObject(user);
        changePasswordButton.setText(getString("change"));
        changePasswordButton.addActionListener((evt)->passwordChangeAction.actionPerformed());
        usernameLabel.setText(user.getUsername());

        // PRD 050: probe self-edit capabilities; for external-auth users
        // (managed by Keycloak / LDAP / etc.) disable the three change
        // buttons and explain why. Server-side guards already reject the
        // calls; this just removes the dead-click affordance.
        applyEditCapabilities(changeNameButton, changeEmailButton, changePasswordButton);
    }

    private void applyEditCapabilities(RaplaButton changeNameButton, RaplaButton changeEmailButton, RaplaButton changePasswordButton)
    {
        RemoteStorage.ProfileEditCapabilities caps;
        try
        {
            caps = remoteStorage.getProfileEditCapabilities();
        }
        catch (Exception ex)
        {
            LOGGER.warn("getProfileEditCapabilities failed; leaving change buttons enabled: {}", ex.getMessage());
            return;
        }
        if (caps.externalIdpLabel() == null)
        {
            // Local user — no changes. canChangePassword may still be false
            // (e.g. operator disabled it deployment-wide); honour that.
            changePasswordButton.setEnabled(caps.canChangePassword());
            return;
        }
        String hint = "Managed by " + caps.externalIdpLabel() + " — change there";
        if (!caps.canChangeName())
        {
            changeNameButton.setEnabled(false);
            changeNameButton.setToolTipText(hint);
        }
        if (!caps.canChangeEmail())
        {
            changeEmailButton.setEnabled(false);
            changeEmailButton.setToolTipText(hint);
        }
        if (!caps.canChangePassword())
        {
            changePasswordButton.setEnabled(false);
            changePasswordButton.setToolTipText(hint);
        }
        // Visible per-row hint so the user understands without hovering.
        superPanel.add(new JLabel(hint), "6,4");
        superPanel.add(new JLabel(hint), "6,6");
        superPanel.add(new JLabel(hint), "6,8");
    }

    public void show() throws RaplaException
    {
        create();
        String language;
        try
        {
            LOGGER.info("UserOption.show(): fetching /settings/me via REST");
            UserSettings me = settings.getMe();
            language = me.language();
        }
        catch (Exception e)
        {
            LOGGER.warn("GET /settings/me failed, falling back to local cache: {}", e.getMessage());
            language = preferences.getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, null);
        }
        if (language != null && language.isEmpty()) language = null;
        languageChooser.setSelectedLanguage(language);
    }

    public void setPreferences(Preferences preferences)
    {
        this.preferences = preferences;
    }

    public void commit()
    {
        String language = languageChooser.getSelectedLanguage();
        preferences.putEntry(RaplaLocale.LANGUAGE_ENTRY, language);
    }

    public String getName(Locale locale)
    {
        return getString("personal_options");
    }

    public JComponent getComponent()
    {
        return superPanel;
    }

    class MyActionListener implements ActionListener
    {

        public void actionPerformed(ActionEvent arg0)
        {
            final PopupContext popupContext = new SwingPopupContext(getComponent(), null);

            try
            {
                JPanel test = new JPanel();
                test.setLayout(new BorderLayout());
                JPanel content = new JPanel();
                GridLayout layout = new GridLayout();
                layout.setColumns(2);
                layout.setHgap(5);
                layout.setVgap(5);

                //content.setLayout(new TableLayout(new double[][]{{TableLayout.PREFERRED,5,TableLayout.PREFERRED},{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED}}));
                content.setLayout(layout);
                test.add(new JLabel(getString("enter_name")), BorderLayout.NORTH);
                test.add(content, BorderLayout.CENTER);
                User user = getUser();

                Allocatable person = user.getPerson();
                JTextField inputSurname = new JTextField();
                addCopyPaste(inputSurname, getI18n(), getRaplaLocale(), ioInterface);
                JTextField inputFirstname = new JTextField();
                addCopyPaste(inputFirstname, getI18n(), getRaplaLocale(), ioInterface);
                JTextField inputTitle = new JTextField();
                addCopyPaste(inputTitle, getI18n(), getRaplaLocale(), ioInterface);
                // Person connected?
                if (person != null)
                {
                    Classification classification = person.getClassification();
                    DynamicType type = classification.getType();
                    Map<String, JTextField> map = new LinkedHashMap<>();
                    map.put("title", inputTitle);
                    map.put("firstname", inputFirstname);
                    map.put("forename", inputFirstname);
                    map.put("surname", inputSurname);
                    map.put("lastname", inputSurname);
                    int rows = 0;
                    for (Map.Entry<String, JTextField> entry : map.entrySet())
                    {
                        String fieldName = entry.getKey();
                        Attribute attribute = type.getAttribute(fieldName);
                        JTextField value = entry.getValue();
                        if (attribute != null && !content.isAncestorOf(value))
                        {
                            Locale locale = getLocale();
                            content.add(new JLabel(attribute.getName(locale)));
                            content.add(value);
                            Object value2 = classification.getValueForAttribute(attribute);
                            rows++;
                            if (value2 != null)
                            {
                                value.setText(value2.toString());
                            }
                        }
                    }
                    layout.setRows(rows);
                }
                else
                {
                    content.add(new JLabel(getString("name")));
                    content.add(inputSurname);
                    inputSurname.setText(user.getName());
                    layout.setRows(1);
                }
                DialogInterface dlg = dialogUiFactory
                        .createContentDialog(popupContext, test, new String[] { getString("save"), getString("abort") });
                dlg.start(true).execOn(SwingUtilities::invokeLater).thenAccept( index->
                        {
                            if (index == 0) {
                                String title = inputTitle.getText();
                                String firstname = inputFirstname.getText();
                                String surname = inputSurname.getText();
                                getClientFacade().changeName(title, firstname, surname);
                                nameLabel.setText(user.getName());
                            }
                        }
                    ).exceptionally(ex->dialogUiFactory.showException(ex, popupContext));
            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, popupContext);
            }
        }
    }

    class MyActionListener2 implements ActionListener
    {

        JTextField emailField = new JTextField();
        JTextField codeField = new JTextField();
        RaplaButton sendCode = new RaplaButton();
        RaplaButton validate = new RaplaButton();

        public void actionPerformed(ActionEvent arg0)
        {
            PopupContext popupContext = dialogUiFactory.createPopupContext(() -> getComponent());
            try
            {
                // FIXME DialogUI -> DialogInterface
                DialogUI dlg;
                JPanel content = new JPanel();
                GridLayout layout = new GridLayout();
                layout.setColumns(3);
                layout.setRows(2);
                content.setLayout(layout);
                content.add(new JLabel(getString("new_mail")));
                content.add(emailField);
                sendCode.setText(getString("send_code"));
                content.add(sendCode);
                sendCode.setAction(new EmailChangeActionB(emailField, codeField, validate));
                content.add(new JLabel(getString("code_message3") + "  "));
                codeField.setEnabled(false);
                content.add(codeField);
                validate.setText(getString("code_validate"));
                content.add(validate);
                addCopyPaste(emailField, getI18n(), getRaplaLocale(), ioInterface);
                addCopyPaste(codeField, getI18n(), getRaplaLocale(), ioInterface);
                dlg = (DialogUI) dialogUiFactory.createContentDialog(popupContext, content, new String[] { getString("save"), getString("abort") });
                validate.setAction(new EmailChangeActionA(dlg));
                validate.setEnabled(false);
                dlg.setDefault(0);
                dlg.setTitle("Email");
                dlg.getButton(0).setAction(new EmailChangeActionC(getUser(), dlg));
                dlg.getButton(0).setEnabled(false);
                dlg.start(true).exceptionally( ex->dialogUiFactory.showException( ex,popupContext));
            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, popupContext);
            }

        }

        class EmailChangeActionA extends AbstractAction
        {

            private static final long serialVersionUID = 1L;

            DialogUI dlg;

            public EmailChangeActionA(DialogUI dlg)
            {
                this.dlg = dlg;
            }

            public void actionPerformed(ActionEvent arg0)
            {
                PopupContext popupContext = dialogUiFactory.createPopupContext( ()->getComponent());
                try
                {
                    User user = getUser();
                    boolean correct;
                    try
                    {
                        int wert = Integer.parseInt(codeField.getText());
                        correct = wert == (Math.abs(user.getEmail().hashCode()));
                    }
                    catch (NumberFormatException er)
                    {
                        correct = false;
                    }
                    if (correct)
                    {
                        dlg.getButton(0).setEnabled(true);
                    }
                    else
                    {
                        dialogUiFactory.showWarning(getString("code_error1"), popupContext);
                    }

                }
                catch (RaplaException ex)
                {
                    dialogUiFactory.showException(ex, popupContext);
                }
            }

        }

        class EmailChangeActionB extends AbstractAction
        {

            private static final long serialVersionUID = 1L;

            JTextField rec;
            JTextField code;
            RaplaButton button;

            public EmailChangeActionB(JTextField rec, JTextField code, RaplaButton button)
            {
                this.rec = rec;
                this.button = button;
                this.code = code;
            }

            public void actionPerformed(ActionEvent arg0)
            {
                try
                {
                    String recepient = rec.getText();
                    getClientFacade().confirmEmail(recepient);

                    button.setEnabled(true);
                    code.setEnabled(true);
                }
                catch (Exception ex)
                {
                    PopupContext popupContext = dialogUiFactory.createPopupContext( () -> getComponent());
                    dialogUiFactory.showException(ex, popupContext);
                }
            }

        }

        class EmailChangeActionC extends AbstractAction
        {

            private static final long serialVersionUID = 1L;
            User user;
            DialogUI dlg;

            public EmailChangeActionC(User user, DialogUI dlg)
            {
                this.user = user;
                this.dlg = dlg;
            }

            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    String newMail = emailField.getText();
                    getClientFacade().changeEmail(newMail);
                    emailLabel.setText(user.getEmail());
                    dlg.close();
                }
                catch (RaplaException e1)
                {
                    LOGGER.error("changeEmail failed", e1);
                }
            }
        }
    }
}