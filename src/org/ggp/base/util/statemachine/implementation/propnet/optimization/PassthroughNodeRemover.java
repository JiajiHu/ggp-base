package org.ggp.base.util.statemachine.implementation.propnet.optimization;

import java.util.HashSet;
import java.util.Set;

import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;

public class PassthroughNodeRemover extends Optimization {

	public PassthroughNodeRemover(PropNet propnet) {
		super(propnet);
	}

	public boolean isPassthrough (Component c) {
		return !isSpecial(c) && (c instanceof Proposition || c instanceof And || c instanceof Or) && c.getInputs().size() == 1;
	}

	// Removes propositions that have only a single input (and which therefore can be eliminated;
	// just have their input pass straight to the output
	@Override
	public void runPass() {
		Set<Component> components = new HashSet<Component>(propnet.getComponents());

		for (Component c : components) {
			if (isPassthrough(c)) {
				propnet.removeComponent(c);
				Component input = c.getSingleInput();
				for (Component output : c.getOutputs()) {
					output.addInput(input);
					input.addOutput(output);
				}
			}
		}
	}
}
