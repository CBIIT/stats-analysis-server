/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.IdGroup;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.REXP;
import org.rosuda.JRclient.RFileInputStream;
import org.rosuda.JRclient.RSrvException;
//import org.rosuda.JRclient.Rconnection;

/**
 * This is the base class for all analysis tasks that are implemented in R. This class
 * relies on Rserve (see http://stats.math.uni-augsburg.de/Rserve/) to handle communication with an 
 * R process.
 * 
 * @author harrismic
 *
 */





public abstract class AnalysisTaskR extends AnalysisTask {

	private RComputeConnection computeConnection = null;

	private boolean debugRcommands = false;
	
	private static Logger logger = Logger.getLogger(AnalysisTaskR.class);

	public AnalysisTaskR(AnalysisRequest request) {
		super(request);
	}

	public AnalysisTaskR(AnalysisRequest request, boolean debugRcommands) {
		super(request);
		this.debugRcommands = debugRcommands;
	}

	/**
	 * The run method implemented in each of the subclasses will call
	 * getRconnection() to get a connection to the Rserve and perform the
	 * computation.
	 */
	public abstract void run();

	public void setDataFile(String dataFileName) throws AnalysisServerException {
		
		if (dataFileName == null) {
		  logger.error("setDataFile dataFileName=null");
		  throw new AnalysisServerException("Null dataFileName passed to setDataFileName");
		}
		
		String connectionDataFileName = getRComputeConnection().getRdataFileName();
		if (!dataFileName.equals(connectionDataFileName)) {
		  
		  logger.info("AnalysisTaskR.setDataFile request=" + getRequest() + " switching data file from=" + connectionDataFileName + 
				       " to=" + dataFileName);
	      computeConnection.setRDataFile(dataFileName);
		  
		}
		else {
		  logger.info("AnalysisTaskR.setDataFile request=" + getRequest() + " no data file switch required correct file already loaded current=" + connectionDataFileName + 
					       " request=" + dataFileName);
		}
	}
	
	public void setRComputeConnection(RComputeConnection connection) throws AnalysisServerException {
		this.computeConnection = connection;
	}

	public RComputeConnection getRComputeConnection() {
		return computeConnection;
	}

	public boolean getDebugRcommands() {
		return debugRcommands;
	}

	public void setDebugRcommands(boolean debugRcommands) {
		this.debugRcommands = debugRcommands;
	}

	/**
	 * Evaluate an R command with no return value
	 * 
	 * @param c
	 * @param command
	 * @throws AnalysisServerException 
	 * @throws RSrvException 
	 */
	protected void doRvoidEval(String command) throws AnalysisServerException  {
		if (debugRcommands) {
			logger.debug(command);
		}
		try {
			computeConnection.voidEval(command);
		} catch (RSrvException e) {
			logger.error("doRvoidEval threw RSrvException when executing command=" + command);
			logStackTrace(logger, e);
			throw new AnalysisServerException("Internal Error. command=" + command);
		}
	}

	/**
	 * Execute an R command with a return value
	 * 
	 * @param c
	 * @param command
	 * @return
	 * @throws AnalysisServerException 
	 */
	protected REXP doREval(String command) throws AnalysisServerException {
		REXP returnVal = null;
		try {
			if (debugRcommands) {
			  logger.debug(command);
			}
			returnVal = computeConnection.eval(command);
		} catch (RSrvException e) {
		  logger.error("doREval threw RSrvException when executing command=" + command);
		  logStackTrace(logger, e);
		  throw new AnalysisServerException("Internal Error. command=" + command);
		}
		return returnVal;
	}

	public static String getQuotedString(String inputStr) {
		return "\"" + inputStr + "\"";
	}

	/**
	 * This method will take a SampleGroup and generate the R command for to
	 * create the sampleId list. The returned lists can then be used as input
	 * parameters to the statistical methods (for example ttest).
	 * 
	 * @param rName The name that R should use for the group
	 * @param group The group of IDs to use.
	 * @return A string containing the R command to create the group.
	 */
	public static String getRgroupCmd(String rName, IdGroup group) {
		StringBuffer sb = new StringBuffer();
		sb.append(rName);
		sb.append(" <- c(");
		String id;
		
		if (group != null) {
			for (Iterator i = group.iterator(); i.hasNext();) {
				id = (String) i.next();
				sb.append("\"").append(id).append("\"");
				if (i.hasNext()) {
					sb.append(",");
				} 
			}
		}
		sb.append(")");
		return sb.toString();
	}
	
