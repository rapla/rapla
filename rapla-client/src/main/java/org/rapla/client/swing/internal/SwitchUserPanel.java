package org.rapla.client.swing.internal;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.rapla.storage.dbrm.UserSummary;

/** PRD 131 R17: filterable list of the users the actor may switch to, by username. */
final class SwitchUserPanel extends JPanel
{
    private static final long serialVersionUID = 1L;

    private final List<UserSummary> users;
    private final DefaultListModel<UserSummary> model = new DefaultListModel<>();
    private final JList<UserSummary> list = new JList<>(model);
    private final JTextField filter = new JTextField();

    SwitchUserPanel(List<UserSummary> users, String filterLabel, Consumer<String> selectionChanged, Runnable chosen)
    {
        super(new BorderLayout(0, 6));
        this.users = users;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus)
            {
                return super.getListCellRendererComponent(l, label((UserSummary) value), index, selected, focus);
            }
        });
        list.addListSelectionListener(e -> selectionChanged.accept(selectedUsername()));
        list.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && selectedUsername() != null) chosen.run();
            }
        });
        filter.addActionListener(e -> { if (selectedUsername() != null) chosen.run(); });
        filter.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { refill(); }
            public void removeUpdate(DocumentEvent e) { refill(); }
            public void changedUpdate(DocumentEvent e) { refill(); }
        });
        add(new JLabel(filterLabel), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(filter, BorderLayout.NORTH);
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(380, 320));
        setSize(380, 320);
        refill();
    }

    String selectedUsername()
    {
        UserSummary u = list.getSelectedValue();
        return u == null ? null : u.getUsername();
    }

    void focusFilter()
    {
        filter.requestFocusInWindow();
    }

    private void refill()
    {
        model.clear();
        filter(users, filter.getText()).forEach(model::addElement);
        if (!model.isEmpty()) list.setSelectedIndex(0);
        else list.clearSelection();
    }

    static String label(UserSummary u)
    {
        String name = u.getDisplayName();
        return name == null || name.isBlank() || name.equals(u.getUsername()) ? u.getUsername() : name + " (" + u.getUsername() + ")";
    }

    static List<UserSummary> filter(List<UserSummary> users, String needle)
    {
        String n = needle == null ? "" : needle.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return users;
        return users.stream()
                .filter(u -> u.getUsername().toLowerCase(Locale.ROOT).contains(n) || u.getDisplayName().toLowerCase(Locale.ROOT).contains(n))
                .toList();
    }
}
