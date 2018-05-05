package org.rapla.client.swing.internal;

import io.reactivex.disposables.Disposable;
import org.rapla.RaplaResources;
import org.rapla.client.ApplicationView;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.internal.RaplaColors;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.AWTColorUtil;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@DefaultImplementation(of = ApplicationView.class, context = InjectionContext.swing)
@Singleton
public class ApplicationViewSwing implements ApplicationView<JComponent>
{
    private static final String MENU_ACTION = "RAPLA_MENU_ACTION";
    private final RaplaResources i18n;
    private Presenter presenter;

    private final Logger logger;
    RaplaMenuBar menuBar;
    private final RaplaFrame frame;
    private final Map<ApplicationEvent, DialogUI> childFrames = new HashMap<>();
    //CalendarPlaceViewSwing cal;
    JLabel statusBar = new JLabel("");
    private final DialogUiFactoryInterface dialogUiFactory;
    CommandScheduler scheduler;

    @Inject
    public ApplicationViewSwing(RaplaMenuBarContainer menuBarContainer, RaplaResources i18n, RaplaFrame frame, RaplaLocale raplaLocale, Logger logger,
            RaplaMenuBar raplaMenuBar, CommandScheduler scheduler,
            DialogUiFactoryInterface dialogUiFactory) throws RaplaInitializationException
    {
        this.i18n = i18n;
        this.scheduler = scheduler;
        this.logger = logger;
        this.menuBar = raplaMenuBar;
        this.dialogUiFactory = dialogUiFactory;
        this.frame = frame;
        // CKO TODO Title should be set in config along with the facade used

        JMenuBar menuBar = menuBarContainer.getMenubar();
        menuBar.add(Box.createHorizontalGlue());
        menuBar.add(statusBar);
        menuBar.add(Box.createHorizontalStrut(5));
        frame.setJMenuBar(menuBar);

        getContentPane().setLayout(new BorderLayout());
        //  getContentPane().add ( statusBar, BorderLayout.SOUTH);

    }

    // FIXME should be moved to Presenter
    public void updateView(ModificationEvent event) throws RaplaException
    {
        menuBar.updateView(event);
    }

