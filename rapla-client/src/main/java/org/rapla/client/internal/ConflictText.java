package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaLocale;

import java.time.LocalDateTime;

public class ConflictText {

  private ConflictText() {}

  public static String getConflictText(Conflict conflict, RaplaLocale raplaLocale, RaplaResources i18n) {
    StringBuilder sb = new StringBuilder();
    LocalDateTime startDate = conflict.getStartDate();
    sb.append(raplaLocale.formatDate(startDate));
    if (!startDate.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
      sb.append(' ');
      sb.append(raplaLocale.formatTime(startDate));
    }
    sb.append("\n");
    sb.append(conflict.getReservation1Name());
    if (conflict.getRepeatingType1() != null) {
      sb.append(getRepeatingType(conflict.getRepeatingType1(), i18n));
    }
    sb.append(' ');
    sb.append(i18n.getString("with"));
    sb.append('\n');
    sb.append(conflict.getReservation2Name());
    if (conflict.getRepeatingType2() != null) {
      sb.append(getRepeatingType(conflict.getRepeatingType2(), i18n));
    }
    return sb.toString();
  }

  private static String getRepeatingType(RepeatingType repeatingType, RaplaResources i18n) {
    final String keyName = repeatingType.name().toLowerCase();
    final String reslt = i18n.getString(keyName);
    return " [" + reslt + "]";
  }

}
