package gov.nih.nci.caintegrator.analysis.server;


import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

/**
 * Classes that implement this interface can send analysis results and analysis exceptions. 
 * 
 * @author harrismic
 *
 */

public interface AnalysisResultSender {

	public void sendResult(AnalysisResult result);

	public void sendException(AnalysisServerException ex);
}