    @Override
    public void init(boolean showTooltips, String windowTitle)
    {
        frame.setTitle(windowTitle);
        javax.swing.ToolTipManager.sharedInstance().setEnabled(showTooltips);
        //javax.swing.ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        javax.swing.ToolTipManager.sharedInstance().setInitialDelay(1000);
        javax.swing.ToolTipManager.sharedInstance().setDismissDelay(10000);
        javax.swing.ToolTipManager.sharedInstance().setReshowDelay(0);

        RaplaGUIComponent.setMainComponent(frame);
        frame.addVetoableChangeListener(evt ->
        {
            if (!presenter.mainClosing())
            {
                throw new PropertyVetoException("Should not close", evt);
            }
        });
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setPreferredSize(new Dimension(Math.min(dimension.width, 1200), Math.min(dimension.height - 20, 900)));
        //frame.setPreferredSize(new Dimension(1280, 1000));
        frame.pack();
        frame.requestFocus();
        frame.setVisible(true);
        frame.setIconImage(RaplaImages.getImage( i18n.getIcon("icon.rapla_small")));
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
            alpha += 50;
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
            alpha -= 50;
            final Color color = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
            label.setForeground(color);
            label.repaint();
        }
    }

    @Override
    public void setStatusMessage(String message, boolean highlight)
    {
        SwingUtilities.invokeLater(() ->
        {
            fadeOut(statusBar);
            statusBar.setText(message);
            final Font boldFont = statusBar.getFont().deriveFont(Font.BOLD);
            statusBar.setFont(boldFont);
            if (highlight)
            {
                final Color highlightColor = AWTColorUtil.getColorForHex(RaplaColors.HIGHLICHT_COLOR);
                statusBar.setForeground(highlightColor);
            }
            else
            {
                statusBar.setForeground(new Color(30, 30, 30));
            }
            SwingUtilities.invokeLater(() -> fadeIn(statusBar));
        });
    }

    @Override
    public void updateMenu()
    {

    }

    static boolean lookAndFeelSet;

    public static void setLookandFeel()
    {
        if (lookAndFeelSet)
        {
            return;
        }
        UIDefaults defaults = UIManager.getDefaults();
        Font textFont = defaults.getFont("Label.font");
        if (textFont == null)
        {
            textFont = new Font("SansSerif", Font.PLAIN, 12);
        }
        else
        {
            textFont = textFont.deriveFont(Font.PLAIN);
        }
        defaults.put("Label.font", textFont);
        defaults.put("Button.font", textFont);
        defaults.put("Menu.font", textFont);
        defaults.put("MenuItem.font", textFont);
        defaults.put("RadioButton.font", textFont);
        defaults.put("CheckBoxMenuItem.font", textFont);
        defaults.put("CheckBox.font", textFont);
        defaults.put("ComboBox.font", textFont);
        defaults.put("Tree.expandedIcon", RaplaImages.getIcon("/org/rapla/gui/images/eclipse-icons/tree_minus.gif"));
        defaults.put("Tree.collapsedIcon", RaplaImages.getIcon("/org/rapla/gui/images/eclipse-icons/tree_plus.gif"));
        defaults.put("TitledBorder.font", textFont.deriveFont(Font.PLAIN, (float) 10.));
        lookAndFeelSet = true;

        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
    }

    @Override
    public void updateContent(RaplaWidget<JComponent> widget)
    {
        JComponent component = widget.getComponent();
        final JPanel contentPane = getContentPane();
        final Component[] components = contentPane.getComponents();
        for (Component comp : components)
        {
            if (comp == component)
            {
                return;
            }
        }
        contentPane.add(component, BorderLayout.CENTER);
    }

    private JPanel getContentPane()
    {
        return (JPanel) frame.getContentPane();
    }

    public Window getFrame()
    {
        return frame;
    }

    public JComponent getComponent()
    {
        return (JComponent) frame.getContentPane();
    }

    public void close()
    {
        frame.close();
        final Collection<DialogUI> openDialogs = this.childFrames.values();
        for (DialogUI di : openDialogs)
        {
            di.dispose();
        }
    }

    @Override
    public PopupContext createPopupContext()
    {
        return new SwingPopupContext(frame, null);
    }

    @Override
    public void removeWindow(ApplicationEvent windowId)
    {
        final DialogUI dialogInterface = childFrames.remove(windowId);
        if (dialogInterface != null)
        {
            dialogInterface.close();
        }
    }

    @Override
    public boolean hasWindow(ApplicationEvent windowId)
    {
        final boolean result = childFrames.containsKey(windowId);
        return result;
    }

    @Override
    synchronized public void openWindow(ApplicationEvent windowId, PopupContext popupContext, RaplaWidget<JComponent> objectRaplaWidget, String title,
                           Function<ApplicationEvent, Boolean> windowClosing, Observable<String> busyIdleObservable)
    {
        if ( childFrames.get( windowId)!= null)
        {
            return;
        }
        AtomicReference<DialogUI> frame = new AtomicReference<>();
        final Disposable subscribe = busyIdleObservable.subscribe((message) -> {if ( message!= null && message.length() > 0) frame.get().busy( message); else frame.get().idle();});
        final Container component = (Container) objectRaplaWidget.getComponent();
        String[] options = new String[] {"ok"};
        final DialogUI dialog = (DialogUI) dialogUiFactory.createContentDialog( popupContext, component, options);
        frame.set(dialog);

        dialog.setContentPane(component);
        dialog.setTitle( title);
        dialog.setIconImage(RaplaImages.getImage(i18n.getIcon("icon.edit_window_small")));
        dialog.setSize(1050, 700);

        childFrames.put(windowId, dialog);
        dialog.addVetoableChangeListener(evt -> {

            final ApplicationEvent applicationEvent = new ApplicationEvent(windowId.getApplicationEventId(), windowId.getInfo(),
                    new SwingPopupContext(component, null), null);
            logger.debug("Closing");
            if (windowClosing.apply(applicationEvent))
            {
                subscribe.dispose();
                dialog.dispose();
            }
            else
            {
                throw new PropertyVetoException("close", evt);
            }
        });
        dialog.start( null);
    }

    @Override
    public void requestFocus(ApplicationEvent windowId)
    {
        final DialogUI dialogInterface = childFrames.get(windowId);
        if (dialogInterface != null)
        {
            dialogInterface.toFront();
            dialogInterface.requestFocus();
        }

    }

}