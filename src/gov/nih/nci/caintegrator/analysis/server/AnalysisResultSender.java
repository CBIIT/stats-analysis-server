/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import javax.jms.Destination;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

/**
 * Classes that implement this interface can send analysis results and analysis exceptions. 
 * 
 * @author harrismic
 *
 */





public interface AnalysisResultSender {

	public void sendResult(AnalysisResult result, Destination resultDestination);

	public void sendException(AnalysisServerException ex, Destination exceptionDestination);
}
