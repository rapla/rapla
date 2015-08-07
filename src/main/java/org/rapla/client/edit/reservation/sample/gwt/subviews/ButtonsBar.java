package org.rapla.client.edit.reservation.sample.gwt.subviews;

import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.edit.reservation.sample.gwt.gfx.ImageImport;
import org.rapla.entities.domain.Reservation;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;

public class ButtonsBar extends FlowPanel
{
    private Presenter presenter;
    private Reservation reservation;

    public ButtonsBar()
    {
        final Image cancel = new Image(ImageImport.INSTANCE.cancelIcon());
        cancel.setStyleName("cancelButton");
        cancel.setTitle("Abbrechen");
        cancel.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent e)
            {
                presenter.onCancelButtonClicked(reservation);
            }
        });

        final Image save = new Image(ImageImport.INSTANCE.saveIcon());
        //save = new Button("speichern");
        save.setStyleName("saveButton");
        save.setTitle("Veranstaltung speichern");
        save.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent e)
            {
                presenter.onSaveButtonClicked(reservation);
            }
        });

        final Image delete = new Image(ImageImport.INSTANCE.deleteIcon());
        //delete = new Button("l\u00F6schen");
        delete.setStyleName("deleteButton");
        delete.setTitle("Veranstaltung l\u00F6schen");
        delete.addClickHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent e)
            {
                presenter.onDeleteButtonClicked(reservation);
            }
        });

        final Image undo = new Image(ImageImport.INSTANCE.undoIcon());
        undo.setStyleName("undoButton");
        undo.setTitle("R\u00FCckg\u00E4ngig");

        final Image redo = new Image(ImageImport.INSTANCE.redoIcon());
        redo.setStyleName("redoButton");
        redo.setTitle("Wiederholen");

        add(save);
        add(cancel);
        add(delete);
        add(undo);
        add(redo);
        //      buttonsPanel.add(plus);
        setStyleName("mainButtonsBar");
    }

    public void setPresenter(Presenter presenter)
    {
        this.presenter = presenter;
    }

    public void setReservation(Reservation reservation)
    {
        this.reservation = reservation;
    }
}
