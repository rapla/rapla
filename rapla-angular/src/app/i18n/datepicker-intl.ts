import { Injectable } from '@angular/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';

import { t } from './i18n.service';

/** PRD 124 — the Material datepicker's built-in labels (mostly aria / tooltips) from the catalogue. */
@Injectable()
export class RaplaDatepickerIntl extends MatDatepickerIntl {
  override calendarLabel = t('calendar');
  override openCalendarLabel = t('datepicker_open_calendar');
  override closeCalendarLabel = t('datepicker_close_calendar');
  override prevMonthLabel = t('datepicker_prev_month');
  override nextMonthLabel = t('datepicker_next_month');
  override prevYearLabel = t('datepicker_prev_year');
  override nextYearLabel = t('datepicker_next_year');
  override prevMultiYearLabel = t('datepicker_prev_multi_year');
  override nextMultiYearLabel = t('datepicker_next_multi_year');
  override switchToMonthViewLabel = t('datepicker_choose_date');
  override switchToMultiYearViewLabel = t('datepicker_choose_month_year');
  override startDateLabel = t('start_date');
  override endDateLabel = t('end_date');
  override comparisonDateLabel = t('datepicker_comparison_range');

  override formatYearRangeLabel(start: string, end: string): string {
    return t('datepicker_year_range', start, end);
  }
}
