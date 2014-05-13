package org.ggp.base.util.statemachine.implementation.propnet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.factory.PropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

@SuppressWarnings("unused")
public class MyPropNetStateMachine extends StateMachine {

	private int currentState = -1;


	/** The underlying proposition network  */
	private PropNet propNet;
	/** An index from GdlSentence to Base Propositions.  The truth value of base propositions determines the state */
	private Map<GdlSentence, Proposition> basePropositions;
	/** An index from GdlSentence to Input Propositions.  Input propositions correspond to moves a player can take */
	private Map<GdlSentence, Proposition> inputPropositions;
	/** The terminal proposition.  If the terminal proposition's value is true, the game is over */
	private Proposition terminal;
	/** Maps roles to their legal propositions */
	private Map<Role, Set<Proposition>> legalPropositions;
	/** Maps roles to their goal propositions */
	private Map<Role, Set<Proposition>> goalPropositions;
	/** The topological ordering of the propositions */
	private List<Proposition> ordering;
	/** Set to true and everything else false, then propagate the truth values to compute the initial state*/
	private Proposition init;
	/** The roles of different players in the game */
	private List<Role> roles;
	/** A map between legal and input propositions.  The map contains mappings in both directions*/
	private Map<Proposition, Proposition> legalInputMap;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
    	System.out.println("initializing!");
		propNet = PropNetFactory.create(description);
		roles = propNet.getRoles();
		basePropositions = propNet.getBasePropositions();
		inputPropositions = propNet.getInputPropositions();
		terminal = propNet.getTerminalProposition();
		legalPropositions = propNet.getLegalPropositions();
		init = propNet.getInitProposition();
		goalPropositions = propNet.getGoalPropositions();
		legalInputMap = propNet.getLegalInputMap();
		ordering = getOrdering();
		//testOrdering();
		//printOrdering();
	}

	protected void clearBasePropositions(){
		for(GdlSentence key : basePropositions.keySet()){
			basePropositions.get(key).setValue(false);
		}
	}

	protected void clearInputPropositions(){
		for(GdlSentence key : inputPropositions.keySet()){
			inputPropositions.get(key).setValue(false);
		}
	}

	protected void propagatePropositionValues()
	{
		// compute truth values for propositions
		for(Proposition p : ordering){
			boolean val = p.getSingleInput().getValue();
			p.setValue(val);
		}
	}

	protected void initializeBasePropositions(MachineState state){

		clearBasePropositions();

		Set<GdlSentence> g = state.getContents();
		for(GdlSentence s : g){
			Proposition p = basePropositions.get(s);
			p.setValue(true);
		}
	}


	protected void initializeInputPropositions(List<Move> moves)
	{
		clearInputPropositions();
		List<GdlSentence> doesMoves = toDoes(moves);
		// set props to true based on moves:
		for(GdlSentence g : doesMoves){
			Proposition p = inputPropositions.get(g);
			p.setValue(true);
		}
	}

	/**
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		initializeBasePropositions(state);
		propagatePropositionValues();
		return terminal.getValue();
	}

	/**
	 * Computes the goal for a role in the current state.
	 * Should return the value of the goal proposition that
	 * is true for that role. If there is not exactly one goal
	 * proposition true for that role, then you should throw a
	 * GoalDefinitionException because the goal is ill-defined.
	 */
	@Override
	public int getGoal(MachineState state, Role role)
	throws GoalDefinitionException {

		initializeBasePropositions(state);
		propagatePropositionValues();
		Set<Proposition> p = goalPropositions.get(role);
		boolean set = false;
		Proposition goalProp = null;
		for(Proposition curr : p ){
			if(curr.getValue()){
				if(!set){
					goalProp = curr;
					set = true;
				}else{
					throw new GoalDefinitionException(state,role);
				}
			}
		}

		return new Integer(getGoalValue(goalProp));
	}

	/**
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 */
	@Override
	public MachineState getInitialState() {


		clearBasePropositions();
		init.setValue(true);
		propagatePropositionValues();
		return getStateFromBase();
	}


	protected Set<GdlSentence> getNextStepTrueBaseProps()
	{
		Set<GdlSentence> sentences = new HashSet<GdlSentence>();
		// get new values of base propositions:
		for(GdlSentence p: basePropositions.keySet()){
			Proposition prop = basePropositions.get(p);
			// all inputs to base propositions == transitions
			if(prop.getSingleInput().getValue()){
				sentences.add(prop.getName());
			}
		}

		return sentences;

	}
	/**
	 * Computes the legal moves for role in state.
	 */
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role)
	throws MoveDefinitionException {

		System.err.println(state);
		List<Move> ret = new ArrayList<Move>();
		initializeBasePropositions(state);
 		propagatePropositionValues();

		Set<Proposition> legalMoves = legalPropositions.get(role);
		for(Proposition p : legalMoves){
			if(p.getValue()){
				Move m = getMoveFromProposition(p);
				ret.add(m);
			}

		}

		//System.out.println(ret);
		return ret;
	}

	/**
	 * Computes the next state given state and the list of moves.
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves)
	throws TransitionDefinitionException {
		initializeBasePropositions(state);
		initializeInputPropositions(moves);
		propagatePropositionValues();
		Set<GdlSentence> nextStepTrueBaseProps = getNextStepTrueBaseProps();
		return  getMachineStateFromSentenceList(nextStepTrueBaseProps);
	}

	/**
	 * This should compute the topological ordering of propositions.
	 * Each component is either a proposition, logical gate, or transition.
	 * Logical gates and transitions only have propositions as inputs.
	 *
	 * The base propositions and input propositions should always be exempt
	 * from this ordering.
	 *
	 * The base propositions values are set from the MachineState that
	 * operations are performed on and the input propositions are set from
	 * the Moves that operations are performed on as well (if any).
	 *
	 * @return The order in which the truth values of propositions need to be set.
	 */

	public List<Proposition> getOrdering()
	{
		//TODO compute the topological ordering
		List<Proposition> order = new LinkedList<Proposition>();
		//All of the components in the PropNet
		List<Component> components = new ArrayList<Component>(propNet.getComponents());
		//All of the propositions in the prop net
		List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

		/*
		for(GdlTerm key : basePropositions.keySet()) basePropositions.get(key).isBaseProposition=true;

		for(Proposition p: propositions) visit(p,order);

		*/
		/*Ordering Algorithm
		*
		* Go through each of the unordered propositions
		* 	if it has any dependencies - put it back into the queue
		* 	if it doesn't have any dependencies - append it to the list of ordered propositions
		*
		* To check for a dependency -
		* 	Make sure that each of the propositions leading in to the connective before it are accounted for
		*
		*/

		//Loop through all propositions

		while(!propositions.isEmpty())
		{
			Proposition currProp = propositions.remove(0);

			// TODO: Question - Are the input and base propositions in this list?
			// Should we exempt them?  What about goal and terminal?
			if(isBaseOrInputOrInitProposition(currProp)) continue;

			Set<Component> inputs = currProp.getSingleInput().getInputs();

			boolean inputsAccounted = true;

			// check if all of the inputs are satisfied
			for(Component c : inputs)
			{
				//Check to make sure that it is ordered already or is a base proposition or a intial proposition
				// TODO That they are already accounted for?
				if(!(order.contains(c) || isBaseOrInputOrInitProposition((Proposition) c)))
				{
					inputsAccounted = false;
					break;
				}
			}

			//Either add to the ordered list or move to the end of the waiting proposition list
			if(inputsAccounted)
				order.add(currProp);
			else
				propositions.add(currProp);
		}

		//System.out.println("order");
		System.out.println(order);
		return order;

	}

	//Checks to see whether a proposition is a base or input
	private boolean isBaseOrInputOrInitProposition(Proposition p)
	{
		//if(base or input (or init?) ) return true;
		if(basePropositions.containsValue(p)) return true;
		if(inputPropositions.containsValue(p)) return true;
		if(init.equals(p)) return true;
		return false;
	}

	/** Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return roles;
	}

	/**
	 * The Input propositions are indexed by (does ?player ?action).
	 *
	 * This translates a list of Moves (backed by a sentence that is simply ?action)
	 * into GdlSentences that can be used to get Propositions from inputPropositions.
	 * and accordingly set their values etc.  This is a naive implementation when coupled with
	 * setting input values, feel free to change this for a more efficient implementation.
	 *
	 * @param moves
	 * @return
	 */
	private List<GdlSentence> toDoes(List<Move> moves)
	{
		List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
		Map<Role, Integer> roleIndices = getRoleIndices();

		for (int i = 0; i < roles.size(); i++)
		{
			int index = roleIndices.get(roles.get(i));
			doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
		}
		return doeses;
	}

	/**
	 * Takes in a Legal Proposition and returns the appropriate corresponding Move
	 * @param p
	 * @return a PropNetMove
	 */

	public static Move getMoveFromProposition(Proposition p)
	{
		return new Move(p.getName().get(1));
	}

	/**
	 * Helper method for parsing the value of a goal proposition
	 * @param goalProposition
	 * @return the integer value of the goal proposition
	 */
    private int getGoalValue(Proposition goalProposition)
	{
		GdlRelation relation = (GdlRelation) goalProposition.getName();
		GdlConstant constant = (GdlConstant) relation.get(1);
		return Integer.parseInt(constant.toString());
	}

	/**
	 * A Naive implementation that computes a PropNetMachineState
	 * from the true BasePropositions.  This is correct but slower than more advanced implementations
	 * You need not use this method!
	 * @return PropNetMachineState
	 */
	public MachineState getStateFromBase()
	{
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
		for (Proposition p : propNet.getBasePropositions().values())
		{
			p.setValue(p.getSingleInput().getValue());
			if (p.getValue())
			{
				contents.add(p.getName());
			}

		}
		return new MachineState(contents);
	}

}