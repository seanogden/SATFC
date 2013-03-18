package experiment.instanceencoder.componentgrouper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import data.Station;

/**
 * Partition station sets so that each member can be encoded in a CNF separately (i.e. without missing any constraints).
 * @author afrechet
 *
 */
public interface IComponentGrouper {
	
	/**
	 * @param aStations - a set of stations.
	 * @return A partition of the input station sets (such that each station can be encoded separately).
	 */
	public ArrayList<HashSet<Station>> group(Set<Station> aStations);
	
	
}
