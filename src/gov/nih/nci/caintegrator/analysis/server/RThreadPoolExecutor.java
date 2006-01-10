package gov.nih.nci.caintegrator.analysis.server;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.*;

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
	
	private static Logger logger = Logger.getLogger(RThreadPoolExecutor.class);

	public RThreadPoolExecutor(int nThreads, String RserveIp, String RdataFile,
			AnalysisResultSender sender) {

		// create a new fixed thread pool
		super(nThreads, nThreads, Long.MAX_VALUE, TimeUnit.NANOSECONDS,
				new LinkedBlockingQueue<Runnable>(), new RThreadFactory(
						RserveIp, RdataFile));

		this.sender = sender;
		this.hostName = getHostName();

		prestartAllCoreThreads();
	}

	protected void beforeExecute(Thread thread, Runnable task) {
		
		AnalysisTaskR rTask = (AnalysisTaskR) task;
		RThread rThread = (RThread) thread;
		
		rTask.setExecutingThreadName(rThread.getName());
		rTask.setRconnection(rThread.getRconnection());
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
		  sender.sendException(rTask.getException());
		}
		else {
		  logger.info(rTask.getExecutingThreadName() + " completed task=" + rTask + " host=" + getHostName() + " computeTime(ms)=" + rTask.getComputeTime());
		  sender.sendResult(rTask.getResult());
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
			}
		}
		return hostName;
	}

}
