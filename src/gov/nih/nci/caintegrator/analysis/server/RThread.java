package gov.nih.nci.caintegrator.analysis.server;


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

	private String rDataFileName;

	private Rconnection rConnection;
	
	private static Logger logger = Logger.getLogger(RThread.class);


	public RThread(Runnable target, String rServeIp, String rDataFileName) {
		super(target);
		this.rServeIp = rServeIp;
		this.rDataFileName = rDataFileName;
		initializeRconnection();
		logger.info("RThread name=" + getName()
				+ " successfully initialized R connection.");
	}

	public void initializeRconnection() {
		// load the test matrix and function definitions
		try {
			rConnection = new Rconnection(rServeIp);

			String rCmd;
			// System.out.println("Server vesion: "+c.getServerVersion());
			long start, elapsedtime;
			if (rConnection.needLogin()) { // if server requires
											// authentication, send one
				logger.info("authentication required.");
				rConnection.login("guest", "guest");
			}

			// System.out.println("\tInitializing the Rserver with data and
			// functions");

			start = System.currentTimeMillis();

			rCmd = "source(\"" + rDataFileName + "\")";
			rConnection.voidEval(rCmd);
			elapsedtime = System.currentTimeMillis() - start;
			logger.info("\tDone initializing Rserver connection elapsedTime="
							+ elapsedtime);

		} catch (RSrvException rse) {
			logger.error("Rserve exception: " + rse.getMessage());
		} catch (Exception e) {
			logger.error("Something went wrong, but it's not the Rserve: "
							+ e.getMessage());
			logger.error(e);
		}
	}

	public Rconnection getRconnection() {
		return rConnection;
	}

	
	public String getRDataFileName() {
		return rDataFileName;
	}

	public String getRServeIp() {
		return rServeIp;
	}

	public void setRServeIp(String serveIp) {
		rServeIp = serveIp;
	}

	public void finalize() {
		rConnection.close();
		try {
			super.finalize();
		} catch (Throwable e) {
			logger.error(e);
		}
	}

}
