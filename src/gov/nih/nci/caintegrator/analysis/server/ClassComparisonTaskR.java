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

		SampleGroup group1 = ccRequest.getGroup1();
		SampleGroup group2 = ccRequest.getGroup2();
		
		if ((group1 == null) || (group1.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"Group1 is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(ccRequest);
		      setException(ex);
		      return;
		}
		
		
		if ((group2 == null) || (group2.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"Group2 is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(ccRequest);
		      setException(ex);
		      return;
		}
		
		
		//check to see if there are any overlapping samples between the two groups
		if ((group1 != null)&&(group2 != null)) {
		  
		  //get overlap between the two sets
		  Set<String> intersection = new HashSet<String>();
		  intersection.addAll(group1);
		  intersection.retainAll(group2);
		  
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
				      return;   
		  }
		}
		
		//For now assume that there are two groups. When we get data for two channel array then
		//allow only one group so leaving in the possiblity of having only one group in the code 
		//below eventhough the one group case won't be executed because of the tests above.
		
		
		int grp1Len = 0, grp2Len = 0;
		
		grp1Len = group1.size();
		
		String grp1RName = "GRP1IDS";
		String grp2RName = "GRP2IDS";
		
		
		String rCmd = null;
	
		rCmd = getRgroupCmd(grp1RName, group1);

		doRvoidEval(rCmd);

		if (group2 != null) {
			// two group comparison
			grp2Len = group2.size();
			
			rCmd = getRgroupCmd(grp2RName, group2);
			doRvoidEval(rCmd);

			// create the input data matrix using the sample groups
			rCmd = "ccInputMatrix <- getSubmatrix.twogrps(dataMatrix,"
					+ grp1RName + "," + grp2RName + ")";
			doRvoidEval(rCmd);

			// check to make sure all identifiers matched in the R data file
			rCmd = "dim(ccInputMatrix)[2]";
			int numMatched = doREval(rCmd).asInt();
			if (numMatched != (grp1Len + grp2Len)) {
				AnalysisServerException ex = new AnalysisServerException(
						"Some sample ids did not match R data file for class comparison request.");
				ex.setFailedRequest(ccRequest);
				setException(ex);
				return;
			}
		} else {
			// single group comparison
			grp2Len = 0;
			rCmd = "ccInputMatrix <- getSubmatrix.onegrp(dataMatrix,"
					+ grp1RName + ")";
			doRvoidEval(rCmd);
		}

		rCmd = "dim(ccInputMatrix)[2]";
		int numMatched = doREval(rCmd).asInt();
		if (numMatched != (grp1Len + grp2Len)) {
			AnalysisServerException ex = new AnalysisServerException(
					"Some sample ids did not match R data file for class comparison request.");
			ex.setFailedRequest(ccRequest);
			ex.setFailedRequest(ccRequest);
			setException(ex);
			return;
		}

		if (ccRequest.getStatisticalMethod() == StatisticalMethodType.TTest) {
			// do the TTest computation
			rCmd = "ccResult <- myttest(ccInputMatrix, " + grp1Len + ","
					+ grp2Len + ")";
			doRvoidEval(rCmd);
		} else if (ccRequest.getStatisticalMethod() == StatisticalMethodType.Wilcoxin) {
			// do the Wilcox computation
			rCmd = "ccResult <- mywilcox(ccInputMatrix, " + grp1Len + ","
					+ grp2Len + ")";
			doRvoidEval(rCmd);
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

		// get the results and send

		double[] meanGrp1 = doREval("mean1 <- ccResult[,1]").asDoubleArray();
		double[] meanGrp2 = doREval("mean2 <- ccResult[,2]").asDoubleArray();
		double[] meanDif = doREval("meanDif <- ccResult[,3]").asDoubleArray();
		double[] foldChange = doREval("fc <- ccResult[,4]").asDoubleArray();
		double[] pva = doREval("pva <- ccResult[,5]").asDoubleArray();

		// get the labels
		Vector reporterIds = doREval("ccLabels <- dimnames(ccResult)[[1]]")
				.asVector();

		// load the result object
		// need to see if this works for single group comparison
		List<ClassComparisonResultEntry> resultEntries = new ArrayList<ClassComparisonResultEntry>(
				meanGrp1.length);
		ClassComparisonResultEntry resultEntry;

		for (int i = 0; i < meanGrp1.length; i++) {
			resultEntry = new ClassComparisonResultEntry();
			resultEntry.setReporterId(((REXP) reporterIds.get(i)).asString());
			resultEntry.setMeanGrp1(meanGrp1[i]);
			resultEntry.setMeanGrp2(meanGrp2[i]);
			resultEntry.setMeanDiff(meanDif[i]);
			resultEntry.setFoldChange(foldChange[i]);
			resultEntry.setPvalue(pva[i]);
			resultEntries.add(resultEntry);
		}
		
		
		Collections.sort(resultEntries, classComparisonComparator);

		ccResult.setResultEntries(resultEntries);

		ccResult.setGroup1(group1);
		if (group2 != null) {
			ccResult.setGroup2(group2);
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
		setRconnection(null);
	}
}
