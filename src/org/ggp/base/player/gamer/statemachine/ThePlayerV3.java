package org.ggp.base.player.gamer.statemachine;

import java.util.Arrays;
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

// THE Player. version 3. May.5.2014.
// Add Monte Carlo heuristics, use real metagaming

public class ThePlayerV3 extends StateMachineGamer {

  static double HEUR_MIN_SCORE = 20;
  static double HEUR_MAX_SCORE = 80;

  static double MC_NUM_ATTEMPTS = 10;

  int max_depth = 1;
  int most_moves;
  int least_moves;
  // double[] heur_weight = new double[3];
  double[] heur_weight = { 0.0, 0.0, 0.0, 1.0 };


  private int[] depth = new int[1];

  public double monteCarlo(MachineState state)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    StateMachine theMachine = getStateMachine();

    int moveTotalPoints = 0;
    int numAttempts = 0;

    for (; numAttempts < MC_NUM_ATTEMPTS; numAttempts++) {
      MachineState finalState = theMachine.performDepthCharge(state, depth);
      int theScore = theMachine.getGoal(finalState, getRole());
      moveTotalPoints += theScore;
    }
    // System.out.println(1.0 * moveTotalPoints / numAttempts);
    return normalize(1.0 * moveTotalPoints / numAttempts);
  }

  private double normalize(double input) {
    return (int) (input * (HEUR_MAX_SCORE - HEUR_MIN_SCORE) / 100 + 0.5)
        + HEUR_MIN_SCORE + 0.001;
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
      score = score + heur_weight[3] * monteCarlo(state);
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
      throws MoveDefinitionException, GoalDefinitionException {
    double score = 0;
    if (heur == 0) {
      score = mobility(state);
    }
    if (heur == 1) {
      score = focus(state);
    }
    if (heur == 2) {
      score = goalProximity(state);
    }
    return (int) (score + 0.5) + 0.001;
  }

  @Override
  public void stateMachineMetaGame(long timeout)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    long finishBy = timeout - 1000;
    StateMachine stateMachine = getStateMachine();
    MachineState rootState = getCurrentState();

    stateMachine.getInitialState();
    most_moves = stateMachine.getLegalMoves(rootState, getRole()).size();
    least_moves = most_moves;
    MachineState currentState;
    int numStatesExplored = 0;
    int numRuns = 0;
    int validMoves = 0;
    List<Move> myMoves;
    Move bestMove;
    double bestScore;
    double thisScore;
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
  public Move stateMachineSelectMove(long timeout)
      throws TransitionDefinitionException, MoveDefinitionException,
      GoalDefinitionException {
    StateMachine theMachine = getStateMachine();
    long start = System.currentTimeMillis();
    long finishBy = timeout - 1000;
    max_depth = 1;

    MachineState currentState = getCurrentState();
    List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
    Random random = new Random();
    Move selection = moves.get(random.nextInt(moves.size()));
    System.out.println(moves);
    Move temp_selection;
    double score = 0;

    if (moves.size() == 1) {
      return exitSequence(moves, selection, start, timeout);
    }

    while (max_depth < Integer.MAX_VALUE) {
      double temp_score = Integer.MIN_VALUE;
      double alpha = Integer.MIN_VALUE;
      double beta = Integer.MAX_VALUE;
      temp_selection = moves.get(random.nextInt(moves.size()));
      for (Move move : moves) {
        if (System.currentTimeMillis() > finishBy) {
          System.out.println("OMG I'm gonna time out!!!!!!!!!");
          if (((int) temp_score == temp_score) && (temp_score >= score)) {
            selection = temp_selection;
            score = temp_score;
          }
          System.out.println("move: " + selection + " score: " + score);
          return exitSequence(moves, selection, start, timeout);
        }

        double result = minScore(currentState, move, alpha, beta, max_depth,
            finishBy);
        if (result > temp_score) {
          temp_score = result;
          temp_selection = move;
          if (temp_score == 100) {
            System.out.println("max score: 100");
            System.out.println(temp_selection);
            return exitSequence(moves, temp_selection, start, timeout);
          }
        }
      }
      selection = temp_selection;
      score = temp_score;
      max_depth++;
      System.out.println("Current searching depth set to " + max_depth);
    }
    System.out.println("selection: " + selection);

    return exitSequence(moves, selection, start, timeout);
  }

  private Move exitSequence(List<Move> moves, Move selection, long start,
      long timeout) throws TransitionDefinitionException,
      MoveDefinitionException {
    stateMachineExplore(timeout, selection);
    long stop = System.currentTimeMillis();
    System.out.println("time left: " + (stop - start));
    notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
    return selection;
  }

  private double minScore(MachineState currentState, Move move, double alpha,
      double beta, int depth, long finishBy) throws MoveDefinitionException,
      TransitionDefinitionException, GoalDefinitionException {
    StateMachine theMachine = getStateMachine();
    List<List<Move>> list_moves = theMachine.getLegalJointMoves(currentState,
        getRole(), move);

    for (List<Move> moves : list_moves) {
      MachineState nextState = theMachine.getNextState(currentState, moves);
      if (theMachine.isTerminal(nextState)) {
        double terminal = theMachine.getGoal(nextState, getRole());
        if (terminal == 0) {
          terminal = terminal + 0.001 * (max_depth - depth);
          beta = Math.min(beta, terminal);
          return terminal;
        }
        beta = Math.min(beta, terminal);
        if (beta <= alpha)
          return alpha;
      } else if (depth == 0
          || (max_depth - depth < 2 && System.currentTimeMillis() > finishBy)) {
        double h_score = getHeuristicScore(nextState);
        beta = Math.min(beta, h_score);
        if (h_score == 0)
          return h_score;
        if (beta <= alpha)
          return alpha;
      } else {
        double highest = maxScore(nextState, alpha, beta, depth, finishBy);
        beta = Math.min(beta, highest);
        // if(highest < 1)
        // return highest;
        if (beta <= alpha)
          return alpha;
      }
    }
    return beta;
  }

  private double maxScore(MachineState nextState, double alpha, double beta,
      int depth, long finishBy) throws MoveDefinitionException,
      TransitionDefinitionException, GoalDefinitionException {
    StateMachine theMachine = getStateMachine();
    List<Move> nextMoves = theMachine.getLegalMoves(nextState, getRole());

    for (Move nextMove : nextMoves) {
      double result = minScore(nextState, nextMove, alpha, beta, depth - 1,
          finishBy);
      alpha = Math.max(alpha, result);
      if (result > 99)
        return result;
      if (alpha >= beta)
        return beta;
    }
    return alpha;
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
