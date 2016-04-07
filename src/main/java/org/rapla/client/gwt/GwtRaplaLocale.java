package org.rapla.client.gwt;

import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.client.GwtBundleManager;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.NumberFormat;

@DefaultImplementation(of = RaplaLocale.class, context = InjectionContext.gwt)
public class GwtRaplaLocale extends AbstractRaplaLocale {

	//static String CET_STRING = "{\"transitions\": [89953, 60, 94153, 0, 98521, 60, 102889, 0, 107257, 60, 111625, 0, 115993, 60, 120361, 0, 124729, 60, 129265, 0, 133633, 60, 138001, 0, 142369, 60, 146737, 0, 151105, 60, 155473, 0, 159841, 60, 164209, 0, 168577, 60, 172945, 0, 177313, 60, 181849, 0, 186217, 60, 190585, 0, 194953, 60, 199321, 0, 203689, 60, 208057, 0, 212425, 60, 216793, 0, 221161, 60, 225529, 0, 230065, 60, 235105, 0, 238801, 60, 243841, 0, 247537, 60, 252577, 0, 256273, 60, 261481, 0, 265009, 60, 270217, 0, 273745, 60, 278953, 0, 282649, 60, 287689, 0, 291385, 60, 296425, 0, 300121, 60, 305329, 0, 308857, 60, 314065, 0, 317593, 60, 322801, 0, 326329, 60, 331537, 0, 335233, 60, 340273, 0, 343969, 60, 349009, 0, 352705, 60, 357913, 0, 361441, 60, 366649, 0, 370177, 60, 375385, 0, 379081, 60, 384121, 0, 387817, 60, 392857, 0, 396553, 60, 401593, 0, 405289, 60, 410497, 0, 414025, 60, 419233, 0, 422761, 60, 427969, 0, 431665, 60, 436705, 0, 440401, 60, 445441, 0, 449137, 60, 454345, 0, 457873, 60, 463081, 0, 466609, 60, 471817, 0, 475513, 60, 480553, 0, 484249, 60, 489289, 0, 492985, 60, 498025, 0, 501721, 60, 506929, 0, 510457, 60, 515665, 0, 519193, 60, 524401, 0, 528097, 60, 533137, 0, 536833, 60, 541873, 0, 545569, 60, 550777, 0, 554305, 60, 559513, 0, 563041, 60, 568249, 0, 571777, 60, 576985, 0, 580681, 60, 585721, 0, 589417, 60, 594457, 0], \"names\": [\"CET\", \"Central European Time\", \"CEST\", \"Central European Summer Time\"], \"id\": \"Europe/Berlin\", \"std_offset\": 60}";
	static String GMT_STRING = "{\"transitions\": [], \"names\": [\"GMT\", \"Greenwich Mean Time\"], \"id\": \"Africa/Dakar\", \"std_offset\": 0}";
	com.google.gwt.i18n.shared.TimeZone timezoneG = com.google.gwt.i18n.client.TimeZone.createTimeZone(GMT_STRING);

	@Inject
	public GwtRaplaLocale(BundleManager bundleManager) {
		super(bundleManager);
	}

	public String formatTime(Date date) {
		DateTimeFormat format = DateTimeFormat.getFormat(DateTimeFormat.PredefinedFormat.TIME_SHORT);
	    return format.format(date, timezoneG);
	}

	public String formatNumber(long number) {
		String result = NumberFormat.getDecimalFormat().format( number);
		return result;
	}

	public String getWeekday(Date date) {
		DateTimeFormat format = DateTimeFormat.getFormat("EE");
	    return format.format( date, timezoneG );
	}

	public String getCharsetNonUtf() 
	{
		throw new UnsupportedOperationException("Not supported in gwt. Please call on server");
	}

    @Override
    public Date fromUTCTimestamp(Date timestamp) {
       throw new UnsupportedOperationException();
    }

    @Override
    protected String _format(Date date, String pattern)
    {
        final DateTimeFormat formatter = DateTimeFormat.getFormat(pattern);
        final String format = formatter.format(date, timezoneG);
        return format;
    }
    
    @Override
    public Locale newLocale(String language, String country)
    {
        return ((GwtBundleManager)bundleManager).newLocale(language, country);
    }
    
    

}
