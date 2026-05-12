package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.logger.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.function.Consumer;

public final class OAuthCallbackPasteDialog
{
    private OAuthCallbackPasteDialog() {}

    /**
     * Non-modal helper dialog that lets the user paste the OAuth callback URL
     * from their browser when the automatic loopback redirect can't reach the
     * Swing client (typical under WSL2 NAT networking).
     * <p>
     * The caller wires the dialog's lifecycle to the OAuth flow's future:
     * call {@code dispose()} on the returned dialog when the future completes
     * so the dialog disappears once the login succeeds via the normal path.
     *
     * @param owner       parent for centering and z-order
     * @param i18n        resource bundle (or null — falls back to English)
     * @param logger      diagnostic logger
     * @param onSubmit    receives the trimmed URL the user pasted; the caller
     *                    is responsible for delivering it to the local
     *                    listener and surfacing any error
     */
    public static JDialog show(JFrame owner, RaplaResources i18n, Logger logger, Consumer<String> onSubmit)
    {
        return show(owner, i18n, logger, onSubmit, null);
    }

    /**
     * Same as {@link #show(JFrame, RaplaResources, Logger, Consumer)}, plus an
     * onCancel hook fired when the user closes the dialog. Use it to abort the
     * underlying OAuth flow so the user can retry without waiting for the
     * 5-minute callback timeout.
     */
    public static JDialog show(JFrame owner, RaplaResources i18n, Logger logger,
                               Consumer<String> onSubmit, Runnable onCancel)
    {
        JDialog dialog = new JDialog(owner, label(i18n, "login.oauth.paste.title", "Browser callback"), false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel info = new JLabel("<html><body style='width:380px;'>"
                + label(i18n, "login.oauth.paste.help",
                        "If the browser shows \"This site can't be reached\" after sign-in, "
                                + "copy the URL from the address bar and paste it here.")
                + "</body></html>");
        content.add(info, BorderLayout.NORTH);

        JTextArea area = new JTextArea(4, 50);
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        content.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton pasteBtn = new JButton(label(i18n, "login.oauth.paste.paste", "Paste"));
        JButton submitBtn = new JButton(label(i18n, "login.oauth.paste.submit", "Submit"));
        JButton cancelBtn = new JButton(label(i18n, "cancel", "Cancel"));

        pasteBtn.addActionListener(e -> {
            try
            {
                Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                if (data instanceof String s)
                {
                    area.setText(s);
                }
            }
            catch (Exception ex)
            {
                if (logger != null) logger.warn("clipboard paste failed: " + ex.getMessage());
            }
        });

        submitBtn.addActionListener(e -> {
            String text = area.getText();
            if (text == null || text.trim().isEmpty())
            {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                try
                {
                    onSubmit.accept(text.trim());
                }
                catch (Exception ex)
                {
                    if (logger != null) logger.error("paste-submit failed", ex);
                }
            });
        });

        cancelBtn.addActionListener(e -> {
            if (onCancel != null) onCancel.run();
            dialog.dispose();
        });
        // Window close (X button) also cancels.
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (onCancel != null) onCancel.run();
            }
        });

        buttons.add(pasteBtn);
        buttons.add(submitBtn);
        buttons.add(cancelBtn);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setSize(new Dimension(Math.max(dialog.getWidth(), 480), Math.max(dialog.getHeight(), 220)));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return dialog;
    }

    private static String label(RaplaResources i18n, String key, String fallback)
    {
        if (i18n == null) return fallback;
        try
        {
            String v = i18n.getString(key);
            return v == null || v.isEmpty() ? fallback : v;
        }
        catch (Exception ex)
        {
            return fallback;
        }
    }
}
