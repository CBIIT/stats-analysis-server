package gov.nih.nci.caintegrator.analysis.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.REXP;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonResultEntry;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationResult;
import gov.nih.nci.caintegrator.analysis.messaging.FTestRequest;
import gov.nih.nci.caintegrator.analysis.messaging.FTestResult;
import gov.nih.nci.caintegrator.analysis.messaging.FTestResultEntry;
import gov.nih.nci.caintegrator.analysis.messaging.SampleGroup;
import gov.nih.nci.caintegrator.enumeration.CorrelationType;
import gov.nih.nci.caintegrator.enumeration.MultiGroupComparisonAdjustmentType;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

public class FTestTaskR extends AnalysisTaskR {

    private FTestResult result;
	private static Logger logger = Logger.getLogger(FTestTaskR.class);
	private Comparator ftComparator = new FTestComparator();

	public FTestTaskR(AnalysisRequest request) {
		this(request, false);
	}

	public FTestTaskR(AnalysisRequest request, boolean debugRcommands) {
		super(request, debugRcommands);
	}

	@Override
	public void run() {
		FTestRequest ftRequest = (FTestRequest) getRequest();
		result = new FTestResult(getRequest().getSessionId(), getRequest().getTaskId());
		logger.info(getExecutingThreadName() + " processing FTestRequest request="
						+ ftRequest);
		
		
		try {
			
			String dataFileName = ftRequest.getDataFileName();
			
			if (dataFileName != null) {
			  setDataFile(ftRequest.getDataFileName());
			}
			else {
			  throw new AnalysisServerException("Null data file name");
			}
			
		} catch (AnalysisServerException e) {
			e.setFailedRequest(ftRequest);
			logger.error("Internal Error. " + e.getMessage());
			setException(e);
			return;
		}

		
		try {
		
			List<SampleGroup> sampleGroups = ftRequest.getSampleGroups();
			SampleGroup grp = null;
			String cmd;
			String cmd2;
			String grpName = null;
			String bindCmd = "compMat <- cbind(";
		    String matName = null;
		    String phenoCmd = "pheno <- as.factor(c(";
		    
		    result.setSampleGroups(sampleGroups);
		    
			for (int i=0; i < sampleGroups.size(); i++) {
			  grp = (SampleGroup)sampleGroups.get(i);
			  grpName = "GRP" + i;
			  cmd = getRgroupCmd(grpName, grp);
			  doRvoidEval(cmd);
			  matName = "M" + grpName;
			  cmd = matName + " <- getSubmatrix.onegrp(dataMatrix," + grpName + ")";
			  doRvoidEval(cmd);
			 
			  bindCmd += matName;
			  
			  phenoCmd += "rep(" + i + "," + grp.size() + ")";
			  
			  
			  if (i < sampleGroups.size()-1) {
			    bindCmd += ",";
			    phenoCmd += ",";
			  }
			  else {
				bindCmd += ")"; 
				phenoCmd += "))";
			  }
			  
			  //to build pheno matrix use c(rep(i,grp.size()) 
			  //then outside the loop use pheno <- as.factor(pheno)
			}
			
			doRvoidEval(bindCmd);
			doRvoidEval(phenoCmd);
			
			//now call the Ftest function
			cmd ="ftResult <- Ftests(compMat,pheno)";
			
			doRvoidEval(cmd);
			
			
			// do filtering
            String rCmd;
			double foldChangeThreshold = ftRequest.getFoldChangeThreshold();
			double pValueThreshold = ftRequest.getPValueThreshold();
			MultiGroupComparisonAdjustmentType adjMethod = ftRequest.getMultiGrpComparisonAdjType();

			if (adjMethod == MultiGroupComparisonAdjustmentType.NONE) {
				// get differentially expressed reporters using
				// unadjusted Pvalue
					
				rCmd = "ftResult  <- filterDiffExpressedGenes.FTest(ftResult,"
						+ foldChangeThreshold + "," + pValueThreshold + ")";
				doRvoidEval(rCmd);
				result.setArePvaluesAdjusted(false);
			} else if (adjMethod == MultiGroupComparisonAdjustmentType.FDR) {
				// do adjustment
				rCmd = "adjust.result <- adjustP.Benjamini.Hochberg(ftResult)";
				doRvoidEval(rCmd);
				// get differentially expressed reporters using adjusted Pvalue
				rCmd = "ftResult  <- filterDiffExpressedGenes.FTest.adjustP(adjust.result,"
						+ foldChangeThreshold + "," + pValueThreshold + ")";
				doRvoidEval(rCmd);
				result.setArePvaluesAdjusted(true);
			} else if (adjMethod == MultiGroupComparisonAdjustmentType.FWER) {
				// do adjustment
				rCmd = "adjust.result <- adjustP.Bonferroni(ftResult)";
				doRvoidEval(rCmd);
				// get differentially expresseed reporters using adjusted Pvalue
				rCmd = "ftResult  <- filterDiffExpressedGenes.FTest.adjustP(adjust.result,"
						+ foldChangeThreshold + "," + pValueThreshold + ")";
				doRvoidEval(rCmd);
				result.setArePvaluesAdjusted(true);
			}
			else {
				logger.error("FTest Adjustment Type unrecognized.");
				this.setException(new AnalysisServerException("Internal error: unrecognized adjustment type."));
				return;
			}
			
			
			double[][] grpMean = new double[sampleGroups.size()][];
			int ind;
			for (int i=0; i < sampleGroups.size(); i++) {
			  ind = i+1;
		      grpMean[i] = doREval("mean <- ftResult[," + ind + "]").asDoubleArray();
			}
			
			//double[][] grpMean = doREval("mean <- as.matrix(ftResult[,1:" + sampleGroups.size()  + "])").asDoubleMatrix();
	
			logger.info("grpMean[0].length=" + grpMean[0].length);
			
			
			double[] maxFoldChange = doREval("maxFC <- ftResult$mfc").asDoubleArray();
			
			
			double[] pval = doREval("pval <- ftResult$pval").asDoubleArray();
		
			logger.info("FTest: maxFoldChange.length=" + maxFoldChange.length);
			logger.info("FTest: pval.length=" + pval.length);
			
//			 get the labels
			Vector reporterIds = doREval("ftLabels <- dimnames(ftResult)[[1]]")
					.asVector();
			
			logger.info("reporterIds.size=" + reporterIds.size());
			
			
			
			// load the result object
			// need to see if this works for single group comparison
			List<FTestResultEntry> resultEntries = new ArrayList<FTestResultEntry>(
					maxFoldChange.length);
			FTestResultEntry resultEntry;
	        int numEntries = maxFoldChange.length;
	        double[] grpm;
	       
			for (int i = 0; i < numEntries; i++) {
				resultEntry = new FTestResultEntry();
				resultEntry.setReporterId(((REXP) reporterIds.get(i)).asString());
				grpm = new double[sampleGroups.size()];
				for (int j=0; j <  sampleGroups.size(); j++) {
				  grpm[j] = grpMean[j][i];
				  resultEntry.setGroupMeans(grpm);
				}
				
				resultEntry.setMaximumFoldChange(maxFoldChange[i]);
				resultEntry.setPvalue(pval[i]);
				resultEntries.add(resultEntry);
			}
			
			
			Collections.sort(resultEntries, ftComparator);
	
			result.setResultEntries(resultEntries);
	
			
			
			//create the data matrix
			//computeMatrix 
			//for (each group) {
			  //submat[i] <- getSubmatrix(dataMatrix, groupIds[i])
			  //computeMatix <- cbind(submat[i])
			//pheno <- as.factor(i,rep(length(group)[i])
			//perform the FTest 
			
			
			
			
		}
		catch (AnalysisServerException asex) {
			AnalysisServerException aex = new AnalysisServerException(
			"Problem computing FTest. Caught AnalysisServerException in FTestTaskR." + asex.getMessage());
	        aex.setFailedRequest(ftRequest);
	        setException(aex);
	        logger.error("Caught AnalysisServerException in FTestTaskR");
	        logger.error(asex);
	        return;  
		}
		catch (Exception ex) {
			AnalysisServerException asex = new AnalysisServerException(
			"Internal Error. Caught Exception in FTestTaskR exClass=" + ex.getClass() + " msg=" + ex.getMessage());
	        asex.setFailedRequest(ftRequest);
	        setException(asex);
	        logger.error("Caught Exception in FTestTaskR");
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

}
