package org.rapla.plugin.abstractcalendar;


import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;

public interface MultiCalendarPrint {

	static String getIncrementName(DateTools.IncrementSize incrementSize, RaplaResources i18n) {
		switch (incrementSize)
		{
			case DAY_OF_YEAR:
				return i18n.getString("days");
			case WEEK_OF_YEAR:
				return i18n.getString("weeks");
			case MONTH:
				return i18n.getString("months");
			default:
				return "";
		}
	}

	String getCalendarUnit();
	void setUnits(int units);
	int getUnits();

}
