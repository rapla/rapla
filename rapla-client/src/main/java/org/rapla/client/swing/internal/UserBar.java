package org.rapla.client.swing.internal;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;

import org.rapla.RaplaResources;
import org.rapla.client.internal.RaplaColors;
import org.rapla.client.menu.SessionMenuModel;
import org.rapla.client.menu.SessionMenuModel.Entry;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.AWTColorUtil;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.TokenStore;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PRD 131 R1, R9, R10, R13, R15: the top-right user button with its popup menu, the logout icon inside it and the
 * storage hint; amber "Impersonating" cue while the session acts as another user.
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class UserBar
{
    private static final Color AMBER = new Color(0xFF, 0xC1, 0x07);

    private final RaplaResources i18n;
    private final SessionMenu session;
    private final RemoteConnectionInfo connectionInfo;
    private final JLabel userLabel = new JLabel();
    private final JLabel logoutIcon = new JLabel();
    private final JButton userButton = new JButton()
    {
        private static final long serialVersionUID = 1L;

        @Override public Dimension getPreferredSize()
        {
            Dimension d = getLayout().preferredLayoutSize(this);
            Insets in = getInsets();
            return new Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
        }
    };
    private final JButton hintButton = new JButton();
    private final Box component = Box.createHorizontalBox();
    private String statusText = "";
    private boolean statusHighlight;

    @Autowired
    public UserBar(RaplaResources i18n, SessionMenu session, RemoteConnectionInfo connectionInfo)
    {
        this.i18n = i18n;
        this.session = session;
        this.connectionInfo = connectionInfo;

        userButton.setFocusable(false);
        userButton.setMargin(new Insets(2, 6, 2, 6));
        userButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        userButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        userButton.setLayout(new BoxLayout(userButton, BoxLayout.X_AXIS));
        userLabel.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.tree.persons")));
        userLabel.setFont(userLabel.getFont().deriveFont(Font.BOLD));
        logoutIcon.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.logout")));
        logoutIcon.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        userButton.add(userLabel);
        userButton.add(logoutIcon);
        userButton.addActionListener(e -> showUserMenu());

        flat(hintButton);
        hintButton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.info_small")));
        hintButton.addActionListener(e -> showHint());

        component.add(hintButton);
        component.add(userButton);
        render();
    }

    public JComponent getComponent()
    {
        return component;
    }

    /** Status text next to the user icon (welcome message, then the user's name); keeps the cue while impersonating. */
    public void setStatus(String message, boolean highlight)
    {
        this.statusText = message;
        this.statusHighlight = highlight;
        render();
    }

    public void refresh()
    {
        render();
    }

    private void render()
    {
        boolean impersonating = session.impersonating();
        if (impersonating)
        {
            String target = connectionInfo.getImpersonationTargetUsername();
            userLabel.setText(" " + i18n.format("session.impersonating", target == null ? "" : target) + " ");
            userButton.setContentAreaFilled(true);
            userButton.setOpaque(true);
            userButton.setBackground(AMBER);
            userLabel.setForeground(Color.BLACK);
            userButton.setToolTipText(i18n.getString("session.impersonating.tooltip"));
        }
        else
        {
            userLabel.setText(statusText);
            userButton.setOpaque(false);
            userLabel.setForeground(statusHighlight ? AWTColorUtil.getColorForHex(RaplaColors.HIGHLICHT_COLOR) : new Color(30, 30, 30));
            userButton.setToolTipText(i18n.getString("session.menu"));
        }
        logoutIcon.setVisible(SessionMenuModel.logoutIconVisible(impersonating));
        hintButton.setVisible(session.storageHint().isPresent());
        component.revalidate();
        component.repaint();
    }

    private void showUserMenu()
    {
        JPopupMenu menu = new JPopupMenu();
        for (Entry entry : session.userEntries())
        {
            if (entry == Entry.SEPARATOR) menu.addSeparator();
            else menu.add(session.item(entry));
        }
        menu.show(userButton, 0, userButton.getHeight());
    }

    private void showHint()
    {
        Optional<TokenStore.Failure> failure = session.storageHint();
        if (failure.isEmpty()) return;
        String path = failure.get().path() == null ? "" : failure.get().path().toString();
        String text = i18n.format("session.not_stored", path).replace("&", "&amp;").replace("<", "&lt;");
        JPopupMenu popup = new JPopupMenu();
        popup.add(new JLabel("<html><body style='width:320px'>" + text + "</body></html>"));
        popup.show(hintButton, 0, hintButton.getHeight());
    }

    private static void flat(JButton button)
    {
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setAlignmentY(Component.CENTER_ALIGNMENT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent e) { button.setBorderPainted(true); }
            @Override public void mouseExited(MouseEvent e) { button.setBorderPainted(false); }
        });
    }
}
