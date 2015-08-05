package org.rapla.client.edit.reservation.sample.gwt.gfx;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;

public interface ImageImport extends ClientBundle {
	ImageImport INSTANCE = GWT.create(ImageImport.class);


	@Source("iconSave.png")
	ImageResource saveIcon();	
	
	@Source("iconCancel.png")
	ImageResource cancelIcon();	
	
	@Source("iconDelete.png")
	ImageResource deleteIcon();		
	
	@Source("cross.png")
	ImageResource crossIcon();		
	
	@Source("next.png")
	ImageResource nextIcon();
	
	@Source("plus.png")
	ImageResource plusIcon();	
	
	@Source("cross_grey.png")
	ImageResource crossGreyIcon();		
	
	@Source("next_grey.png")
	ImageResource nextGreyIcon();
	
	@Source("plus_grey.png")
	ImageResource plusGreyIcon();	
	
	@Source("change.png")
	ImageResource changeIcon();	
	
	@Source("redo.png")
	ImageResource redoIcon();	
	
	@Source("undo.png")
	ImageResource undoIcon();		
	
	@Source("loupe.png")
	ImageResource loupeIcon();		
	
	@Source("filter.png")
	ImageResource filterIcon();		
	
	@Source("menu.png")
	ImageResource menuIcon();
}
