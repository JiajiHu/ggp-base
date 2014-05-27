package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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

  /** The underlying proposition network */
  private PropNet propNet;
  /** The topological ordering of the propositions */
  private List<Proposition> ordering;
  private int lastLegal;
  /** The player roles */
  private List<Role> roles;

  private Map<GdlSentence, Proposition> basePropositions;
  private Map<GdlSentence, Proposition> inputPropositions;
  private Map<Role, Set<Proposition>> legalPropositions;
  private Map<Role, Set<Proposition>> goalPropositions;
  private Map<Proposition, Proposition> legalInputMap;
  private Proposition pTerminal;
  private Proposition pInit;

  /**
   * Initializes the PropNetStateMachine. You should compute the topological
   * ordering here. Additionally you may compute the initial state here, at your
   * discretion.
   */
  @Override
  public void initialize(List<Gdl> description) {
    propNet = PropNetFactory.create(description);
    roles = propNet.getRoles();
    basePropositions = propNet.getBasePropositions();
    inputPropositions = propNet.getInputPropositions();
    legalPropositions = propNet.getLegalPropositions();
    goalPropositions = propNet.getGoalPropositions();
    legalInputMap = propNet.getLegalInputMap();
    pTerminal = propNet.getTerminalProposition();
    pInit = propNet.getInitProposition();
    ordering = getOrdering();

    if (roles.size() == 1) {
      System.out.println("Here 1");
      propNet = new PropNet(roles, makeDisjunctiveFactor());
      System.out.println("Here 2");
      setUpPnet();
      System.out.println("Here 3");

    }
  }

  private void setUpPnet() {
    basePropositions = propNet.getBasePropositions();
    inputPropositions = propNet.getInputPropositions();
    pTerminal = propNet.getTerminalProposition();
    legalPropositions = propNet.getLegalPropositions();
    pInit = propNet.getInitProposition();
    goalPropositions = propNet.getGoalPropositions();
    legalInputMap = propNet.getLegalInputMap();
    System.out.println("Here 5");
    ordering = getOrdering();
    System.out.println("Here 6");
  }

  protected void clearBases() {
    for (GdlSentence key : basePropositions.keySet()) {
      basePropositions.get(key).setValue(false);
    }
  }

  protected void clearInputs() {
    for (GdlSentence key : inputPropositions.keySet()) {
      inputPropositions.get(key).setValue(false);
    }
  }

  // TODO: do we need to consider and, or, not gates? --Ding
  protected void propmarkp() {
    for (Proposition p : ordering) {
      boolean val = p.getSingleInput().getValue();
      p.setValue(val);
    }
  }

  protected void propmarkp(Proposition target) {
    for (Proposition p : ordering) {
      boolean val = p.getSingleInput().getValue();
      p.setValue(val);
      if (p.equals(target))
        break;
    }
  }

  protected void propmarkp(int length) {
    int i = 0;
    for (Proposition p : ordering) {
      boolean val = p.getSingleInput().getValue();
      p.setValue(val);
      if (i == length)
        break;
      i++;
    }
  }

  /*
   * protected void propmarkp() { long start = System.currentTimeMillis(); //
   * compute truth values for propositions for(Proposition p : ordering){ String
   * label = getLabelFromComponent(p.getSingleInput()); Set<Component> children
   * = p.getSingleInput().getInputs(); if(label.equalsIgnoreCase("and")){
   * boolean val = true; for(Component c: children){ if(!c.getValue()){ val =
   * false; break; } } p.setValue(val); } else if(label.equalsIgnoreCase("or")){
   * boolean val = false; for(Component c: children){ if(c.getValue()){ val =
   * true; break; } } p.setValue(val); } else if(label.equalsIgnoreCase("not")){
   * for(Component c: children){ p.setValue(!c.getValue()); break; } } else{
   * boolean val = p.getSingleInput().getValue(); //System.out.println("  " +
   * p.getSingleInput().toString()); p.setValue(val); } }
   * System.out.println("prop values (new) took : " +
   * (System.currentTimeMillis()-start)); }
   */
  protected void markBases(MachineState state) {
    clearBases();
    Set<GdlSentence> sentences = state.getContents();
    for (GdlSentence s : sentences) {
      Proposition p = basePropositions.get(s);
      p.setValue(true);
    }
  }

  protected void markActions(List<Move> moves) {
    clearInputs();
    List<GdlSentence> doesMoves = toDoes(moves);
    for (GdlSentence s : doesMoves) {
      Proposition p = inputPropositions.get(s);
      p.setValue(true);
    }
  }

  /**
   * Computes if the state is terminal. Should return the value of the terminal
   * proposition for the state.
   */
  @Override
  public boolean isTerminal(MachineState state) {
    markBases(state);
    propmarkp(pTerminal);
    return pTerminal.getValue();
  }

  /**
   * Computes the goal for a role in the current state. Should return the value
   * of the goal proposition that is true for that role. If there is not exactly
   * one goal proposition true for that role, then you should throw a
   * GoalDefinitionException because the goal is ill-defined.
   */
  @Override
  public int getGoal(MachineState state, Role role)
      throws GoalDefinitionException {

    markBases(state);
    propmarkp();
    Set<Proposition> p = goalPropositions.get(role);
    boolean set = false;
    Proposition goalProp = null;
    for (Proposition curr : p) {
      if (curr.getValue()) {
        if (!set) {
          goalProp = curr;
          set = true;
        } else {
          throw new GoalDefinitionException(state, role);
        }
      }
    }

    return getGoalValue(goalProp);
  }

  /**
   * Returns the initial state. The initial state can be computed by only
   * setting the truth value of the INIT proposition to true, and then computing
   * the resulting state.
   */
  @Override
  public MachineState getInitialState() {
    clearBases();
    pInit.setValue(true);
    propmarkp();
    MachineState initState = getStateFromBase();
    pInit.setValue(false);
    return initState;
  }

  /**
   * Computes the legal moves for role in state.
   */
  @Override
  public List<Move> getLegalMoves(MachineState state, Role role)
      throws MoveDefinitionException {
    markBases(state);
    propmarkp(lastLegal);
    List<Move> legalMoves = new ArrayList<Move>();
    Set<Proposition> legalProps = legalPropositions.get(role);
    for (Proposition p : legalProps) {
      if (p.getValue()) {
        Move m = getMoveFromProposition(p);
        legalMoves.add(m);
      }
    }
    return legalMoves;
  }

  /**
   * Computes the next state given state and the list of moves.
   */
  @Override
  public MachineState getNextState(MachineState state, List<Move> moves)
      throws TransitionDefinitionException {
    markBases(state);
    markActions(moves);
    propmarkp();
    Set<GdlSentence> sentences = new HashSet<GdlSentence>();
    for (GdlSentence s : basePropositions.keySet()) {
      Proposition p = basePropositions.get(s);
      if (p.getSingleInput().getValue()) {
        sentences.add(p.getName());
      }
    }
    return getMachineStateFromSentenceList(sentences);
  }

  void reorder(List<Proposition> propositions) {
    HashSet<Proposition> legalProps = new HashSet<Proposition>();
    for (Set<Proposition> ps : legalPropositions.values())
      legalProps.addAll(ps);
    int firstNonLegal = 0;
    for (int i = 0; i < propositions.size(); i++) {
      if (pTerminal.equals(propositions.get(i))) {
        // System.out.println("reorder terminal");
        Proposition temp = propositions.get(i);
        propositions.set(i, propositions.get(firstNonLegal));
        propositions.set(firstNonLegal, temp);
        firstNonLegal++;
        break;
      }
    }
    for (int i = firstNonLegal; i < propositions.size(); i++) {
      if (legalProps.contains(propositions.get(i))) {
        // System.out.println("reorder legal");
        Proposition temp = propositions.get(i);
        propositions.set(i, propositions.get(firstNonLegal));
        propositions.set(firstNonLegal, temp);
        firstNonLegal++;
      }
    }
  }

  /**
   * This should compute the topological ordering of propositions. Each
   * component is either a proposition, logical gate, or transition. Logical
   * gates and transitions only have propositions as inputs.
   *
   * The base propositions and input propositions should always be exempt from
   * this ordering.
   *
   * The base propositions values are set from the MachineState that operations
   * are performed on and the input propositions are set from the Moves that
   * operations are performed on as well (if any).
   *
   * @return The order in which the truth values of propositions need to be set.
   */
  // TODO: I haven't read this part yet, may need revising --Ding
  public List<Proposition> getOrdering() {
    System.out.println("getordering");
    // TODO compute the topological ordering
    List<Proposition> order = new LinkedList<Proposition>();
    // All of the components in the PropNet
    List<Component> components = new ArrayList<Component>(
        propNet.getComponents());
    // All of the propositions in the prop net
    List<Proposition> propositions = new ArrayList<Proposition>(
        propNet.getPropositions());

    /*
     * for(GdlTerm key : basePropositions.keySet())
     * basePropositions.get(key).isBaseProposition=true;
     *
     * for(Proposition p: propositions) visit(p,order);
     */
    /*
     * Ordering Algorithm
     *
     * Go through each of the unordered propositions if it has any dependencies
     * - put it back into the queue if it doesn't have any dependencies - append
     * it to the list of ordered propositions
     *
     * To check for a dependency - Make sure that each of the propositions
     * leading in to the connective before it are accounted for
     */
    reorder(propositions);

    // Loop through all propositions

    while (!propositions.isEmpty()) {
      Proposition currProp = propositions.remove(0);
      if (!components.contains(currProp))
        continue;
      // TODO: Question - Are the input and base propositions in this list?
      // Should we exempt them? What about goal and terminal?
      if (isBaseOrInputOrInitProposition(currProp))
        continue;
      if (currProp.getInputs().size() == 0) {
        System.out.println("spurious prop found! omgzz " + currProp);
        continue;
      }

      Set<Component> inputs = currProp.getSingleInput().getInputs();

      boolean inputsAccounted = true;

      // check if all of the inputs are satisfied
      for (Component c : inputs) {
        // Check to make sure that it is ordered already or is a base
        // proposition or a intial proposition
        // TODO That they are already accounted for?
        if (components.contains(c)
            && (!(order.contains(c) || isBaseOrInputOrInitProposition((Proposition) c)))) {
          inputsAccounted = false;
          break;
        }
      }

      // Either add to the ordered list or move to the end of the waiting
      // proposition list
      if (inputsAccounted) {
        order.add(currProp);
//        reorder(propositions);
      } else
        propositions.add(currProp);
    }
    System.out.println("getordering finished");

    HashSet<Proposition> legalProps = new HashSet<Proposition>();
    for (Set<Proposition> ps : legalPropositions.values())
      legalProps.addAll(ps);

    int max = 0;
    for (int i = 0; i < order.size(); i++) {
      if (legalProps.contains(order.get(i))) {
        max = i;
      }
    }
    lastLegal = max;

    int terminalPos = 0;
    for (int i = 0; i < order.size(); i++) {
      if (pTerminal.equals(order.get(i))) {
        terminalPos = i;
        break;
      }
    }

    System.out.println("isTerminal position: " + terminalPos);
    System.out.println("Total legal: " + legalProps.size());
    System.out.println("Last legal: " + lastLegal);
    System.out.println("Total props: " + order.size());

    return order;

  }

  // Checks to see whether a proposition is a base or input
  private boolean isBaseOrInputOrInitProposition(Proposition p) {
    if (p.getInputs().size() == 0)
      return true;
    // if(base or input (or init?) ) return true;
    if (basePropositions.containsValue(p))
      return true;
    if (inputPropositions.containsValue(p))
      return true;
    if (pInit.equals(p))
      return true;
    return false;
  }

  /* Already implemented for you */
  @Override
  public List<Role> getRoles() {
    return roles;
  }

  /* Helper methods */

  /**
   * The Input propositions are indexed by (does ?player ?action).
   *
   * This translates a list of Moves (backed by a sentence that is simply
   * ?action) into GdlSentences that can be used to get Propositions from
   * inputPropositions. and accordingly set their values etc. This is a naive
   * implementation when coupled with setting input values, feel free to change
   * this for a more efficient implementation.
   *
   * @param moves
   * @return
   */
  private List<GdlSentence> toDoes(List<Move> moves) {
    List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
    Map<Role, Integer> roleIndices = getRoleIndices();

    for (int i = 0; i < roles.size(); i++) {
      int index = roleIndices.get(roles.get(i));
      doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
    }
    return doeses;
  }

  /**
   * Takes in a Legal Proposition and returns the appropriate corresponding Move
   *
   * @param p
   * @return a PropNetMove
   */
  public static Move getMoveFromProposition(Proposition p) {
    return new Move(p.getName().get(1));
  }

  /**
   * Helper method for parsing the value of a goal proposition
   *
   * @param goalProposition
   * @return the integer value of the goal proposition
   */
  private int getGoalValue(Proposition goalProposition) {
    GdlRelation relation = (GdlRelation) goalProposition.getName();
    GdlConstant constant = (GdlConstant) relation.get(1);
    return Integer.parseInt(constant.toString());
  }

  /**
   * A Naive implementation that computes a PropNetMachineState from the true
   * BasePropositions. This is correct but slower than more advanced
   * implementations You need not use this method!
   *
   * @return PropNetMachineState
   */
  public MachineState getStateFromBase() {
    Set<GdlSentence> contents = new HashSet<GdlSentence>();
    for (Proposition p : propNet.getBasePropositions().values()) {
      p.setValue(p.getSingleInput().getValue());
      if (p.getValue()) {
        contents.add(p.getName());
      }

    }
    return new MachineState(contents);
  }

  /*--------------------below is code for factoring propnet, we should write our own!-----------------------------
   *            These are only for references...
   *                              --by Ding
   */

  private String getLabelFromComponent(Component c) {
    String label = c.toString().replaceAll(".*label=\"", "");
    label = label.replaceAll("\"].*", "");
    return label;
  }

  private Set<Component> getTransitiveClosure(Component c) {
    Queue<Component> q = new LinkedList<Component>();
    q.add(c);
    Set<Component> comps = new HashSet<Component>();
    while (!q.isEmpty()) {
      Component curr = q.poll();
      for (Component child : curr.getInputs()) {
        q.add(child);
      }
      comps.add(curr);
    }
    return comps;
  }

  private Set<Component> getValidLegals(Set<Component> validInputs,
      Set<Component> factor) {
    Set<Component> ret = new HashSet<Component>();
    for (Component start : validInputs) {
      Queue<Component> q = new LinkedList<Component>();
      q.add(start);
      Set<Component> seen = new HashSet<Component>();
      while (!q.isEmpty()) {
        Component curr = q.poll();
        for (Component out : curr.getOutputs()) {
          if (legalPropositions.containsValue(out)) {
            ret.add(out);
          }
          if (out.getInputs().size() != 1) {
            if (getLabelFromComponent(out).equalsIgnoreCase("or")) {
              // its an or node just add it:
              if (!seen.contains(out)) {
                q.add(out);
                seen.add(out);
              }
            } else {
              // and node : only add if all inputs contained in the factor
              boolean allFound = true;
              for (Component d : out.getInputs()) {
                if (!factor.contains(d)) {
                  allFound = false;
                  break;
                }
              }
              if (allFound) {
                if (!seen.contains(out)) {
                  q.add(out);
                  seen.add(out);
                }
              }
            }
          } else {
            if (!seen.contains(out)) {
              q.add(out);
              seen.add(out);
            }
          }
        }
      }
    }
    return ret;
  }

  private Set<Component> makeDisjunctiveFactor() {

    // put each goal/terminal state's child prop node with a single input of a
    // disjunct (or) into its own seperate set
    ArrayList<Set<Component>> goalProps = new ArrayList<Set<Component>>();
    // System.out.println("terminals single child " +
    // getLabelFromComponent(terminal.getSingleInput()));

    // if(getLabelFromComponent(terminal.getSingleInput()).equalsIgnoreCase("or")){
    for (Component c : pTerminal.getSingleInput().getInputs()) {
      HashSet<Component> toAdd = new HashSet<Component>();
      toAdd.add(c);
      goalProps.add(toAdd);
    }
    // }
    for (Role r : goalPropositions.keySet()) { // get all goals with input from
                                               // disjuncts, then add their prop
                                               // children to the mix
      for (Proposition prop : goalPropositions.get(r)) {
        // if(getLabelFromComponent(prop.getSingleInput()).equalsIgnoreCase("or")){

        for (Component g : prop.getSingleInput().getInputs()) {
          HashSet<Component> toAdd = new HashSet<Component>();
          toAdd.add(g);
          goalProps.add(toAdd);
        }
        // }
      }
    }
    // go through each set:

    for (int i = 0; i < goalProps.size(); i++) {

      Set<Component> propset = goalProps.get(i);
      // Component p = propset.iterator().next();

      Queue<Component> q = new LinkedList<Component>();
      // enqueue props
      // q.add(p);
      for (Component c : propset)
        q.add(c);

      // compute transitive closure:
      while (!q.isEmpty()) {
        Component curr = q.poll();
        for (Component child : curr.getInputs()) {
          if (!inputPropositions.containsValue(child)) {
            q.add(child);

          }
        }
        propset.add(curr);
        // combine with any other components that contain
        for (int j = 0; j < goalProps.size(); j++) {
          if (i == j)
            continue;

          if (goalProps.get(j).contains(curr)) {

            propset.addAll(goalProps.get(j));
            goalProps.remove(j);
            i--;
            break;
          }

        }
      }
    }

    System.out.println("Number of Factors " + goalProps.size());

    // choose the smallest:
    int smallestIndex = 0;
    int smallestSize = goalProps.get(0).size();
    for (int i = 1; i < goalProps.size(); i++) {
      System.out.println("Size of factor " + i + " = "
          + goalProps.get(i).size());
      if (goalProps.get(i).size() < smallestSize) {
        smallestIndex = i;
        smallestSize = goalProps.get(i).size();
      }
    }

    Set<Component> factor = goalProps.get(smallestIndex);

    /* Input Props */
    // add input props that link to this factor
    int inputPropsAdded = 0;
    Set<Proposition> validInputs = new HashSet<Proposition>();
    for (GdlSentence key : inputPropositions.keySet()) {
      Proposition input = inputPropositions.get(key);
      for (Component output : input.getOutputs()) {
        if (factor.contains(output)) {
          factor.add(input);
          validInputs.add(input);
          inputPropsAdded++;
          break; // don't double add
        }
      }
    }
    System.out.println("Number of Input Props Added: " + inputPropsAdded);

    /* Goal Propositions */
    for (Role key : goalPropositions.keySet()) {
      Set<Proposition> g = goalPropositions.get(key);
      for (Proposition p : g) {
        factor.add(p);
        factor.add(p.getSingleInput());
      }
    }

    /* Init Prop */
    factor.add(pInit);

    System.out
        .println("Size of factor before adding terminal and the conjunction"
            + factor.size());
    /* Terminal Prop */
    factor.add(pTerminal);
    factor.add(pTerminal.getSingleInput());
    System.out
        .println("Size of factor adding adding terminal and the conjunction"
            + factor.size());

    /* Input Props */
    /* Getting the legal inputs to add */
    // Algorithm
    // Convert the input props to legal props
    // Trace all the way back and add everything into the component set

    // Get each inputProp
    int legalAdded = 0;
    for (Proposition p : validInputs) {
      // Add the proposition
      Proposition legalProp = legalInputMap.get(p);
      Queue<Component> q = new LinkedList<Component>();
      q.add(legalProp);
      while (!q.isEmpty()) {
        Component curr = q.poll();
        factor.add(curr);
        legalAdded++;
        q.addAll(curr.getInputs());
      }
    }
    System.out.println("Number of legal props added " + legalAdded++);

    /*
     * Remove Non-Connected
     */
    for (Component c : factor) {
      Iterator i = c.getInputs().iterator();
      while (i.hasNext()) {
        Component input = (Component) i.next();
        if (!factor.contains(input)) {
          // System.out.println("Removed input");
          i.remove();
        }
      }

      Iterator i2 = c.getOutputs().iterator();
      while (i2.hasNext()) {
        Component input = (Component) i2.next();
        if (!factor.contains(input)) {
          // System.out.println("Removed output");
          i2.remove();
        }
      }
    }
    System.out.println("Factor size : " + factor.size());
    System.out.println("Components:" + propNet.getComponents().size());
    return factor;
  }

}