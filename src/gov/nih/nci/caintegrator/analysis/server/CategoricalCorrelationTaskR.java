package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.CategoricalCorrelationRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CategoricalCorrelationResult;
import gov.nih.nci.caintegrator.analysis.messaging.DataPoint;
import gov.nih.nci.caintegrator.analysis.messaging.DataPointVector;
import gov.nih.nci.caintegrator.analysis.messaging.ReporterInfo;
import gov.nih.nci.caintegrator.analysis.messaging.SampleGroup;
import gov.nih.nci.caintegrator.enumeration.AxisType;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.REXP;

public class CategoricalCorrelationTaskR extends AnalysisTaskR {

	private CategoricalCorrelationResult result;
	
	private static Logger logger = Logger.getLogger(CategoricalCorrelationTaskR.class);
	private CategoricalCorrelationRequest corrRequest;

	
	public CategoricalCorrelationTaskR(AnalysisRequest request) {
		super(request);
	}

	public CategoricalCorrelationTaskR(AnalysisRequest request, boolean debugRcommands) {
		super(request, debugRcommands);
		
	}

	@Override
	public void run() {
		corrRequest = (CategoricalCorrelationRequest) getRequest();
		result = new CategoricalCorrelationResult(getRequest().getSessionId(), getRequest().getTaskId());
		logger.info(getExecutingThreadName() + " processing correlation request="
						+ corrRequest);
							
		try {
			if ((corrRequest.getDataVectors()!=null)&&(!corrRequest.getDataVectors().isEmpty())) {
			  //should probably create a new copy and set 
			  //compute group stats if needed for now just return.
			  result.setDataVectors(corrRequest.getDataVectors());
			  return;
			}
			
			//no data vectors were send to look up reporters 
			List<ReporterInfo> reporters = corrRequest.getReporters();
			DataPointVector dpv ;
			for (ReporterInfo ri : reporters) {
			  dpv = getDataPointVector(ri, AxisType.X_AXIS);
			  if (dpv != null) {
			    result.addDataVector(dpv);
			  }
			  else {
			    logger.error("Error getting data point vector for reporter =" + ri.getReporterName());
			    return;
			  }
			}
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

	/**
	 * Get the expression values for the reporter specified in the ReporterInfo object.
	 * @param ri
	 * @param axis
	 * @return
	 */
	private DataPointVector getDataPointVector(ReporterInfo ri, AxisType axis) {
		
		try {
			DataPointVector dpVector = new DataPointVector(ri.getSampleGroup().getGroupName());
			setDataFile(ri.getDataFileName());
		    String cmd;
		    SampleGroup sampleIds = ri.getSampleGroup();
		
			if ((sampleIds != null)&&(!sampleIds.isEmpty())) {
			  String sampleIdscmd = getRgroupCmd("sampleIds", sampleIds);
			  doRvoidEval(sampleIdscmd);
			
			  cmd = "SM <- getSubmatrix.onegrp(dataMatrix, sampleIds)";
			  doRvoidEval(cmd);			  
			}
			else {
			  //use the entire data matrix without restricting on samples
			  cmd = "SM <- dataMatrix";
			  doRvoidEval(cmd);
			}
			
			
			cmd = "RM <- getSubmatrix.rep(SM,\"" + ri.getReporterName() + "\")";
			double[] vec =  doREval(cmd).asDoubleArray();
			//need to make sure that the vectors are in the same order wrt sample ids
			cmd = "RMlabels <- dimnames(RM)[[1]]";
			Vector RM_ids = doREval(cmd).asVector();
			
		    if (RM_ids == null) {
		      throw new AnalysisServerException("No expression data found for reporter " + ri.getReporterName() + " in data file=" + ri.getDataFileName() + ".");
		    }
			
			DataPoint point;
			String id;
			REXP exp;
			for (int i=0; i < RM_ids.size(); i++) {
			  exp = (REXP) RM_ids.get(i);
			  id = exp.asString();
			  
			  //logger.info("Adding data point with id=" + id);
			  point = new DataPoint(id);	
			  
			  if (axis == AxisType.X_AXIS) {
			    point.setX(vec[i]);
			  }
			  else if (axis == AxisType.Y_AXIS) {
			    point.setY(vec[i]);
			  }
			  else if (axis == AxisType.Z_AXIS) {
			    point.setZ(vec[i]);
			  }
			  dpVector.addDataPoint(point);
			}
			return dpVector;
		}
		catch (AnalysisServerException asex) {
		    asex.setFailedRequest(corrRequest);
	        setException(asex);
	        logger.error("Caught AnalysisServerException");
	        logger.error(asex);
	        return null;  
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
	        return null;  
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
	  boolean pointAppended = false;
	  for (Iterator i=points.iterator(); i.hasNext(); ) {
		 point = (DataPoint) i.next();
		 
		 if ((point.getX()!=null)&&(point.getY()!=null)) {
			 if (pointAppended) {
		       sb.append(",");
			 }
			 if (useX) {
			   sb.append("\"").append(point.getX()).append("\"");
			 } 
			 
			 
			 if (useY) {
			   sb.append("\"").append(point.getY()).append("\"");
			 }
			 pointAppended = true;
		 }
	  }
	  sb.append(")");
	  return sb.toString();
      
	}
	  
}
