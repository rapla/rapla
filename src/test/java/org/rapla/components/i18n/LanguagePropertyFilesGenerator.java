package org.rapla.components.i18n;

import com.ibm.icu.impl.CalendarData;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DateFormatSymbols;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ULocale;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;

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
                writeIntoFile(uLocale, pw);
                pw.close();
            }
            {// create default fallback:
                final PrintWriter pw = new PrintWriter(new File(parentDir, "format.properties"), "UTF-8");
                writeIntoFile(ULocale.ENGLISH, pw);
                pw.close();
            }
            
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private static void writeIntoFile(ULocale uLocale, final PrintWriter pw)
    {
        final DateFormatSymbols dateFormatSymbols = new DateFormatSymbols(uLocale);
        pw.println("amPm=" + Arrays.toString(dateFormatSymbols.getAmPmStrings()));
        pw.println("isAmPm=" + isAmPm(uLocale, dateFormatSymbols));
        pw.println("shortMonths=" + Arrays.toString(dateFormatSymbols.getShortMonths()));
        pw.println("months=" + Arrays.toString(dateFormatSymbols.getMonths()));
        pw.println("shortWeekdays=" + Arrays.toString(dateFormatSymbols.getShortWeekdays()));
        pw.println("weekdays=" + Arrays.toString(dateFormatSymbols.getWeekdays()));
        Calendar cal = Calendar.getInstance(uLocale);
        CalendarData calData = new CalendarData(uLocale, cal.getType());
        pw.println("formatDateLong=" + calData.getDateTimePatterns()[6]);
        pw.println("formatDateShort=" + calData.getDateTimePatterns()[7]);
        pw.println("formatHour=" + calData.getDateTimePatterns()[3]);
        pw.println("formatMonthYear=" + calData.getDateTimePatterns()[5]);
        pw.println("formatTime=" + calData.getDateTimePatterns()[2]);
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
