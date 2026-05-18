/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Frithjof Kurtz                  |
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
package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.toolkit.DisabledGlassPane;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.components.i18n.LocaleChangeEvent;
import org.rapla.components.i18n.LocaleChangeListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;
import java.awt.image.ImageObserver;
import java.net.URL;
import java.util.List;

public final class LoginDialog extends JFrame implements LocaleChangeListener
{
	private static final long	serialVersionUID	= -1887723833652617352L;

	Container					container;
	JPanel						upperpanel			= new JPanel();
	JPanel						lowerpanel			= new JPanel();
	JLabel chooseLanguageLabel = new JLabel();
	JPanel						userandpassword		= new JPanel();
	JPanel						buttonPanel			= new JPanel();
	JTextField					username			= new JTextField(15);
	JPasswordField				password			= new JPasswordField(15);
	JLabel						usernameLabel		= new JLabel();
	JLabel						passwordLabel		= new JLabel();
	// PRD 029 Phase 4 — sign-in method chooser (Password, rapla, Keycloak, …),
	// populated from OAuth discovery. Replaces the standalone OAuth button.
	JLabel						methodLabel			= new JLabel();
	JComboBox<String>			methodChooser		= new JComboBox<>();
	JButton						loginBtn			= new JButton();
	JButton						exitBtn				= new JButton();
	JButton						abortBtn			= new JButton();
	JLabel						statusLabel			= new JLabel();
	RaplaResources				i18n;
	ImageObserver				observer;
	Image						image;
	JPanel						canvas;
	protected AbstractBundleManager localeSelector;
    StartupEnvironment env;
    // we have to add an extra gui component here because LoginDialog extends RaplaFrame and therefore can't extent RaplaGUIComponent
    private final RaplaLocale raplaLocale;
    private final Logger logger;
    // Whether the method chooser row should be shown — true once more than one
    // sign-in method is available. Remembered so clearBrowserLoginInProgress
    // can restore it.
    private boolean methodChooserVisible = false;

    private LoginDialog(StartupEnvironment env, RaplaResources i18n, AbstractBundleManager bundleManager, Logger logger, RaplaLocale raplaLocale) throws RaplaException
	{
		super();
		this.env =  env;
		this.i18n = i18n;
		localeSelector = bundleManager;
		localeSelector.addLocaleChangeListener(this);
		this.logger = logger;
		this.raplaLocale = raplaLocale;
		getRootPane().setGlassPane(new DisabledGlassPane());
	}

	public void busy( String message)
	{
		final DisabledGlassPane glassPane = (DisabledGlassPane) getRootPane().getGlassPane();
		glassPane.activate(message);
	}

	public void idle(){
		final DisabledGlassPane glassPane = (DisabledGlassPane) getRootPane().getGlassPane();
		glassPane.deactivate();
	}

	public static LoginDialog create(StartupEnvironment env, RaplaResources i18n, AbstractBundleManager bundleManager, Logger logger, RaplaLocale raplaLocale, JComponent languageSelector) throws RaplaException
	{
		LoginDialog dlg = new LoginDialog(env, i18n, bundleManager, logger, raplaLocale);
		dlg.init(languageSelector);
		return dlg;
	}

	Action exitAction;

	@Override
	protected void processWindowEvent(WindowEvent e) {
		final int id = e.getID();
		if (id == WindowEvent.WINDOW_CLOSING) {
			exitAction.actionPerformed(null);
		}
		super.processWindowEvent(e);
	}

	public void setLoginAction(Action action)
	{
		loginBtn.setAction(action);
	}

	public void setExitAction(Action action)
	{
		exitAction = action;
		exitBtn.setAction( action );
	}

	/** The "Abort" button shown in "browser sign-in in progress" mode — cancels
	 *  the browser-login wait and returns to the credential dialog, as opposed
	 *  to Exit which quits the application. */
	public void setAbortAction(Action action)
	{
		abortBtn.setAction(action);
	}

	/**
	 * Populates the sign-in method chooser (PRD 029 Phase 4). Index 0 is the
	 * local username/password method; the remaining entries are browser-based
	 * OAuth providers (rapla SAS, Keycloak, …) from discovery. The chooser is
	 * only shown when more than one method is available.
	 */
	public void setLoginMethods(List<String> methodLabels)
	{
		methodChooser.removeAllItems();
		for (String label : methodLabels)
		{
			methodChooser.addItem(label);
		}
		methodChooserVisible = methodLabels.size() > 1;
		methodLabel.setVisible(methodChooserVisible);
		methodChooser.setVisible(methodChooserVisible);
	}

