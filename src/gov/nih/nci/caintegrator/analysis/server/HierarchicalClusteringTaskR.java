/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.HierarchicalClusteringRequest;
import gov.nih.nci.caintegrator.analysis.messaging.HierarchicalClusteringResult;
import gov.nih.nci.caintegrator.enumeration.ClusterByType;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.rosuda.JRclient.REXP;
//import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

/**
 * Performs Hierarchical Clustering using R.
 * 
 * @author harrismic
 *
 */




public class HierarchicalClusteringTaskR extends AnalysisTaskR {

	private HierarchicalClusteringResult result;
	
	public static final int MAX_REPORTERS_FOR_GENE_CLUSTERING = 3000;
	
	private static Logger logger = Logger.getLogger(HierarchicalClusteringTaskR.class);

	public HierarchicalClusteringTaskR(HierarchicalClusteringRequest request) {
		this(request, false);
	}

	public HierarchicalClusteringTaskR(HierarchicalClusteringRequest request,
			boolean debugRcommands) {
		super(request, debugRcommands);
	}
	
	public HierarchicalClusteringRequest getRequest() {
		return (HierarchicalClusteringRequest) super.getRequest();
	}
	
	
	/**
	 * This method is used to keep an enumerated type value change from breaking the call to
	 * the R function. The R function is expecting an exact match on the string passed 
	 * as a parameter.
	 * @return the quoted string representing the distance matrix type.
	 */
	public String getDistanceMatrixRparamStr() {
	  switch(getRequest().getDistanceMatrix()) {
	  case Correlation : return getQuotedString("Correlation");
	  case Euclidean : return getQuotedString("Euclidean");
	  }
	  return null;
	}
	
	/**
	 * This method is used to keep an enumerated type value change from breaking the call to
	 * the R function. The R function is expecting an exact match on the string passed 
	 * as a parameter.
	 * @return the quoted string representing the linkage method 
	 */
	public String getLinkageMethodRparamStr() {
	  switch(getRequest().getLinkageMethod()) {
	  case Average: return getQuotedString("average");
	  case Complete: return getQuotedString("complete");
	  case Single: return getQuotedString("single");
	  }
	  return null;
	}

