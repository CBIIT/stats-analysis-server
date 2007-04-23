package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelResult;


public class GeneralizedLinearModelTaskR extends AnalysisTaskR {
	
	private GeneralizedLinearModelResult glmResult = null;
	
	GeneralizedLinearModelComparator glmComparator = new GeneralizedLinearModelComparator();
	
	public static final int MIN_GROUP_SIZE = 3;
	
	private static Logger logger = Logger.getLogger(GeneralizedLinearModelTaskR.class);
	
	public GeneralizedLinearModelTaskR(GeneralizedLinearModelRequest request) {
		this(request, false);
	}
	
	public GeneralizedLinearModelTaskR(GeneralizedLinearModelRequest request,
			boolean debugRcommands) {
		super(request, debugRcommands);
	}
	
	
	public void run() {
		
		GeneralizedLinearModelRequest glmRequest = (GeneralizedLinearModelRequest) getRequest();
		glmResult = new GeneralizedLinearModelResult(glmRequest.getSessionId(), glmRequest.getTaskId());
		
		logger.info(getExecutingThreadName() + ": processing generalized linear model request=" + glmRequest);
		
		
		
		//set the data file
        //	check to see if the data file on the compute connection is the 
		//same as that for the analysis task
		
		
		try {
			setDataFile(glmRequest.getDataFileName());
		   } 
		catch (AnalysisServerException e) {
			e.setFailedRequest(glmRequest);
			logger.error("Internal Error. Error setting data file to fileName for generalized linear model =" + glmRequest.getDataFileName());
			setException(e);
			return;
		}
		
		
		SampleGroup group1 = glmRequest.getGroup1();
		SampleGroup baselineGroup = glmRequest.getBaselineGroup();
		
		/*
		 * check if any group 1 is null or group1 has < 3 samples
		 */
		
		if ((group1 == null) || (group1.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"Group1 is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(glmRequest);
		      setException(ex);
		      return;
		}
		
		/*
		 * check is baseline is null or has < 3 samples
		 */
		if ((baselineGroup == null) || (baselineGroup.size() < MIN_GROUP_SIZE)) {
			  AnalysisServerException ex = new AnalysisServerException(
				"BaselineGroup is null or has less than " + MIN_GROUP_SIZE + " entries.");		 
		      ex.setFailedRequest(glmRequest);
		      setException(ex);
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
				      "Can not perform generalized linear model request with overlapping groups. Overlapping ids=" + ids.toString());		 
				      ex.setFailedRequest(glmRequest);
				      setException(ex);
				      return;   
		  }
		}
		
		// end of checking overlapping of the two groups
		
		
       int grp1Length = 0;
       int baselineGrpLength = 0;
		
       grp1Length = group1.size();
		
	   String grp1RName = "GRP1IDS";
	   String baselineGrpRName = "BLGRPIDS";
		
		
	   String rCmd = null;
	   
	   /**
		 * This method will take a SampleGroup and generate the R command for to
		 * create the sampleId list. The returned lists can then be used as input
		 * parameters to the statistical methods (for example: glm method).
		 * 
		 */
	   
		rCmd = getRgroupCmd(grp1RName, group1);
		
