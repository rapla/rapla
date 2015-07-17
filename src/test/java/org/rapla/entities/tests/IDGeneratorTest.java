package org.rapla.entities.tests;

import java.util.UUID;

import org.rapla.RaplaTestCase;

public class IDGeneratorTest extends RaplaTestCase {

	public IDGeneratorTest(String name) {
		super(name);
	}
	
	public void test()
	{
		UUID randomUUID = UUID.randomUUID();
		String string = randomUUID.toString();
		System.out.println( "[" + string.length()  +"] "+ string );
		
	}
	

}
