package org.rapla.client.swing.internal.edit;

import org.rapla.client.RaplaWidget;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.i18n.I18nBundle;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;

final class RaplaTreeEdit implements
        RaplaWidget
{
    int oldIndex = -1;

    JPanel mainPanel = new JPanel();

    JLabel nothingSelectedLabel = new JLabel();
    JScrollPane scrollPane;

    Color selectionBackground = UIManager.getColor("List.selectionBackground");
    Color background = UIManager.getColor("List.background");

    JPanel jointPanel = new JPanel() {
        private static final long serialVersionUID = 1L;

            int xa[] = new int[4];
            int ya[] = new int[4];
            public void paint(Graphics g) {
                super.paint(g);
                TreePath selectedPath = tree.getPathForRow( getSelectedIndex() );
                Rectangle rect = tree.getPathBounds( selectedPath );
                Dimension dim = getSize();
                if (rect != null) {
                    int y = rect.y -scrollPane.getViewport().getViewPosition().y;
                    int y1= Math.min(dim.height,Math.max(0, y)  + scrollPane.getLocation().y);
                    int y2= Math.min(dim.height,Math.max(0,y + rect.height) + scrollPane.getLocation().y);
                    xa[0]=0;
                    ya[0]=y1;
                    xa[1]=dim.width;
                    ya[1]=0;
                    xa[2]=dim.width;
                    ya[2]=dim.height;
                    xa[3]=0;
                    ya[3]=y2;
                    g.setColor(selectionBackground);
                    g.fillPolygon(xa,ya,4);
                    g.setColor(background);
                    g.drawLine(xa[0],ya[0],xa[1],ya[1]);
                    g.drawLine(xa[3],ya[3],xa[2],ya[2]);
                }
            }
        };
    JPanel content = new JPanel();
    JPanel detailContainer = new JPanel();
    JPanel editPanel = new JPanel();

    JTree tree = new JTree() {
        private static final long serialVersionUID = 1L;

            public void setModel(TreeModel model) {
            	if ( this.treeModel!= null)
            	{
            		treeModel.removeTreeModelListener( listener);
            	}
            	super.setModel( model );
                model.addTreeModelListener(listener);
            }
        };

    CardLayout cardLayout = new CardLayout();
    private Listener listener = new Listener();
    private ActionListener callback;
    I18nBundle i18n;

    public RaplaTreeEdit(I18nBundle i18n,JComponent detailContent,ActionListener callback) {
        this.i18n = i18n;
        this.callback = callback;
        mainPanel.setLayout(new TableLayout(new double[][] {
            {TableLayout.PREFERRED,TableLayout.PREFERRED,TableLayout.FILL}
            ,{TableLayout.FILL}
        }));
        jointPanel.setPreferredSize(new Dimension(20,50));
        mainPanel.add(content,"0,0");
        mainPanel.add(jointPanel,"1,0");
        mainPanel.add(editPanel,"2,0");
        editPanel.setLayout(cardLayout);
        editPanel.add(nothingSelectedLabel, "0");
        editPanel.add(detailContainer, "1");
        content.setLayout(new BorderLayout());
        scrollPane = new JScrollPane(tree
                                     ,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                                     ,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(310,80));
        content.add(scrollPane, BorderLayout.CENTER);
        detailContainer.setLayout(new BorderLayout());
        editPanel.setBorder(BorderFactory.createRaisedBevelBorder());
        detailContainer.add(detailContent, BorderLayout.CENTER);
        scrollPane.getViewport().addChangeListener(listener);

        tree.addMouseListener(listener);
        tree.addTreeSelectionListener(listener);

        modelUpdate();
        nothingSelectedLabel.setHorizontalAlignment(JLabel.CENTER);
        nothingSelectedLabel.setText(i18n.getString("nothing_selected"));
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    public JTree getTree() {
        return tree;
    }

    public void setListDimension(Dimension d) {
        scrollPane.setPreferredSize(d);
    }

    public int getSelectedIndex() {
        return tree.getMinSelectionRow();
    }

    public void select(int index) {
        tree.setSelectionRow(index);
        if (index >=0) {
            TreePath selectedPath = tree.getPathForRow(index);
            tree.makeVisible( selectedPath );
        }
    }

    private void modelUpdate() {
        jointPanel.repaint();
    }

    public Object getSelectedValue() {
        TreePath treePath = tree.getSelectionPath();
        if (treePath == null)
            return null;
        return ((DefaultMutableTreeNode)treePath.getLastPathComponent()).getUserObject();
    }

    private void editSelectedEntry() {
        Object selected = getSelectedValue();
        if (selected == null) {
            cardLayout.first(editPanel);
            return;
        } else {
            cardLayout.last(editPanel);
            callback.actionPerformed(new ActionEvent(this
                                                     ,ActionEvent.ACTION_PERFORMED
                                                     ,"edit"
                                                     )
                                     );
        }
    }

    class Listener extends MouseAdapter implements TreeSelectionListener,ChangeListener, TreeModelListener {
        public void valueChanged(TreeSelectionEvent evt) {
            int index = getSelectedIndex();
            if (index != oldIndex) {
                oldIndex = index;
                editSelectedEntry();
                modelUpdate();
            }
        }

        public void stateChanged(ChangeEvent evt) {
            if (evt.getSource() == scrollPane.getViewport()) {
                jointPanel.repaint();
            }
        }
        
        public void treeNodesChanged(TreeModelEvent e) {
            modelUpdate();
        }
        public void treeNodesInserted(TreeModelEvent e) {
        }
        public void treeNodesRemoved(TreeModelEvent e) {
        }
        public void treeStructureChanged(TreeModelEvent e) {
        }

    }
}

