package gov.nih.nci.caintegrator.analysis.server;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.RSrvException;
import org.rosuda.JRclient.Rconnection;


/**
 * An RComputeConnection represents an Rconnections that has been bound
 * to a specific R data file (usually an Rda file which contains microarray intentisy values).
 * @author harrismic
 *
 */
public class RComputeConnection extends Rconnection {

	private String rDataFileName = null;
	private String rDataFileDirectory = null;
	private static Logger logger = Logger.getLogger(RComputeConnection.class);
	
//	public RComputeConnection(String rDataFileName) throws RSrvException   {
//		super();
//		this.rDataFileName = rDataFileName;	
//	}
	
	
	public RComputeConnection(String host, String rDataFileDirectory) throws RSrvException {
	  super(host);
	  this.rDataFileDirectory = rDataFileDirectory;
	}
	
	public RComputeConnection(String host, int port, String rDataFileDirectory) throws RSrvException {
	  super(host, port);
	  this.rDataFileDirectory = rDataFileDirectory;
	}
	
	public String getRdataFileName() { return rDataFileName; }
	
	
	public void setRDataFile(String rDataFileName) throws RSrvException  {
	  
		//load the rDataFile from disk
		long start = System.currentTimeMillis();

		String fullFileName = rDataFileDirectory + rDataFileName;
		
		String rCmd = "load(\"" + fullFileName  + "\")";
		voidEval(rCmd);
		long elapsedTime = System.currentTimeMillis() - start;
		logger.info("Successfully loaded rDataFile=" + fullFileName + " elapsedTimeMS=" + elapsedTime);
		
	}

}