	/**
	 * Implement Hierarchical
	 */
	public void run() {
		HierarchicalClusteringRequest hcRequest = (HierarchicalClusteringRequest) getRequest();
		result = new HierarchicalClusteringResult(getRequest().getSessionId(),
				getRequest().getTaskId());
		logger.info(getExecutingThreadName() + " processing hierarchical clustering analysis request="
						+ hcRequest);
		
		try {
			setDataFile(hcRequest.getDataFileName());
		} catch (AnalysisServerException e) {
			e.setFailedRequest(hcRequest);
			logger.error("Internal Error. Error setting data file to fileName=" + hcRequest.getDataFileName());
			logStackTrace(logger, e);
			setException(e);
			return;
		}

		try {
		
			// get the submatrix to operate on
			doRvoidEval("hcInputMatrix <- dataMatrix");
	
			doRvoidEval("hcInputMatrix <- GeneFilterWithVariance(hcInputMatrix,"
					+ hcRequest.getVarianceFilterValue() + ")");
			
			
	
			String rCmd = null;
			
			if ((hcRequest.getSampleGroup()==null)||(hcRequest.getSampleGroup().size() < 2)) {
			   //sample group should never be null when passed from middle tier
			   AnalysisServerException ex = new AnalysisServerException(
						"Not enough samples to cluster.");		 
			   ex.setFailedRequest(hcRequest);
			   setException(ex);
			   logger.error("Sample group is null or not enough samples for Hierarchical clustering.");
			   return;
			}
			
			rCmd = getRgroupCmd("sampleIds", hcRequest.getSampleGroup());
			doRvoidEval(rCmd);
			rCmd = "hcInputMatrix <- getSubmatrix.onegrp(hcInputMatrix, sampleIds)";
			doRvoidEval(rCmd);
				
			if (hcRequest.getReporterGroup() != null) {
				rCmd = getRgroupCmd("reporterIds", hcRequest.getReporterGroup());
				doRvoidEval(rCmd);
				rCmd = "hcInputMatrix <- getSubmatrix.rep(hcInputMatrix, reporterIds)";
				doRvoidEval(rCmd);
			}
			
			
			String plotCmd = null;
			// get the request parameters
			if (hcRequest.getClusterBy() == ClusterByType.Samples) {
				// cluster by samples
				rCmd = "mycluster <- mysamplecluster(hcInputMatrix,"
						+ getDistanceMatrixRparamStr()
						+ ","
						+ getLinkageMethodRparamStr()
						+ ")";
				doRvoidEval(rCmd);
				plotCmd = "plot(mycluster, labels=dimnames(hcInputMatrix)[[2]], xlab=\"\", ylab=\"\",ps=8,sub=\"\", hang=-1)";
			} else if (hcRequest.getClusterBy() == ClusterByType.Genes) {
				// cluster by genes
				
				//check the hcInputMatrix size. If there are more than 1000 reporters then 
				//throw an exception. 
				
	//			check to see if the number of reporters to be used for the clustering is 
				//too large. If it is then return an error
				
				int numReportersToUse = doREval("dim(hcInputMatrix)[1]").asInt();
				
				if (numReportersToUse > MAX_REPORTERS_FOR_GENE_CLUSTERING) {
					AnalysisServerException ex = new AnalysisServerException(
					"Too many reporters to cluster , try increasing the variance filter value, attempted to use numReporters=" + numReportersToUse);
					ex.setFailedRequest(hcRequest);
					setException(ex);
					logger.info("Attempted to use numReporters=" + numReportersToUse + " in hcClustering. Returning exception.");
					return;
				}
				
				
				rCmd = "mycluster <- mygenecluster(hcInputMatrix,"
						+ getDistanceMatrixRparamStr()
						+ ","
						+ getLinkageMethodRparamStr()
						+ ")";
				doRvoidEval(rCmd);
				plotCmd = "plot(mycluster, labels=dimnames(hcInputMatrix)[[1]], xlab=\"\", ylab=\"\",ps=8,sub=\"\", hang=-1)";
			}
			else {
				AnalysisServerException ex = new AnalysisServerException("Unrecognized cluster by type");
				ex.setFailedRequest(hcRequest);
				setException(ex);
				logger.error("Unrecognized cluster by type");
				return;
			}
	
			
			Vector orderedLabels = doREval("clusterLabels <-  mycluster$labels[mycluster$order]").asVector();
			float numPix = (float)orderedLabels.size() * 15.0f;
			int imgWidth = Math.round(numPix/72.0f);
			imgWidth = Math.max(3, imgWidth);
			int imgHeight = 10;
			
			byte[] imgCode = getImageCode(plotCmd, imgHeight, imgWidth);
			result.setImageCode(imgCode);
			
			List<String> orderedLabelList = new ArrayList<String>(orderedLabels.size());
			String label = null;
			for (int i=0; i < orderedLabels.size(); i++ ) {
			  label = ((REXP) orderedLabels.get(i)).asString();
			  orderedLabelList.add(i,label);
			}
			
			if (hcRequest.getClusterBy() == ClusterByType.Genes) {
			  result.setClusteredReporterIDs(orderedLabelList);
			}
			else if (hcRequest.getClusterBy() == ClusterByType.Samples) {
			  result.setClusteredSampleIDs(orderedLabelList);
			}
		}
		catch (AnalysisServerException asex) {
			AnalysisServerException aex = new AnalysisServerException(
			"Problem with clustering computation (Try using a less stringent variance filter). Caught AnalysisServerException in HierarchicalClusteringTaskR." + asex.getMessage());
	        aex.setFailedRequest(hcRequest);
	        setException(aex);
	        logStackTrace(logger, asex);
	        return;  
		}
		catch (Exception ex) {
			AnalysisServerException asex = new AnalysisServerException(
			"Internal Error. Caught AnalysisServerException in HierarchicalClusteringTaskR." + ex.getMessage());
	        asex.setFailedRequest(hcRequest);
	        setException(asex);
	        logStackTrace(logger, ex);
	        return;  
		}
		
	}

	@Override
	public AnalysisResult getResult() {
		return result;
	}

	/**
	 * Clean up some of the memory on the R server
	 */
	public void cleanUp() {
		//doRvoidEval("remove(hcInputMatrix)");
		//doRvoidEval("remove(mycluster)");
		try {
			setRComputeConnection(null);
		} catch (AnalysisServerException e) {
			logger.error("Error in cleanUp method");
			logStackTrace(logger, e);
		    setException(e);
		}
	}

}
