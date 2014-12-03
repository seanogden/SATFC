package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.clasp;

import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ISolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ISolverBundleFactory;

/**
 * Factory for SAT solver bundle.
 * @author afrechet
 */
public class ClaspSATSolverBundleFactory implements ISolverBundleFactory {

	private final String fClaspLibrary;
	
	/**
	 * @param aClaspSATLibrary - clasp library to use in clasp bundles.
	 */
	public ClaspSATSolverBundleFactory(String aClaspSATLibrary)
	{
		fClaspLibrary = aClaspSATLibrary;
	}
	
	@Override
	public ISolverBundle getBundle(IStationManager aStationManager,
			IConstraintManager aConstraintManager) {
		return new ClaspSATSolverBundle(fClaspLibrary, aStationManager, aConstraintManager);
	}



}