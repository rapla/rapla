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

package org.rapla.components.calendar;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Timer;
import java.util.TimerTask;
/** This is an alternative ComboBox implementation. The main differences are:
   <ul>
   <li>No pluggable look and feel</li>
   <li>drop-down button and editor in a separate component (each can get focus)</li>
   <li>the popup-component can be an arbitrary JComponent.</li>
   </ul>
*/
public abstract class RaplaComboBox extends JPanel {
   
   private static final long serialVersionUID = 1L;
   protected JPopupMenu m_popup;
   private boolean m_popupVisible = false;
   private boolean m_isDropDown = true;
   protected RaplaArrowButton m_popupButton;
   protected JLabel jLabel;
   protected JComponent m_editorComponent;

   // If you click on the popupButton while the PopupWindow is shown,
   // the Window will be closed, because you clicked outside the Popup
   // (not because you clicked that special button).
   // The flag popupVisible is unset.
   // After that the same click will trigger the actionPerfomed method
   // on our button. And the window would popup again.
   // Solution: We track down that one mouseclick that closes the popup
   // with the following flags:
   private boolean m_closing = false;
   private boolean m_pressed = false;
   private Listener m_listener = new Listener();

   public RaplaComboBox(JComponent editorComponent) {
       this(true,editorComponent);
   }

   RaplaComboBox(boolean isDropDown,JComponent editorComponent) {
       m_editorComponent = editorComponent;
       m_isDropDown = isDropDown;
       jLabel = new JLabel();
       setLayout(new BorderLayout());
       add(jLabel,BorderLayout.WEST);
       add(m_editorComponent,BorderLayout.CENTER);

       if (m_isDropDown) {
           installPopupButton();
           add(m_popupButton,BorderLayout.EAST);
           m_popupButton.addActionListener(m_listener);
           m_popupButton.addMouseListener(m_listener);
           m_popupVisible = false;
       }
   }
   
   public JLabel getLabel() {
       return jLabel;
   }

   class Listener implements ActionListener,MouseListener,PopupMenuListener {
       // Implementation of ActionListener
       public void actionPerformed(ActionEvent e) {
           // Ignore action, when Popup is closing
           log("ApplicationEvent Performed");
           if (m_pressed && m_closing) {
               m_closing = false;
                           return;
           }
           if ( !m_popupVisible && !m_closing) {
               log("Open");
               showPopup();
           } else {
               m_popupVisible = false;
               closePopup();
           } // end of else
       }
       
       private void log(@SuppressWarnings("unused") String string) {
       //    System.out.println(System.currentTimeMillis() / 1000 + "m_visible:" + m_popupVisible + " closing: " + m_closing + " [" + string + "]");
       }
    // Implementation of MouseListener
       public void mousePressed(MouseEvent evt) {
           m_pressed = true;
           m_closing = false;
           log("Pressed");
       }
       public void mouseClicked(MouseEvent evt) {
           m_closing = false;
       }
       public void mouseReleased(MouseEvent evt) {
           m_pressed = false;
           m_closing = false;
           log("Released");
       }
       public void mouseEntered(MouseEvent me) {
       }
       public void mouseExited(MouseEvent me) {
       }


       // Implementation of PopupListener
       public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
           m_popupVisible = true;
       }

       public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
           m_popupVisible = false;
           m_closing = true;
           m_popupButton.requestFocus();
           m_popupButton.setChar('v');
           log("Invisible");
           final boolean oldState = m_popupButton.isEnabled();
           m_popupButton.setEnabled( false);
           final Timer timer = new Timer(true);
           TimerTask task = new TimerTask()
           {
               public void run() 
               {
                   m_popupButton.setEnabled( oldState);    
               }
           };
           timer.schedule(task, 100);
       }

       public void popupMenuCanceled(PopupMenuEvent e) {
           m_popupVisible = false;
           m_closing = true;
           m_popupButton.requestFocus();
           log("Cancel");
       }

   }

   private void installPopupButton() {
       // Maybe we could use the combobox drop-down button here.
       m_popupButton = new RaplaArrowButton('v');
   }

   public void setFont(Font font) {
       super.setFont(font);
       // Method called during constructor?
       if (font == null)
           return;
       if (m_editorComponent != null)
           m_editorComponent.setFont(font);
       if (m_popupButton != null && m_isDropDown) {
           int size = (int) getPreferredSize().getHeight();
           m_popupButton.setSize(size,size);
       }
   }

   public void setEnabled( boolean enabled ) {
       super.setEnabled( enabled );
       if ( m_editorComponent != null ) {
           m_editorComponent.setEnabled( enabled );
       }
       if ( m_popupButton != null ) {
           m_popupButton.setEnabled ( enabled );
       }
   }

   protected void showPopup() {
       if (m_popup == null)
           createPopup();
       
       Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
       Dimension menuSize = m_popup.getPreferredSize();
       Dimension buttonSize = m_popupButton.getSize();
       Point location = m_popupButton.getLocationOnScreen();
       int diffx= buttonSize.width - menuSize.width;
       if (location.x + diffx<0)
           diffx = - location.x;
       int diffy= buttonSize.height;
       if (location.y + diffy + menuSize.height > screenSize.height)
           diffy = screenSize.height - menuSize.height - location.y;
       m_popup.show(m_popupButton,diffx,diffy);
       m_popup.requestFocus();
       m_popupButton.setChar('^');
   }

   protected void closePopup() {
       if (m_popup != null && m_popup.isVisible()) {
           // #Workaround for JMenuPopup-Bug in JDK 1.4
           // intended behaviour: m_popup.setVisible(false);
           if ( getPopupComponent() != null)
           {
               javax.swing.SwingUtilities.invokeLater(() -> {
                   // Show JMenuItem and fire a mouse-click
                   cardLayout.last(m_popup);
                   menuItem.menuSelectionChanged(true);
                   menuItem.dispatchEvent(new MouseEvent(m_popup, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 0, 0, 1, false));
                   // show original popup again
                   cardLayout.first(m_popup);
                   m_popupButton.requestFocus();
               });
           }
           else
           {
               m_popup.setVisible( false);
           }
       }
       m_popupVisible = false;
   }

   private JMenuItem menuItem;
   private CardLayout cardLayout;

   class MyPopup extends JPopupMenu {
       private static final long serialVersionUID = 1L;

       MyPopup() {
           super();
       }
       public void menuSelectionChanged(boolean isIncluded) {
           closePopup();
       }
   }

   private void createPopup() {
       m_popup = new JPopupMenu();
       /*      try {
               PopupMenu.class.getMethod("isPopupTrigger",new Object[]{});
       } catch (Exception ex) {
           m_popup.setLightWeightPopupEnabled(true);
           }*/
       m_popup.setBorder(null);
       m_popup.setInvoker(this);
       final JComponent popupComponent = getPopupComponent();
       if ( popupComponent != null)
       {
           cardLayout = new CardLayout();
           m_popup.setLayout(cardLayout);
           m_popup.add(popupComponent, "0");
           menuItem = new JMenuItem("");
           m_popup.add(menuItem,"1");
       }


       final JMenuItem[] popupMenuItems = createMenuItems();
       for ( JMenuItem item: popupMenuItems)
       {
           m_popup.add( item);
       }
       m_popup.setBorderPainted(true);
       m_popup.addPopupMenuListener(m_listener);
   }

   /** the component that should apear in the popup menu */
   protected abstract JComponent getPopupComponent();

   protected JMenuItem[] createMenuItems()
   {
       return new JMenuItem[] {};
   }
}


