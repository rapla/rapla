package org.rapla.client.edit.reservation.sample.gwt.subviews;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.gwtbootstrap3.client.ui.Column;
import org.gwtbootstrap3.client.ui.Container;
import org.gwtbootstrap3.client.ui.Row;
import org.gwtbootstrap3.client.ui.constants.ColumnSize;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
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
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaLocale;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class InfoView
{
    private static final int MAX_COLUMNS_PER_ROW = 2;
    private static final String COLUMN_SIZE = ColumnSize.MD_6 + " " + ColumnSize.LG_6 + " " + ColumnSize.SM_6 + " " + ColumnSize.XS_6;

    private final FlowPanel contentPanel;
    private final Presenter presenter;
    private final RaplaLocale raplaLocale;
    private final User user;

    public InfoView(Presenter presenter, RaplaLocale raplaLocale, User user)
    {
        this.presenter = presenter;
        this.raplaLocale = raplaLocale;
        this.user = user;
        contentPanel = new FlowPanel();
        contentPanel.setStyleName("appointmentInfo");
    }

    private Presenter getPresenter()
    {
        return presenter;
    }

    public Widget provideContent()
    {
        return contentPanel;
    }

    public void createContent(final Reservation reservation)
    {
        contentPanel.clear();
        final Container container = new Container();
        contentPanel.add(container);
        container.setFluid(true);
        final Locale locale = raplaLocale.getLocale();
        int actualColumnsPerRow = 0;
        Row row = new Row();
        container.add(row);
        final Classification classification = reservation.getClassification();
        {
            final Collection<DynamicType> dynamicTypes = getPresenter().getChangeableReservationDynamicTypes();
            final Map<String, DynamicType> idToDynamicType = new HashMap<String, DynamicType>();
            final Collection<DropDownItem> values = new ArrayList<DropDownInputField.DropDownItem>(dynamicTypes.size());
            String selectedId = classification.getType().getId();
            for (final DynamicType dynamicType : dynamicTypes)
            {
                values.add(new DropDownItem(dynamicType.getName(locale), dynamicType.getId()));
                idToDynamicType.put(dynamicType.getId(), dynamicType);
            }
            final DropDownInputField input = new DropDownInputField("Veranstaltungsart", new DropDownValueChanged()
            {
                @Override
                public void valueChanged(String newValue)
                {
                    DynamicType newDynamicType = idToDynamicType.get(newValue);
                    getPresenter().changeClassification(reservation, newDynamicType);
                }
            }, values, selectedId);
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
            final Object value = classification.getValue(attribute);
            if (InputUtils.isAttributeInt(attribute))
            {
                final LongInputField input = new LongInputField(attributeName, (Long) value, new LongValueChange()
                {
                    @Override
                    public void valueChanged(Long newValue)
                    {
                        getPresenter().changeAttribute(reservation, attribute, newValue);
                    }
                });
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isAttributeString(attribute))
            {
                final TextInputField input = new TextInputField(attributeName, (String) value, new TextValueChanged()
                {
                    @Override
                    public void valueChanged(String newValue)
                    {
                        getPresenter().changeAttribute(reservation, attribute, newValue);
                    }
                });
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isAttributeDate(attribute))
            {
                final DateComponent input = new DateComponent((Date) value, raplaLocale, new DateValueChanged()
                {
                    @Override
                    public void valueChanged(Date newValue)
                    {
                        getPresenter().changeAttribute(reservation, attribute, newValue);
                    }
                });
                final Column column = new Column(COLUMN_SIZE);
                column.add(input);
                row.add(column);
                actualColumnsPerRow++;
            }
            else if (InputUtils.isAttributeBoolean(attribute))
            {
                final BooleanInputField input = new BooleanInputField(attributeName, (Boolean) value, new BooleanValueChange()
                {
                    @Override
                    public void valueChanged(Boolean newValue)
                    {
                        getPresenter().changeAttribute(reservation, attribute, newValue);
                    }
                });
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
                final Collection<DropDownItem> values = InputUtils.createDropDownItems(idToCategory, locale);
                final Collection<Object> categories = classification.getValues(attribute);
                final String[] selectedIds = new String[categories.size()];
                int i = 0;
                for (Object cat : categories)
                {
                    selectedIds[i] = ((Category) cat).getId();
                    i++;
                }
                final DropDownInputField input = new DropDownInputField(attributeName, new DropDownValueChanged()
                {
                    @Override
                    public void valueChanged(String newValue)
                    {
                        final Category newCategory = idToCategory.get(newValue);
                        getPresenter().changeAttribute(reservation, attribute, newCategory);
                    }
                }, values, multipleSelectionPossible, selectedIds);
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
}