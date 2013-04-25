package ca.ubc.cs.beta.stationpacking.experiment.instanceencoder.cnfencoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

import ca.ubc.cs.beta.stationpacking.data.Station;
import ca.ubc.cs.beta.stationpacking.data.manager.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.experiment.instance.IInstance;
import ca.ubc.cs.beta.stationpacking.experiment.instanceencoder.cnfencoder.ICNFEncoder;


public class NickCNFEncoder implements ICNFEncoder {
	
	//Whenever a trivially UNSAT problem is detected, return this string
	String fUNSAT_CNF = "p cnf 1 2\n 1 0\n -1 0\n";

	@Override
	/* NA - takes an Instance and a set of Constraints, and returns
	 * the DIMACS CNF corresponding to this instance of SAT.
	 */
	public String encode(IInstance aInstance, IConstraintManager aConstraintManager) {
		Map<Station,Integer> aInternalStationIDs = getInternalIDs(aInstance.getStations());
		Map<Integer,Integer> aInternalChannelIDs = getInternalIDs(aInstance.getChannelRange());	
		StringBuilder aBuilder = new StringBuilder();
		aBuilder.append("c Stations: ");
		for(Station aStation : aInternalStationIDs.keySet()){
			aBuilder.append(aStation.getID()+" ");
		}
		aBuilder.append("\nc Channels: ");
		for(Integer aChannel : aInternalChannelIDs.keySet()){
			aBuilder.append(aChannel+" ");
		}
		aBuilder.append("\n");
		int aNumClauses = 0;
		Set<Integer> aDomainInternal;
		Set<Integer> aCOInterferingInternal;
		Set<Integer> aADJInterferingInternal;
		//For each station, add CNF clauses 
		for(Station aStation : aInternalStationIDs.keySet()){
			Integer aInternalID = aInternalStationIDs.get(aStation);
			//Encode that aStation can be assigned to at most one channel
			aNumClauses += writeBaseClauses(aInternalID,aInternalChannelIDs.values(),aBuilder);
			//Encode that aStation must be assigned to at least one channel
			aDomainInternal = mapSet(aInternalChannelIDs,aStation.getDomain());
			if(aDomainInternal.isEmpty()) return fUNSAT_CNF;
			else aNumClauses += writeDomainClauses(aInternalID,aDomainInternal,aInternalChannelIDs.size(),aBuilder);
			//Encode that aStation cannot share a channel with any station in aConstraintManager.getCOInterferingStations(aStation)
			aCOInterferingInternal = mapSet(aInternalStationIDs,aConstraintManager.getCOInterferingStations(aStation));
			aNumClauses += writeConstraints("CO",aInternalID,aCOInterferingInternal,aInternalChannelIDs,aBuilder);
			//Encode that aStation cannot be one channel below any station in aConstraintManager.getADJplusInterferingStations(aStation)
			aADJInterferingInternal = mapSet(aInternalStationIDs,aConstraintManager.getADJplusInterferingStations(aStation));
			aNumClauses += writeConstraints("ADJ",aInternalID,aADJInterferingInternal,aInternalChannelIDs,aBuilder);
		}
		aBuilder.insert(0,"p cnf "+aInternalStationIDs.size()*aInternalChannelIDs.size()+" "+aNumClauses+"\n");
		return aBuilder.toString();
	}
	
	
	/* NA - takes an Instance and a string corresponding to a satisfying variable assignment.
	 * Checks that each station is assigned exactly one channel, and that this channel is in its domain.
	 * If these conditions are not met, it throws and catches an exception describing the problem, and
	 * returns an empty Map.
	 */
	public Map<Integer,Set<Station>> decode(IInstance aInstance, String aCNFAssignment){
		Map<Station,Integer> aInternalStationIDs = getInternalIDs(aInstance.getStations());
		Map<Integer,Integer> aInternalChannelIDs = getInternalIDs(aInstance.getChannelRange());
		Map<Integer,Set<Station>> aStationAssignment = new HashMap<Integer,Set<Station>>();
		try{
			Map<Integer,Boolean> aCNFdecoding = stringToAssignment(aCNFAssignment);
			int aNumCNFVars = aCNFdecoding.size();
			int aNumChannels = aInternalChannelIDs.size();
			int aExpectedNumCNFVars = aInternalStationIDs.size()*aNumChannels;
			if(aNumCNFVars == aExpectedNumCNFVars){
				for(Station aStation : aInternalStationIDs.keySet()){
					Integer aChannelAssignment = null;
					for(Integer aChannel : aInternalChannelIDs.keySet()){
						Integer aVar = get_variable(aInternalStationIDs.get(aStation),aInternalChannelIDs.get(aChannel),aNumChannels);
						if(aCNFdecoding.get(aVar)) {
							if(aStationAssignment.containsKey(aChannel)){
								aStationAssignment.get(aChannel).add(aStation);
							} else {
								Set<Station> aSet = new HashSet<Station>();
								aSet.add(aStation);
								aStationAssignment.put(aChannel,aSet);
							}
							if(aChannelAssignment != null) throw new Exception(aStation+" assigned to multiple channels.");
							aChannelAssignment = aChannel;
						}	
					}
					if(aChannelAssignment == null) throw new Exception(aStation+" not assigned to a channel.");
					else if(! aStation.getDomain().contains(aChannelAssignment)){
						throw new Exception(aStation+"assigned channel "+aChannelAssignment+", which is not in its domain.");
					}
				}
			} else throw new Exception(	"CNF Assignment includes "+aNumCNFVars+" variables, but encoding this instance requires "
										+aExpectedNumCNFVars+" variables.");
		} catch(Exception e) {
			aStationAssignment.clear();
			e.printStackTrace();
		}
		return aStationAssignment;
	}
	
