package org.pealfactory.compose.halfleadspliced;

import java.io.*;
import java.util.*;

/**
 * An application version of Elf; not packaged with the applet.
 *
 * @author MBD
 */
public class ComposerApp implements ComposerHost, StandardMethods, Runnable
{
	private static Tables gTables;

	private Composer fComposer;
	private PrintWriter fOut;
	private Vector fBestComps = new Vector();

	public static void main(String[] args)
	{
		gTables = new Tables();
		gTables.buildNodeTable();

		ComposerApp app = new ComposerApp();
		app.testCompose();
	}

	public void testCompose()
	{
		//Method gloucester = new Method("Gloucester", "x34x14x58x36x12x58x34x18 l18");

		int minCOM = 5, minScore = 1, minBalance = 0;
		Method[] m = {kCAMBRIDGE, kYORKSHIRE, kLINCOLNSHIRE, kSUPERLATIVE, kUXBRIDGE};

		//int minCOM = 0, minScore = 0,  minBalance = 1;
		//Method[] m = {kBRISTOL, gloucester};

		//int minCOM = 8, minScore = 1, minBalance = 100;
		//Method[] m = {kCAMBRIDGE, kYORKSHIRE, kLINCOLNSHIRE, kSUPERLATIVE, kBRISTOL, kLONDON, kPUDSEY, kRUTLAND};

		//int minCOM = 16, minScore = 1, minBalance = 100;
		//Method[] m = {kCAMBRIDGE, kYORKSHIRE, kBRISTOL, kSUPERLATIVE, kLINCOLNSHIRE, kPUDSEY, kRUTLAND, kLONDON};

		gTables.setMethods(m);

		Composer c = Composer.create(gTables, 8, 5, true, true, false, true, 1);

		//Composer c = Composer.create(gTables, 8, 7, false, true, true, false, 0);
		//c.setStartComp("YC SB SB YC BY BY CS CS");

		//c.setStartComp(m[0].getAbbrev()+m[0].getAbbrev()+" "+m[1].getAbbrev()+m[0].getAbbrev());

		//c.setAllowCalls(1);
		c.setMinBalance(minBalance);
		c.setMinScore(minScore);
		c.setMinCOM(minCOM);

		Music[] music = new Music[1+Music.kDEFAULT.length];
		int i;
		for (i=0; i<Music.kDEFAULT.length; i++)
			music[i] = Music.kDEFAULT[i];
		String[] vlb = {"xxxx1234", "xxxx4321", "1234xxxx", "4321xxxx"};
		music[i] = new Music("VLB", 1, vlb);
		gTables.setMusic(music);
		gTables.prepareMusic();

		gTables.prepareMethods();
		gTables.prepareLeadMusic();
		fComposer = c;
		fOut = null;
		try
		{
			fOut = new PrintWriter(new FileWriter("comps.lst"));
		}
		catch (IOException e)
		{
			System.out.println("ERROR: Failed to open output file: "+e);
			return;
		}
		Thread watcher = new Thread(this, "Comp watcher");
		watcher.start();
		try
		{
			c.compose(this);
		}
		catch (Throwable t)
		{
			System.out.println("FATAL: "+t);
			t.printStackTrace(System.out);
		}
		if (fOut!=null)
		{
			fOut.close();
		}

	}

	public void outputComp(OutputComp latest)
	{
		int nCompsToKeep = 10;
		synchronized (this)
		{
			int ncomps = fBestComps.size();
			int i;
			for (i=0; i<ncomps; i++)
			{
				OutputComp comp = (OutputComp)fBestComps.elementAt(i);
				if (latest.compareTo(comp)>=1)
				{
					fBestComps.insertElementAt(latest, i);
					if (ncomps>=nCompsToKeep)
					{
						fBestComps.removeElementAt(ncomps);
						comp = (OutputComp)fBestComps.elementAt(ncomps-1);
						if (fComposer!=null)
						{
							// Update min scores to keep search pruned
							int minScore = Integer.MAX_VALUE;
							int minCOM = Integer.MAX_VALUE;
							int minBalance = Integer.MAX_VALUE;
							for (int j=0; j<ncomps; j++)
							{
								comp = (OutputComp)fBestComps.elementAt(j);
								if (comp.getScore()<minScore)
									minScore = comp.getScore();
								if (comp.getCOM()<minCOM)
									minCOM = comp.getCOM();
								if (comp.getBalance()<minBalance)
									minBalance = comp.getBalance();
							}
							fComposer.setMinScore(minScore);
							fComposer.setMinCOM(minCOM);
							fComposer.setMinBalance(minBalance);
						}
					}
					break;
				}
			}
			if (i>=ncomps && ncomps<nCompsToKeep)
				fBestComps.addElement(latest);
		}

		fOut.println(latest.toString(""));
		fOut.println("");
		fOut.flush();
	}

	public void run()
	{
		do
		{
			try
			{
				Thread.sleep(5000);
			}
			catch (InterruptedException e)
			{
			}
			if (fComposer.isComposing())
				System.out.println(fComposer.getProgress(2)+"% "+fComposer.estimateTimeLeft()+" n="+fComposer.getNComps()+" bal="+fComposer.getBestBalance()+" com="+fComposer.getBestCOM()+" score="+fComposer.getBestScore()+" node/s="+fComposer.getNodesPerSec());
		} while (fComposer.isComposing());
	}
}
