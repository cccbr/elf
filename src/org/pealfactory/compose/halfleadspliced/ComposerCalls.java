package org.pealfactory.compose.halfleadspliced;

/**
 * Extends the base Composer class to provide an inner doCompose() search loop
 * for the half-lead with-calls search.
 *
 * @author MBD
 * @since Tinuviel
 */
public class ComposerCalls extends Composer
{
	ComposerCalls(Tables tables, int leadsPerPart, int nParts, boolean tenorsTogether, boolean nicePartEnds, boolean ATW, int calls)
	{
		super(tables, leadsPerPart, nParts, tenorsTogether, nicePartEnds, ATW);
		setAllowCalls(calls);
	}

	/**
	 * HL, calls
	 */
	void doCompose()
	{
		RowNode start = fRounds;
		int minCOMconstant = fMinCOM+1-2*fLeadsPerPart;
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
					int index = fMethodIndices[i];
					reduceMethodCounts(f1stHalfMethodIndices[index], f2ndHalfMethodIndices[index]);
				} while (i>j);
			}
			else
			{
				if (++fCounter>=kCHECK_FREQ)
					if (checkStats())
						break;

				// Not reached part end - generate new lead
				int index = fMethodIndices[i];
				int one = f1stHalfMethodIndices[index];
				int two = f2ndHalfMethodIndices[index];
				if (f1stHalfMethodCounts[one]>=fMethodRepeatLimit)
				{
					fMethodIndices[i]+= fNMethods-two-1;
					fCalls[i] = fAllowCalls;
				}
				else if (f2ndHalfMethodCounts[two]<fMethodRepeatLimit)
				{
					if (++f1stHalfMethodCounts[one] >= fMethodRepeatLimit)
						fN1stHalfAtMaxRepeat++;
					if (fN1stHalfAtMaxRepeat>fMaxMethodsAtRepeatLimit)
					{
						f1stHalfMethodCounts[one]--;
						fN1stHalfAtMaxRepeat--;
						fMethodIndices[i]+= fNMethods-two-1;
						fCalls[i] = fAllowCalls;
					}
					else
					{
						if (++f2ndHalfMethodCounts[two] >= fMethodRepeatLimit)
							fN2ndHalfAtMaxRepeat++;
						if (fN2ndHalfAtMaxRepeat>fMaxMethodsAtRepeatLimit)
						{
							f2ndHalfMethodCounts[two]--;
							fN2ndHalfAtMaxRepeat--;
							fCalls[i] = fAllowCalls;
						}
						else
						{
							RowNode next = fComp.setLead(i, start, fCompositeMethods[index], fCalls[i]);
							int leadNum = next.getLeadheadNumber();
							if (!fTruthTable[leadNum] && (!fTenorsTogether || next.isTenorsTogether()))
							{
								// Prune branches where min COM not achievable.
								if (fComp.getCOM(i) >= 2*i + minCOMconstant)
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
									reduceMethodCounts(one, two);
									// If 1st & 2nd methods different, min COM must have been
									// violated by 1st method (compared to previous lead)
									if (one!=two)
										fMethodIndices[i]+= fNMethods-two-1;
									fCalls[i] = fAllowCalls;
								}
							}
							else
								reduceMethodCounts(one, two);
						}
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
	 */
	int backtrack(int i)
	{
		fCalls[i]++;
		if (fCalls[i]>fAllowCalls)
		{
			fCalls[i] = 0;
			fMethodIndices[i]++;
			if (fMethodIndices[i]>=fNCompMethods)
			{
				fMethodIndices[i] = 0;
				if (i==0)
					return -1;
				i--;
				int leadNum = fComp.getLead(i).getLastRow().getLeadheadNumber();
				fTruthTable[leadNum] = false;
				int index = fMethodIndices[i];
				reduceMethodCounts(f1stHalfMethodIndices[index], f2ndHalfMethodIndices[index]);
				i = backtrack(i);
			}
		}
		return i;
	}

}
