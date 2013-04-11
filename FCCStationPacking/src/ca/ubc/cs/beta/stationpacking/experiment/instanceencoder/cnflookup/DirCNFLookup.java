package ca.ubc.cs.beta.stationpacking.experiment.instanceencoder.cnflookup;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.stationpacking.data.Station;
import ca.ubc.cs.beta.stationpacking.experiment.InstanceGeneration;

/**
 * CNF lookup that searches a particular directory for CNFs.
 * @author afrechet
 *
 */
public class DirCNFLookup implements ICNFLookup{

	private static Logger log = LoggerFactory.getLogger(InstanceGeneration.class);
	
	private String fCNFDirectory;

	public DirCNFLookup(String aCNFDirectory)
	{
		fCNFDirectory = aCNFDirectory;
	}
	
	private String hashforFilename(String aString)
	{
		MessageDigest aDigest = DigestUtils.getSha1Digest();
	
		try {
		        byte[] aResult = aDigest.digest(aString.getBytes("UTF-8"));
		        String aResultString = new String(Hex.encodeHex(aResult));
		
		        return aResultString;
		}
		catch (UnsupportedEncodingException e) 
		{
		        throw new IllegalStateException("Could not encode filename", e);
		}
	}
	
	@Override
	public boolean hasCNFfor(Set<Station> aStations) {
		String aCNFFilename = fCNFDirectory+File.separatorChar+getCNFNamefor(aStations);
		File aCNFFile = new File(aCNFFilename);
		return aCNFFile.exists();
	}

	@Override
	public String getCNFfor(Set<Station> aStations) throws Exception {
		String aCNFName = getCNFNamefor(aStations);
		String aCNFFilename = fCNFDirectory+File.separatorChar+getCNFNamefor(aStations);
		File aCNFFile = new File(aCNFFilename);
		if(!aCNFFile.exists())
		{
			throw new Exception("Tried to lookup a CNF for a set of stations that is unavailable.");
		}
		else
		{
			return aCNFName;
		}
	}

	@Override
	public String getCNFNamefor(Set<Station> aStations) {
		return hashforFilename(Station.hashStationSet(aStations))+".cnf";
	}

	@Override
	public String addCNFfor(Set<Station> aStations) throws Exception {
		log.warn("Not assigning station set to any CNF - CNF must be put with the right name in the right directory.");
		return getCNFNamefor(aStations);
	}

}