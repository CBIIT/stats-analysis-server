package gov.nih.nci.caintegrator.analysis.server;

import org.rosuda.JRclient.RSrvException;
import org.rosuda.JRclient.Rconnection;


/**
 * An RComputeConnection represents an Rconnections that has been bound
 * to a specific R data file (usually an Rda file which contains microarray intentisy values).
 * @author harrismic
 *
 */
public class RComputeConnection extends Rconnection {

	private String rDataFileName;
	
//	public RComputeConnection(String rDataFileName) throws RSrvException   {
//		super();
//		this.rDataFileName = rDataFileName;	
//	}
	
	public RComputeConnection(String host) throws RSrvException {
	  this(host, null);
	}
	
	public RComputeConnection(String host, String rDataFileName) throws RSrvException {
	  super(host);
	  this.rDataFileName = rDataFileName;
	}
	
	public RComputeConnection(String host, int port, String rDataFileName) throws RSrvException {
	  super(host, port);
	  this.rDataFileName = rDataFileName;
	}
	
	public String getRdataFileName() { return rDataFileName; }
	
	
	public void setRDataFile(String rDataFileName) throws RSrvException  {
	  
		//load the rDataFile from disk
		
		
	}

}
