package org.rapla.plugin.rightsreport;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.MenuElement;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.gui.toolkit.RaplaMenuItem;

public class RightsReportMenu extends RaplaGUIComponent implements IdentifiableMenuEntry, ActionListener{

	private RaplaMenuItem report;
	final String name = getString("user") +"/"+ getString("groups") + " "+getString("report") ;

	public RightsReportMenu(RaplaContext context) {
		super(context);
		
		report = new RaplaMenuItem("report");
		report.getMenuElement().setText( name);
		final Icon icon = getIcon("icon.info_small");
		report.getMenuElement().setIcon( icon);
		report.addActionListener( this);
	}

	public String getId() {
		return "report";
	}

	@Override
	public MenuElement getMenuElement() {
		return report;
	}
	
	public void actionPerformed( ActionEvent e )
    {
        try {
           
        	RaplaRightsReport report = new RaplaRightsReport( getContext());
            DialogUI dialog = DialogUI.create( getContext(),getMainComponent(),true, report.getComponent(), new String[] {getString("ok")});
            dialog.setTitle( name);
            dialog.setSize( 650, 550);
            report.show();
            dialog.startNoPack();
        } catch (RaplaException ex) {
            showException( ex, getMainComponent());
        }
    }

}
