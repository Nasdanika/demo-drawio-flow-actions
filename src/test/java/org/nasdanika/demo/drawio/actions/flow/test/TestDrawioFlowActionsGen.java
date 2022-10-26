package org.nasdanika.demo.drawio.actions.flow.test;

import org.junit.jupiter.api.Test;
import org.nasdanika.demo.drawio.actions.flow.DrawioFlowActionsGenerator;

public class TestDrawioFlowActionsGen {
	
	@Test
	public void generate() throws Exception {
		new DrawioFlowActionsGenerator().generate();
	}
	
}
