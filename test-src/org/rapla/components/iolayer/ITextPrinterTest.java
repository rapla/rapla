package org.rapla.components.iolayer;

import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTable.PrintMode;
import javax.swing.table.DefaultTableModel;

import org.rapla.RaplaTestCase;

public class ITextPrinterTest extends RaplaTestCase {

    public ITextPrinterTest(String name) {
        super(name);
    }

    public void test() throws Exception
    {
    // Table Tester
        JTable table = new JTable();
        int size = 50;
        DefaultTableModel model = new DefaultTableModel(size+1,1);
        table.setModel( model );
        for ( int i = 0;i<size;i++)
        {
            table.getModel().setValueAt("Test " + i, i, 0);
        }
        JFrame test = new JFrame();
        test.add( new JScrollPane(table));
        test.setSize(400, 300);
        test.setVisible(true);
        Printable printable = table.getPrintable(PrintMode.NORMAL, null, null);
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(TEST_FOLDER_NAME +"/my_jtable_fonts.pdf");
            PageFormat format = new PageFormat();
            ITextPrinter.createPdf( printable, fileOutputStream, format);
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        test.setVisible( false);
    }
}