	/* NA - writes clasues of the form !x1 ^ !x2 for variables xi corresponding to the assignment of the
	 * station with InternalID aInternalStationID to channels with InternalID in aInternalChannelIDs.
	 */
	private int writeBaseClauses(Integer aInternalStationID, Collection<Integer> aInternalChannelIDs, StringBuilder aBuilder){
		int aNumWrittenClauses = 0;
		Integer numChannels = aInternalChannelIDs.size();
		final Set<Integer> aEmpty = new HashSet<Integer>();
		Set<Integer> aNegatedVars = new HashSet<Integer>();
		for(Integer aInternalChannel1 : aInternalChannelIDs){
			for(Integer aInternalChannel2 : aInternalChannelIDs){
				if(aInternalChannel1 < aInternalChannel2){
					aNegatedVars.clear();
					aNegatedVars.add(get_variable(aInternalStationID,aInternalChannel1,numChannels));
					aNegatedVars.add(get_variable(aInternalStationID,aInternalChannel2,numChannels));
					aNumWrittenClauses += writeClause(aEmpty, aNegatedVars, aBuilder);
				}
			}
		}
		return aNumWrittenClauses;
	}
	
	/* NA - writes a clause of the form x1 ^ x2 ^ ... ^ xn where the xi represent assigning the
	 * station with InternalID aInternalStationID to one of the channels in its domain.
	 */
	private int writeDomainClauses(Integer aInternalStationID,Set<Integer> aInternalDomain, int aNumChannels, StringBuilder aBuilder){
		int aNumWrittenClauses = 0;
		Set<Integer> aVars = new HashSet<Integer>();
		for(Integer aInternalChannel : aInternalDomain){
			aVars.add(get_variable(aInternalStationID,aInternalChannel,aNumChannels));
		}
		aNumWrittenClauses += writeClause(aVars,new HashSet<Integer>(),aBuilder);
		return aNumWrittenClauses;
	}
	
