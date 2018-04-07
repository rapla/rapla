package org.rapla.client.edit.reservation.sample.gwt.subviews;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import org.gwtbootstrap3.client.ui.Column;
import org.gwtbootstrap3.client.ui.Container;
import org.gwtbootstrap3.client.ui.Row;
import org.gwtbootstrap3.client.ui.constants.ColumnSize;
import org.rapla.RaplaResources;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.edit.reservation.sample.gwt.ReservationViewImpl.ReservationViewPart;
import org.rapla.client.gwt.components.BooleanInputField;
import org.rapla.client.gwt.components.BooleanInputField.BooleanValueChange;
import org.rapla.client.gwt.components.DateComponent;
import org.rapla.client.gwt.components.DateComponent.DateValueChanged;
import org.rapla.client.gwt.components.DropDownInputField;
import org.rapla.client.gwt.components.DropDownInputField.DropDownItem;
import org.rapla.client.gwt.components.DropDownInputField.DropDownValueChanged;
import org.rapla.client.gwt.components.InputUtils;
import org.rapla.client.gwt.components.LongInputField;
import org.rapla.client.gwt.components.LongInputField.LongValueChange;
import org.rapla.client.gwt.components.TextInputField;
import org.rapla.client.gwt.components.TextInputField.TextValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class InfoView implements ReservationViewPart
{
    private static final int MAX_COLUMNS_PER_ROW = 2;
    private static final String COLUMN_SIZE = ColumnSize.MD_6 + " " + ColumnSize.LG_6 + " " + ColumnSize.SM_12 + " " + ColumnSize.XS_12;

    private final FlowPanel contentPanel;
    private final RaplaResources i18n;
    private final BundleManager bundleManager;
    private final Presenter presenter;

    public InfoView(RaplaResources i18n, BundleManager bundleManager, Presenter presenter)
    {
        this.i18n = i18n;
        this.bundleManager = bundleManager;
        this.presenter = presenter;
        contentPanel = new FlowPanel();
        contentPanel.setStyleName("appointmentInfo");
    }

    private Presenter getPresenter()
    {
        return presenter;
    }

    @Override
    public IsWidget provideContent()
    {
        return contentPanel;
    }

    @Override
    public void createContent(final Reservation reservation)
    {
        contentPanel.clear();
        final Container container = new Container();
        contentPanel.add(container);
        final Locale locale = i18n.getLocale();
        int actualColumnsPerRow = 0;
        Row row = new Row();
        container.add(row);
        final Classification classification = reservation.getClassification();
        {
            final Collection<DynamicType> dynamicTypes = getPresenter().getChangeableReservationDynamicTypes();
            final Map<String, DynamicType> idToDynamicType = new HashMap<>();
            final Collection<DropDownItem> values = new ArrayList<>(dynamicTypes.size());
            for (final DynamicType dynamicType : dynamicTypes)
            {
                boolean selected = dynamicType.equals(classification.getType());
                values.add(new DropDownItem(dynamicType.getName(locale), dynamicType.getId(), selected));
                idToDynamicType.put(dynamicType.getId(), dynamicType);
            }
            final DropDownInputField input = new DropDownInputField(i18n.getString("reservation_type"), newValue -> {
                DynamicType newDynamicType = idToDynamicType.get(newValue);
                getPresenter().changeClassification(newDynamicType);
            }, values);
            final Column column = new Column(COLUMN_SIZE);
            column.add(input);
            row.add(column);
            actualColumnsPerRow++;
        }
        final Attribute[] attributes = classification.getAttributes();
        for (final Attribute attribute : attributes)
        {
            if (actualColumnsPerRow % MAX_COLUMNS_PER_ROW == 0)
            {
                actualColumnsPerRow = 0;
                row = new Row();
                container.add(row);
            }
            final String attributeName = attribute.getName(locale);
            final Object value = classification.getValueForAttribute(attribute);
            if (InputUtils.isAttributeInt(attribute))
            {
                final LongInputField input = new LongInputField(attributeName, (Long) value, newValue -> getPresenter().changeAttribute(attribute, newValue));
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isAttributeString(attribute))
            {
                final TextInputField input = new TextInputField(attributeName, (String) value, newValue -> getPresenter().changeAttribute(attribute, newValue));
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isAttributeDate(attribute))
            {
                final DateComponent input = new DateComponent((Date) value, newValue -> getPresenter().changeAttribute(attribute, newValue), bundleManager);
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isAttributeBoolean(attribute))
            {
                final BooleanInputField input = new BooleanInputField(attributeName, (Boolean) value, newValue -> getPresenter().changeAttribute(attribute, newValue));
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isCategory(attribute))
            {
                Category rootCategory = (Category) attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                Boolean multipleSelectionPossible = (Boolean) attribute.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
                final Map<String, Category> idToCategory = InputUtils.createIdMap(rootCategory);
                final Collection<Object> categories = classification.getValues(attribute);
                final Collection<DropDownItem> values = InputUtils.createDropDownItems(idToCategory, locale, categories);
                final DropDownInputField input = new DropDownInputField(attributeName, newValue -> {
                    final Category newCategory = idToCategory.get(newValue);
                    getPresenter().changeAttribute(attribute, newCategory);
                }, values, multipleSelectionPossible);
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isAllocatable(attribute))
            {

            }
        }
    }

    public void clearContent()
    {
        contentPanel.clear();
    }

    public void update(Reservation reservation)
    {
        createContent(reservation);
    }

    @Override
    public void updateAppointments(Appointment[] allAppointments, Appointment selectedAppointment)
    {
        // nothing to do as no appointment specific informations are shown here
    }
}