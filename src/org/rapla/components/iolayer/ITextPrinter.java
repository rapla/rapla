package org.rapla.components.iolayer;

import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

public class ITextPrinter {

	static public void createPdf( Printable printable, java.io.OutputStream  fileOutputStream, PageFormat format) {
		float width = (float)format.getWidth();
		float height = (float)format.getHeight();
		Rectangle pageSize = new Rectangle(width, height);
		Document document = new Document(pageSize);
		
	   try {
	      PdfWriter writer = PdfWriter.getInstance(document, fileOutputStream);
	      document.open();
	      PdfContentByte cb = writer.getDirectContent();
    	   
	      for  (int page = 0;page<5000;page++)
	      {
	    	  Graphics2D g2;
	    	  PdfTemplate tp = cb.createTemplate(width, height);
	    	  g2 = tp.createGraphics(width, height);		
		      int status = printable.print(g2, format, page);
		      g2.dispose();
		      if ( status == Printable.NO_SUCH_PAGE)
		      {
		    	  break;
		      }
		      if ( status != Printable.PAGE_EXISTS)
		      {
		    	  break;
		      }
		      cb.addTemplate(tp, 0, 0);
		        document.newPage();  
		     
	      }
	   } catch (Exception e) {
	      System.err.println(e.getMessage());
	   }
	   document.close();
	}
	

}
