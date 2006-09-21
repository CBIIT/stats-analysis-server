package gov.nih.nci.caintegrator.analysis.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationResult;
import gov.nih.nci.caintegrator.analysis.messaging.DataPoint;
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
		double rVal = -1.0;
		List<DataPoint> points = Collections.emptyList();
	
		try {
		
			//Check to see if a reporter was passed in
			ReporterInfo reporter1 = corrRequest.getReporter1();
			ReporterInfo reporter2 = corrRequest.getReporter2();
			
			result.setGroup1Name(reporter1.getGeneSymbol() + "_" + reporter1.getReporterName());
			result.setGroup2Name(reporter2.getGeneSymbol() + "_" + reporter2.getReporterName());
			
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
			  cmd = "RM1 <- getSubmatrix.rep(SM,\"" + reporter1.getReporterName() + "\")";
			  double[] vec1 =  doREval(cmd).asDoubleArray();
			  //need to make sure that the vectors are in the same order wrt sample ids
			  cmd = "RM1labels <- dimnames(RM1)[[1]]";
			  Vector RM1_ids = doREval(cmd).asVector();
			  
			  Map <String, DataPoint> vecMap = new HashMap<String, DataPoint>();
			  String id;
			  DataPoint point;
			  for (int i=0; i < RM1_ids.size(); i++) {
				  //create objects and store.
				  id = ((REXP)RM1_ids.get(i)).toString();
				  point = vecMap.get(id);
				  if (point == null) {
				    point = new DataPoint(id);
				    vecMap.put(id, point);
				  }
				  point.setX(vec1[i]);
			  }
			  
			  //get the data matrix for reporter 2
			  setDataFile(reporter2.getDataFileName());
			  doRvoidEval(sampleIdscmd);
			  cmd = "SM <- getSubmatrix.onegrp(dataMatrix, sampleIds)";
			  doRvoidEval(cmd);			  
			  cmd = "RM2 <- getSubmatrix.rep(SM,\"" + reporter2.getReporterName() + "\")";
			  double[] vec2 = doREval(cmd).asDoubleArray();
			  
			  
			  cmd = "RM2labels <- dimnames(RM2)[[1]]";
			  Vector RM2_ids = doREval(cmd).asVector();
			 
			  //set the vector with the values for vector2
			  for (int i=0; i < RM2_ids.size(); i++) {
				  //create objects and store.
				  id = ((REXP)RM2_ids.get(i)).toString();
				  point = vecMap.get(id);
				  
				  if (point != null) {
					point.setY(vec2[i]);				   
				  }
				  else {
					logger.warn("Correlation skipping id=" + id + " not found in data file 2");
					vecMap.remove(id);
				  }
			  }
			  
			  points = new ArrayList<DataPoint>(vecMap.values());
			  
			  
			  String v1str = "v1 <- c(" + getDataString(points, true,false);
			  String v2str = "v2 <- c(" + getDataString(points,false,true);
			  
			  doRvoidEval(v1str);
			  doRvoidEval(v2str);
			  if (corrRequest.getCorrelationType() == CorrelationType.PEARSON) {
				  cmd = "r <- correlation(v1,v2,\"pearson\")";
				  r = doREval(cmd).asDouble();
				}
				else if (corrRequest.getCorrelationType() == CorrelationType.SPEARMAN) {
				  cmd = "r <- correlation(v1,v2, \"spearman\")";
				  r = doREval(cmd).asDouble();
				}
			  
			}
			
			
			//String cmd = CorrelationTaskR.getRgroupCmd("GRP1", corrRequest.getVector1().getValues());
//			doRvoidEval(cmd);
//			
//			cmd = CorrelationTaskR.getRgroupCmd("GRP2", corrRequest.getVector2().getValues());
//			doRvoidEval(cmd);
			
			//REXP rVal = null;
			
//			if (corrRequest.getCorrelationType() == CorrelationType.PEARSON) {
//			  cmd = "r <- correlation(GRP1,GRP2,\"pearson\")";
//			  rVal = doREval(cmd);
//			}
//			else if (corrRequest.getCorrelationType() == CorrelationType.SPEARMAN) {
//			  cmd = "r <- correlation(GRP1,GRP2, \"spearman\")";
//			  rVal = doREval(cmd);
//			}
//			else {
//			  throw new AnalysisServerException("Unrecognized correlationType or correlation type is null.");
//			}
	
			//double r = rVal.asDouble();
			result.setCorrelationValue(r);
			//result.setVector1(vec1);
			//result.setVector2(vec2);
			result.setDataPoints(points);
		}
		catch (AnalysisServerException asex) {
			AnalysisServerException aex = new AnalysisServerException(
			"Problem computing correlation. Caught AnalysisServerException in CorrelationTaskR." + asex.getMessage());
	        aex.setFailedRequest(corrRequest);
	        setException(aex);
	        logger.error("Caught AnalysisServerException");
	        logger.error(asex);
	        return;  
		}
		catch (Exception ex) {
			AnalysisServerException asex = new AnalysisServerException(
			"Internal Error. Caught Exception in CorrelationTaskR exClass=" + ex.getClass() + " msg=" + ex.getMessage());
	        asex.setFailedRequest(corrRequest);
	        setException(asex);
	        logger.error("Caught Exception in CorrelationTaskR");
	        StringWriter sw = new StringWriter();
	        PrintWriter pw  = new PrintWriter(sw);
	        ex.printStackTrace(pw);
	        logger.error(sw.toString());
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
	
	/**
	 * 
	 * @param points
	 * @param useX
	 * @param useY
	 * @return Quoted string for use in R command
	 */
	private String getDataString(List<DataPoint> points, boolean useX, boolean useY) {
	  StringBuffer sb = new StringBuffer();
	  DataPoint point;
	  for (Iterator i=points.iterator(); i.hasNext(); ) {
		 point = (DataPoint) i.next();
		 if (useX) {
		   sb.append("\"").append(point.getX()).append("\"");
		 } 
		 
		 
		 if (useY) {
		   sb.append("\"").append(point.getY()).append("\"");
		 }
		 
		 if (i.hasNext()) {
		   sb.append(",");
		 }
		 else {
		   sb.append(")");	 
		 }		
	  }
	  return sb.toString();
      
	}
	  
}
