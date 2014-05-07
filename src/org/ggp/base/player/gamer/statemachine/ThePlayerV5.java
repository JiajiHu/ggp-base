package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

// THE Player. version 5. May.6.2014.
// MCTS multi player

public class ThePlayerV5 extends StateMachineGamer {

  static double HEUR_MIN_SCORE = 20;
  static double HEUR_MAX_SCORE = 80;
  //TODO: learn MC_NUM_ATTEMPTS during metagaming
  static double MC_NUM_ATTEMPTS = 5;

  // TODO: better way to store this relation -- how?
  HashMap<MachineState, Node> stateToNode = new HashMap<MachineState, Node>();
  HashMap<Node, MachineState> nodeToState = new HashMap<Node, MachineState>();

  int max_depth = 1;
  int most_moves;
  int least_moves;
  int num_roles;
  int num_me;
  //TODO: learn C during metagaming
  int C = 100;
  // double[] heur_weight = new double[3];
  double[] heur_weight = { 0.0, 0.0, 0.0, 1.0 };

  public class Node {
    // TODO: expand to multiplayer: need a boolean for is_me,
    int visits = 0;
    int active_player;
//    double my_util = 0;
    double[] utility = new double[num_roles];
    Node parent = null;
    ArrayList<Node> children = new ArrayList<Node>();

    Node(MachineState state) throws MoveDefinitionException {
      this.active_player = getActiveRole(state);
      stateToNode.put(state, this);
      nodeToState.put(this, state);
    }

    Node(Node p, MachineState state) throws MoveDefinitionException {
      this.parent = p;
      p.children.add(this);
      this.active_player = getActiveRole(state);
      stateToNode.put(state, this);
      nodeToState.put(this, state);
    }
  };

  double selectfn(Node node, int player) {
    return (node.utility[player]/node.visits) + C * Math.sqrt(Math.log(node.parent.visits) / node.visits);
  }

  Node select(Node node) {
    if (node.visits == 0)
      return node;
    for (int i = 0; i < node.children.size(); i++) {
      if (node.children.get(i).visits == 0)
        return node.children.get(i);
    }
    double score = 0;
    Node result = node;
    for (int i = 0; i < node.children.size(); i++) {
      double newScore = selectfn(node.children.get(i), node.active_player);
      if (newScore > score) {
        score = newScore;
        result = node.children.get(i);
      }
    }
    //prevent an infinite loop
    if (result == node)
      return node;
    result.parent = node;
    return select(result);
  }

  void expand(Node node) throws MoveDefinitionException,
      TransitionDefinitionException {
    StateMachine theMachine = getStateMachine();
    MachineState currentState = nodeToState.get(node);
    // Role will change for multiplayer games
    Role currentRole = getRole();
    List<Move> myMoves = theMachine.getLegalMoves(currentState, currentRole);
    for (Move move : myMoves) {
      // TODO: getRandomNextState works only if we assume no simultaneous
      // actions -- works for current games, fix in the future
      MachineState nextState = theMachine.getRandomNextState(currentState,
          currentRole, move);
      Node newNode = new Node(node, nextState);
    }
  }

  int getActiveRole(MachineState state) throws MoveDefinitionException{
    StateMachine theMachine = getStateMachine();
    if(theMachine.isTerminal(state))
      return num_me;
    for(int i=0; i<num_roles; i++){
      if(theMachine.getLegalMoves(state, theMachine.getRoles().get(i)).size() > 1)
        return i;
    }
    return num_me;
  }

  void backPropogate(Node node, double[] score) {
    node.visits += 1;
    for(int i=0;i<num_roles;i++){
      node.utility[i] += score[i];
    }
    /**************************************/
//    if (node.active_player == num_me)
//      node.my_util += node.utility[num_me];
//    else
//      node.my_util -= node.utility[num_me];
    /**************************************/
    if (node.parent != null)
      backPropogate(node.parent, score);
  }

  private int[] depth = new int[1];

  public double[] monteCarlo(MachineState state)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    StateMachine theMachine = getStateMachine();

    int numAttempts = 0;

    double[] scores = new double[num_roles];
    for (; numAttempts < MC_NUM_ATTEMPTS; numAttempts++) {
      MachineState finalState = theMachine.performDepthCharge(state, depth);
      List<Integer> thisScores = theMachine.getGoals(finalState);
      for (int i=0;i<num_roles;i++)
        scores[i] += thisScores.get(i);
    }
    for (int i=0;i<num_roles;i++)
      scores[i] = scores[i]/numAttempts;