		try {
			
			/**
			 * Evaluate an R command with no return value
			 * 
			 * @param c
			 * @param command
			 * @throws AnalysisServerException 
			 * @throws RSrvException 
			 */		
			
			doRvoidEval(rCmd);
			
			
			if (baselineGroup != null) {
				
				// two group comparison
				baselineGrpLength = baselineGroup.size();
				
				rCmd = getRgroupCmd(baselineGrpRName, baselineGroup);
				doRvoidEval(rCmd);
	
				// create the input data matrix using the sample groups
				rCmd = "glmInputMatrix <- getSubmatrix.twogrps(dataMatrix,"
						+ grp1RName + "," + baselineGrpRName + ")";
				doRvoidEval(rCmd);
	
				// check to make sure all identifiers matched in the R data file
				rCmd = "dim(glmInputMatrix)[2]";
				int numMatched = doREval(rCmd).asInt();
				if (numMatched != (grp1Length + baselineGrpLength)) {
					AnalysisServerException ex = new AnalysisServerException(
							"Some sample ids did not match R data file for class comparison request.");
					ex.setFailedRequest(glmRequest);
					setException(ex);
					return;
				}
				
			else {
				
                //single group comparison??
				baselineGrpLength = 0;
				rCmd = "glmInputMatrix <- getSubmatrix.onegrp(dataMatrix,"
						+ grp1RName + ")";
				doRvoidEval(rCmd);}
			}
			
			/*
			 * this is used to checked if the numbers of sample ids fetched from R will match the total 
			 * number of sample ids from both group1 and baseline group
			 */
			rCmd = "dim(glmInputMatrix)[2]";
			int numMatched = doREval(rCmd).asInt();
			if (numMatched != (grp1Length + baselineGrpLength)) {
				AnalysisServerException ex = new AnalysisServerException(
						"Some sample ids did not match R data file for generalized linear model request.");
				ex.setFailedRequest(glmRequest);
				ex.setFailedRequest(glmRequest);
				setException(ex);
				return;
			}
			
			/*
			 * make sure it is GLM type gets seleted as the statistical method in the first place
			 */
			if (glmRequest.getStatisticalMethod() == StatisticalMethodType.GLM) {
				// do the GLM computation
				rCmd = "glmResult <- myglm(glmInputMatrix, " + grp1Length + ","
						+ baselineGrpLength + ")";
				doRvoidEval(rCmd);
			} 
			
			else {
			  logger.error("Generalized linear model unrecognized statistical method.");
			  this.setException(new AnalysisServerException("Internal error: unrecognized adjustment type."));
			  return;
			}
			
            //	get the results and send
			
			double[] meanGrp1 = doREval("mean1 <- glmResult[,1]").asDoubleArray();
			double[] meanBaselineGrp = doREval("meanBaseline <- glmResult[,2]").asDoubleArray();
			double[] meanDif = doREval("meanDif <- glmResult[,3]").asDoubleArray();
			double[] absoluteFoldChange = doREval("fc <- glmResult[,4]").asDoubleArray();
			double[] pva = doREval("pva <- glmResult[,5]").asDoubleArray();
			double[] ajustedPva = doREval("ajustedPva <- glmResult[,6]").asDoubleArray();
			
           // get the labels
			Vector reporterIds = doREval("glmLabels <- dimnames(glmResult)[[1]]")
					.asVector();
			
            // load the result object
			// need to see if this works for single group comparison
			List<GeneralizedLinearModelResultEntry> resultEntries = new ArrayList<GeneralizedLinearModelResultEntry>(
					meanGrp1.length);
			GeneralizedLinearModelResultEntry resultEntry;
	
			for (int i = 0; i < meanGrp1.length; i++) {
				resultEntry = new GeneralizedLinearModelResultEntry();
				resultEntry.setReporterId(((REXP) reporterIds.get(i)).asString());
				resultEntry.setMeanGrp1(meanGrp1[i]);
				resultEntry.setMeanBaselineGrp(meanBaselineGrp[i]);
				resultEntry.setMeanDiff(meanDif[i]);
				resultEntry.setAbsoluteFoldChange(absoluteFoldChange[i]);
				resultEntry.setPvalue(pva[i]);
				resultEntry.setAdjustedPvalue(ajustedPva[i]);
				resultEntries.add(resultEntry);
			}
			
			
			Collections.sort(resultEntries, glmComparator);
	
			glmResult.setResultEntries(resultEntries);
	
			glmResult.setGroup1(group1);
			if (baselineGroup != null) {
				glmResult.setBaselineGroup(baselineGroup);
			}
			
		}// end of try block
		
		
		
		
		catch (AnalysisServerException asex) {
			AnalysisServerException aex = new AnalysisServerException(
			"Internal Error. Caught AnalysisServerException in GeneralizedLinearModelTaskR." + asex.getMessage());
	        aex.setFailedRequest(glmRequest);
	        setException(aex);
	        return;  
		}
		catch (Exception ex) {
			AnalysisServerException asex = new AnalysisServerException(
			"Internal Error. Caught AnalysisServerException in GeneralizedLinearModelTaskR." + ex.getMessage());
	        asex.setFailedRequest(glmRequest);
	        setException(asex);
	        return;  
		}
		
	
		
		
		
	}
	
	public AnalysisResult getResult() {
		return glmResult;
	}

	public GeneralizedLinearModelResult getGeneralizedLinearModelResult() {
		return glmResult;
	}
	
	/**
	 * Clean up some of the resources
	 */
	public void cleanUp() {		
		try {
			setRComputeConnection(null);
		} catch (AnalysisServerException e) {
			logger.error("Error in cleanUp method.");
			logger.error(e);
			setException(e);
		}
	}
	
}