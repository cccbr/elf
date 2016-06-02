package org.pealfactory.compose.halfleadspliced;

import org.pealfactory.bronze.*;
import org.pealfactory.ring.*;

import java.util.*;

/**
 * Calculates and holds tables of permutations and nodes - vital to the
 * performance of Elf. There are five stages of table building which must
 * all be performed before a search can begin:
 * <ol>
 * <li>buildNodeTable() - this is performed once only, as it does not depend
 * on choice of methods and music. It creates the initial table of 40320
 * RowNodes (note this is hardwired to 8 bells). Leadhead and tenors-together
 * RowNodes are also identified during this process, and referenced by separate
 * tables.
 * <li>prepareMusic() - must be performed if the music definition have changed.
 * For each RowNode, it calculates a single music score value.
 * <li>prepareMethods() - must be performed if the methods have changed, even if
 * only the order has been changed. For half-lead spliced searches, it creates the
 * table of composite methods (a composite method has its first half-lead from
 * one method and the second half-lead from another). It then populates the table of
 * RowNodes with permutation links for all place notations and leadhead permutations
 * in the table of methods.
 * <li>prepareLeadMusic() - must be performed if either the methods OR the music
 * have changed. It calculates the tables of "lead music" for leadhead RowNodes.
 * These tables are held in the appropriate RowNode and contain music counts for
 * an entire lead of each method.
 * <li>prepareRegenPtrs() - must be performed before every search. Calculates
 * regeneration pointers for the rotationally-sorted search.
 * </ol>
 *
 * Each of these table-build jobs can be monitored using the {@link Tracker}
 * task-management system, except for the last one, which is too quick to bother with.
 *
 * @author MBD
 */
class Tables extends Tracker
{
	private int fNBells;
	private Method[] fMethods;
	private Method[] fCompositeMethods;
	private Music[] fMusic;
	private Hashtable fAllNodes;
	private int fNextNodeNumber = 0;
	private RowNode[] fTenorsTogetherLeads;
	private int fNextTTLeadNumber = 0;
	private RowNode[] fLeadheadNodes;
	private int fNextLeadHeadNumber = 0;
	private boolean fMethodsDirty = true;
	private boolean fMusicDirty = true;
	private boolean fLeadMusicDirty = true;
	/** Set to true when tables are built (one-time only - not populated */
	private boolean fTablePass1Done = false;

	/** All permuatations stored in these Vectors are Strings, but
	 *  with bell numbers stored as bytes 1..nbells. */
	private Vector fPNPerms = new Vector();
	private Vector fLeadheadPerms = new Vector();


	public Tables()
	{
		super(100);
		fNBells = Composer.kNBELLS;
		fMusic = Music.kDEFAULT;
	}

	/**
	 * Final for speed
	 */
	public final int getNBells()
	{
		return fNBells;
	}

	/**
	 * Final for speed
	 */
	public final int getNNodes()
	{
		return fNextNodeNumber;
	}

	/**
	 * Final for speed
	 */
	public final int getNLeadheadNodes()
	{
		return fNextLeadHeadNumber;
	}

	/**
	 * Final for speed
	 */
	public final RowNode getNode(Row row)
	{
		Object o = fAllNodes.get(row);
    if (o!=null)
			return (RowNode)o;
		return null;
	}

	/**
	 * Returns true when pass 1 of table building done.
	 */
	public boolean isBuilt()
	{
		return fTablePass1Done;
	}

	public final int getNMethods()
	{
		return fMethods.length;
	}

	public Method[] getMethods()
	{
		return fMethods;
	}

	public final int getNCompositeMethods()
	{
		return fCompositeMethods.length;
	}

	public Method[] getCompositeMethods()
	{
		return fCompositeMethods;
	}

	public void setMethods(Method[] methods)
	{
		fMethods = methods;
		fMethodsDirty = true;
		fLeadMusicDirty = true;
	}

