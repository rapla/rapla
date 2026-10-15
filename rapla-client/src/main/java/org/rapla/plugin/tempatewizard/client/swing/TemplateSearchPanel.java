package org.rapla.plugin.tempatewizard.client.swing;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

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

/** PRD 104 Phase 7: all templates in one sorted, scrollable list, narrowed by a search field (AND over terms, like the SPA picker). */
public final class TemplateSearchPanel<T> extends JPanel
{
    private static final long serialVersionUID = 1L;

    private final List<T> items;
    private final Function<T, String> name;
    private final DefaultListModel<T> model = new DefaultListModel<>();
    private final JList<T> list = new JList<>(model);
    private final JTextField filter = new JTextField();

    public TemplateSearchPanel(List<T> items, Function<T, String> name, Locale locale, String filterLabel, Consumer<T> selectionChanged, Runnable chosen)
    {
        super(new BorderLayout(0, 6));
        this.items = sorted(items, name, locale);
        this.name = name;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer()
        {
            private static final long serialVersionUID = 1L;

            @Override
            @SuppressWarnings("unchecked")
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus)
            {
                return super.getListCellRendererComponent(l, name.apply((T) value), index, selected, focus);
            }
        });
        list.addListSelectionListener(e -> selectionChanged.accept(selected()));
        list.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && selected() != null) chosen.run();
            }
        });
        filter.addActionListener(e -> { if (selected() != null) chosen.run(); });
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
        setPreferredSize(new Dimension(560, 480));
        setSize(560, 480);
        refill();
    }

    public T selected()
    {
        return list.getSelectedValue();
    }

    public void focusFilter()
    {
        filter.requestFocusInWindow();
    }

    private void refill()
    {
        model.clear();
        model.addAll(filter(items, name, filter.getText()));
        if (!model.isEmpty()) list.setSelectedIndex(0);
        else list.clearSelection();
    }

    static <T> List<T> sorted(List<T> items, Function<T, String> name, Locale locale)
    {
        Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
        return items.stream().sorted(Comparator.comparing(name, collator)).toList();
    }

    static <T> List<T> filter(List<T> items, Function<T, String> name, String query)
    {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return items;
        String[] terms = q.split("\\s+");
        return items.stream().filter(t -> {
            String hay = name.apply(t).toLowerCase(Locale.ROOT);
            return Arrays.stream(terms).allMatch(hay::contains);
        }).toList();
    }
}
