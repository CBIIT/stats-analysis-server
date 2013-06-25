/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import java.util.concurrent.ThreadFactory;


/**
 * This class implements the ThreadFactory interface by returning RThreads
 * in the newThread method.  This class is used by the RThreadPoolExecutor to create new threads.
 * 
 * @see gov.nih.nci.caintegrator.analysis.server.RThreadPoolExecutor
 * 
 * @author harrismic
 *
 */




public class RThreadFactory implements ThreadFactory {

	private String rServeIp;
	private int rServePort;

	private String rInitializationFileName;
	private String rDataFileDirectory;

	public RThreadFactory(String rServeIp, int rServePort, String rInitializationFileName, String rDataFileDirectory) {
		super();
		this.rServeIp = rServeIp;
		this.rServePort = rServePort;
		this.rInitializationFileName = rInitializationFileName;
		this.rDataFileDirectory = rDataFileDirectory;
	}

	
	/**
	 * Return a new RThread. 
	 */
	public Thread newThread(Runnable r) {
		RThread thread = new RThread(r, rServeIp, rServePort, rInitializationFileName, rDataFileDirectory);
		return thread;
	}

	public String getRInitializationFileName() {
		return rInitializationFileName;
	}
	
	public String getRDataFileDirectory() {
	  return rDataFileDirectory;
	}

	public String getRServeIp() {
		return rServeIp;
	}
	
	public int getRServePort() {
	    return rServePort;
	}

}
