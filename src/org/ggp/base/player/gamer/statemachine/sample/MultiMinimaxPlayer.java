package org.ggp.base.player.gamer.statemachine.sample;

import java.util.List;
import java.util.Random;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class MultiMinimaxPlayer extends StateMachineGamer
{
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

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		StateMachine theMachine = getStateMachine();
		long start = System.currentTimeMillis();
    long finishBy = timeout - 1000;


		MachineState currentState = getCurrentState();
		List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
		Random random = new Random();
    Move selection = moves.get(random.nextInt(moves.size()));
    System.out.println(moves);

		if (moves.size() == 1){
		  long stop = System.currentTimeMillis();
      notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
      return selection;
		}

		int score = Integer.MIN_VALUE;

		for(Move move: moves)
		{
		  if(System.currentTimeMillis() > finishBy){
		    long stop = System.currentTimeMillis();
	      notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
	      return selection;
		  }

		  int result = nextScore(currentState, move);
			if(result > score)
			{
				score = result;
				selection = move;
				if (score == 100){
				  System.out.println("max score: 100");
          System.out.println(selection);
				  long stop = System.currentTimeMillis();
		      notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		      return selection;
				}
			}
		}
  		System.out.println("max score:");
  		System.out.println(score);
  		System.out.println(selection);
  	  long stop = System.currentTimeMillis();

	    notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
	    return selection;
	}

	private int nextScore(MachineState currentState, Move move) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException
	{
	  StateMachine theMachine = getStateMachine();
    List<List<Move>> list_moves = theMachine.getLegalJointMoves(currentState, getRole(), move);

    int lowest = Integer.MAX_VALUE;

    for (List<Move> moves: list_moves){
      MachineState nextState = theMachine.getNextState(currentState, moves);
      if(theMachine.isTerminal(nextState)) {
        int terminal = theMachine.getGoal(nextState, getRole());
//        if(terminal == 0){
//          return 0;
//        }
        lowest = Math.min(lowest, terminal);
      }
      else {
        List<Move> nextMoves = theMachine.getLegalMoves(nextState, getRole());
        int highest = Integer.MIN_VALUE;

        for (Move nextMove: nextMoves){
          int result = nextScore(nextState, nextMove);
//          if(result == 100){
//            highest = result;
//            break;
//          }
          highest = Math.max(highest, result);
        }
        lowest = Math.min(lowest, highest);
      }
    }
    return lowest;
  }
}