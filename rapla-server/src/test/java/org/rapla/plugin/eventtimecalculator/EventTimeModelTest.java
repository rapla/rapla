package org.rapla.plugin.eventtimecalculator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.server.ServerBundleManager;

@RunWith(JUnit4.class)
public class EventTimeModelTest {

	EventTimeCalculatorResources i18n = new EventTimeCalculatorResources(new ServerBundleManager());
    EventTimeModel model = new EventTimeModel(i18n);
	{
		model.setDurationOfBreak(15);
		model.setTimeUnit( 45 );
		model.setTimeTillBreak( 90 );
	}
	
	@Test
	public void durationCalculation()
	{
//		Brutto: 90min => 2 x 45 = 2 TimeUnits (Pause 0)
		Assert.assertEquals(90, model.calcDuration( 90 ));
		Assert.assertEquals(90, model.calcDuration( 91 ));
		Assert.assertEquals(90, model.calcDuration( 104 ));
//		Brutto: 105min => 2 TimeUnits (Pause 15)
		Assert.assertEquals(90, model.calcDuration( 105 ));
		Assert.assertEquals(91, model.calcDuration( 106 ));
//		Brutto: 120min => 2 TimeUnits + 15 min (Pause 15min)
		Assert.assertEquals(105, model.calcDuration( 120 ));
//		Brutto: 150 min => 3 TimeUnits (Pause 15min)
		Assert.assertEquals(135, model.calcDuration( 150 ));
//		Brutto: 195 min => 4 TimeUnits (Pause 15min)
		Assert.assertEquals(180, model.calcDuration( 195 ));
//			Brutto: 210min (3h 30min) => 4 TimeUnits (Pause 30min)
		Assert.assertEquals(180, model.calcDuration( 210 ));
//			Brutto: 220min (3h 40min) => 4 TimeUnit + 10min (Pause 30min)
		Assert.assertEquals(190, model.calcDuration( 220 ));
	}
	
}
