/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.*;

import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonRequest;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonResult;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonResultEntry;
import gov.nih.nci.caintegrator.analysis.messaging.SampleGroup;
import gov.nih.nci.caintegrator.enumeration.*;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.*;

/**
 * Performs the class comparison computation using R.
 * 
 * @author harrismic
 *
 */




public class ClassComparisonTaskR extends AnalysisTaskR {

	private ClassComparisonResult ccResult = null;
	private Comparator classComparisonComparator = new ClassComparisonComparator();
	public static final int MIN_GROUP_SIZE = 3;
	
	private static Logger logger = Logger.getLogger(ClassComparisonTaskR.class);

	public ClassComparisonTaskR(ClassComparisonRequest request) {
		this(request, false);
	}

	public ClassComparisonTaskR(ClassComparisonRequest request,
			boolean debugRcommands) {
		super(request, debugRcommands);
	}

	public void run() {

	
		
		ClassComparisonRequest ccRequest = (ClassComparisonRequest) getRequest();
		
		ccResult = new ClassComparisonResult(ccRequest.getSessionId(), ccRequest.getTaskId());

		logger.info(getExecutingThreadName() + ": processing class comparison request=" + ccRequest);

		
		
		//set the data file
//		check to see if the data file on the compute connection is the 
		//same as that for the analysis task
		
		
		try {
			setDataFile(ccRequest.getDataFileName());
		} catch (AnalysisServerException e) {
			e.setFailedRequest(ccRequest);
			logger.error("Internal Error. Error setting data file to fileName=" + ccRequest.getDataFileName());
			logStackTrace(logger, e);
			setException(e);
			return;
		}
		
		
		SampleGroup group1 = ccRequest.getGroup1();
		SampleGroup baselineGroup = ccRequest.getBaselineGroup();
		
		if ((group1 == null) || (group1.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"Group1 is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(ccRequest);
		      setException(ex);
		      logger.error(ex.getMessage());
		      return;
		}
		
		
		if ((baselineGroup == null) || (baselineGroup.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"BaselineGroup is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(ccRequest);
		      setException(ex);
		      logger.error(ex.getMessage());
		      return;
		}
		
		
		//check to see if there are any overlapping samples between the two groups
		if ((group1 != null)&&(baselineGroup != null)) {
		  
		  //get overlap between the two sets
		  Set<String> intersection = new HashSet<String>();
		  intersection.addAll(group1);
		  intersection.retainAll(baselineGroup);
		  
		  if (intersection.size() > 0) {
		     //the groups are overlapping so return an exception
			 StringBuffer ids = new StringBuffer();
			 for (Iterator i=intersection.iterator(); i.hasNext(); ) {
			   ids.append(i.next());
			   if (i.hasNext()) {
			     ids.append(",");
			   }
			 }
			 
			 AnalysisServerException ex = new AnalysisServerException(
				      "Can not perform class comparison with overlapping groups. Overlapping ids=" + ids.toString());		 
				      ex.setFailedRequest(ccRequest);
				      setException(ex);
				      logger.error(ex.getMessage());
				      return;   
		  }
		}
		
		//For now assume that there are two groups. When we get data for two channel array then
		//allow only one group so leaving in the possiblity of having only one group in the code 
		//below eventhough the one group case won't be executed because of the tests above.
		
		
		int grp1Len = 0, baselineGrpLen = 0;
		
		grp1Len = group1.size();
		
		String grp1RName = "GRP1IDS";
		String baselineGrpRName = "BLGRPIDS";
		
		
		String rCmd = null;
	
		rCmd = getRgroupCmd(grp1RName, group1);

		try {
		
			doRvoidEval(rCmd);
	
			if (baselineGroup != null) {
				// two group comparison
				baselineGrpLen = baselineGroup.size();
				
				rCmd = getRgroupCmd(baselineGrpRName, baselineGroup);
				doRvoidEval(rCmd);
	
				// create the input data matrix using the sample groups
				rCmd = "ccInputMatrix <- getSubmatrix.twogrps(dataMatrix,"
						+ grp1RName + "," + baselineGrpRName + ")";
				doRvoidEval(rCmd);
	
				// check to make sure all identifiers matched in the R data file
				rCmd = "dim(ccInputMatrix)[2]";
				int numMatched = doREval(rCmd).asInt();
				if (numMatched != (grp1Len + baselineGrpLen)) {
					AnalysisServerException ex = new AnalysisServerException(
							"Some sample ids did not match R data file for class comparison request.");
					ex.setFailedRequest(ccRequest);
					setException(ex);
					logger.error(ex.getMessage());
					return;
				}
			} else {
				// single group comparison
//				baselineGrpLen = 0;
//				rCmd = "ccInputMatrix <- getSubmatrix.onegrp(dataMatrix,"
//						+ grp1RName + ")";
//				doRvoidEval(rCmd);
				logger.error("Single group comparison is not currently supported.");
				throw new AnalysisServerException("Unsupported operation: Attempted to do a single group comparison.");
			}
	
			rCmd = "dim(ccInputMatrix)[2]";
			int numMatched = doREval(rCmd).asInt();
			if (numMatched != (grp1Len + baselineGrpLen)) {
				AnalysisServerException ex = new AnalysisServerException(
						"Some sample ids did not match R data file for class comparison request.");
				ex.setFailedRequest(ccRequest);
				ex.setFailedRequest(ccRequest);
				setException(ex);
				logger.error(ex.getMessage());
				return;
			}
	
			if (ccRequest.getStatisticalMethod() == StatisticalMethodType.TTest) {
				// do the TTest computation
				rCmd = "ccResult <- myttest(ccInputMatrix, " + grp1Len + ","
						+ baselineGrpLen + ")";
				doRvoidEval(rCmd);
			} else if (ccRequest.getStatisticalMethod() == StatisticalMethodType.Wilcoxin) {
				// do the Wilcox computation
				rCmd = "ccResult <- mywilcox(ccInputMatrix, " + grp1Len + ","
						+ baselineGrpLen + ")";
				doRvoidEval(rCmd);
			}
			else {
			  logger.error("ClassComparision unrecognized statistical method.");
			  this.setException(new AnalysisServerException("Internal error: unrecognized adjustment type."));
			  return;
			}
	
			// do filtering
			double foldChangeThreshold = ccRequest.getFoldChangeThreshold();
			double pValueThreshold = ccRequest.getPvalueThreshold();
			MultiGroupComparisonAdjustmentType adjMethod = ccRequest
					.getMultiGroupComparisonAdjustmentType();
			if (adjMethod == MultiGroupComparisonAdjustmentType.NONE) {
				// get differentially expressed reporters using
				// unadjusted Pvalue
	
				// shouldn't need to pass in ccInputMatrix
				rCmd = "ccResult  <- mydiferentiallygenes(ccResult,"
						+ foldChangeThreshold + "," + pValueThreshold + ")";
				doRvoidEval(rCmd);
				ccResult.setPvaluesAreAdjusted(false);
			} else if (adjMethod == MultiGroupComparisonAdjustmentType.FDR) {
				// do adjustment
				rCmd = "adjust.result <- adjustP.Benjamini.Hochberg(ccResult)";
				doRvoidEval(rCmd);
				// get differentially expressed reporters using adjusted Pvalue
				rCmd = "ccResult  <- mydiferentiallygenes.adjustP(adjust.result,"
						+ foldChangeThreshold + "," + pValueThreshold + ")";
				doRvoidEval(rCmd);
				ccResult.setPvaluesAreAdjusted(true);
			} else if (adjMethod == MultiGroupComparisonAdjustmentType.FWER) {
				// do adjustment
				rCmd = "adjust.result <- adjustP.Bonferroni(ccResult)";
				doRvoidEval(rCmd);
				// get differentially expresseed reporters using adjusted Pvalue
				rCmd = "ccResult  <- mydiferentiallygenes.adjustP(adjust.result,"
						+ foldChangeThreshold + "," + pValueThreshold + ")";
				doRvoidEval(rCmd);
				ccResult.setPvaluesAreAdjusted(true);
			}
			else {
				logger.error("ClassComparision Adjustment Type unrecognized.");
				this.setException(new AnalysisServerException("Internal error: unrecognized adjustment type."));
				return;
			}
	
			// get the results and send
	
//			double[] meanGrp1 = doREval("mean1 <- ccResult[,1]").asDoubleArray();
//			double[] meanBaselineGrp = doREval("meanBaseline <- ccResult[,2]").asDoubleArray();
//			double[] meanDif = doREval("meanDif <- ccResult[,3]").asDoubleArray();
//			double[] absoluteFoldChange = doREval("fc <- ccResult[,4]").asDoubleArray();
//			double[] pva = doREval("pva <- ccResult[,5]").asDoubleArray();
	
//			double[] meanGrp1 = doREval("mean1 <- ccResult$mean1").asDoubleArray();
//			double[] meanBaselineGrp = doREval("meanBaseline <- ccResult$mean2").asDoubleArray();
//			double[] meanDif = doREval("meanDif <- ccResult$mean.dif").asDoubleArray();
			
			double[] meanGrp1 = doREval("mean1 <- ccResult[,1]").asDoubleArray();
			double[] meanBaselineGrp = doREval("meanBaseline <- ccResult[,2]").asDoubleArray();
			double[] meanDif = doREval("meanDif <- ccResult[,3]").asDoubleArray();
			double[] absoluteFoldChange = doREval("fc <- ccResult[,4]").asDoubleArray();
			double[] pva = doREval("pva <- ccResult[,5]").asDoubleArray();
			double[] stdG1 = doREval("stdG1 <- ccResult$std1").asDoubleArray();
			double[] stdBaseline = doREval("stdBL <- ccResult$std2").asDoubleArray();
			
			//double[] absoluteFoldChange = doREval("fc <- ccResult$fc").asDoubleArray();
			//double[] pva = doREval("pva <- ccResult$pval").asDoubleArray();
			//double[] pva = doREval("pva <- ccResult[,5]").asDoubleArray();
			
			
			
			// get the labels
			Vector reporterIds = doREval("ccLabels <- dimnames(ccResult)[[1]]")
					.asVector();
	
			// load the result object
			// need to see if this works for single group comparison
			List<ClassComparisonResultEntry> resultEntries = new ArrayList<ClassComparisonResultEntry>(
					meanGrp1.length);
			ClassComparisonResultEntry resultEntry;
	
			logger.info("meanGrp1.length=" + meanGrp1.length);
			
			if (reporterIds==null) {
			  logger.error(">> reporterIds vector is null <<");	
			  String reporterId = doREval("rId <- dimnames(ccResult)[[1]]").asString();
			  logger.error(">> Work around method reporter id=" + reporterId);
			}
			else {
			  logger.info("reporterIds vector.length=" + reporterIds.size());
			}
			
			for (int i = 0; i < meanGrp1.length; i++) {
				resultEntry = new ClassComparisonResultEntry();
				
				if (meanGrp1.length == 1) {
				   //had to do this work around because rServce doesn't seem to handle
				   //vectors of size one correctly
				   String reporterId = doREval("rId <- dimnames(ccResult)[[1]]").asString();
				   resultEntry.setReporterId(reporterId);
				}
				else {
				   resultEntry.setReporterId(((REXP) reporterIds.get(i)).asString());
				}
				
				resultEntry.setMeanGrp1(meanGrp1[i]);
				resultEntry.setMeanBaselineGrp(meanBaselineGrp[i]);
				resultEntry.setMeanDiff(meanDif[i]);
				resultEntry.setAbsoluteFoldChange(absoluteFoldChange[i]);
				resultEntry.setPvalue(pva[i]);
				resultEntry.setStdGrp1(stdG1[i]);
				resultEntry.setStdBaselineGrp(stdBaseline[i]);
				resultEntries.add(resultEntry);
			}
				
				
			
			
			Collections.sort(resultEntries, classComparisonComparator);
	
			ccResult.setResultEntries(resultEntries);
	
			ccResult.setGroup1(group1);
			if (baselineGroup != null) {
				ccResult.setBaselineGroup(baselineGroup);
			}
		}
		catch (AnalysisServerException asex) {
			AnalysisServerException aex = new AnalysisServerException(
			"Internal Error. Caught AnalysisServerException in ClassComparisonTaskR." + asex.getMessage());
	        aex.setFailedRequest(ccRequest);
	        setException(aex);
	        logStackTrace(logger, asex);
	        return;  
		}
		catch (Exception ex) {
			AnalysisServerException asex = new AnalysisServerException(
			"Internal Error. Caught AnalysisServerException in ClassComparisonTaskR." + ex.getMessage());
	        asex.setFailedRequest(ccRequest);
	        setException(asex);	        
	        logStackTrace(logger, ex);
	        return;  
		}
	}

	public AnalysisResult getResult() {
		return ccResult;
	}

	public ClassComparisonResult getClassComparisonResult() {
		return ccResult;
	}

	/**
	 * Clean up some of the resources
	 */
	public void cleanUp() {
		//doRvoidEval("remove(ccInputMatrix)");
		//doRvoidEval("remove(ccResult)");
		try {
			setRComputeConnection(null);
		} catch (AnalysisServerException e) {
			logger.error("Error in cleanUp method.");
			logStackTrace(logger, e);
			setException(e);
		}
	}
}
