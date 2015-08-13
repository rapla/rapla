package org.rapla.components.i18n;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;

import com.ibm.icu.impl.CalendarData;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DateFormatSymbols;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ULocale;

public class LanguagePropertyFilesGenerator
{
    public static void main(String[] args)
    {
        try
        {
            final ULocale[] availableLocales = ULocale.getAvailableLocales();
            int i = 0;
            final File parentDir = new File("src" + File.separator + "main" + File.separator + "resources" + File.separator + "org" + File.separator + "rapla"
                    + File.separator + "components" + File.separator + "i18n" + File.separator + "server" + File.separator + "locales");
            if (parentDir.exists())
            {
                System.out.println("deleting properties dir");
                deleteDir(parentDir);
            }
            parentDir.mkdirs();
            System.out.println("generating language properties into " + parentDir.getAbsolutePath());
            for (ULocale uLocale : availableLocales)
            {
                i++;
                final PrintWriter pw = new PrintWriter(new File(parentDir, "format_"+uLocale.toString() + ".properties"), "UTF-8");
                final DateFormatSymbols dateFormatSymbols = new DateFormatSymbols(uLocale);
                pw.println("amPm=" + Arrays.toString(dateFormatSymbols.getAmPmStrings()));
                pw.println("isAmPm=" + isAmPm(uLocale, dateFormatSymbols));
                pw.println("shortMonths=" + Arrays.toString(dateFormatSymbols.getShortMonths()));
                pw.println("months=" + Arrays.toString(dateFormatSymbols.getMonths()));
                pw.println("shortWeekdays=" + Arrays.toString(dateFormatSymbols.getShortWeekdays()));
                pw.println("weekdays=" + Arrays.toString(dateFormatSymbols.getWeekdays()));
                Calendar cal = Calendar.getInstance(uLocale);
                CalendarData calData = new CalendarData(uLocale, cal.getType());
                pw.println("formatDateShort=" + calData.getDateTimePatterns()[6]);
                pw.println("formatDateLong=" + calData.getDateTimePatterns()[7]);
                pw.println("formatHour=" + calData.getDateTimePatterns()[3]);
                pw.println("formatMonthYear=" + calData.getDateTimePatterns()[5]);
                pw.println("formatTime=" + calData.getDateTimePatterns()[2]);
                pw.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private static boolean isAmPm(ULocale locale, DateFormatSymbols dateFormatSymbols)
    {
        final DateFormat dateTimeInstance = DateFormat.getTimeInstance(DateFormat.LONG, locale);
        final String format = dateTimeInstance.format(new Date());
        final String[] amPmStrings = dateFormatSymbols.getAmPmStrings();
        for (String string : amPmStrings)
        {
            if (format.contains(string))
            {
                return true;
            }
        }
        return false;
    }

    private static void deleteDir(File parentDir)
    {
        final File[] listFiles = parentDir.listFiles();
        for (File file : listFiles)
        {
            if (file.isDirectory())
            {
                deleteDir(file);
            }
            file.delete();
        }
        parentDir.delete();
    }

}
