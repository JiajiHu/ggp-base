package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

// THE Player. version 6. May.11.2014.
// MCTS multi player -- only keeps score for self -- use metagaming for MCTS

public class ThePlayerV6 extends StateMachineGamer {

  // TODO: learn MC_NUM_ATTEMPTS during metagaming
  static double MC_NUM_ATTEMPTS = 2;

  // TODO: better way to store this relation -- how?
  HashMap<MachineState, Node> stateToNode = new HashMap<MachineState, Node>();
  HashMap<Node, MachineState> nodeToState = new HashMap<Node, MachineState>();

  // TODO: learn C during metagaming
  int C = 40;

  public class Node {
    int visits = 0;
    double utility = 0;
    Node parent = null;
    ArrayList<Node> children = new ArrayList<Node>();

    Node(MachineState state) throws MoveDefinitionException {
      stateToNode.put(state, this);
      nodeToState.put(this, state);
    }

    Node(Node p, MachineState state) throws MoveDefinitionException {
      this.parent = p;
      p.children.add(this);
      stateToNode.put(state, this);
      nodeToState.put(this, state);
    }
  };

  double selectfn(Node node, boolean max) {
    if (max)
      return (node.utility / node.visits) + C
          * Math.sqrt(Math.log(node.parent.visits) / node.visits);
    else
      return 100 - (node.utility / node.visits) + C
          * Math.sqrt(Math.log(node.parent.visits) / node.visits);
  }

  Node select(Node node) throws MoveDefinitionException,
      TransitionDefinitionException {
    if (node.visits == 0)
      return node;
    for (Node child : node.children) {
      if (child.visits == 0)
        return child;
    }
    Node result = selectBest(node);

    // prevent an infinite loop
    if (result == node)
      return node;
    result.parent = node;
    return select(result);
  }

  Node selectBest(Node node) throws MoveDefinitionException,
      TransitionDefinitionException {
    // selectBest does all the dirty work now:
    // includes the 'min' step for opponents,
    // calculates selectfn for both player and opponents
    StateMachine theMachine = getStateMachine();
    MachineState currentState = nodeToState.get(node);
    if (theMachine.isTerminal(currentState))
      return node;
    // outer loop is 'max' loop
    // inner loop is 'min' loop
    double maxScore = 0;
    Node maxNode = node;

    for (Move move : theMachine.getLegalMoves(currentState, getRole())) {
      double minScore = 0;
      Node minNode = node;
      List<List<Move>> jointMoves = theMachine.getLegalJointMoves(currentState,
          getRole(), move);
      for (List<Move> jointMove : jointMoves) {
        Node nextNode = stateToNode.get(theMachine.getNextState(currentState,
            jointMove));
        // this happens due to the competing parents phenomenon
        if (nextNode.visits == 0) {
          return nextNode;
        }
        double nextScore = selectfn(nextNode, false);
        if (nextScore > minScore) {
          minScore = nextScore;
          minNode = nextNode;
        }
      }
      double midScore = selectfn(minNode, true);
      if (midScore > maxScore) {
        maxScore = midScore;
        maxNode = minNode;
      }
    }

    return maxNode;
  }

  void expand(Node node) throws MoveDefinitionException,
      TransitionDefinitionException {
    StateMachine theMachine = getStateMachine();
    MachineState currentState = nodeToState.get(node);

    for (MachineState nextState : theMachine.getNextStates(currentState)) {
      Node newNode = new Node(node, nextState);
    }
  }

  void backPropogate(Node node, double score) {
    node.visits += 1;
    node.utility += score;
    if (node.parent != null)
      backPropogate(node.parent, score);
  }

  private int[] depth = new int[1];

  public double monteCarlo(MachineState state)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    // TODO: prefer early win to late win
    StateMachine theMachine = getStateMachine();

    int numAttempts = 0;

    double score = 0;
    for (; numAttempts < MC_NUM_ATTEMPTS; numAttempts++) {
      MachineState finalState = theMachine.performDepthCharge(state, depth);
      score += theMachine.getGoal(finalState, getRole());
    }

