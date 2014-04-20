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
   MyFirstGamer is a Random Gamer

 */

public class MyFirstGamer extends StateMachineGamer
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

