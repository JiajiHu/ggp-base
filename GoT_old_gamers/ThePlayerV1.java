package org.ggp.base.player.gamer.statemachine;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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


// THE Player. version 1. Apr.26.2014.
// depth limited heuristic alpha-beta player

public class ThePlayerV1 extends StateMachineGamer
{

  static int MAX_DEPTH = 5;
  int most_moves = 0;
  int least_moves = 0;

	private int calMobility(MachineState state) throws MoveDefinitionException
	{
		StateMachine stateMachine = getStateMachine();
		int numMoves = stateMachine.getLegalMoves(state, getRole()).size();
		int proxMaxMoves = stateMachine.getLegalMoves(stateMachine.getInitialState(), getRole()).size();

		return Math.min(80 * numMoves / proxMaxMoves, 80);

	}

	private int calFocus(MachineState state) throws MoveDefinitionException
	{
		StateMachine stateMachine = getStateMachine();
		List<List <Move> > jointMoves = stateMachine.getLegalJointMoves(state);
		int myIndex = stateMachine.getRoles().indexOf(getRole());

		int totalMoves = 0;
		for (int i = 0; i < stateMachine.getRoles().size(); i++) {
			if (i == myIndex) continue;
			Set<Move> playerMoves = new HashSet<Move>();
			for (int j = 0; j < jointMoves.size(); j ++) {
				playerMoves.add(jointMoves.get(j).get(i));
			}
			totalMoves += playerMoves.size();
		}

		return totalMoves / (stateMachine.getRoles().size() - 1);

	}

	private int goalProximity(MachineState state) throws GoalDefinitionException
	{
		StateMachine stateMachine = getStateMachine();
		return 0;
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long currentTime = System.currentTimeMillis();
		long finishBy = timeout - 1000;
		StateMachine stateMachine = getStateMachine();
		MachineState rootState = getCurrentState();
		stateMachine.getInitialState();

		MachineState currentState;
		int numStatesExplored = 0;
		int numRuns = 0;
		int validMoves = 0;
		while(true) {
			currentState = rootState;
			numRuns++;
			boolean isTerminal  = stateMachine.isTerminal(currentState);
			while(!isTerminal) {
				validMoves = validMoves + stateMachine.getLegalJointMoves(currentState).size();
				currentState = stateMachine.getRandomNextState(currentState);
				isTerminal = stateMachine.isTerminal(currentState);
				numStatesExplored++;
			}

			if(System.currentTimeMillis() > finishBy)
				break;
		}

		System.out.println("MetaGaming done");
		System.out.println("Number of runs made: "+numRuns);
		System.out.println("Number of states explored: "+ numStatesExplored);
		System.out.println("Estimated depth: "+ (numStatesExplored+0.0)/numRuns);
		System.out.println("Estimated branching factor: "+ (validMoves+0.0)/numStatesExplored);
	}

	public void stateMachineExplore(long timeout, Move move) throws MoveDefinitionException, TransitionDefinitionException
  {
    long finishBy = timeout - 1000;
    StateMachine stateMachine = getStateMachine();
    MachineState rootState = getCurrentState();

    MachineState currentState;
    while(true) {
      if(System.currentTimeMillis() > finishBy)
        break;
      currentState = stateMachine.getRandomNextState(rootState, getRole(), move);
      boolean isTerminal  = stateMachine.isTerminal(currentState);
      while(!isTerminal) {
        currentState = stateMachine.getRandomNextState(currentState);
        isTerminal = stateMachine.isTerminal(currentState);
      }
    }
  }

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		StateMachine theMachine = getStateMachine();
		long start = System.currentTimeMillis();
    long finishBy = timeout - 1000;
    int depth = MAX_DEPTH;

    MachineState currentState = getCurrentState();
		List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
		Random random = new Random();
		Move selection = moves.get(random.nextInt(moves.size()));
		System.out.println(moves);

		if (moves.size() == 1){
	    return exitSequence(moves, selection, start, timeout);
	  }

		int score = Integer.MIN_VALUE;
		int alpha = Integer.MIN_VALUE;
		int beta = Integer.MAX_VALUE;

		for(Move move: moves)
		{
		  if(System.currentTimeMillis() > finishBy){
		    System.out.println("OMG I'm gonna time out!!!!!!!!!");
		    return exitSequence(moves, selection, start, timeout);
		  }

		  int result = minScore(currentState, move, alpha, beta, depth);
			if(result > score)
			{
				score = result;
				selection = move;
				if (score == 100){
					System.out.println("max score: 100");
					System.out.println(selection);
			    return exitSequence(moves, selection, start, timeout);
				}
			}
		}
		System.out.println("max score:");
		System.out.println(score);
		System.out.println(selection);

		return exitSequence(moves, selection, start, timeout);
	}

	private Move exitSequence(List<Move> moves, Move selection, long start, long timeout) throws TransitionDefinitionException, MoveDefinitionException{
	  stateMachineExplore(timeout, selection);
	  long stop = System.currentTimeMillis();
    notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
    return selection;
	}

	private int minScore(MachineState currentState, Move move, int alpha, int beta, int depth) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException
	{
	  StateMachine theMachine = getStateMachine();
    List<List<Move>> list_moves = theMachine.getLegalJointMoves(currentState, getRole(), move);

    for (List<Move> moves: list_moves){
      MachineState nextState = theMachine.getNextState(currentState, moves);
      if(theMachine.isTerminal(nextState)) {
        int terminal = theMachine.getGoal(nextState, getRole());
        beta = Math.min(beta, terminal);
        if (terminal == 0)
          return terminal;
        if (beta <= alpha)
          return alpha;
      }
      else if (depth == 0) {
        int h_score = 20;
        beta = Math.min(beta, h_score);
        if(h_score == 0)
          return h_score;
        if (beta <= alpha)
          return alpha;
      }
      else {
        int highest = maxScore(nextState, alpha, beta, depth);
        beta = Math.min(beta, highest);
        if(highest == 0)
          return highest;
        if (beta <= alpha)
          return alpha;
      }
    }
    return beta;
  }

	private int maxScore(MachineState nextState, int alpha, int beta, int depth) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
	  StateMachine theMachine = getStateMachine();
    List<Move> nextMoves = theMachine.getLegalMoves(nextState, getRole());

    for (Move nextMove: nextMoves){
      int result = minScore(nextState, nextMove, alpha, beta, depth-1);
      alpha = Math.max(alpha, result);
      if(result == 100)
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


