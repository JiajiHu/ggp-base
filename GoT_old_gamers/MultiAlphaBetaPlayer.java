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

public class MultiAlphaBetaPlayer extends StateMachineGamer
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
		int alpha = Integer.MIN_VALUE;
		int beta = Integer.MAX_VALUE;

		for(Move move: moves)
		{
			if(System.currentTimeMillis() > finishBy){
				long stop = System.currentTimeMillis();
				notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
				return selection;
			}

			int result = minScore(currentState, move, alpha, beta);
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

	private int minScore(MachineState currentState, Move move, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException
	{
		StateMachine theMachine = getStateMachine();
		List<List<Move>> list_moves = theMachine.getLegalJointMoves(currentState, getRole(), move);

		for (List<Move> moves: list_moves){
			MachineState nextState = theMachine.getNextState(currentState, moves);
			if(theMachine.isTerminal(nextState)) {
				int terminal = theMachine.getGoal(nextState, getRole());
				beta = Math.min(beta, terminal);
				if(terminal == 0)
					return 0;
				if (beta <= alpha)
					return alpha;
			}
			else {
				int highest = maxScore(nextState, alpha, beta);
				beta = Math.min(beta, highest);
				if(highest == 0)
					return 0;
				if (beta <= alpha)
					return alpha;
			}
		}
		return beta;
	}

	private int maxScore(MachineState nextState, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		StateMachine theMachine = getStateMachine();
		List<Move> nextMoves = theMachine.getLegalMoves(nextState, getRole());

		for (Move nextMove: nextMoves){
			int result = minScore(nextState, nextMove, alpha, beta);
			alpha = Math.max(alpha, result);
			if(result == 100)
				return 100;
			if (alpha >= beta)
				return beta;
		}
		return alpha;
	}
}