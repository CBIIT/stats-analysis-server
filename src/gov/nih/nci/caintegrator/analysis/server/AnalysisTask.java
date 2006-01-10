package gov.nih.nci.caintegrator.analysis.server;


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
}
