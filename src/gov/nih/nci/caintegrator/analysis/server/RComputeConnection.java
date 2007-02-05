package gov.nih.nci.caintegrator.analysis.server;

import java.io.PrintWriter;
import java.io.StringWriter;

import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

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
	
	/**
	 * This method will log an error and will print the stack trace to the log file
	 * @param ex
	 */
	private static void logStackTrace(Exception ex) {	 
	  StringWriter sw = new StringWriter();
	  PrintWriter pw = new PrintWriter(sw);
	  ex.printStackTrace(pw);
	  logger.error(sw.toString());
	}
	
	public RComputeConnection(String host, String rDataFileDirectory) throws RSrvException {
	  super(host);
	  this.rDataFileDirectory = rDataFileDirectory;
	}
	
	public RComputeConnection(String host, int port, String rDataFileDirectory) throws RSrvException {
	  super(host, port);
	  this.rDataFileDirectory = rDataFileDirectory;
	}
	
	public String getRdataFileName() { return rDataFileName; }
	
	
	public void setRDataFile(String rDataFileName) throws AnalysisServerException   {
	  
		//load the rDataFile from disk
		long start = System.currentTimeMillis();
		
		this.rDataFileName = rDataFileName;

		String fullFileName = rDataFileDirectory + rDataFileName;
		
		String rCmd = "load(\"" + fullFileName  + "\")";
		try {
			voidEval(rCmd);
		} catch (RSrvException e) {
			logger.error("Error (setRDataFile rDataFileName=" + rDataFileName);
			logStackTrace(e);			
			throw new AnalysisServerException("Error setting the RDataFile to rDataFileName=" + rDataFileName);
		}
		long elapsedTime = System.currentTimeMillis() - start;
		logger.info("Successfully loaded rDataFile=" + fullFileName + " elapsedTimeMS=" + elapsedTime);
		
	}

}
