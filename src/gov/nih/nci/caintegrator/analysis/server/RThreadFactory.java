package gov.nih.nci.caintegrator.analysis.server;


import java.util.concurrent.ThreadFactory;

public class RThreadFactory implements ThreadFactory {

	private String rServeIp;

	private String rDataFileName;

	public RThreadFactory(String rServeIp, String rDataFileName) {
		super();
		this.rServeIp = rServeIp;
		this.rDataFileName = rDataFileName;
	}

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