	public void setMusic(Music[] music)
	{
		fMusic = music;
		fMusicDirty = true;
		fLeadMusicDirty = true;
	}

	protected int newNodeNumber()
	{
		return fNextNodeNumber++;
	}

	protected String[] getPNPerms()
	{
		String[] perms = new String[fPNPerms.size()];
		fPNPerms.copyInto(perms);
		return perms;
	}

	protected int getNPnPerms()
	{
		return fPNPerms.size();
	}

	protected String[] getLeadheadPerms()
	{
		String[] perms = new String[fLeadheadPerms.size()];
		fLeadheadPerms.copyInto(perms);
		return perms;
	}

	protected int addPNPerm(byte[] perm)
	{
		String p = new String(perm);
		int i = fPNPerms.indexOf(p);
		if (i<0)
		{
			i = fPNPerms.size();
			fPNPerms.addElement(p);
		}
		return i;
	}

	protected int addLeadheadPerm(byte[] perm)
	{
		String p = new String(perm);
		int i = fLeadheadPerms.indexOf(p);
		if (i<0)
		{
			i = fLeadheadPerms.size();
			fLeadheadPerms.addElement(p);
		}
		return i;
	}

	/**
	 * Table build pass 1 - not dependent on methods or music, so we
	 * can do it once only.
	 * NB also does music preparation for default music.
	 * @todo Major only.
	 */
	public void buildNodeTable()
	{
		setTotalDuration(40320);
		setProgress(0);
		setJobName("Building node table");
		if (fAllNodes==null)
		{
			long time = System.currentTimeMillis();
			fAllNodes = new Hashtable();
			fTenorsTogetherLeads = new RowNode[120*fNBells];
			fLeadheadNodes = new RowNode[5040];
			generateNodes(new Row(fNBells), 1);
			if (isAborted())
			{
				fAllNodes = null;
			}
			else
			{
				//System.out.println("Nodes: "+fAllNodes.size());
				System.out.println("Table build took: "+((System.currentTimeMillis()-time)/1000)+"s");
				fTablePass1Done = true;
				fMusicDirty = false;
			}
		}
	}

	private void generateNodes(Row row, int n)
	{
		if (isAborted())
			return;
		if (n>=fNBells)
		{
			RowNode node = new RowNode(row, this);
			node.calcMusicScore(fMusic);
			// !!! Very important that we clone the Row before using it
			// 		 as the hashtable key !!!
			fAllNodes.put(row.clone(), node);
			// Keep a separate list of all leadhead and tenors-together nodes
			if (node.isLeadhead())
			{
        node.setLeadheadNumber(fNextLeadHeadNumber);
				fLeadheadNodes[fNextLeadHeadNumber++] = node;
				if (node.isTenorsTogether())
					fTenorsTogetherLeads[fNextTTLeadNumber++] = node;
			}

			setProgress(fAllNodes.size());
		}
		else
		{
			generateNodes(row, n+1);
			for (int i=n+1; i<=fNBells; i++)
			{
				row.swap(n, i);
				generateNodes(row, n+1);
				row.swap(n, i);
			}
		}
	}

	/**
	 * Music calculation (per row, not the lead-music tables).
	 * Must be done every time the music defs are changed.
	 */
	public void prepareMusic()
	{
		setJobName("Preparing music");
		setProgress(0);
		setTotalDuration(getNNodes());
		if (fMusicDirty)
		{
			int p = 0;
			Enumeration e = fAllNodes.elements();
			while (e.hasMoreElements())
			{
				if (isAborted())
					return;
				RowNode node = (RowNode)e.nextElement();
				node.calcMusicScore(fMusic);
				setProgress(++p);
			}

			fMusicDirty = false;
		}
		setProgress(getNNodes());
	}

