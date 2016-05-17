package org.rapla.client.swing.internal;

import org.rapla.RaplaResources;
import org.rapla.client.ApplicationView;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.client.RaplaWidget;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

@DefaultImplementation(of =ApplicationView.class,context= InjectionContext.swing)
@Singleton
public class ApplicationViewSwing implements ApplicationView<JComponent>
{
    private static final String MENU_ACTION = "RAPLA_MENU_ACTION";
    private final RaplaResources i18n;
    private Presenter presenter;

    private final Logger logger;
    RaplaMenuBar menuBar;
    private final RaplaFrame frame ;
    Listener listener = new Listener();
    //CalendarPlaceViewSwing cal;
    JLabel statusBar = new JLabel("");
    private final RaplaImages raplaImages;
    private final FrameControllerList frameControllerList;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public ApplicationViewSwing(RaplaMenuBarContainer menuBarContainer, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, RaplaMenuBar raplaMenuBar,
             RaplaImages raplaImages, FrameControllerList frameControllerList, DialogUiFactoryInterface dialogUiFactory) throws RaplaInitializationException {
        this.i18n = i18n;
        this.logger = logger;
        this.menuBar = raplaMenuBar;
        this.raplaImages = raplaImages;
        this.frameControllerList = frameControllerList;
        this.dialogUiFactory = dialogUiFactory;
        frame =  new RaplaFrame(frameControllerList);
        // CKO TODO Title should be set in config along with the facade used



        JMenuBar menuBar = menuBarContainer.getMenubar();
        menuBar.add(Box.createHorizontalGlue());
        menuBar.add(statusBar);
        menuBar.add(Box.createHorizontalStrut(5));
        frame.setJMenuBar( menuBar );

        getContentPane().setLayout( new BorderLayout() );
        //  getContentPane().add ( statusBar, BorderLayout.SOUTH);

    }

    // FIXME should be moved to Presenter
    public void updateView( ModificationEvent event) throws RaplaException
    {
        menuBar.updateView( event);
    }

    @Override
    public void init(boolean showTooltips, String windowTitle)
    {
        frame.setTitle(windowTitle );
        javax.swing.ToolTipManager.sharedInstance().setEnabled(showTooltips);
        //javax.swing.ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        javax.swing.ToolTipManager.sharedInstance().setInitialDelay( 1000 );
        javax.swing.ToolTipManager.sharedInstance().setDismissDelay( 10000 );
        javax.swing.ToolTipManager.sharedInstance().setReshowDelay( 0 );

        RaplaGUIComponent.setMainComponent( frame);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                presenter.mainClosing();
            }

        });
        frame.setPreferredSize(new Dimension(1280, 1000));
        frame.pack();
        frame.requestFocus();
        frame.setVisible(true);
    }


    public void setPresenter(Presenter presenter)
    {
        this.presenter = presenter;
    }

    //new StatusFader(statusBar).updateStatus();