	/** Index of the selected sign-in method (0 = local username/password). */
	public int getSelectedMethodIndex()
	{
		return Math.max(0, methodChooser.getSelectedIndex());
	}

	/** Pre-selects a sign-in method (e.g. the one used at the last login).
	 *  Out-of-range indices are ignored. */
	public void setSelectedMethodIndex(int index)
	{
		if (index >= 0 && index < methodChooser.getItemCount())
		{
			methodChooser.setSelectedIndex(index);
		}
	}

	/** Registers a listener fired whenever the user picks a different sign-in method. */
	public void setMethodChangeListener(ActionListener listener)
	{
		methodChooser.addActionListener(listener);
	}

	/** Greys out (or re-enables) the username/password fields — used to disable
	 *  them when a browser-based provider is selected in the method chooser. */
	public void setCredentialsEnabled(boolean enabled)
	{
		username.setEnabled(enabled);
		password.setEnabled(enabled);
		usernameLabel.setEnabled(enabled);
		passwordLabel.setEnabled(enabled);
	}

	private static GridLayout buttonGrid(int columns)
	{
		GridLayout g = new GridLayout(1, columns);
		g.setHgap(20);
		return g;
	}

	/**
	 * "Browser sign-in in progress" mode. The credential fields and the method
	 * chooser are hidden, a status message is shown, and the Exit + Abort
	 * buttons stay clickable.
	 *
	 * @param message status text displayed where the username/password rows
	 *                normally are (e.g. "Sign in via the browser tab that just opened")
	 */
	public void setBrowserLoginInProgress(String message)
	{
		statusLabel.setText("<html><body style='width:280px;padding:8px;'>" + message + "</body></html>");
		statusLabel.setVisible(true);
		username.setVisible(false);
		password.setVisible(false);
		usernameLabel.setVisible(false);
		passwordLabel.setVisible(false);
		methodLabel.setVisible(false);
		methodChooser.setVisible(false);
		// Button row becomes Exit + Abort. Abort cancels the browser-login
		// wait and returns to the credential dialog; Exit quits the app.
		buttonPanel.removeAll();
		buttonPanel.setLayout(buttonGrid(2));
		buttonPanel.add(exitBtn);
		buttonPanel.add(abortBtn);
		revalidate();
		repaint();
	}

	/** Restore the dialog to its normal state (e.g. when OAuth fails or is
	 *  aborted and we want the user to type credentials). */
	public void clearBrowserLoginInProgress()
	{
		statusLabel.setVisible(false);
		username.setVisible(true);
		password.setVisible(true);
		usernameLabel.setVisible(true);
		passwordLabel.setVisible(true);
		loginBtn.setVisible(true);
		methodLabel.setVisible(methodChooserVisible);
		methodChooser.setVisible(methodChooserVisible);
		buttonPanel.removeAll();
		buttonPanel.setLayout(buttonGrid(2));
		buttonPanel.add(exitBtn);
		buttonPanel.add(loginBtn);
		revalidate();
		repaint();
	}

