package org.rapla.entities.tests;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.UUID;

@RunWith(JUnit4.class)
public class IDGeneratorTest  {

	@Test
	public void test()
	{
		UUID randomUUID = UUID.randomUUID();
		String string = randomUUID.toString();
		System.out.println( "[" + string.length()  +"] "+ string );
		
	}
	

}
