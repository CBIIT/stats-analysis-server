package gov.nih.nci.caintegrator.analysis.server;

import java.util.List;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationResult;
import gov.nih.nci.caintegrator.analysis.messaging.ReporterInfo;
import gov.nih.nci.caintegrator.enumeration.CorrelationType;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.REXP;

public class CorrelationTaskR extends AnalysisTaskR {

	private CorrelationResult result;
	
	private static Logger logger = Logger.getLogger(CorrelationTaskR.class);

	
	public CorrelationTaskR(AnalysisRequest request) {
		super(request);
		
	}

	public CorrelationTaskR(AnalysisRequest request, boolean debugRcommands) {
		super(request, debugRcommands);
		
	}

	@Override
	public void run() {
		CorrelationRequest corrRequest = (CorrelationRequest) getRequest();
		result = new CorrelationResult(getRequest().getSessionId(), getRequest().getTaskId());
		logger.info(getExecutingThreadName() + " processing correlation request="
						+ corrRequest);
		REXP rVal;
		
		try {
			
			String dataFileName = corrRequest.getDataFileName();
			
			if (dataFileName == null) {
			  //check to make sure that the vectors have data
			  if ((corrRequest.getVector1()==null)||(corrRequest.getVector2()==null)) {
				throw new AnalysisServerException("Problem with correlation request. No data file specified and vectors are null.");
			  }	  
			}
			else {
			  setDataFile(corrRequest.getDataFileName());
			}
			
		} catch (AnalysisServerException e) {
			e.setFailedRequest(corrRequest);
			logger.error("Internal Error. " + e.getMessage());
			setException(e);
			return;
		}

		
		try {
		
			//Check to see if a reporter was passed in
			ReporterInfo reporter1 = corrRequest.getReporter1();
			ReporterInfo reporter2 = corrRequest.getReporter2();
			
			//get the submatrix of the patients specified
			List<String> sampleIds = corrRequest.getSampleIds();
			String sampleIdscmd = getRgroupCmd("sampleIds", sampleIds);
			String cmd;
			Double r = -1.0;
			
			cmd = "SM <- getSubmatrix.onegrp(dataMatrix, sampleIds)";
		
			
			if ((reporter1 != null) && (reporter2 != null)) {
			  //CASE 1:  correlation between two reporters
				
              //get the data matrix for reporter 1
			  setDataFile(reporter1.getDataFileName());
			  doRvoidEval(sampleIdscmd);
			  cmd = "SM <- getSubmatrix.onegrp(dataMatrix, sampleIds)";
			  doRvoidEval(cmd);			  
			  cmd = "RM1 <- getSubmatrix.rep(SM," + reporter1.getReporterName() + ")";
			  doRvoidEval(cmd);
			 // result.setVector1(RM1);
			  
			  //need to set vector 1 with RM1
			  
			  
			  //get the data matrix for reporter 2
			  setDataFile(reporter2.getDataFileName());
			  doRvoidEval(sampleIdscmd);
			  cmd = "SM <- getSubmatrix.onegrp(dataMatrix, sampleIds)";
			  doRvoidEval(cmd);			  
			  cmd = "RM2 <- getSubmatrix.rep(SM," + reporter2.getReporterName() + ")";
				
			  //need to set vector 2 with RM2
			  //result.setVector2(RM2);
			  
			  if (corrRequest.getCorrelationType() == CorrelationType.PEARSON) {
				  cmd = "r <- correlation(RM1,RM2,\"pearson\")";
				  rVal = doREval(cmd);
				}
				else if (corrRequest.getCorrelationType() == CorrelationType.SPEARMAN) {
				  cmd = "r <- correlation(RM1,RM2, \"spearman\")";
				  rVal = doREval(cmd);
				}
			  
			}
			
			
			
			
			
			//String cmd = CorrelationTaskR.getRgroupCmd("GRP1", corrRequest.getVector1().getValues());
			doRvoidEval(cmd);
			
			cmd = CorrelationTaskR.getRgroupCmd("GRP2", corrRequest.getVector2().getValues());
			doRvoidEval(cmd);
			
			//REXP rVal = null;
			
			if (corrRequest.getCorrelationType() == CorrelationType.PEARSON) {
			  cmd = "r <- correlation(GRP1,GRP2,\"pearson\")";
			  rVal = doREval(cmd);
			}
			else if (corrRequest.getCorrelationType() == CorrelationType.SPEARMAN) {
			  cmd = "r <- correlation(GRP1,GRP2, \"spearman\")";
			  rVal = doREval(cmd);
			}
			else {
			  throw new AnalysisServerException("Unrecognized correlationType or correlation type is null.");
			}
	
			//double r = rVal.asDouble();
			result.setCorrelationValue(r);
			result.setVector1(corrRequest.getVector1());
			result.setVector2(corrRequest.getVector2());
		}
		catch (AnalysisServerException asex) {
			AnalysisServerException aex = new AnalysisServerException(
			"Problem computing correlation. Caught AnalysisServerException in CorrelationTaskR." + asex.getMessage());
	        aex.setFailedRequest(corrRequest);
	        setException(aex);
	        return;  
		}
		catch (Exception ex) {
			AnalysisServerException asex = new AnalysisServerException(
			"Internal Error. Caught Exception in CorrelationTaskR exClass=" + ex.getClass() + " msg=" + ex.getMessage());
	        asex.setFailedRequest(corrRequest);
	        setException(asex);
	        return;  
		}
	}

	@Override
	public void cleanUp() {
		try {
			setRComputeConnection(null);
		} catch (AnalysisServerException e) {
			logger.error("Error in cleanUp method.");
			logger.error(e);
			setException(e);
		}
	}

	@Override
	public AnalysisResult getResult() {
		return result;
	}

}
