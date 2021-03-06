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
package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.incremental;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.IncrementalClaspLibrary;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ClaspResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ClaspSATSolver;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Consumer runnable in charge of feeding problems for and reading answers from the JNA clasp library (in an online fashion since we're working incrementally).
 * 
 * NOT THREAD SAFE.
 * 
 * @author alex, gsauln
 */
public class IncrementalClaspJNAConsumer implements Runnable{

	private static Logger log = LoggerFactory.getLogger(IncrementalClaspJNAConsumer.class);
	
	private final BlockingQueue<IncrementalClaspProblem> fProblemQueue;
	private final BlockingQueue<ClaspResult> fAnswerQueue;
	
	//Library 
	private final static int MAX_NUMBER_OF_PARAMETERS = 128;
	private final IncrementalClaspLibrary fLib;
	private String fParameters;
	private long fSeed;
	
	private Pointer fJNAFacade;
	private Pointer fJNAResult;
	
	
	//Current job objects.
	private IncrementalClaspProblem fCurrentIncrementalProblem;
	ScheduledExecutorService fTimerServices;
	
	//Solving status flag.
	private AtomicBoolean fInterrupted = new AtomicBoolean(false);
	private AtomicBoolean fTimedOut = new AtomicBoolean(false); // true if the library timed out during solving.
	private AtomicBoolean fTerminated = new AtomicBoolean(false);
	private AtomicBoolean fSolving = new AtomicBoolean(false); // true when the library is actively solving a problem
	
	private Watch fSolveTimeStopWatch = new Watch();
	
	/**
	 * Constructs a problem consumer that feeds problems to incremental clasp (in an online fashion) and sends back answers, 
	 * all through thread safe blocking queues.
	 */
	public IncrementalClaspJNAConsumer(String aIncrementalClaspLibraryPath,
										String aParameters,
										long aSeed,
										BlockingQueue<IncrementalClaspProblem> aProblemQueue, 
										BlockingQueue<ClaspResult> aAnswerQueue)
	{
		//Setup the queues.
		fProblemQueue = aProblemQueue;
		fAnswerQueue = aAnswerQueue;
		
		//Setup the necessary library components.
		fLib = (IncrementalClaspLibrary) Native.loadLibrary(aIncrementalClaspLibraryPath, IncrementalClaspLibrary.class);
		fJNAFacade = fLib.createFacade();
		fJNAResult = fLib.createResult();
		fParameters = aParameters;
		fSeed = aSeed;
		
		testLibrary();
	}
	
	/**
	 * Test the current incremental clasp library. Checks if the given string of parameters and seed are valid.
	 */
	private void testLibrary()
	{
		if (fParameters.contains("--seed"))
		{
			throw new IllegalArgumentException("The parameter string cannot contain a seed as it is set upon the first call to solve!");
		}
		
		// check if the configuration is valid.
		String params = fParameters+" --seed=1";
		Pointer config = fLib.createConfig(params, params.length(), MAX_NUMBER_OF_PARAMETERS);
		try {
			int status = fLib.getConfigStatus(config);
			if (status == 2)
			{
				String configError = fLib.getConfigErrorMessage(config);
				String claspError = fLib.getConfigClaspErrorMessage(config);
				String error = configError + "\n" + claspError;
				throw new IllegalArgumentException(error);
			}
		}
		finally 
		{
			fLib.destroyConfig(config);
		}
	}
	
	@Override
	public void run() {
		Thread.currentThread().setName("Incremental Clasp JNA Consumer");
		
		log.info("Incremental Clasp JNA Consumer processing problems on a single thread.");

		// Create the configuration object
		// the construction of the config should always work as it as been
		// checked in the constructor.
		int seed = (new Random(fSeed)).nextInt();
		String params = fParameters + " --seed=" + seed;
		Pointer config = fLib.createConfig(params, params.length(),	MAX_NUMBER_OF_PARAMETERS);

		// create Problem object and give the callback function.
		Pointer problem = fLib.createIncProblem(fReadProblemCallback);

		// Create incremental controller
		Pointer control = fLib.createIncControl(fProcessAnswerAndContinueCallback, fJNAResult);

		// call solve incremental, the thread will stop here until the call
		// is terminated by incremental control returning false.
		fLib.jnasolveIncremental(fJNAFacade, problem, config, control, fJNAResult);

		// destroy all objects created in this call
		log.info("Shutting down Incremental Clasp JNA Consumer.");
		fLib.destroyConfig(config);
		fLib.destroyProblem(problem);
		fLib.destroyIncControl(control);
	}
	
	/**
	 * Interrupt the current solving, if any.
	 */
	public void interrupt()
	{
		if (fSolving.get())
		{
			log.debug("Interrupting Clasp library.");
			fInterrupted.set(true);
			fLib.interrupt(fJNAFacade);
		}
		else
		{
			log.debug("Not currently solving, no problem to interrupt.");
		}
	}
	
	/**
	 * Shutdown the consumer, preventing any further use.
	 */
	public void notifyShutdown()
	{
		//TODO Implement shutting down.
	}
	
	/**
	 * Call back object used by JNA Clasp library when in need of a next problem to solve.
	 */
	private final IncrementalClaspLibrary.jnaIncRead fReadProblemCallback = new IncrementalClaspLibrary.jnaIncRead()
	{
		@Override
		public Pointer read() {
			
			takeProblem();
			
			// reset the flags
			fTimedOut.set(false);
			fInterrupted.set(false);
			fSolving.set(true);
			
			// Reset the state of the result object.
			fLib.resetResult(fJNAResult);
			
			fSolveTimeStopWatch.reset();
			fSolveTimeStopWatch.start();
			
			// create the time thread to set cutoff
			double cutoff = fCurrentIncrementalProblem.getTerminationCriterion().getRemainingTime();
			// Launches a timer that will set the interrupt flag of the result object to true after aCutOff seconds.
			fTimerServices = Executors.newScheduledThreadPool(2,new SequentiallyNamedThreadFactory("Incremental Clasp SAT Solver Timers", true));
			fTimerServices.schedule(new Runnable(){
				@Override
				public void run() {
					log.debug("Solving timed out.");
					interrupt();
					fTimedOut.set(true);
				}}, (long) cutoff*1000, TimeUnit.MILLISECONDS);
		
			
			log.debug("Starting to solve.");
			return fCurrentIncrementalProblem.getProblemPointer();
		}
	};
	
	/**
	 * Take a problem from the problem queue.
	 */
	private void takeProblem()
	{
		IncrementalClaspProblem problem;
		log.debug("Incremental Clasp Consumer is taking a problem from queue.");
		try {
			problem = fProblemQueue.take();
			log.debug("Got a problem from the queue.");
			
			if(problem.getSeed()!=fSeed)
			{
				throw new IllegalStateException("Cannot solve a problem with seed "+problem.getSeed()+" different than seed "+fSeed+" provided on construction.");
			}
			
			fCurrentIncrementalProblem = problem;
		} catch (InterruptedException e) {
			log.error("Incremental Clasp Consumer was interrupted while waiting for new problem.",e.getMessage());
			Thread.currentThread().interrupt();
		}
	}
	
	/**
	 * Call back object used by JNA Clasp library when in need of a next problem to solve.
	 */
	private final IncrementalClaspLibrary.jnaIncContinue fProcessAnswerAndContinueCallback = new IncrementalClaspLibrary.jnaIncContinue() 
	{
		@Override
		public boolean doContinue() {
			// Retrieve answer and fill the answer queue.
			fSolving.set(false);
			//Terminate timing tasks.
			fTimerServices.shutdownNow();
			try {
				if(!fTimerServices.awaitTermination(3, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Could not terminate incremetal clasp timer tasks within 3 seconds.");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new IllegalStateException("Interrupted while trying to terminate incremetal clasp timer tasks.");
			}
			
			log.debug("Came back from solving - processing result.");
	
			fSolveTimeStopWatch.stop();
			double runtime = fSolveTimeStopWatch.getElapsedTime();
			
			ClaspResult result = ClaspSATSolver.getSolverResult(fLib, fJNAResult, fTimedOut, fInterrupted, runtime);
			try {
				fAnswerQueue.put(result);
			} catch (InterruptedException e) {
				log.error("Incremental Clasp Consumer was interrupted while putting the answer.",e.getMessage());
				Thread.currentThread().interrupt();
			}
			
			// Check if we should continue solving.
			return !fTerminated.get();
		}
		
	};
	
}