	public static String getRgroupCmd(String rName, List items) {
	  StringBuffer sb = new StringBuffer();
	  sb.append(rName);
	  sb.append(" <- c(");
	  String str;
	  
	  for (Iterator i = items.iterator(); i.hasNext(); ) {
		str = i.next().toString();
		sb.append("\"").append(str).append("\"");
		if (i.hasNext()) {
			sb.append(",");
		} 
	  }
	  sb.append(")");
	  return sb.toString();
	}
	
	
	/**
	 * Get the byte representation of the image created with the plot command.
	 * This code follows the example of how to transfer an image using Rserve in the Rserve examples. 
	 * 
	 * @param plotCmd
	 * @param imgHeight the height of the image to create
	 * @param imgWidth the width of the image to create
	 * @return a byte array containing the image. 
	 * @throws AnalysisServerException 
	 */
	public byte[] getImageCode(String plotCmd, int imgHeight, int imgWidth) throws AnalysisServerException {

		byte[] imgCode = new byte[0];
		
		String fileName = null;

		try {
			fileName = "image_" + getRequest().getSessionId() + "_"
					+ System.currentTimeMillis() + ".png";
			
		    fileName = fileName.replace(' ','_');  //should never have spaces but just to be sure
			
			REXP xp = null;

			xp = doREval("try(bitmap(\"" + fileName
					+ "\", height = " + imgHeight + ", width = " + imgWidth + ", res = 72 ))");

			if (xp.asString() != null) { // if there's a string then we have
											// a problem, R sent an error
				logger.error("Problem getting the graphics device:\n"
						+ xp.asString());
				// this is analogous to 'warnings', but for us it's sufficient
				// to get just the 1st warning
				REXP w = doREval("if (exists(\"last.warning\") && length(last.warning)>0) names(last.warning)[1] else 0");
				if (w.asString() != null)
					logger.warn(w.asString());
				return new byte[0];
			}

			//do the plot
			doRvoidEval(plotCmd);
			doRvoidEval("dev.off()");

			RFileInputStream is = computeConnection.openFile(fileName);
			imgCode = getBytes(is);
			is.close();
			computeConnection.removeFile(fileName);
		} catch (IOException ex) {
			logger.error("Caught IOException in getImageCode. FileName=" + fileName);
			logStackTrace(logger, ex);
			throw new AnalysisServerException("Internal Error. Caught IOException in getImageCode plotCmd=" + plotCmd);
		} catch (RSrvException e) {
			logger.error("Caught RSrvException in getImageCode. FileName=" + fileName);
			logStackTrace(logger, e);
			throw new AnalysisServerException("Internal Error. Caught IOException in getImageCode plotCmd=" + plotCmd);
		} catch (Exception exg) {
			logger.error("Caught exception in getImageCode. ex=" + exg.getMessage());
			logStackTrace(logger, exg);
			throw new AnalysisServerException("Internal Error. Caught Exception in getImageCode plotCmd=" + plotCmd);
		}

		logger.info("getImageCode returning image numBytes=" + imgCode.length);
		return imgCode;

	}

	/**
	 * Get an array of bytes from a stream
	 * @param is
	 * @return
	 */
	private byte[] getBytes(InputStream is) {
	  ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
	  int numRead = -1;
	  try {
		  //using the buffer size from the Rserve example. Not sure why they are
		  //setting it to this value.
		  byte[] buff = new byte[65536];
		  while ((numRead = is.read(buff)) != -1) {
		    byteStream.write(buff, 0, numRead);
		  }
	  }
	  catch (IOException ex) {
		logger.error("Caught IOException in getBytes method.");
	    logStackTrace(logger, ex);
	  }
	  byte[] returnArray = byteStream.toByteArray();
	  logger.debug("getBytes returning numbytes=" + returnArray.length);
	  return byteStream.toByteArray();
	}
}