    return score / numAttempts;
  }

  public void performMCTSCycle(Node node) throws MoveDefinitionException,
      TransitionDefinitionException, GoalDefinitionException {
    Node selected_node = select(node);
    MachineState selected_state = nodeToState.get(selected_node);
    if (!getStateMachine().isTerminal(selected_state))
      expand(selected_node);
    // NOTE: monteCarlo performs MC_NUM_ATTEMPTS depth-charges, so all visit
    // counts should in fact all be multiplied by that number.
    double score = monteCarlo(selected_state);
    backPropogate(selected_node, score);
  }

  public Move MCTSSelectMove(Node node) throws MoveDefinitionException,
      TransitionDefinitionException {
    Random random = new Random();
    StateMachine theMachine = getStateMachine();
    MachineState currentState = nodeToState.get(node);
    List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
    Move selection = moves.get(random.nextInt(moves.size()));
    double curBest = -Double.MAX_VALUE;
    for (Move move : moves) {
      double minScore = 100;
      List<List<Move>> jointMoves = theMachine.getLegalJointMoves(currentState,
          getRole(), move);
      for (List<Move> jointMove : jointMoves) {
        Node nextNode = stateToNode.get(theMachine.getNextState(currentState,
            jointMove));
        if (nextNode.utility / nextNode.visits < minScore)
          minScore = nextNode.utility / nextNode.visits;
        System.out.print("joint move: " + jointMove);
        System.out.print("  visits: " + nextNode.visits);
        System.out.println("  average score for me: " + nextNode.utility
            / nextNode.visits);
      }

      if (minScore > curBest) {
        curBest = minScore;
        selection = move;
      }
    }
    System.out.println("chose: " + selection);
    return selection;
  }

  @Override
  public Move stateMachineSelectMove(long timeout)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    StateMachine theMachine = getStateMachine();
    MachineState currentState = getCurrentState();
    long start = System.currentTimeMillis();
    long finishBy = timeout - 1000;
    List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
    if (moves.size() == 1)
      return exitSequence(moves, moves.get(0), start, timeout);

    Node rootNode;
    if (stateToNode.containsKey(currentState))
      rootNode = stateToNode.get(currentState);
    else
      rootNode = new Node(currentState);

    while (true) {
      if (System.currentTimeMillis() > finishBy) {
        System.out.println("Time up! Cycles competed: " + rootNode.visits);
        break;
      }
      performMCTSCycle(rootNode);
    }

    Move selection = MCTSSelectMove(rootNode);
    return exitSequence(moves, selection, start, timeout);
  }

  private Move exitSequence(List<Move> moves, Move selection, long start,
      long timeout) throws TransitionDefinitionException,
      MoveDefinitionException, GoalDefinitionException {
    stateMachineExplore(timeout);
    long stop = System.currentTimeMillis();
    System.out.println("time left: " + (timeout - stop));
    notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
    return selection;
  }

  public void stateMachineExplore(long timeout) throws MoveDefinitionException,
      TransitionDefinitionException, GoalDefinitionException {
    long finishBy = timeout - 1000;
    if (System.currentTimeMillis() > finishBy)
      return;
    MachineState currentState = getCurrentState();
    Node rootNode;
    if (stateToNode.containsKey(currentState))
      rootNode = stateToNode.get(currentState);
    else
      rootNode = new Node(currentState);
    while (true) {
      performMCTSCycle(rootNode);
      if (System.currentTimeMillis() > finishBy)
        break;
    }
  }

  @Override
  public void stateMachineMetaGame(long timeout)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    // TODO: rather than choose between heuristics, maybe just try to set a good
    // C for MCTS
    // TODO: maybe learn timeouts as well
    // TODO: play against searchlight gamer instead of random gamer
    long finishBy = timeout - 1000;
    StateMachine stateMachine = getStateMachine();
    MachineState rootState = getCurrentState();

    stateMachine.getInitialState();
    MachineState currentState;
    int numStatesExplored = 0;
    int numRuns = 0;
    int validMoves = 0;
    while (true) {
      currentState = rootState;
      numRuns++;
      boolean isTerminal = stateMachine.isTerminal(currentState);
      while (!isTerminal) {
        validMoves = validMoves
            + stateMachine.getLegalJointMoves(currentState).size();
        currentState = stateMachine.getRandomNextState(currentState);
        isTerminal = stateMachine.isTerminal(currentState);
        numStatesExplored++;
      }
      if (System.currentTimeMillis() > finishBy) {
        break;
      }
    }

    System.out.println("MetaGaming done");
    System.out.println("Number of runs made: " + numRuns);
    System.out.println("Number of states explored: " + numStatesExplored);
    System.out.println("Estimated depth: " + (numStatesExplored + 0.0)
        / numRuns);
    System.out.println("Estimated branching factor: " + (validMoves + 0.0)
        / numStatesExplored);
  }

  @Override
  public String getName() {
    return getClass().getSimpleName();
  }

  // This is the default State Machine
  @Override
  public StateMachine getInitialStateMachine() {
    return new CachedStateMachine(new ProverStateMachine());
  }

  // This is the default Sample Panel
  @Override
  public DetailPanel getDetailPanel() {
    return new SimpleDetailPanel();
  }

  @Override
  public void stateMachineStop() {
    // Sample gamers do no special cleanup when the match ends normally.
  }

  @Override
  public void stateMachineAbort() {
    // Sample gamers do no special cleanup when the match ends abruptly.
  }

  @Override
  public void preview(Game g, long timeout) throws GamePreviewException {
    // Sample gamers do no game previewing.
  }
}
