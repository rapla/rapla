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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.internal.LanguageChooser;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.action.user.PasswordChangeAction;
import org.rapla.client.swing.toolkit.ActionWrapper;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides = UserOptionPanel.class,id="userOption")
public class UserOption extends RaplaGUIComponent
    implements
        UserOptionPanel
{
	JPanel superPanel = new JPanel();

	JLabel emailLabel = new JLabel();
	JLabel nameLabel = new JLabel();
	JLabel usernameLabel = new JLabel();
	
	LanguageChooser languageChooser; 		
	
	Preferences preferences;

    private final RaplaImages raplaImages;

    private final DialogUiFactoryInterface dialogUiFactory;

    private final IOInterface ioInterface;
	@Inject
    public UserOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface) {
        super(facade, i18n, raplaLocale, logger);
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
    }
	
	@Override
	public boolean isEnabled()
	{
	    return true;
	}

    
    private void create() throws RaplaException {
    	superPanel.removeAll();
    	TableLayout tableLayout = new TableLayout(new double[][]{{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED},{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED}});
		languageChooser= new LanguageChooser( getLogger(), getI18n(),getRaplaLocale());
	    RaplaButton changeNameButton = new RaplaButton();
	    RaplaButton changeEmailButton = new RaplaButton();
	    RaplaButton changePasswordButton = new RaplaButton();
    	    

        superPanel.setLayout(tableLayout);
        superPanel.add( new JLabel(getString("language") + ": "),"0,0" );
        superPanel.add( languageChooser.getComponent(),"2,0");
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
        changeEmailButton.addActionListener(new MyActionListener2( ));
        superPanel.add(new JLabel(getString("password") + ":"), "0,8");
        superPanel.add(new JLabel("****"), "2,8");
        superPanel.add(changePasswordButton, "4,8");
        PasswordChangeAction passwordChangeAction = new PasswordChangeAction(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(),createPopupContext(getComponent(), null), raplaImages, dialogUiFactory);
        User user = getUser();
		passwordChangeAction.changeObject(user);
        changePasswordButton.setAction(new ActionWrapper(passwordChangeAction));
        changePasswordButton.setText(getString("change"));
    	usernameLabel.setText(user.getUsername());
    }

    public void show() throws RaplaException {
    	create();
    	String language = preferences.getEntryAsString(RaplaLocale.LANGUAGE_ENTRY,null);
    	languageChooser.setSelectedLanguage( language);
    }

    
    public void setPreferences(Preferences preferences) {
    	this.preferences = preferences;
    }


    public void commit() {
    	String language = languageChooser.getSelectedLanguage();
    	preferences.putEntry( RaplaLocale.LANGUAGE_ENTRY,language );
    }

    
    public String getName(Locale locale) {
        return getString("personal_options");
    }

    public JComponent getComponent() {
        return superPanel;
    }
	
	class MyActionListener implements ActionListener{
		
		public void actionPerformed(ActionEvent arg0) 
		{
			try 
			{
				JPanel test = new JPanel();
				test.setLayout( new BorderLayout());
				JPanel content = new JPanel();
				GridLayout layout = new GridLayout();
				layout.setColumns(2);
				layout.setHgap(5);
				layout.setVgap(5);
			
				//content.setLayout(new TableLayout(new double[][]{{TableLayout.PREFERRED,5,TableLayout.PREFERRED},{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED}}));
				content.setLayout( layout);
				test.add(new JLabel(getString("enter_name")), BorderLayout.NORTH);
				test.add(content, BorderLayout.CENTER);
				User user = getUserModule().getUser();
				
				Allocatable person = user.getPerson();
				JTextField inputSurname = new JTextField();
				addCopyPaste(inputSurname, getI18n(), getRaplaLocale(), ioInterface, getLogger());
				JTextField inputFirstname = new JTextField();
				addCopyPaste(inputFirstname, getI18n(), getRaplaLocale(), ioInterface, getLogger());
				JTextField inputTitle= new JTextField();
				addCopyPaste(inputTitle, getI18n(), getRaplaLocale(), ioInterface, getLogger());
				// Person connected?
				if ( person != null)
				{
					Classification classification = person.getClassification();
					DynamicType type = classification.getType();
					Map<String,JTextField> map = new LinkedHashMap<String, JTextField>();
					map.put("title", inputTitle);
					map.put("firstname", inputFirstname);
					map.put("forename", inputFirstname);
					map.put("surname", inputSurname);
					map.put("lastname", inputSurname);
					int rows = 0;
					for (Map.Entry<String, JTextField> entry: map.entrySet())
					{
						String fieldName = entry.getKey();
						Attribute attribute = type.getAttribute(fieldName);
						JTextField value = entry.getValue();
						if ( attribute!= null && !content.isAncestorOf(value))
						{
							Locale locale = getLocale();
							content.add( new JLabel(attribute.getName(locale)));
							content.add( value);
							Object value2 = classification.getValue( attribute);
							rows++;
							if ( value2 != null)
							{
								value.setText( value2.toString());
							}
						}
					}
					layout.setRows(rows);			}
				else
				{
					content.add( new JLabel(getString("name")));
					content.add( inputSurname);
					inputSurname.setText( user.getName());
					layout.setRows(1);
				}
				DialogInterface dlg = dialogUiFactory.create(new SwingPopupContext(getComponent(), null),true,test,new String[] {getString("save"),getString("abort")});
				dlg.start(true);
				if (dlg.getSelectedIndex() == 0)
				{
					String title = inputTitle.getText();
					String firstname = inputFirstname.getText();
					String surname =inputSurname.getText();
					getUserModule().changeName(title,firstname,surname);
					
			    	nameLabel.setText(user.getName());
				}
			} 
			catch (RaplaException ex) 
			{
			    dialogUiFactory.showException( ex, new SwingPopupContext(getMainComponent(), null));
			}
		}
	}

	class MyActionListener2 implements ActionListener {
	
		JTextField emailField = new JTextField();
  	    JTextField codeField = new JTextField();
  	    RaplaButton sendCode = new RaplaButton();
  	    RaplaButton validate = new RaplaButton();
  	    
		public void actionPerformed(ActionEvent arg0) {
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
			sendCode.setAction(new EmailChangeActionB( emailField, codeField, validate));
			content.add(new JLabel(getString("code_message3") + "  "));
			codeField.setEnabled(false);
			content.add(codeField);
			validate.setText(getString("code_validate"));
			content.add(validate);
			addCopyPaste(emailField, getI18n(), getRaplaLocale(), ioInterface, getLogger());
			addCopyPaste(codeField, getI18n(), getRaplaLocale(), ioInterface, getLogger());
			dlg = (DialogUI) dialogUiFactory.create(new SwingPopupContext(getComponent(), null),true,content,new String[] {getString("save"),getString("abort")});
			validate.setAction(new EmailChangeActionA(dlg));
			validate.setEnabled(false);
			dlg.setDefault(0);
	        dlg.setTitle("Email");
	        dlg.getButton(0).setAction(new EmailChangeActionC(getUserModule().getUser(), dlg));
	        dlg.getButton(0).setEnabled(false);
	        dlg.start(true);
			}
			catch (RaplaException ex) 
			{
			    dialogUiFactory.showException( ex, new SwingPopupContext(getMainComponent(), null));
			}
			
		}
		
		 class EmailChangeActionA extends AbstractAction {
	
			private static final long serialVersionUID = 1L;
			
			DialogUI dlg;
			public EmailChangeActionA( DialogUI dlg){
				this.dlg = dlg;
			}
			
			public void actionPerformed(ActionEvent arg0) {
				try
				{
					User user = getUserModule().getUser();
					boolean correct;
					try {
						int wert  = Integer.parseInt(codeField.getText());
						correct = 	wert == (Math.abs(user.getEmail().hashCode()));
					} catch (NumberFormatException er) 
					{
						correct = false;
					}
					if ( correct)
					{
						dlg.getButton(0).setEnabled(true);
					}
					else
					{
						JOptionPane.showMessageDialog(getMainComponent(),
							    getString("code_error1"),
							    getString("error"),
							    JOptionPane.ERROR_MESSAGE);
					}
					
				} catch (RaplaException ex) {
				    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
				}
			}
			 
		 }
		
		 class EmailChangeActionB extends AbstractAction {
	
				private static final long serialVersionUID = 1L;
	
				JTextField rec;
				JTextField code;
				RaplaButton button;
				
				public EmailChangeActionB(JTextField rec, JTextField code, RaplaButton button){
					this.rec = rec;
					this.button = button;
					this.code = code;
				}
				
				public void actionPerformed(ActionEvent arg0) 
				{
					try 					
					{
						String recepient = rec.getText();
			            getUserModule().confirmEmail(recepient);
						
			            button.setEnabled(true);
			            code.setEnabled(true);
					} 
					catch (Exception ex) {
					    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
					}
				}
				 
			 }
		 class EmailChangeActionC extends AbstractAction {
				
				private static final long serialVersionUID = 1L;
				User user;
				DialogUI dlg;
				public EmailChangeActionC(User user, DialogUI dlg){
					this.user = user;
					this.dlg = dlg;
				}
				public void actionPerformed(ActionEvent e) {
					try {
						String newMail = emailField.getText();
						getUserModule().changeEmail(newMail);
						emailLabel.setText(user.getEmail());
						dlg.close();
					} catch (RaplaException e1) {
						e1.printStackTrace();
					}
				}
		 }
	}
}