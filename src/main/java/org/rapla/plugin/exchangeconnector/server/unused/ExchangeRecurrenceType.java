/**
 * 
 */
package org.rapla.plugin.exchangeconnector.server.unused;

import microsoft.exchange.webservices.data.property.complex.recurrence.pattern.Recurrence;
import microsoft.exchange.webservices.data.property.complex.recurrence.pattern.Recurrence.DailyPattern;
import microsoft.exchange.webservices.data.property.complex.recurrence.pattern.Recurrence.MonthlyPattern;
import microsoft.exchange.webservices.data.property.complex.recurrence.pattern.Recurrence.WeeklyPattern;
import microsoft.exchange.webservices.data.property.complex.recurrence.pattern.Recurrence.YearlyPattern;

/**
 * This construction transfers the MS Exchange class Recurrence, along with its child-classes into 
 * a switchable enumeration.
 *  
 * @author lutz
 *
 */
public enum ExchangeRecurrenceType {
	DAILY(new DailyPattern()),
	WEEKLY(new WeeklyPattern()),
	MONTHLY(new MonthlyPattern()),
	YEARLY(new YearlyPattern());
	
	private Recurrence recurrenceInstance;
	
	/**
	 * The constructor
	 * 
	 * @param recurrenceInstance : {@link Recurrence}
	 */
	ExchangeRecurrenceType(Recurrence recurrenceInstance){
		setRecurrenceInstance(recurrenceInstance);
	}
	

	/**
	 * Convert the type to a message {@link String}
	 * 
	 * @see java.lang.Enum#toString()
	 */
	public String toString(){
		return "Recurrence: "+getRecurrenceInstance().toString();
	}
	
	
	/**
	 * Static method to receive an {@link ExchangeRecurrenceType} object from a particular {@link Recurrence}
	 * 
	 * @param type : {@link Recurrence}
	 * @return {@link ExchangeRecurrenceType}
	 */
	public static ExchangeRecurrenceType getExchangeRecurrenceTypeENUM(Recurrence type) {
		ExchangeRecurrenceType returnType = null;
		// iterate over all existing recurrence-types
		for (ExchangeRecurrenceType recurrenceObj : ExchangeRecurrenceType.values()) {
			// given RepeatingType equals an existing RepeatingType
			if (recurrenceObj.getRecurrenceInstance().getClass().equals(type.getClass()))
				// return the found RepeatingTypeEnum
				returnType = recurrenceObj;
		}		
		return returnType;
	}

	/**
	 * Get the classic {@link Recurrence} of the current enum-object
	 * 
	 * @return the recurrenceInstance : {@link Recurrence}
	 */
	public Recurrence getRecurrenceInstance() {
		return recurrenceInstance;
	}

	/**
	 * Set the {@link Recurrence}
	 * 
	 * @param repeatingType : {@link Recurrence} 
	 */
	private void setRecurrenceInstance(Recurrence recurrenceInstance) {
		this.recurrenceInstance = recurrenceInstance;
	}
	

}