    return scores;
  }

  public void performMCTSCycle(Node node) throws MoveDefinitionException,
      TransitionDefinitionException, GoalDefinitionException {
    Node selected_node = select(node);
    MachineState selected_state = nodeToState.get(selected_node);
    if (!getStateMachine().isTerminal(selected_state))
      expand(selected_node);
    // NOTE: monteCarlo performs MC_NUM_ATTEMPTS depth-charges, so all visit
    // counts should in fact all be multiplied by that number.
    double[] score = monteCarlo(selected_state);
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
      // TODO: SAME AS ABOVE: getRandomNextState works only if we assume no
      // simultaneous actions -- works for current games, fix in the future
      MachineState nextState = theMachine.getRandomNextState(currentState,
          getRole(), move);
      Node nextNode = stateToNode.get(nextState);
      System.out.print("move: "+move);
      System.out.print("  visits: "+nextNode.visits);
      System.out.println("  average score for me: "+nextNode.utility[num_me] / nextNode.visits);
      if (nextNode.utility[num_me] / nextNode.visits > curBest) {
        curBest = nextNode.utility[num_me] / nextNode.visits;
        selection = move;
      }
    }
    System.out.println("chose: "+selection);
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
        System.out.println("Time up! Cycles competed: "+rootNode.visits);
        break;
      }
      performMCTSCycle(rootNode);
    }

    Move selection = MCTSSelectMove(rootNode);
    return exitSequence(moves, selection, start, timeout);
  }

  private Move exitSequence(List<Move> moves, Move selection, long start,
      long timeout) throws TransitionDefinitionException,
      MoveDefinitionException {
    stateMachineExplore(timeout, selection);
    long stop = System.currentTimeMillis();
    System.out.println("time left: " + (timeout-stop));
    notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
    return selection;
  }

  private double mobility(MachineState state) throws MoveDefinitionException {
    int numMoves = getStateMachine().getLegalMoves(state, getRole()).size();
    return normalize((numMoves - least_moves + 0.0)
        / (most_moves - least_moves));
  }

  private double focus(MachineState state) throws MoveDefinitionException {
    int numMoves = getStateMachine().getLegalMoves(state, getRole()).size();
    return normalize((most_moves - numMoves + 0.0) / (most_moves - least_moves));
  }

  private double goalProximity(MachineState state)
      throws GoalDefinitionException {
    StateMachine stateMachine = getStateMachine();
    return normalize((stateMachine.getGoal(state, getRole()) + 0.0));
  }

  private double normalize(double input) {
    return (int) (input * (HEUR_MAX_SCORE - HEUR_MIN_SCORE) / 100 + 0.5)
        + HEUR_MIN_SCORE + 0.001;
  }

  private double getHeuristicScore(MachineState state)
      throws MoveDefinitionException, GoalDefinitionException,
      TransitionDefinitionException {

    // return 0.0;
    double score = 0;
    double normalize = 0;
    if (heur_weight[0] > 0) {
      score = score + heur_weight[0] * mobility(state);
      normalize = normalize + heur_weight[0];
    }
    if (heur_weight[1] > 0) {
      score = score + heur_weight[1] * focus(state);
      normalize = normalize + heur_weight[1];
    }
    if (heur_weight[2] > 0) {
      score = score + heur_weight[2] * goalProximity(state);
      normalize = normalize + heur_weight[2];
    }
    if (heur_weight[3] > 0) {
      score = score + heur_weight[3] * monteCarlo(state)[num_me];
      normalize = normalize + heur_weight[3];
    }

    score = score / normalize;
    return (int) (score + 0.5) + 0.001;
  }

  private void setHeuristicWeights(double[] metaResults) {
    boolean chooseOne = true;
    boolean weighted = false;
    double bestScore = -Double.MAX_VALUE;
    System.out.println("This is what I got from metagaming: "
        + Arrays.toString(metaResults));
    int winner = 0;
    if (chooseOne) {
      for (int i = 0; i < metaResults.length; i++) {
        if (metaResults[i] > bestScore) {
          winner = i;
          bestScore = metaResults[i];
        }
      }
      System.out.println("Choosing heuristic: " + winner);
      for (int i = 0; i < metaResults.length; i++) {
        if (i == winner)
          heur_weight[i] = 1;
        else
          heur_weight[i] = 0;
      }
    }
    if (weighted) {
      // TODO:add stuff
    }
  }

  private double getMetaHeuristic(MachineState state, int heur)
      throws MoveDefinitionException, GoalDefinitionException,
      TransitionDefinitionException {
    double score = 0;
//    if (heur == 0) {
//      score = mobility(state);
//    }
//    if (heur == 1) {
//      score = focus(state);
//    }
//    if (heur == 2) {
//      score = goalProximity(state);
//    }
//    if (heur == 3) {
//      score = monteCarlo(state)[num_me];
//    }
    return (int) (score + 0.5) + 0.001;
  }

  @Override
  public void stateMachineMetaGame(long timeout)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    // TODO: rather than choose between heuristics, maybe just try to set a good
    // C for MCTS
    // TODO: play against searchlight gamer instead of random gamer
    long finishBy = timeout - 1000;
    StateMachine stateMachine = getStateMachine();
    MachineState rootState = getCurrentState();
    List<Role> roles = stateMachine.getRoles();
    num_roles = roles.size();
    for (int i=0;i<num_roles;i++){
      if (roles.get(i).equals(getRole()))
        num_me = i;
    }

    System.out.println("total num players: "+num_roles);
    System.out.println("my role num: "+num_me);
    System.out.println("players: "+roles);
    System.out.println("my player: "+roles.get(num_me));

    stateMachine.getInitialState();
    most_moves = stateMachine.getLegalMoves(rootState, getRole()).size();
    least_moves = most_moves;
    MachineState currentState;
    int numStatesExplored = 0;
    int numRuns = 0;
    int validMoves = 0;
    List<Move> myMoves;
    Move bestMove;
    double bestScore, thisScore;
    int myMoveLen;
    int heurNum = 0;
    int heurTotal = heur_weight.length;
    double[] heurScores = new double[heurTotal];
    int[] heurCount = new int[heurTotal];

    while (true) {
      currentState = rootState;
      numRuns++;
      boolean isTerminal = stateMachine.isTerminal(currentState);
      while (!isTerminal) {
        myMoves = stateMachine.getLegalMoves(currentState, getRole());
        myMoveLen = myMoves.size();
        if (myMoveLen > most_moves)
          most_moves = myMoveLen;
        else if (myMoveLen < least_moves)
          least_moves = myMoveLen;
        validMoves = validMoves
            + stateMachine.getLegalJointMoves(currentState).size();
        bestScore = -Double.MAX_VALUE;
        bestMove = myMoves.get(0);
        for (Move thisMove : myMoves) {
          thisScore = getMetaHeuristic(stateMachine.getRandomNextState(
              currentState, getRole(), thisMove), heurNum);
          if (thisScore > bestScore) {
            bestScore = thisScore;
            bestMove = thisMove;
          }
        }
        currentState = stateMachine.getRandomNextState(currentState, getRole(),
            bestMove);
        isTerminal = stateMachine.isTerminal(currentState);
        if (isTerminal) {
          heurScores[heurNum] = heurScores[heurNum]
              + stateMachine.getGoal(currentState, getRole());
          heurCount[heurNum] = heurCount[heurNum] + 1;
        }
        numStatesExplored++;
      }
      if (System.currentTimeMillis() > finishBy) {
        for (int i = 0; i < heurTotal; i++) {
          heurScores[i] = heurScores[i] / heurCount[i];
        }
        setHeuristicWeights(heurScores);
        break;
      }
      heurNum = (heurNum + 1) % heurTotal;
    }

    System.out.println("MetaGaming done");
    System.out.println("Number of runs made: " + numRuns);
    System.out.println("Number of states explored: " + numStatesExplored);
    System.out.println("Estimated depth: " + (numStatesExplored + 0.0)
        / numRuns);
    System.out.println("Estimated branching factor: " + (validMoves + 0.0)
        / numStatesExplored);
  }

  public void stateMachineExplore(long timeout, Move move)
      throws MoveDefinitionException, TransitionDefinitionException {
    // TODO: modify for MCTS
    long finishBy = timeout - 1000;
    StateMachine stateMachine = getStateMachine();
    MachineState rootState = getCurrentState();

    MachineState currentState;
    while (true) {
      if (System.currentTimeMillis() > finishBy)
        break;
      currentState = stateMachine
          .getRandomNextState(rootState, getRole(), move);
      boolean isTerminal = stateMachine.isTerminal(currentState);
      while (!isTerminal) {
        currentState = stateMachine.getRandomNextState(currentState);
        isTerminal = stateMachine.isTerminal(currentState);
      }
    }
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
