package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.List;

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
 * SampleGamer is a simplified version of the StateMachineGamer, dropping some
 * advanced functionality so the example gamers can be presented concisely.
 * This class implements 7 of the 8 core functions that need to be implemented
 * for any gamer.
 *
 * If you want to quickly create a gamer of your own, extend this class and
 * add the last core function : public Move stateMachineSelectMove(long timeout)
 */

public class DeliberationPlayer extends StateMachineGamer
{
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// do nothing
	}



	/** This will currently return "SampleGamer"
	 * If you are working on : public abstract class MyGamer extends SampleGamer
	 * Then this function would return "MyGamer"
	 */
	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	// This is the default State Machine
	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	// This is the defaul Sample Panel
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
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {

		StateMachine theMachine = getStateMachine();
		long start = System.currentTimeMillis();

		MachineState rootState = getCurrentState();
		List<Move> moves = theMachine.getLegalMoves(rootState, getRole());

		System.out.println(theMachine.getRoles());
		Move selection = null;

		int score = Integer.MIN_VALUE;

		for(Move move: moves)
		{
			List<Move> jointMoves = new ArrayList<Move>();
			jointMoves.add(move);
			MachineState nextState = theMachine.getNextState(rootState, theMachine.getRandomJointMove(getCurrentState(), getRole(), move));//.getRandomNextState(rootState);
			int result = maxScore(nextState);
			if(result > score)
			{
				score = result;
				selection = move;
			}
		}
		//System.out.println(score);
	    long stop = System.currentTimeMillis();

	    notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
	    return selection;
	}

	private int maxScore(MachineState currentState) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
	{
		StateMachine theMachine = getStateMachine();
		if(theMachine.isTerminal(currentState))
		{
			return theMachine.getGoal(currentState, getRole());
		}
		int score = Integer.MIN_VALUE;
		List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
		//System.out.println(moves);
		for(Move move: moves)
		{
			List<Move> jointMoves = new ArrayList<Move>();
			jointMoves.add(move);
			MachineState nextState = theMachine.getNextState(currentState, theMachine.getRandomJointMove(getCurrentState(), getRole(), move));//.getRandomNextState(rootState);
			int result = maxScore(nextState);
			if(result > score)
			{
				score = result;
			}
		}
		return score;

	}
}