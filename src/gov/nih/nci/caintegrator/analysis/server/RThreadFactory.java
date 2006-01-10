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

	private String rDataFileName;

	public RThreadFactory(String rServeIp, String rDataFileName) {
		super();
		this.rServeIp = rServeIp;
		this.rDataFileName = rDataFileName;
	}

	
	/**
	 * Return a new RThread. 
	 */
	public Thread newThread(Runnable r) {
		RThread thread = new RThread(r, rServeIp, rDataFileName);
		return thread;
	}

	public String getRDataFileName() {
		return rDataFileName;
	}

	public String getRServeIp() {
		return rServeIp;
	}

}
