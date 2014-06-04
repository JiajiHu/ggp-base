package org.ggp.base.util.statemachine.implementation.propnet.optimization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;

public class DeadNodeEliminator extends Optimization {

	public DeadNodeEliminator(PropNet propnet) {
		super(propnet);
	}

	@Override
	public void runPass() {
		if (true) {
			return;
		}
		Set<Component> nodesToKeep = new HashSet<Component>();
		List<Component> fringe = new ArrayList<Component>();

		for (Set<Proposition> legals : propnet.getLegalPropositions().values()) {
			for (Proposition p : legals) {
				fringe.add(p);
				nodesToKeep.add(p);
			}
		}
		for (Set<Proposition> goals : propnet.getGoalPropositions().values()) {
			for (Proposition p : goals) {
				fringe.add(p);
				nodesToKeep.add(p);
			}
		}
		fringe.add(propnet.getTerminalProposition());
		nodesToKeep.add(propnet.getTerminalProposition());

		while (!fringe.isEmpty()) {
			Component c = fringe.remove(fringe.size()-1);
			for (Component input : c.getInputs()) {
				if (!nodesToKeep.contains(input)) {
					fringe.add(input);
					nodesToKeep.add(input);
				}
			}
		}

		Set<Component> allComponents = new HashSet<Component>(propnet.getComponents());
		for (Component c : allComponents) {
			if (!nodesToKeep.contains(c)) {
				propnet.removeComponent(c);
			}
		}
	}

}