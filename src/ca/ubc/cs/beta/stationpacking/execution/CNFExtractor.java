package ca.ubc.cs.beta.stationpacking.execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.daemon.datamanager.DataManager;
import ca.ubc.cs.beta.stationpacking.daemon.datamanager.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.DACConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.CNFCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATEncoder;

public class CNFExtractor {
	
	private static Logger log = LoggerFactory.getLogger(CNFExtractor.class);
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		DataManager aDataManager = new DataManager();
		
		String aCNFdir = args[0];
		if(aCNFdir.charAt(aCNFdir.length()-1) != File.separatorChar)
		{
			aCNFdir += File.separator;
		}
		log.info("CNF will be put in {}.",aCNFdir);
		
		//Each entry of args represent a question filename that must be parsed into a problem instance and then encoded to CNF.
		for(int i=1;i<args.length;i++)
		{
			//Question filename.
			String aQuestionFilename = args[i];
			log.info("{}/{}",i,args.length-1);
			log.info("Processing {}...", aQuestionFilename);
			
			String aStationConfig = null;
			String aBand = null;
			Integer aHighest = null;
			Set<Integer> aPackingStations = null;
			
			
			//Parse the question file.
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(aQuestionFilename));
			} catch (FileNotFoundException e) {
				throw new IllegalArgumentException("Could not read from question file "+aQuestionFilename+" ("+e.getMessage()+").");
			}
			String line;
			try {
				while ((line = br.readLine()) != null) {
				  
					line = line.trim();
					String key = line.split(",")[0];
					
					if(key.equals("STATION_CONFIG"))
					{
						aStationConfig = line.split(",")[1].trim();
					}
					else if (key.equals("BAND"))
					{
						aBand = line.split(",")[1].trim();
					}
					else if (key.equals("HIGHEST"))
					{
						aHighest = Integer.parseInt(line.split(",")[1]);
					}
					else if (isInteger(key))
					{
						if(aPackingStations==null)
						{
							aPackingStations = new HashSet<Integer>();
						}
						aPackingStations.add(Integer.parseInt(key));
					}
				}
				br.close();
			} catch (IOException e) {
				throw new IllegalStateException("Encountered an error while reading question file ("+e.getMessage()+").");
			}
			
			ManagerBundle aManagerBundle = null;
			//Build problem instance
			if(aStationConfig!=null)
			{
				try {
					aManagerBundle = aDataManager.getData(aStationConfig);
				} catch (FileNotFoundException e) {
					throw new IllegalArgumentException("Provided STATION_CONFIG "+aStationConfig+" is not valid ("+e.getMessage()+").");
				}
			}
			else
			{
				throw new IllegalArgumentException("Did not find STATION_CONFIG in question file "+aQuestionFilename);
			}
			
			Set<Integer> aPackingChannels = null;
			if(aBand != null)
			{
				if(aBand.equals("UHF") || aBand.equals("3"))
				{
					if(aHighest != null)
					{
						final Integer aFilterHighest = aHighest;
						Collections2.filter(
								DACConstraintManager.UHF_CHANNELS, 
								new Predicate<Integer>()
								{
									@Override
									public boolean apply(Integer c)
									{
										return c<=aFilterHighest;
									}
								});
					}
					else
					{
						aPackingChannels = DACConstraintManager.UHF_CHANNELS;
					}
					
				}
				else if(aBand.equals("LVHF"))
				{
					aPackingChannels = DACConstraintManager.LVHF_CHANNELS;
				}
				else if(aBand.equals("HVHF") || aBand.equals("UVHF"))
				{
					aPackingChannels = DACConstraintManager.UVHF_CHANNELS;
				}
				else
				{
					throw new IllegalArgumentException("Unrecognized value "+aBand+" for BAND entry in question file "+aQuestionFilename);
				}
			}
			else
			{
				throw new IllegalArgumentException("Did not find BAND in question file "+aQuestionFilename);
			}
			
			log.info("Getting station packing instance ...");
			
			IStationManager aStationManager = aManagerBundle.getStationManager();
			IConstraintManager aConstraintManager = aManagerBundle.getConstraintManager();
			
			String aInstanceString = StringUtils.join(aPackingChannels,"-")+"_"+StringUtils.join(aPackingStations,"-");
			StationPackingInstance aInstance = StationPackingInstance.valueOf(aInstanceString, aStationManager);
			
			ISATEncoder aSATEncoder = new SATEncoder(aStationManager, aConstraintManager);
			CNFCompressor aCNFCompressor = new CNFCompressor();

			log.info("Encoding into SAT...");
			CNF aCNF = aCNFCompressor.compress(aSATEncoder.encode(aInstance));
			
			
			String aCNFFilename = aCNFdir+aInstance.getHashString()+".cnf";
			log.info("Saving CNF to {}...",aCNFFilename);
			
			try {
				FileUtils.writeStringToFile(new File(aCNFFilename), aCNF.toDIMACS(new String[]{"FCC Station Packing Instance","Channels: "+StringUtils.join(aPackingChannels,","),"Stations: "+StringUtils.join(aPackingStations,",")}));
			} catch (IOException e) {
				throw new IllegalStateException("Could not write CNF to file ("+e.getMessage()+").");
			}
		}
	}
	
	 private static boolean isInteger(String s) {
	     try { 
	         Integer.parseInt(s); 
	     } catch(NumberFormatException e) { 
	         return false; 
	     }
	     return true;
	 }

}
