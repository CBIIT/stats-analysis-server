package gov.nih.nci.caintegrator.analysis.server;


import javax.jms.JMSException;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

/**
 * Classes that implement this interface can send requests to and receive results from
 * the analysis queues.
 * 
 * This interface is not currently used but may be used in the future.
 * 
 * @author harrismic
 *
 */

public interface AnalysisRequestSender {

	public void sendRequest(AnalysisRequest request) throws JMSException;
	
	public void receiveResult(AnalysisResult analysisResult);
	
	public void receiveException(AnalysisServerException analysisServerException);

}