/*
    private void setStatus() {
        statusBar.setMaximumSize( new Dimension(400,20));
        final StatusFader runnable = new StatusFader(statusBar);
        final Thread fadeThread = new Thread(runnable);
        fadeThread.setDaemon( true);
        fadeThread.start();
    }
*/

    private void fadeIn(JLabel label)
    {
        int alpha = 0;
        Color c = label.getForeground();
        while (alpha <= 230)
        {
            alpha += 25;
            final Color color = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
            label.setForeground(color);
            label.repaint();
        }
    }

    private void fadeOut(JLabel label)
    {
        int alpha = 250;
        Color c = label.getForeground();
        while (alpha > 0)
        {
            alpha -= 25;
            final Color color = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
            label.setForeground(color);
            label.repaint();
        }
    }


    @Override public void setStatusMessage(String message, boolean highlight)
    {
        fadeOut( statusBar);
        try
        {
            Thread.sleep(200);
        }
        catch (InterruptedException e)
        {
            logger.info(e.getMessage());
        }
        statusBar.setText( message);
        final Font boldFont = statusBar.getFont().deriveFont(Font.BOLD);
        statusBar.setFont( boldFont);
        if ( highlight)
        {
            statusBar.setForeground( new Color(220,30,30));
        }
        else
        {
            statusBar.setForeground( new Color(30,30,30) );
        }
        fadeIn( statusBar );
        try
        {
            Thread.sleep(200);
        }
        catch (InterruptedException e)
        {
            logger.info(e.getMessage());
        }
    }

    @Override public void updateMenu()
    {

    }

    static boolean lookAndFeelSet;

    public static void setLookandFeel() {
        if ( lookAndFeelSet )
        {
            return;
        }
        UIDefaults defaults = UIManager.getDefaults();
        Font textFont = defaults.getFont("Label.font");
        if ( textFont == null)
        {
            textFont = new Font("SansSerif", Font.PLAIN, 12);
        }
        else
        {
            textFont = textFont.deriveFont( Font.PLAIN );
        }
        defaults.put("Label.font", textFont);
        defaults.put("Button.font", textFont);
        defaults.put("Menu.font", textFont);
        defaults.put("MenuItem.font", textFont);
        defaults.put("RadioButton.font", textFont);
        defaults.put("CheckBoxMenuItem.font", textFont);
        defaults.put("CheckBox.font", textFont);
        defaults.put("ComboBox.font", textFont);
        defaults.put("Tree.expandedIcon",RaplaImages.getIcon("/org/rapla/client/swing/gui/images/eclipse-icons/tree_minus.gif"));
        defaults.put("Tree.collapsedIcon",RaplaImages.getIcon("/org/rapla/client/swing/gui/images/eclipse-icons/tree_plus.gif"));
        defaults.put("TitledBorder.font", textFont.deriveFont(Font.PLAIN,(float)10.));
        lookAndFeelSet = true;

        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
    }


    @Override
    public void updateContent(RaplaWidget<JComponent> widget)
    {
        JComponent component = widget.getComponent();
        getContentPane().add(  component , BorderLayout.CENTER );

    }

    protected void initComponent( RaplaWidget<Object> objectRaplaWidget)
    {
        RaplaFrame frame = new RaplaFrame(frameControllerList);
        final Container component = (Container)objectRaplaWidget.getComponent();
        frame.setContentPane(component);
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(new Dimension(
                        Math.min(dimension.width,990)
                        // BJO 00000032 temp fix for filter out of frame bounds
                        ,Math.min(dimension.height-10,720)
                        //,Math.min(dimension.height-10,1000)
                )
        );
        frame.addVetoableChangeListener(evt ->  {
            presenter.mainClosing();
        });
        frame.setIconImage( raplaImages.getIconFromKey("icon.edit_window_small").getImage());
    }





    public void show()  {
        logger.debug("Creating Main-Frame");
        createFrame();
        //dataChanged(null);
        //setStatus();
        frame.setIconImage(raplaImages.getIconFromKey("icon.rapla_small").getImage());
        frame.setVisible(true);
        frameControllerList.setMainWindow(frame);
    }

    private JPanel getContentPane() {
        return (JPanel) frame.getContentPane();
    }

    private void createFrame() {
        Dimension dimension = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(new Dimension(
                        Math.min(dimension.width,1200)
                        ,Math.min(dimension.height-20,900)
                )
        );

        frame.addVetoableChangeListener(listener);
        //statusBar.setBorder( BorderFactory.createEtchedBorder());
    }


    class Listener implements VetoableChangeListener
    {
        public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
            if (shouldExit())
                close();
            else
                throw new PropertyVetoException("Don't close",evt);
        }

    }

    public Window getFrame() {
        return frame;
    }

    public JComponent getComponent() {
        return (JComponent) frame.getContentPane();
    }

    protected boolean shouldExit() {
        try {
            DialogInterface dlg = dialogUiFactory.create(
                    new SwingPopupContext(frame.getRootPane(), null)
                    ,true
                    ,i18n.getString("exit.title")
                    ,i18n.getString("exit.question")
                    ,new String[] {
                            i18n.getString("exit.ok")
                            ,i18n.getString("exit.abort")
                    }
            );
            dlg.setIcon("icon.question");
            //dlg.getButton(0).setIcon(getIcon("icon.confirm"));
            dlg.getAction(0).setIcon("icon.abort");
            dlg.setDefault(1);
            dlg.start(true);
            return (dlg.getSelectedIndex() == 0);
        } catch (RaplaException e) {
            logger.error( e.getMessage(), e);
            return true;
        }

    }

    public void close() {
        frame.close();
    }

    @Override public PopupContext createPopupContext()
    {
        return new SwingPopupContext(frame, null);
    }

}