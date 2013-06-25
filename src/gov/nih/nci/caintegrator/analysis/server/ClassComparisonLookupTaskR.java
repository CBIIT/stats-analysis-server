/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonLookupRequest;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonResult;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonResultEntry;
import gov.nih.nci.caintegrator.analysis.messaging.ReporterGroup;
import gov.nih.nci.caintegrator.analysis.messaging.SampleGroup;
import gov.nih.nci.caintegrator.enumeration.MultiGroupComparisonAdjustmentType;
import gov.nih.nci.caintegrator.enumeration.StatisticalMethodType;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.REXP;

/**
 * Performs the class comparison computation using R.
 * 
 * @author harrismic
 *
 */




public class ClassComparisonLookupTaskR extends AnalysisTaskR {

	private ClassComparisonResult ccResult = null;
	private Comparator classComparisonComparator = new ClassComparisonComparator();
	public static final int MIN_GROUP_SIZE = 3;
	
	private static Logger logger = Logger.getLogger(ClassComparisonLookupTaskR.class);

	public ClassComparisonLookupTaskR(ClassComparisonLookupRequest request) {
		this(request, false);
	}

	public ClassComparisonLookupTaskR(ClassComparisonLookupRequest request,
			boolean debugRcommands) {
		super(request, debugRcommands);
	}

	public void run() {

	
		ClassComparisonLookupRequest ccLookupRequest = (ClassComparisonLookupRequest) getRequest();
		
		ccResult = new ClassComparisonResult(ccLookupRequest.getSessionId(), ccLookupRequest.getTaskId());

		logger.info(getExecutingThreadName() + ": processing class comparison lookup request=" + ccLookupRequest);

		
		
		//set the data file
//		check to see if the data file on the compute connection is the 
		//same as that for the analysis task
		
		
		try {
			setDataFile(ccLookupRequest.getDataFileName());
		} catch (AnalysisServerException e) {
			e.setFailedRequest(ccLookupRequest);
			logger.error("Internal Error. Error setting data file to fileName=" + ccLookupRequest.getDataFileName());
			logStackTrace(logger, e);
			setException(e);
			return;
		}
		
		
		SampleGroup group1 = ccLookupRequest.getGroup1();
		SampleGroup baselineGroup = ccLookupRequest.getBaselineGroup();
		ReporterGroup reporterGroup = ccLookupRequest.getReporterGroup();
		
		if ((group1 == null) || (group1.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"Group1 is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(ccLookupRequest);
		      setException(ex);
		      logger.error(ex.getMessage());
		      return;
		}
		
		
		if ((baselineGroup == null) || (baselineGroup.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"BaselineGroup is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(ccLookupRequest);
		      setException(ex);
		      logger.error(ex.getMessage());
		      return;
		}
		
		if ((reporterGroup == null) || (reporterGroup.isEmpty())) {
			AnalysisServerException ex = new AnalysisServerException(
				"ReporterGroup is null or empty.");		 
			     ex.setFailedRequest(ccLookupRequest);
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
				      ex.setFailedRequest(ccLookupRequest);
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
	
		

		try {
	
			//get the submatirx based on the reporter group
			
			rCmd = getRgroupCmd("reporterIds", reporterGroup);
						
			doRvoidEval(rCmd);
			rCmd = "ccInputMatrix <- getSubmatrix.repNew(dataMatrix, reporterIds)";
			doRvoidEval(rCmd);
			
			
			//Get the group1 ids
			rCmd = getRgroupCmd(grp1RName, group1);
			doRvoidEval(rCmd);
			
			if (baselineGroup != null) {
				// two group comparison
				baselineGrpLen = baselineGroup.size();
				
				rCmd = getRgroupCmd(baselineGrpRName, baselineGroup);
				doRvoidEval(rCmd);
	
				// create the input data matrix using the sample groups
				rCmd = "ccInputMatrix <- getSubmatrix.twogrps(ccInputMatrix,"
						+ grp1RName + "," + baselineGrpRName + ")";
				doRvoidEval(rCmd);
	
				// check to make sure all identifiers matched in the R data file
				rCmd = "dim(ccInputMatrix)[2]";
				int numMatched = doREval(rCmd).asInt();
				if (numMatched != (grp1Len + baselineGrpLen)) {
					AnalysisServerException ex = new AnalysisServerException(
							"Some sample ids did not match R data file for class comparison request.");
					ex.setFailedRequest(ccLookupRequest);
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
				ex.setFailedRequest(ccLookupRequest);
				ex.setFailedRequest(ccLookupRequest);
				setException(ex);
				logger.error(ex.getMessage());
				return;
			}
	
			if (ccLookupRequest.getStatisticalMethod() == StatisticalMethodType.TTest) {
				// do the TTest computation
				rCmd = "ccResult <- myttest(ccInputMatrix, " + grp1Len + ","
						+ baselineGrpLen + ")";
				doRvoidEval(rCmd);
			} else if (ccLookupRequest.getStatisticalMethod() == StatisticalMethodType.Wilcoxin) {
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
			double foldChangeThreshold = ccLookupRequest.getFoldChangeThreshold();
			double pValueThreshold = ccLookupRequest.getPvalueThreshold();
			MultiGroupComparisonAdjustmentType adjMethod = ccLookupRequest
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
	
			double[] meanGrp1 = doREval("mean1 <- ccResult$mean1").asDoubleArray();
			double[] meanBaselineGrp = doREval("meanBaseline <- ccResult$mean2").asDoubleArray();
			double[] meanDif = doREval("meanDif <- ccResult$mean.dif").asDoubleArray();
			double[] absoluteFoldChange = doREval("fc <- ccResult$fc").asDoubleArray();
			double[] pva = doREval("pva <- ccResult$pval").asDoubleArray();
			double[] stdG1 = doREval("stdG1 <- ccResult$std1").asDoubleArray();
			double[] stdBaseline = doREval("stdBL <- ccResult$std2").asDoubleArray();
			
			
			// get the labels
			Vector reporterIds = doREval("ccLabels <- dimnames(ccResult)[[1]]")
					.asVector();
	
			// load the result object
			// need to see if this works for single group comparison
			List<ClassComparisonResultEntry> resultEntries = new ArrayList<ClassComparisonResultEntry>(
					reporterGroup.size());
			ClassComparisonResultEntry resultEntry;
			
			//logger.debug("reporterGroup.size=" + reporterGroup.size());
			//logger.debug("reporterIds.size=" + reporterIds.size());
			//logger.debug("meanBaselineGrp.size=" + meanBaselineGrp.length);
			//logger.debug("meanDiff.size=" + meanDif.length);
	
			int numReporters = reporterGroup.size();
			
			for (int i = 0; i < numReporters; i++) {
				resultEntry = new ClassComparisonResultEntry();
				
				if (numReporters == 1) {
				  resultEntry.setReporterId(reporterGroup.getIdsAsCommaDelimitedString());
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
	        aex.setFailedRequest(ccLookupRequest);
	        setException(aex);
	        logStackTrace(logger, asex);
	        return;  
		}
		catch (Exception ex) {
			AnalysisServerException asex = new AnalysisServerException(
			"Internal Error. Caught AnalysisServerException in ClassComparisonTaskR." + ex.getMessage());
	        asex.setFailedRequest(ccLookupRequest);
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
