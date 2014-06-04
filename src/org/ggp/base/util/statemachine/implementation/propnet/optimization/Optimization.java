package org.ggp.base.util.statemachine.implementation.propnet.optimization;

import java.util.HashSet;
import java.util.Set;

import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;

public abstract class Optimization {

	// CurrentlyTrue and currentlyFalse can be null if we don't know anything
	public static void runPasses(PropNet propNet, Set<Component> currentlyTrue, Set<Component> currentlyFalse) {
		/*if (currentlyTrue != null && currentlyFalse != null) {
			new LatchSquasher(propNet, currentlyTrue, currentlyFalse).runPass();
		}
		new DeadNodeEliminator(propNet).runPass();
		new NotSquasher(propNet).runPass();
		new DeadNodeEliminator(propNet).runPass();
		new PassthroughNodeRemover(propNet).runPass();
		new DeadNodeEliminator(propNet).runPass();
		new EquivalenceMerger(propNet).runPass();
		new DeadNodeEliminator(propNet).runPass();
		new PassthroughNodeRemover(propNet).runPass();
		new DeadNodeEliminator(propNet).runPass();*/
	}

	protected PropNet propnet;
	// Special components are the ones we can't optimize away.
	private Set<Component> specialComponents;

	protected boolean isSpecial(Component c) {
		return specialComponents.contains(c);
	}

	public Optimization(PropNet propnet) {
		this.propnet = propnet;
		this.specialComponents = computeSpecialComponents();
	}

	abstract public void runPass();

	Set<Component> computeSpecialComponents() {
		Set<Component> components = new HashSet<Component>();

		for (Proposition p : propnet.getInputPropositions().values()) {
			components.add(p);
		}
		for (Proposition p : propnet.getBasePropositions().values()) {
			components.add(p);
		}
		for (Set<Proposition> s : propnet.getLegalPropositions().values()) {
			for (Proposition p : s) {
				components.add(p);
			}
		}

		for (Set<Proposition> s : propnet.getGoalPropositions().values()) {
			for (Proposition p : s) {
				components.add(p);
			}
		}

		components.add(propnet.getTerminalProposition());

		return components;
	}
}