	private void init(JComponent languageSelector)
	{
		container = getContentPane();
		container.setLayout(new BorderLayout());
		((JComponent) container).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// ################## BEGIN LOGO ###################

		observer = (img, flags, x, y, w, h) -> {
            if ((flags & ALLBITS) != 0)
            {
                canvas.repaint();
            }
            return (flags & (ALLBITS | ABORT | ERROR)) == 0;
        };

		canvas = new JPanel()
		{
			private static final long	serialVersionUID	= 1L;

			public void paint(Graphics g)
			{
				g.drawImage(image, 0, 0, observer);
			}
		};

		Toolkit toolkit = Toolkit.getDefaultToolkit();
		// creating an URL to the path of the picture
		URL url = LoginDialog.class.getResource("/org/rapla/gui/images/tafel.png");
		// getting it as image object
		image = toolkit.createImage(url);

		container.add(canvas, BorderLayout.CENTER);

		MediaTracker mt = new MediaTracker(container);
		mt.addImage(image, 0);
		try
		{
			mt.waitForID(0);
		}
		catch (InterruptedException e)
		{
		}

		// ################## END LOGO ###################

		// ################## BEGIN LABELS AND TEXTFIELDS ###################

		container.add(lowerpanel, BorderLayout.SOUTH);
		lowerpanel.setLayout(new BorderLayout());
		lowerpanel.add(userandpassword, BorderLayout.NORTH);
		double pre = TableLayout.PREFERRED;
		double fill = TableLayout.FILL;
		// Rows: 0 language, 2 sign-in method, 4 username, 6 password.
		double[][] sizes = { { pre, 10, fill }, { pre, 5, pre, 5, pre, 5, pre, 5 } };
		TableLayout tableLayout = new TableLayout(sizes);
		userandpassword.setLayout(tableLayout);
		userandpassword.add(chooseLanguageLabel,"0,0");
		userandpassword.add(languageSelector, "2,0");
		userandpassword.add(methodLabel, "0,2");
		userandpassword.add(methodChooser, "2,2");
		userandpassword.add(usernameLabel, "0,4");
		userandpassword.add(passwordLabel, "0,6");
		userandpassword.add(username, "2,4");
		userandpassword.add(password, "2,6");
		// statusLabel occupies the same horizontal slot as the username/password rows
		// but spans both columns; only shown in "browser login in progress" mode.
		userandpassword.add(statusLabel, "0,4,2,6");
		statusLabel.setVisible(false);
		methodLabel.setVisible(false);
		methodChooser.setVisible(false);
		username.setColumns(14);
		password.setColumns(14);
		Listener listener = new Listener();
		password.addActionListener(listener);
		languageSelector.addFocusListener(listener);
        IOInterface service = null;
        RaplaGUIComponent.addCopyPaste(username, i18n, raplaLocale, service, logger);
        RaplaGUIComponent.addCopyPaste(password, i18n, raplaLocale, service, logger);
		// ################## END LABELS AND TEXTFIELDS ###################

		// ################## BEGIN BUTTONS ###################

		// this is a separate JPanel for the buttons at the bottom
		buttonPanel.setLayout(buttonGrid(2));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		// adding a button for exiting
		buttonPanel.add(exitBtn);
		// and to login
		buttonPanel.add(loginBtn);
		setLocale();
		username.requestFocus();

        int startupEnv = env.getStartupMode();
        if( startupEnv != StartupEnvironment.WEBSTART) {
        	try
        	{
                String userName = System.getProperty("user.name");
            	username.setText(userName);
            	username.selectAll();
        	}
        	catch (SecurityException ex)
        	{
        	    // Not sure if it is needed, to catch this. I don't know if a custom system property is by default protected in a sandbox environment
        	}
        }

		lowerpanel.add(buttonPanel, BorderLayout.SOUTH);

		// ################## END BUTTONS ###################

		// ################## BEGIN FRAME ###################

		// these are the dimensions of the rapla picture
		int picturewidth = 372;
		int pictureheight = 182;
		// and a border around it
		int border = 10;
		// canvas.setBounds(0, 0, picturewidth, pictureheight);

		this.getRootPane().setDefaultButton(loginBtn);
		// with the picture dimensions as basis we determine the size
		// of the frame, including some additional space below the picture
		this.setSize(picturewidth + 2 * border, pictureheight + 240);
		this.setResizable(false);

		// ################## END FRAME ###################

	}

	/*
	boolean closeCalledFromOutside = false;

	@Override
	public void close() {
		closeCalledFromOutside = true;
		super.close();
	}

	protected void fireFrameClosing() throws PropertyVetoException {
		super.fireFrameClosing();
		if ( !closeCalledFromOutside && exitAction != null)
		{
			exitAction.actionPerformed( new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "exit"));
		}
	}
	*/

	public String getUsername()
	{
		return username.getText();
	}

	public char[] getPassword()
	{
		return password.getPassword();
	}

    public void resetPassword() {
    	password.setText("");
    }

	/** overrides localeChanged from DialogUI */
	public void localeChanged(LocaleChangeEvent evt)
	{
		setLocale();
	}

	private I18nBundle getI18n()
	{
		return i18n;
	}

	private void setLocale()
	{
		chooseLanguageLabel.setText(getI18n().getString("choose_language"));
		exitBtn.setText(getI18n().getString("exit"));
		loginBtn.setText(getI18n().getString("login"));
		abortBtn.setText(getI18n().getString("abort"));
		methodLabel.setText(getI18n().getString("login.method") + ":");
		usernameLabel.setText(getI18n().getString("username") + ":");
		passwordLabel.setText(getI18n().getString("password") + ":");
		setTitle(getI18n().getString("logindialog.title"));
		repaint();
	}

	public void dispose()
	{
		super.dispose();
		localeSelector.removeLocaleChangeListener(this);
	}

	public void testEnter(String newUsername, String newPassword)
	{
		username.setText(newUsername);
		password.setText(newPassword);
	}

	class Listener extends FocusAdapter implements ActionListener
	{
		boolean	bInit	= false;

		public void focusGained(FocusEvent e)
		{
			if (!bInit)
			{
				username.requestFocus();
				bInit = true;
			}
		}

		public void actionPerformed(ActionEvent event)
		{
			if (event.getSource() == password)
			{
				loginBtn.doClick();
            }
		}
	}

}
