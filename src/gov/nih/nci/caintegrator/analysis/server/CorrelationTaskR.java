package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationResult;
import gov.nih.nci.caintegrator.analysis.messaging.DataPoint;
import gov.nih.nci.caintegrator.analysis.messaging.PCAresultEntry;
import gov.nih.nci.caintegrator.analysis.messaging.ReporterInfo;
import gov.nih.nci.caintegrator.enumeration.AxisType;
import gov.nih.nci.caintegrator.enumeration.CorrelationType;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

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
			
			
			
			//get the submatrix of the patients specified
			List<String> sampleIds = corrRequest.getSampleIds();
			String sampleIdscmd = getRgroupCmd("sampleIds", sampleIds);
			String cmd;
			Double r = -1.0;
			
			cmd = "SM <- getSubmatrix.onegrp(dataMatrix, sampleIds)";
		
			
			if ((reporter1 != null) && (reporter2 != null)) {
			  //CASE 1:  correlation between two reporters
			  result.setGroup1Name(reporter1.getGeneSymbol() + "_" + reporter1.getReporterName());
			  result.setGroup2Name(reporter2.getGeneSymbol() + "_" + reporter2.getReporterName());
			  
			  Map <String, DataPoint> vecMap = new HashMap<String, DataPoint>();
			  setDataPoints(reporter1,sampleIds,vecMap, AxisType.X_AXIS, true);
			  setDataPoints(reporter2,sampleIds,vecMap, AxisType.Y_AXIS, false);
			   
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
			else if ((reporter1!=null) && (reporter2==null)) {
			  //CASE2 : a reporter against a vector
			  Map <String, DataPoint> vecMap = new HashMap<String, DataPoint>();
			  setDataPoints(reporter1,sampleIds,vecMap, AxisType.X_AXIS, true);
			  
			  //get the vector
//			  DoubleVector vec1 = corrRequest.getVector1();
//			  vec1.
				
			}
			else if ((reporter1==null) && (reporter2!=null) ) {
			  Map <String, DataPoint> vecMap = new HashMap<String, DataPoint>();
			  setDataPoints(reporter2,sampleIds,vecMap, AxisType.X_AXIS, true);
			}
			else { 
			  //handle the two vector case
				
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
		    asex.setFailedRequest(corrRequest);
	        setException(asex);
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

	private void setDataPoints(ReporterInfo reporter, List<String> sampleIds, Map<String, DataPoint> pointMap, AxisType axis, boolean createNewPoints) throws AnalysisServerException{
		
		try {
			
			List<DataPoint> retList = new ArrayList<DataPoint>();
			setDataFile(reporter.getDataFileName());
		
			String sampleIdscmd = getRgroupCmd("sampleIds", sampleIds);
			doRvoidEval(sampleIdscmd);
			
			String cmd = "SM <- getSubmatrix.onegrp(dataMatrix, sampleIds)";
			doRvoidEval(cmd);			  
			cmd = "RM <- getSubmatrix.rep(SM,\"" + reporter.getReporterName() + "\")";
			double[] vec =  doREval(cmd).asDoubleArray();
			//need to make sure that the vectors are in the same order wrt sample ids
			cmd = "RMlabels <- dimnames(RM)[[1]]";
			Vector RM_ids = doREval(cmd).asVector();
			
		    if (RM_ids == null) {
		      throw new AnalysisServerException("Reporter " + reporter.getReporterName() + " not found in data file=" + reporter.getDataFileName() + ".");
		    }
			
			DataPoint point;
			String id;
			REXP exp;
			for (int i=0; i < RM_ids.size(); i++) {
			  exp = (REXP) RM_ids.get(i);
			  id = exp.asString();
			  
			  logger.info("Adding data point with id=" + id);
			  
			  point = pointMap.get(id);
			  if (point == null) {
				if (createNewPoints) {
			      point = new DataPoint(id);
			      pointMap.put(id, point);
				}
				else {
				  logger.warn("Could not find id=" + id + " in point map reporter=" + reporter.getReporterName());
				  continue;
				}
			  }
			   
			  if (axis == AxisType.X_AXIS) {
			    point.setX(vec[i]);
			  }
			  else if (axis == AxisType.Y_AXIS) {
			    point.setY(vec[i]);
			  }
			  else if (axis == AxisType.Z_AXIS) {
			    point.setZ(vec[i]);
			  }
			}
		
		} 
		catch (Exception ex2) {
			logger.error("Caught exception in setDataPoints method for reporter=" + reporter);
			logger.error(ex2);
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
