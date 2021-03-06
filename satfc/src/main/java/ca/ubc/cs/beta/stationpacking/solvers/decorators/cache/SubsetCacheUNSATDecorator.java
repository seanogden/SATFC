/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Kevin Leyton-Brown.
 *
 * This file is part of satfc.
 *
 * satfc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * satfc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with satfc.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATResult;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics.SolvedByEvent;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

/**
* Created by newmanne on 28/01/15.
*/
@Slf4j
public class SubsetCacheUNSATDecorator extends ASolverDecorator {
    private final ContainmentCacheProxy containmentCache;

    public SubsetCacheUNSATDecorator(ISolver aSolver, ContainmentCacheProxy containmentCacheProxy) {
        super(aSolver);
        this.containmentCache = containmentCacheProxy;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        final SolverResult result;
        log.debug("Querying UNSAT cache");
        ContainmentCacheUNSATResult proveUNSATBySubset = containmentCache.proveUNSATBySubset(aInstance);
        SATFCMetrics.postEvent(new SATFCMetrics.TimingEvent(aInstance.getName(), SATFCMetrics.TimingEvent.FIND_SUBSET, watch.getElapsedTime()));
        if (proveUNSATBySubset.isValid()) {
            log.debug("Found a subset in the UNSAT cache - declaring problem UNSAT due to problem " + proveUNSATBySubset.getKey());
            result = new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
            SATFCMetrics.postEvent(new SATFCMetrics.SolvedByEvent(aInstance.getName(), SolvedByEvent.SUBSET_CACHE, result.getResult()));
            SATFCMetrics.postEvent(new SATFCMetrics.JustifiedByCacheEvent(aInstance.getName(), proveUNSATBySubset.getKey()));
        } else {
            log.debug("UNSAT cache unsuccessful");
            final double preTime = watch.getElapsedTime();
            final SolverResult decoratorResult = super.solve(aInstance, aTerminationCriterion, aSeed);
            result = SolverResult.addTime(decoratorResult, preTime);
        }
        return result;
    }
}
