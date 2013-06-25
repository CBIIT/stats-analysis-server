/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.*;

/**
 * This class handles initialization of the R process. 
 * It is used to run R compute tasks such as ClassComparisonTaskR . 
 * 
 * @see gov.nih.nci.caintegrator.analysis.server.ClassComparisonTaskR
 * @see gov.nih.nci.caintegrator.analysis.server.HierarchicalClusteringTaskR
 * @see gov.nih.nci.caintegrator.analysis.server.PrincipalComponentAnalysisTaskR
 * 
 * @author harrismic
 * 
 *
 */




public class RThread extends Thread {

	private String rServeIp;
	
	private int rServePort;

	private String rInitializationFileName;
	
	private String rDataFileDirectory;

	private RComputeConnection computeConnection;
	
	private static Logger logger = Logger.getLogger(RThread.class);


	public RThread(Runnable target, String rServeIp, int rServePort, String rInitializationFileName, String rDataFileDirectory) {
		super(target);
		this.rServeIp = rServeIp;
		this.rServePort = rServePort;
		this.rInitializationFileName = rInitializationFileName;
		this.rDataFileDirectory = rDataFileDirectory;
		initializeRComputeConnection();
		logger.info("RThread name=" + getName()
				+ " successfully initialized R connection.");
	}

	public void initializeRComputeConnection() {
		// load the test matrix and function definitions
		try {
			
			computeConnection = new RComputeConnection(rServeIp, rServePort, rDataFileDirectory);
			//rConnection = new Rconnection(rServeIp);

			String rCmd;
			// System.out.println("Server vesion: "+c.getServerVersion());
			long start, elapsedtime;
			if (computeConnection.needLogin()) { // if server requires
											// authentication, send one
				logger.info("authentication required.");
				computeConnection.login("guest", "guest");
			}

			// System.out.println("\tInitializing the Rserver with data and
			// functions");

			start = System.currentTimeMillis();

			rCmd = "source(\"" + rInitializationFileName + "\")";
			computeConnection.voidEval(rCmd);
			elapsedtime = System.currentTimeMillis() - start;
			logger.info("\tDone initializing Rserver connection elapsedTime="
							+ elapsedtime);

		} catch (RSrvException rse) {
			logger.error("Rserve exception: " + rse.getMessage());
			logStackTrace(rse);
		} catch (Exception e) {
			logger.error("Something went wrong, but it's not the Rserve: "
							+ e.getMessage());
		    logStackTrace(e);
		}
	}

	public RComputeConnection getRComputeConnection() {
		return computeConnection;
	}

	
	public String getRInitializationFileName() {
		return rInitializationFileName;
	}

	public String getRServeIp() {
		return rServeIp;
	}

	public void setRServeIp(String serveIp) {
		rServeIp = serveIp;
	}

	public void finalize() {
		computeConnection.close();
		try {
			super.finalize();
		} catch (Throwable e) {
			logStackTrace(e);
		}
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

	public int getRServePort() {
		return rServePort;
	}

	public void setRServePort(int servePort) {
		rServePort = servePort;
	}

}
