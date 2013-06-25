/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

/**
 * This class handles execution of R based analysis tasks using the 
 * Java ThreadPoolExecutor class. 
 * 
 * @see gov.nih.nci.caintegrator.analysis.server.RThreadPoolExecutor
 * 
 * @author harrismic
 *
 */




public class RThreadPoolExecutor extends ThreadPoolExecutor {

	private AnalysisResultSender sender;
	private String hostName = null;
	private boolean debugRcommands = false;
	
	//The rConnectionPool will contain a mapping from an Rbinary file name (a data file) to 
	//an Rconnection.  The Rconnection will have the data file preloaded and ready to run an 
	//analysis task. 
	
	private static Logger logger = Logger.getLogger(RThreadPoolExecutor.class);

	public RThreadPoolExecutor(int nThreads, String RserveIp, int RservePort, String RinitializationFile, String RdataFileDirectory,
			AnalysisResultSender sender) {

		// create a new fixed thread pool
		super(nThreads, nThreads, Long.MAX_VALUE, TimeUnit.NANOSECONDS,
				new LinkedBlockingQueue<Runnable>(), new RThreadFactory(
						RserveIp, RservePort, RinitializationFile, RdataFileDirectory));

		this.sender = sender;
		this.hostName = getHostName();
		
		prestartAllCoreThreads();
	}

	protected void beforeExecute(Thread thread, Runnable task) {
		
		AnalysisTaskR rTask = (AnalysisTaskR) task;
		RThread rThread = (RThread) thread;
		rTask.setExecutingThreadName(rThread.getName());
		try {
			rTask.setRComputeConnection(rThread.getRComputeConnection());
		} catch (AnalysisServerException e) {
			rTask.setException(e);
			logger.error("Caught AnalysisServerException when trying to set the RComputeConnection");
			logStackTrace(e);
		}
		rTask.setDebugRcommands(debugRcommands);
		rTask.setStartTime(System.currentTimeMillis());
		
		super.beforeExecute(thread, task);
		
//		System.out.println("Thread name=" + rThread.getName()
//				+ " executing task=" + rTask);
	}

	protected void afterExecute(Runnable task, Throwable throwable) {
		
		AnalysisTaskR rTask = (AnalysisTaskR) task;
		rTask.setComputeTime(System.currentTimeMillis() - rTask.getStartTime());
		
		if (rTask.getException() != null) {
		  logger.info(rTask.getExecutingThreadName() + " failed to complete task=" + rTask + " host=" + getHostName());
		  sender.sendException(rTask.getException(), rTask.getJMSDestination());
		}
		else {
		  logger.info(rTask.getExecutingThreadName() + " completed task=" + rTask + " host=" + getHostName() + " computeTime(ms)=" + rTask.getComputeTime());
		  sender.sendResult(rTask.getResult(), rTask.getJMSDestination());
		}
		
		rTask.cleanUp();
	}

	public void setDebugRcommmands(boolean debugRcommands) {
	  this.debugRcommands = debugRcommands;
	}
	
	
	/**
	 * Get the host name that is performing the execution 
	 * @return the host name that is being used to executed the analysis
	 */
	public String getHostName() {
		if (hostName == null) { 
			try {
				InetAddress addr = InetAddress.getLocalHost();
				hostName = addr.getHostName();
			} 
			catch (UnknownHostException e) {
			  logStackTrace(e);
			}
		}
		return hostName;
	}
	
	/**
	 * This method will log an error and will print the stack trace to the log file
	 * @param ex
	 */
	private static void logStackTrace(Throwable ex) {	 
	  StringWriter sw = new StringWriter();
	  PrintWriter pw = new PrintWriter(sw);
	  ex.printStackTrace(pw);
	  logger.error(sw.toString());
	}

}
