package org.rapla.plugin.eventtimecalculator;

import junit.framework.TestCase;

public class EventTimeModelTest extends TestCase {

	EventTimeModel model = new EventTimeModel();
	{
		model.setDurationOfBreak(15);
		model.setTimeUnit( 45 );
		model.setTimeTillBreak( 90 );
	}
	
	public void testModel()
	{
//		Brutto: 90min => 2 x 45 = 2 TimeUnits (Pause 0)
		assertEquals(90, model.calcDuration( 90 ));
		assertEquals(90, model.calcDuration( 91 ));
		assertEquals(90, model.calcDuration( 104 ));
//		Brutto: 105min => 2 TimeUnits (Pause 15)
		assertEquals(90, model.calcDuration( 105 ));
		assertEquals(91, model.calcDuration( 106 ));
//		Brutto: 120min => 2 TimeUnits + 15 min (Pause 15min)
		assertEquals(105, model.calcDuration( 120 ));
//		Brutto: 150 min => 3 TimeUnits (Pause 15min)
		assertEquals(135, model.calcDuration( 150 ));
//		Brutto: 195 min => 4 TimeUnits (Pause 15min)
		assertEquals(180, model.calcDuration( 195 ));
//			Brutto: 210min (3h 30min) => 4 TimeUnits (Pause 30min)
		assertEquals(180, model.calcDuration( 210 ));
//			Brutto: 220min (3h 40min) => 4 TimeUnit + 10min (Pause 30min)
		assertEquals(190, model.calcDuration( 220 ));
	}
	
}
