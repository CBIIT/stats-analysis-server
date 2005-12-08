package gov.nih.nci.caintegrator.analysis.server;


import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

public interface AnalysisResultSender {

	public void sendResult(AnalysisResult result);

	public void sendException(AnalysisServerException ex);
}
