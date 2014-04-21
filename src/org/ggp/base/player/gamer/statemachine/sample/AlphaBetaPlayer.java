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
import org.ggp.base.util.statemachine.Role;
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

public class AlphaBetaPlayer extends StateMachineGamer
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
    while(true) {
      currentState = rootState;
      numRuns++;
      boolean isTerminal  = stateMachine.isTerminal(currentState);
      while(!isTerminal) {
        currentState = stateMachine.getRandomNextState(currentState);
        isTerminal = stateMachine.isTerminal(currentState);
        numStatesExplored++;
      }

      if(System.currentTimeMillis() > finishBy)
        break;
    }

    System.out.println("MetaGaming done, Number of runs: "+ numRuns);
    System.out.println("MetaGaming done, Number of states explored: "+ numStatesExplored);

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
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {

		StateMachine theMachine = getStateMachine();
		long start = System.currentTimeMillis();
    long finishBy = timeout - 1000;


		MachineState rootState = getCurrentState();
		List<Move> moves = theMachine.getLegalMoves(rootState, getRole());
		Random random = new Random();
    Move selection = moves.get(random.nextInt(moves.size()));
    System.out.println(moves);

		if (moves.size() == 1){
		  long stop = System.currentTimeMillis();
      notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
      return selection;
		}

		int score = Integer.MIN_VALUE;
		int []alpha = new int[1];
		int []beta = new int[1];

		alpha[0] = Integer.MIN_VALUE;
		beta[0] = Integer.MAX_VALUE;

		for(Move move: moves)
		{
		  if(System.currentTimeMillis() > finishBy){
		    long stop = System.currentTimeMillis();
	      notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
	      return selection;
		  }

		  MachineState nextState = theMachine.getNextState(rootState, theMachine.getRandomJointMove(getCurrentState(), getRole(), move));
			int result = minScore(nextState, alpha, beta);
			if(result > score)
			{
				score = result;
				selection = move;
				if (score == 100){
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

	private int minScore(MachineState currentState, int[] alpha, int[] beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException
	{
		StateMachine theMachine = getStateMachine();
		if(theMachine.isTerminal(currentState))
		{
			return theMachine.getGoal(currentState, getRole());
		}
		List<Role> roles = theMachine.getRoles();
		//assuming two-player game
		for(Role role: roles)
		{
			//myself
			if(role.equals(getRole()))
			{
				continue;
			}

			List<Move> moves = theMachine.getLegalMoves(currentState, role);
			//System.out.println(moves);
			for(Move move: moves)
			{
				MachineState nextState = theMachine.getNextState(currentState, theMachine.getRandomJointMove(currentState, role, move));
				int result = maxScore(nextState, alpha, beta);
				if(result == 0){
				  return 0;
				}
				alpha[0] = Math.max(alpha[0], result);
				if (alpha[0] <= beta[0]) return alpha[0];
			}
		}
		return beta[0];
	}

	private int maxScore(MachineState currentState, int[] alpha, int[] beta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
	{
		StateMachine theMachine = getStateMachine();
		if(theMachine.isTerminal(currentState))
		{
			return theMachine.getGoal(currentState, getRole());
		}
		List<Move> moves = theMachine.getLegalMoves(currentState, getRole());
		//System.out.println(moves);
		for(Move move: moves)
		{
			MachineState nextState = theMachine.getNextState(currentState, theMachine.getRandomJointMove(currentState, getRole(), move));
			int result = minScore(nextState, alpha, beta);
			if (result == 100){
			  return 100;
			}
			alpha[0] = Math.max(alpha[0], result);
			if (alpha[0] > beta[0]) return beta[0];
		}
		return alpha[0];
	}
}