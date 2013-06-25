/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import java.io.PrintWriter;
import java.io.StringWriter;

import javax.jms.Destination;

import org.apache.log4j.Logger;

import gov.nih.nci.caintegrator.analysis.messaging.*;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

/**
 * This is the base class for all analysis tasks.  
 * 
 * @author harrismic
 *
 */





public abstract class AnalysisTask implements Runnable {

	private AnalysisRequest request;
	
	private Destination jmsDestination = null;

	private String executingThreadName = "";

	private AnalysisServerException ex = null;
	
	private Long startTime = 0L;
	private Long computeTime = 0L;

	public AnalysisTask(AnalysisRequest request) {
		this.request = request;
	}

	/**
	 * The run method is responsible for doing the work and for creating the
	 * appropriate AnalysisResult object
	 */
	public abstract void run();

	/**
	 * Release resources and clean up R environment. Called after the task
	 * completes execution.
	 * 
	 */
	public abstract void cleanUp();

	public abstract AnalysisResult getResult();

	public AnalysisRequest getRequest() {
		return request;
	}

	public String toString() {
		return "AnalysisTask thread=" + getExecutingThreadName() + " request="
				+ request.toString();
	}

	public String getExecutingThreadName() {
		return executingThreadName;
	}

	public void setExecutingThreadName(String executingThreadName) {
		this.executingThreadName = executingThreadName;
	}

	public AnalysisServerException getException() {
		return ex;
	}

	public void setException(AnalysisServerException ex) {
		this.ex = ex;
	}

	public Long getComputeTime() {
		return computeTime;
	}

	public void setComputeTime(Long computeTime) {
		this.computeTime = computeTime;
	}

	public Long getStartTime() {
		return startTime;
	}

	public void setStartTime(Long startTime) {
		this.startTime = startTime;
	}

	public Destination getJMSDestination() {
		return jmsDestination;
	}

	public void setJMSDestination(Destination jmsDestination) {
		this.jmsDestination = jmsDestination;
	}
	
	/**
	 * This method will log an error and will print the stack trace to the log file
	 * @param ex
	 */
	public static void logStackTrace(Logger logger, Throwable ex) {	 
	  StringWriter sw = new StringWriter();
	  PrintWriter pw = new PrintWriter(sw);
	  ex.printStackTrace(pw);
	  logger.error(sw.toString());
	}
	
}
