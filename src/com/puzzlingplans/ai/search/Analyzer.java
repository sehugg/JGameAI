package com.puzzlingplans.ai.search;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.RandomDecider;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.FastHash;
import com.puzzlingplans.ai.util.MiscUtils;

public class Analyzer extends Simulator<LevelInfo>
{
	RandomDecider rnd;
	Map<Long,Analyzer.Entry> trailEntries = new HashMap<Long,Analyzer.Entry>();
	Map<String,ChoiceInfo> choiceClasses = new TreeMap<String,ChoiceInfo>();
	FastHash<Analyzer.Entry> fastHash = new FastHash<Analyzer.Entry>(22, 60);
	
	public long totalChoicesInspected;
	public int totalChoicesRevisited;
	public int totalPotentialActions;
	
	public long totalActionsTried;
	public long totalActionsSucceeded;
	public long totalCollisionsFound;
	
	//
	
	static class Entry
	{
		GameState<?> state;
		long mask;
	}
	
	static class ChoiceInfo
	{
		Class choiceClass;
		long numVisits;
		public int key;
		public int numPotentialActions;
		public int numResults;
		public int numResultsOk;
	}

	public Analyzer(GameState<?> initialState, int maxLevel)
	{
		super(initialState, maxLevel, 0);
		rnd = new RandomDecider(seekingPlayer)
		{
			@Override
			public MoveResult choose(Choice choice) throws MoveFailedException
			{
				inspectChoice(choice);
				return super.choose(choice);
			}
			@Override
			protected MoveResult tryChoice(Choice choice, int action) throws MoveFailedException
			{
 				totalActionsTried++;
				MoveResult result = Analyzer.this.tryChoice(choice, action);
				recordChoiceResult(choice, action, result);
				if (result == MoveResult.Ok)
					totalActionsSucceeded++;
				return result;
			}
		};
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		return rnd.choose(choice);
	}

	private void inspectChoice(Choice choice)
	{
		long mask = choice.getPotentialMoves();
		int choicekey = choice.key();
		int numActions = BitUtils.countBits(mask);
		
		long key = currentTrail;
		Analyzer.Entry existing = trailEntries.get(key);
		if (existing == null)
		{
			Analyzer.Entry newEntry = new Entry();
			newEntry.mask = mask;
			// TODO: flag? newEntry.state = currentState.copy();
			trailEntries.put(key, newEntry);
			existing = newEntry;
		}
		else
		{
			if (existing.mask != mask)
			{
				prdebug(MiscUtils.format("TRAIL CONFLICT trail=%x mask=%x vs %x", key, existing.mask, mask));
				if (existing.state != null)
					existing.state.dump();
				currentState.dump();
				totalCollisionsFound++;
			}
			existing.state = currentState;
			existing.mask = mask;
			totalChoicesRevisited++;
		}
		totalChoicesInspected++;
		totalPotentialActions += numActions;

		long key2 = currentTrail ^ choicekey ^ mask;
		Entry hashEntry = fastHash.getEntryAt(key, key2);
		if (hashEntry != null)
		{
			if (hashEntry.mask != mask)
			{
				prdebug(MiscUtils.format("HASH CONFLICT key=%x mask=%x vs %x", key2, hashEntry.mask, mask));
				if (existing.state != null)
					existing.state.dump();
				currentState.dump();
				totalCollisionsFound++;
			}
		} else {
			fastHash.insertEntry(key, key2, existing);
		}

		ChoiceInfo choiceInfo = lookupChoiceInfo(choice);
		if (choiceInfo.key != choice.key())
		{
			prdebug(MiscUtils.format("INVALID CHOICE KEY %x != %x", choiceInfo.key, choice.key()));
		}

		choiceInfo.numVisits++;
		choiceInfo.numPotentialActions += numActions;
	}

	private ChoiceInfo lookupChoiceInfo(Choice choice)
	{
		String choiceClassName = choice.getClass().getName();
		ChoiceInfo choiceInfo = choiceClasses.get(choiceClassName);
		if (choiceInfo == null)
		{
			choiceInfo = new ChoiceInfo();
			choiceInfo.choiceClass = choice.getClass();
			choiceInfo.key = choice.key();
			choiceClasses.put(choiceClassName, choiceInfo);
		}
		return choiceInfo;
	}

	protected void recordChoiceResult(Choice choice, int action, MoveResult result)
	{
		ChoiceInfo ci = lookupChoiceInfo(choice);
		ci.numResults++;
		if (result == MoveResult.Ok)
			ci.numResultsOk++;
		// TODO: check probability
	}

	private void prdebug(String string)
	{
		System.out.println(MiscUtils.format("[%d:%d] %s", iterCount, currentLevel, string));
	}

	private void inspectChoice2(Choice choice)
	{
		if (!trailEntries.containsKey(currentTrail))
		{
			trailEntries.put(currentTrail, null);
		}
		else
		{
			long mask = choice.getPotentialMoves();
			Analyzer.Entry existing = trailEntries.get(currentTrail);
			if (existing != null && existing.mask != mask)
			{
				System.out.println(MiscUtils.format("TRAIL CONFLICT @ level %d: trail=%x mask=%x vs %x",
						currentLevel, currentTrail, existing.mask, mask));
				existing.state.dump();
				currentState.dump();
			}
			Analyzer.Entry newEntry = new Entry();
			newEntry.state = currentState;
			newEntry.mask = mask;
			trailEntries.put(currentTrail, newEntry);
		}
	}

	@Override
	public void dump()
	{
		System.out.println(MiscUtils.format("%d key entries", trailEntries.size()));
		System.out.println(MiscUtils.format("%d choices, %3.1f%% revisits, %d/%d succeeded (%3.1f%%), %3.1f actions/choice",
				totalChoicesInspected,
				totalChoicesRevisited * 100f / totalChoicesInspected,
				totalActionsSucceeded, totalActionsTried, totalActionsSucceeded * 100.0f / totalActionsTried,
				totalPotentialActions * 1.0f / totalChoicesInspected));
		for (ChoiceInfo ci : choiceClasses.values())
		{
			System.out.println(MiscUtils.format("  %s: %d visits (%3.1f%%) %3.1f avg actions, %3.1f%% ok", ci.choiceClass.getName(),
					ci.numVisits,
					ci.numVisits * 100f / totalChoicesInspected,
					ci.numPotentialActions * 1f / ci.numVisits,
					ci.numResultsOk * 100f / ci.numResults));
		}
	}
}