	/**
	 * Table build pass 2
	 */
	public void prepareMethods()
	{
		setJobName("Preparing methods");
		setProgress(0);
		if (fMethodsDirty)
		{
			int n = fMethods.length;
			fCompositeMethods = new Method[n*n];
			for (int i=0; i<n; i++)
			{
				fMethods[i].setMethodIndex(i);
				//fMethods[i].calcPerms(this);
			}
			for (int i=0; i<n; i++)
			{
				int k = i*n;
				for (int j=0; j<n; j++)
				{
					fCompositeMethods[k+j] = new Method(fMethods[i], fMethods[j], k+j);
					fCompositeMethods[k+j].calcPerms(this);
				}
			}
			//for (int i=0; i<n; i++)
			//	fMethods[i].updateLeadPerms(this);
			for (int i=0; i<fCompositeMethods.length; i++)
				fCompositeMethods[i].updateLeadPerms(this);

			if (populateNodeTable())
			{
				// Progress has already reached 100% by the time we get here, but
				// this shouldn't take long, so not a problem.
				for (int i=0; i<fNextTTLeadNumber; i++)
					fTenorsTogetherLeads[i].calcLeadsToTenorsHome(fCompositeMethods[0]);
				fMethodsDirty = false;
			}
		}
	}

	/**
	 * This MUST be called ANY TIME the method list is changed.
	 * Amongst other things, this is essential in order to clear
	 * out the node-music tables. Because some tables use method index
	 * numbers, they are dependent on the order of the methods!
	 */
	private boolean populateNodeTable()
	{
		setTotalDuration(getNNodes());
		setProgress(0);

		long time = System.currentTimeMillis();
		int p = 0;
		byte[][] pnPerms = toPermutationArray(getPNPerms());
		byte[][] leadheadPerms = toPermutationArray(getLeadheadPerms());
		Enumeration e = fAllNodes.elements();
		while (e.hasMoreElements())
		{
			if (isAborted())
				return false;
			RowNode node = (RowNode)e.nextElement();
			// Also clears node-music tables.
			if (!node.calcPermLinks(this, pnPerms, leadheadPerms))
			{
				setErrorMsg("FATAL ERROR building node table");
				return false;
			}
			setProgress(++p);
		}
		System.out.println("Table populate took: "+((System.currentTimeMillis()-time)/1000)+"s");
		return true;
	}

	private final byte[][] toPermutationArray(String[] perms)
	{
		int n = perms.length;
		byte[][] ret = new byte[n][];
		for (int i=0; i<n; i++)
			ret[i] = perms[i].getBytes();
		return ret;
	}

	/**
	 * Table build pass 3 - precalculates the lead music counts.
	 * Must be called whenever the music OR the method tables are changed (even reordered).
	 */
	public void prepareLeadMusic()
	{
		setJobName("Building Tables");
		setTotalDuration(fCompositeMethods.length);
		setProgress(0);
		if (fLeadMusicDirty)
		{
			long time = System.currentTimeMillis();
			// Only build lead-music tables for composite methods - the original
			// "single" methods are not used for this in the search.
			for (int m=0; m<fCompositeMethods.length; m++)
			{
				Method method = fCompositeMethods[m];
				for (int i=0; i<fNextLeadHeadNumber; i++)
					fLeadheadNodes[i].calcLeadMusic(method);
				setProgress(m+1);
			}
			fLeadMusicDirty = false;
			System.out.println("Lead-music calc took: "+((System.currentTimeMillis()-time)/1000)+"s");
		}
	}

	/**
	 * Table build pass 4 - sets up the regen backtrack offsets in tenors-together
	 * nodes. Note that the regen offset is used to reset the regen pointer when
	 * backtracking; normally it is set to 0.
	 * This must be called before every search, but is very quick (<1ms).
	 */
	public void prepareRegenPtrs(boolean tenorsTogether)
	{
		for (int i=0; i<fNextTTLeadNumber; i++)
			fTenorsTogetherLeads[i].setRegenOffset(tenorsTogether);
	}
}