	/* NA - writes clauses corresponding to constraints involving the station with internal ID aInternalStationID
	 * and the set of stations represented by aInterferingInternal. The type of constraint (co-channel or adjacent channel)
	 * is specified in the first argument, and the range of internal channel IDs is specified in the fourth parameter.
	 */
	private int writeConstraints(	String aConstraintType, Integer aInternalStationID, Set<Integer> aInterferingInternal,
									Map<Integer,Integer> aInternalChannelIDs, StringBuilder aBuilder){
		int aNumWrittenClauses = 0;
		int aNumChannels = aInternalChannelIDs.size();
		final Set<Integer> aEmpty = new HashSet<Integer>();
		Set<Integer> aNegatedVars = new HashSet<Integer>();
		if(aConstraintType.equals("CO")){
			Collection<Integer> aInternalChannels = aInternalChannelIDs.values();
			for(Integer aInternalStationID2 : aInterferingInternal){
				if(aInternalStationID2 < aInternalStationID){
					for(Integer aInternalChannel : aInternalChannels){
						aNegatedVars.clear();
						aNegatedVars.add(get_variable(aInternalStationID,aInternalChannel,aNumChannels));
						aNegatedVars.add(get_variable(aInternalStationID2,aInternalChannel,aNumChannels));
						aNumWrittenClauses += writeClause(aEmpty, aNegatedVars,aBuilder);
					}
				}
			}
		} else if(aConstraintType.equals("ADJ")){
			for(Integer aInternalStationID2 : aInterferingInternal){
				for(Integer aChannel : aInternalChannelIDs.keySet()){
					if(aInternalChannelIDs.containsKey(aChannel+1)){
						aNegatedVars.clear();
						aNegatedVars.add(get_variable(aInternalStationID,aInternalChannelIDs.get(aChannel),aNumChannels));
						aNegatedVars.add(get_variable(aInternalStationID2,aInternalChannelIDs.get(aChannel+1),aNumChannels));
						aNumWrittenClauses += writeClause(aEmpty, aNegatedVars,aBuilder);
					}
				}
			}	
		} else try {
			throw new Exception("Constraint Type must be either 'CO' or 'ADJ'.");
		} catch(Exception e){
			e.printStackTrace();
		}
		return aNumWrittenClauses;
	}
	
	//NA - Takes the string output of a SAT solver, turns it into the corresponding variable assignment.
	private Map<Integer,Boolean> stringToAssignment(String aCNFAssignment) throws Exception{
		Map<Integer,Boolean> aAssignment = new HashMap<Integer,Boolean>();
		String[] a = aCNFAssignment.substring(aCNFAssignment.indexOf(" v "),aCNFAssignment.indexOf("CPU")).split(" |\n");
		for(int i = 0; i < a.length; i++){
			if(!(a[i].equals("v")||a[i].isEmpty())){
				if(a[i].startsWith("-")) {
					if(aAssignment.put(Integer.parseInt(a[i].substring(1)),false)!=null)
						throw new Exception("Variable assigned to multiple truth values.");
				} else 
					if(aAssignment.put(Integer.parseInt(a[i]),true)!=null)
						throw new Exception("Variable assigned to multiple truth values.");
			}
		}
		if(aAssignment.remove(0)==null) throw new Exception("No terminating 0 in CNF string.");
		return aAssignment;
	}
	
	// NA - given a (Station,Channel) pair, returns the corresponding variable number
	private Integer get_variable(int aStationNumber, int aChannelNumber, int numChannels){
		return aStationNumber*numChannels+aChannelNumber+1;
		
	}

	// NA - given a set of variables and negated variables, writes a corresponding clause
	private int writeClause(Set<Integer> aVars, Set<Integer> aNegatedVars, StringBuilder aBuilder){
		for(Integer aVar : aVars){ aBuilder.append(aVar+" "); }
		for(Integer aVar : aNegatedVars){ aBuilder.append("-"+aVar+" "); }
		aBuilder.append("0\n");
		return 1;
	}
	
	//NA - returns the result of applying aMap to the set aInputSet
	private <T> Set<Integer> mapSet(Map<T,Integer> aMap, Set<T> aInputSet){
		Integer aOutput;
		Set<Integer> aOutputSet = new HashSet<Integer>();
		for(T aT : aInputSet){
			aOutput = aMap.get(aT);
			if(aOutput!=null) aOutputSet.add(aOutput);
		}
		return aOutputSet;
	}
	
	//NA - returns a map from items of type T to internal ID numbers used to represent them.
	private <T extends Comparable<T>> Map<T,Integer> getInternalIDs(Set<T> aSet){
		List<T> aSortedSet = new ArrayList<T>(aSet);
		Collections.sort(aSortedSet);
		Map<T,Integer> aInternalID = new HashMap<T,Integer>();
		for(int i = 0; i < aSortedSet.size(); i++){
			aInternalID.put(aSortedSet.get(i),i);
		}
		return aInternalID;
	}

}