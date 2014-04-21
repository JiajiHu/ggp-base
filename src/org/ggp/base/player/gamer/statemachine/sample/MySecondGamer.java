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

/**
   MySecondPlayer is a simple Minimax Gamer

 */

public class MySecondGamer extends StateMachineGamer
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

	  while(true) {
	    currentState = rootState;

	    boolean isTerminal  = stateMachine.isTerminal(currentState);
	    while(!isTerminal) {
	      currentState = stateMachine.getRandomNextState(currentState);
	      isTerminal = stateMachine.isTerminal(currentState);
	      numStatesExplored++;
	    }

	    if(System.currentTimeMillis() > finishBy)
	      break;
	  }

	  System.out.println("MetaGaming done, Number of states explored: "+ numStatesExplored);

	}

	@Override
  public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
  {
    // We get the current start time
    long start = System.currentTimeMillis();

    /**
     * This is from the LegalGamer
     * We put in memory the list of legal moves from the
     * current state. The goal of every stateMachineSelectMove()
     * is to return one of these moves. The choice of which
     * Move to play is the goal of GGP.
     */
    List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());

    // A legal gamer would blindly choose the first move. Let's make it a random gamer
    Random random = new Random();
    Move selection = moves.get(random.nextInt(moves.size()));

    // We get the end time
    // It is mandatory that stop<timeout
    long stop = System.currentTimeMillis();

    notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
    return selection;
  }

	public Move stateMachineSmartSelect(long timeout, String type) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {

	  List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
	  StateMachine stateMachine = getStateMachine();
	  MachineState currentState = getCurrentState();
    Move selection = null;
    double candidate_val = 0;

    for (Move move: moves){
      List<List<Move>> jointMoves = stateMachine.getLegalJointMoves(currentState, getRole(), move);
      if (type == "random"){
        int count = 0;
        double total = 0;
        for (List<Move> jointMove: jointMoves){
          count++;
          total = total + getUtility(stateMachine.getNextState(currentState, jointMove));
        }
        if (total/count>candidate_val){
          candidate_val = total/count;
          selection = move;
        }
      }
      else if (type == "mini"){
        double cur_min = 100;
        for (List<Move> jointMove: jointMoves){
          double this_min = getUtility(stateMachine.getNextState(currentState,jointMove));
          if (this_min < cur_min){
            cur_min = this_min;
          }
        }
        if (cur_min > candidate_val){
          candidate_val = cur_min;
          selection = move;
        }
      }
    }

    // if selection is null for some reason, random select a legal move
    if (selection == null){
      Random random = new Random();
      selection = moves.get(random.nextInt(moves.size()));
    }
    return selection;
	}

	public double getUtility(MachineState state) throws GoalDefinitionException {
	  StateMachine stateMachine = getStateMachine();
    if (stateMachine.isTerminal(state)){
      return stateMachine.getGoal(state, getRole());
    }
    else {
      String FUUCKK = "FUUCKK";
//      return getUtility(getStateMachine().getNextState(state, FUUCKK));
    }
    return 0;
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

