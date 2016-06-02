package org.pealfactory.compose.halfleadspliced;

/**
 * Extends the base Composer class to provide an inner doCompose() search loop
 * for the leadhead-only with-calls search.
 *
 * @author MBD
 * @since Tinuviel
 */
public class ComposerLH extends Composer
{
	ComposerLH(Tables tables, int leadsPerPart, int nParts, boolean tenorsTogether, boolean nicePartEnds, boolean ATW, int calls)
	{
		super(tables, leadsPerPart, nParts, tenorsTogether, nicePartEnds, ATW);
		setAllowCalls(Math.max(calls, 1));
		fLHSpliced = true;
	}

	/**
	 * This one does does leadhead changes only (plus calls)
	 */
	void doCompose()
	{
		// Create LHOnly method table indexing methods 0..n into Composite table
		// 0, n, 2n, ... n(n-1).
		fLHOnlyMethods = new Method[fNMethods];
		for (int j=0; j<fNMethods; j++)
			fLHOnlyMethods[j] = fCompositeMethods[j*fNMethods+j];

		RowNode start = fRounds;
		int minCOMconstant = fMinCOM-fLeadsPerPart;
		int i = 0;

		while (true)
		{
			if (i>=fLeadsPerPart)
			{
				// Reached end of part - check if we have a good composition.
				int regenMod = fRegenPtr-1;
				int j = fLeadsPerPart - 1;
				if (isLengthGood() && (regenMod<=0 || regenMod*2>=fLeadsPerPart) && fComp.getNParts()==fNParts)
					j = checkComp();
				do
				{
					i--;
					int leadNum = fComp.getLead(i).getLastRow().getLeadheadNumber();
					fTruthTable[leadNum] = false;
					reduceMethodCounts(i);
				} while (i>j);
			}

			else
			{
				if (++fCounter>=kCHECK_FREQ)
					if (checkStats())
						break;

				// Not reached part end - generate new lead
				int one = fMethodIndices[i];
				if (f1stHalfMethodCounts[one]>=fMethodRepeatLimit)
					fCalls[i] = fAllowCalls;
				else
				{
					if (++f1stHalfMethodCounts[one] >= fMethodRepeatLimit)
						fN1stHalfAtMaxRepeat++;
					if (fN1stHalfAtMaxRepeat>fMaxMethodsAtRepeatLimit)
					{
						f1stHalfMethodCounts[one]--;
						fN1stHalfAtMaxRepeat--;
						fCalls[i] = fAllowCalls;
					}
					else
					{
							RowNode next = fComp.setLead(i, start, fLHOnlyMethods[one], fCalls[i]);
							int leadNum = next.getLeadheadNumber();
							if (!fTruthTable[leadNum] && (!fTenorsTogether || next.isTenorsTogether()))
							{
								// Prune branches where min COM not achievable.
								if (fComp.getCOM(i) >= i + minCOMconstant)
								{
									start = next;
									fTruthTable[leadNum] = true;
									i++;
									if (fRegenPtr<0)
									{
										if (fRegenPtr<-100)
										{
											fRegenPtr = start.getRegenOffset();
											if (fRegenPtr>=0)
											{
												fMethodIndices[i] = fMethodIndices[fRegenPtr];
												fCalls[i] = fCalls[fRegenPtr];
											}
										}
									}
									else
									{
										fMethodIndices[i] = fMethodIndices[fRegenPtr];
										fCalls[i] = fCalls[fRegenPtr];
									}
									fRegenPtr++;
									continue;
								}
								else
								{
									fCalls[i] = fAllowCalls;
									reduceMethodCounts(i);
								}
							}
							else
								reduceMethodCounts(i);
					}
				}
			}
			i = backtrack(i);
			if (i>0)
				start = fComp.getLead(i-1).getLastRow();
			else if (i==0)
				start = fRounds;
			else
				break;
			fRegenPtr = -1000;
		}
	}

	/**
	 * LH only!
	 */
	int backtrack(int i)
	{
		fCalls[i]++;
		if (fCalls[i]>fAllowCalls)
		{
			// Tinuviel-C bugfix 2.11.2009.
			// Can't see there's any point starting a search with a method other than method 0;
			// if we did, the rotational composing loop should in theory never be able to include method 0.
			// (In practice it produces trivial rotations).
			if (i==0)
				return -1;
			fCalls[i] = 0;
			fMethodIndices[i]++;
			if (fMethodIndices[i]>=fNCompMethods)
			{
				fMethodIndices[i] = 0;
				// Here's where the test for the end used to be.
				i--;
				int leadNum = fComp.getLead(i).getLastRow().getLeadheadNumber();
				fTruthTable[leadNum] = false;
				reduceMethodCounts(i);
				i = backtrack(i);
			}
		}
		return i;
	}

	/**
	 * LH search only!
	 */
	void reduceMethodCounts(int i)
	{
		if (f1stHalfMethodCounts[fMethodIndices[i]]-- >= fMethodRepeatLimit)
			fN1stHalfAtMaxRepeat--;
	}

	/**
	 * This one for LH-only search!
	 * 100 = perfect balance of all methods in the array
	 * 0 = one or more missing
	 */
	int calcMethodBalance()
	{
		int balance = calcMethodDistribution(f1stHalfMethodCounts, (double)fLeadsPerPart);

		if (balance>0)
		{
			int minRep = 1 + fLeadsPerPart/fNMethods;
			int unbalance = calcUnbalance(f1stHalfMethodCounts, minRep);
			fComp.setBalance(balance, unbalance);
		}
		return balance;
	}


}
