import { RaplaDatepickerIntl } from './datepicker-intl';

describe('RaplaDatepickerIntl', () => {
  it('takes the Material datepicker texts from the catalogue (German in specs)', () => {
    const intl = new RaplaDatepickerIntl();
    expect(intl.calendarLabel).toBe('Kalender');
    expect(intl.prevMonthLabel).toBe('Vorheriger Monat');
    expect(intl.startDateLabel).toBe('Beginn');
    expect(intl.formatYearRangeLabel('2024', '2047')).toBe('2024 bis 2047');
  });
});
