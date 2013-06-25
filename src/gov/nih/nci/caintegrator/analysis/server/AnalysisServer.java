/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.CategoricalCorrelationRequest;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonLookupRequest;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CompoundAnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CopyNumberLookupRequest;
import gov.nih.nci.caintegrator.analysis.messaging.CorrelationRequest;
import gov.nih.nci.caintegrator.analysis.messaging.ExpressionLookupRequest;
import gov.nih.nci.caintegrator.analysis.messaging.FTestRequest;
import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelRequest;
import gov.nih.nci.caintegrator.analysis.messaging.HierarchicalClusteringRequest;
import gov.nih.nci.caintegrator.analysis.messaging.PrincipalComponentAnalysisRequest;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;
import java.util.Properties;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * The AnalysisServer listens for AnalysisRequests on a Java Messaging Service (JMS) queue.  
 * Upon receiving a request, the analysis server performs an analystical task and sends an AnalysisResult object via JMS to the 
 * AnalysisResponseQueue. The AnalysisServer assumes that there is a running JMS instance configured queue destinations called AnalysisRequest and
 * AnalysisResponse. 
 * 
 * @author Michael A. Harris
 * 
 * @see gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest
 * @see gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult
 * 
 * 
 */




public class AnalysisServer implements MessageListener, ExceptionListener, AnalysisResultSender {

	/**
	 * The server version number.
	 */
	public static String version = "12.9.1";
	//12.9.1 fixed a problem with Expresion 
	//12.9 adding copy number lookup capability
	//12.8 added expression lookup capability

	private boolean debugRcommands = false;

	private static String JBossMQ_locationIp = null;
																		
	private static int numComputeThreads = -1;
	
	private static int defaultNumComputeThreads = 1;

	private static String RserverIp = null;
	
	private static String defaultRserverIp = "localhost";

	private static String RinitializationFileName = null;
	
    private static String RdataFileDirectory = null;
	
	private static String requestQueueName;
	
	//private static String responseQueueName;

	private RThreadPoolExecutor executor;

	private QueueConnection queueConnection;

	private Queue requestQueue;

	//private Queue resultQueue;

	private QueueSession queueSession;
	
	private Hashtable contextProperties = new Hashtable();
	
	private String factoryJNDI = null;
	
	private static long reconnectWaitTimeMS = -1L;
	
	private static long defaultReconnectWaitTimeMS = 10000L;

	private static Logger logger = Logger.getLogger(AnalysisServer.class);
	
	
	private QueueReceiver requestReceiver;

	//private QueueSender resultSender;

	
	/**
	 * Initialize the analysis server by initializing the ThreadPoolExecutor and
	 * establishing a connection to the JMS analysis queue destinations.
	 *
	 * @param factoryJNDI
	 *            name of the topic connection factory to look up.
	 *            
	 * @param serverPropertiesFileName 
	 * 			  full path to the server properties file
	 *            
	 */
	public AnalysisServer(String factoryJNDI, String serverPropertiesFileName) throws JMSException,
			NamingException {
		
		this.factoryJNDI = factoryJNDI;

		// load properties from a properties file
		Properties analysisServerConfigProps = new Properties();

		FileInputStream in = null;
		
		
		try {
			
			in = new FileInputStream(serverPropertiesFileName);
			
			analysisServerConfigProps.load(in);
			
			//Configure log4J
			PropertyConfigurator.configure(analysisServerConfigProps);
			
			JBossMQ_locationIp = getMandatoryStringProperty(analysisServerConfigProps, "jmsmq_location");

			RserverIp = getStringProperty(analysisServerConfigProps,"rserve_location", defaultRserverIp);
			
			numComputeThreads = getIntegerProperty(analysisServerConfigProps,"num_compute_threads", defaultNumComputeThreads);
			
			RinitializationFileName = getMandatoryStringProperty(analysisServerConfigProps,"RinitializationFile");
			
			RdataFileDirectory = getMandatoryStringProperty(analysisServerConfigProps, "RdataFileDirectory" );
			
			debugRcommands = getBooleanProperty(analysisServerConfigProps, "debugRcommands", false);
			
			reconnectWaitTimeMS = getLongProperty(analysisServerConfigProps, "reconnectWaitTimeMS", defaultReconnectWaitTimeMS);
			
			requestQueueName = getMandatoryStringProperty(analysisServerConfigProps, "analysis_request_queue");
			
			//responseQueueName = getMandatoryStringProperty(analysisServerConfigProps, "analysis_response_queue");
			
		} catch (Exception ex) {
		  logger.error("Error loading server properties from file: " + analysisServerConfigProps);
		  logger.error(ex);
		  //System.out.println("Error loading server properties from file: " + analysisServerConfigProps);
		  //ex.printStackTrace(System.out);
		}
		finally {
		  try { in.close(); }
		  catch (IOException ex2) {
			 logger.error("Error closing properties file.");
			 logger.error(ex2);
		  }
		}
		
		// initialize the compute threads
		
		executor = new RThreadPoolExecutor(numComputeThreads, RserverIp,6311,
				RinitializationFileName, RdataFileDirectory, this);
		
		executor.setDebugRcommmands(debugRcommands);
		
		//establish the JMS queue connections
		contextProperties.put(Context.INITIAL_CONTEXT_FACTORY,
		   "org.jnp.interfaces.NamingContextFactory");
		contextProperties.put(Context.PROVIDER_URL, JBossMQ_locationIp);
		contextProperties.put("java.naming.rmi.security.manager", "yes");
		contextProperties.put(Context.URL_PKG_PREFIXES, "org.jboss.naming:org.jnp.interfaces:org.jboss.naming.client");
		
		establishQueueConnection();
		
		logger.info("AnalysisServer version=" + version
				+ " successfully initialized. numComputeThreads=" + numComputeThreads + " RserverIp=" + RserverIp + " RinitializationFileName=" + RinitializationFileName);
		

	}
	
	private boolean getBooleanProperty(Properties props, String propertyName, boolean defaultValue) {
	  String propValue = props.getProperty(propertyName);
	  if (propValue == null) {
	    return defaultValue;
	  }
	  return Boolean.parseBoolean(propValue);
	}
	
	private String getMandatoryStringProperty(Properties props, String propertyName) {
	  String propValue = props.getProperty(propertyName);
	  if (propValue == null) {
	    throw new IllegalStateException("Could not load mandatory property name=" + propertyName);
	  }
	  return propValue;
	}
	
	private String getStringProperty(Properties props, String propertyName, String defaultValue) {
		  String propValue = props.getProperty(propertyName);
		  if (propValue == null) {
		    return defaultValue;
		  }
		  return propValue;
	}
	
	private int getIntegerProperty(Properties props, String propertyName, int defaultValue) {
		String propValue = props.getProperty(propertyName);
		if (propValue == null) {
		    return defaultValue;
		}
		return Integer.parseInt(propValue);
	}
	
	private long getLongProperty(Properties props, String propertyName, long defaultValue) {
		String propValue = props.getProperty(propertyName);
		if (propValue == null) {
		    return defaultValue;
		}
		return Long.parseLong(propValue);
	}

	public AnalysisServer(String factoryJNDI) throws JMSException,
	NamingException {
	  this(factoryJNDI, "analysisServer.properties");
	}
	
	
	/**
	 * Establish a connection to the JMS queues.  If it is not possible
	 * to connect then this method will sleep for reconnectWaitTimeMS milliseconds and
	 * then try to connect again.  
	 *
	 */
	private void establishQueueConnection() {
        
		boolean connected = false;
		Context context = null;
		int numConnectAttempts = 0;
		
		while (!connected) {
		
			try {
				
			  //logger.info("Attempting to establish queue connection with provider: " + contextProperties.get(Context.PROVIDER_URL));
				
			  //Get the initial context with given properties
			  context = new InitialContext(contextProperties);
	
			  requestQueue = (Queue) context.lookup(requestQueueName);
			  //resultQueue = (Queue) context.lookup(responseQueueName);
			  
			  QueueConnectionFactory qcf = (QueueConnectionFactory) context
					.lookup(factoryJNDI);
	
			  queueConnection = qcf.createQueueConnection();
			  queueConnection.setExceptionListener(this);
				
			  queueSession = queueConnection.createQueueSession(false,
						QueueSession.AUTO_ACKNOWLEDGE);
				
			  requestReceiver = queueSession.createReceiver(requestQueue);
		
			  requestReceiver.setMessageListener(this);
				 
			  //resultSender = queueSession.createSender(resultQueue);
			  
			  //now creating senders when a message needs to be sent 
			  //because of problem with closed sessions
			  
			  queueConnection.start();
			  
			  connected = true;
			  numConnectAttempts = 0;
			  //System.out.println("  successfully established queue connection.");
			  //System.out.println("Now listening for requests...");
			  logger.info("  successfully established queue connection with provider=" + contextProperties.get(Context.PROVIDER_URL));
			  logger.info("Now listening for requests...");
			}
			catch (Exception ex) {
			  numConnectAttempts++;
			  
			  if (numConnectAttempts <= 10) {
			    logger.warn("  could not establish connection with provider=" + contextProperties.get(Context.PROVIDER_URL) + " after numAttempts=" + numConnectAttempts + "  Will try again in  " + Long.toString(reconnectWaitTimeMS/1000L) + " seconds...");
			    
			    logger.error(">> ERROR trying to establish connection <<");
			    logger.error(ex);
			    logger.error(">> Stack trace of connection error <<");
			    StringWriter sw = new StringWriter();
			    PrintWriter pw  = new PrintWriter(sw);
			    ex.printStackTrace(pw);
			    logger.error(sw.toString());
			    
			    if (numConnectAttempts == 10) {
			      logger.warn("  Will only print connection attempts every 600 atttempts to reduce log size.");
			    }
			  }
			  else if ((numConnectAttempts % 600) == 0) {
				logger.info("  could not establish connection after numAttempts=" + numConnectAttempts + " will keep trying every " + Long.toString(reconnectWaitTimeMS/1000L) + " seconds...");
			  }
			  
			  try { 
			    Thread.sleep(reconnectWaitTimeMS);
			  }
			  catch (Exception ex2) {
			    logger.error("Caugh exception while trying to sleep.." + ex2.getMessage());
			    logger.error(ex2);
			    //ex2.printStackTrace(System.out);
			    return;
			  }
		    }
		}
	}
	

	/**
	 * Implementation of the MessageListener interface, messages will be
	 * received through this method.
	 */
	public void onMessage(Message m) {

		// Unpack the message, be careful when casting to the correct
		// message type. onMessage should not throw any application
		// exceptions.
		try {

			logger.info("AnalysisServer: in onMessage.. ");
			
			if (m==null) {
			  logger.info("Got null messge! This should not happen.");
			}
			
			logger.info(" messge=" + m.getJMSType());
			
			// String msg = ((TextMessage)m).getText();
			ObjectMessage msg = (ObjectMessage) m;
			AnalysisRequest request = (AnalysisRequest) msg.getObject();
			//System.out.println("AnalysisProcessor got request: " + request);
			logger.info("AnalysisProcessor got request: " + request);
			
			Destination resultDestination =  m.getJMSReplyTo();
			if (request instanceof ClassComparisonLookupRequest) {
				processClassComparisonLookupRequest((ClassComparisonLookupRequest) request, resultDestination);
			} else if (request instanceof ClassComparisonRequest) {
				processClassComparisonRequest((ClassComparisonRequest) request, resultDestination);
			} else if (request instanceof HierarchicalClusteringRequest) {
				processHierarchicalClusteringRequest((HierarchicalClusteringRequest) request, resultDestination);
			} else if (request instanceof PrincipalComponentAnalysisRequest) {
				processPrincipalComponentAnalysisRequest((PrincipalComponentAnalysisRequest) request, resultDestination);
			} else if (request instanceof CompoundAnalysisRequest) {
				processCompoundAnalysisReqeust((CompoundAnalysisRequest) request, resultDestination);
			} else if (request instanceof CorrelationRequest) {
			    processCorrelationRequest((CorrelationRequest) request, resultDestination);
			} else if (request instanceof FTestRequest) {
				processFTest((FTestRequest) request, resultDestination);				
			} else if (request instanceof CategoricalCorrelationRequest) {
			    processCategoricalCorrelationRequest((CategoricalCorrelationRequest) request, resultDestination);
			} else if (request instanceof GeneralizedLinearModelRequest) {
				processGeneralizedLinearModelRequest((GeneralizedLinearModelRequest) request, resultDestination);
			} else if (request instanceof ExpressionLookupRequest) {
				processExpressionLookupRequest((ExpressionLookupRequest) request, resultDestination);
			} else if (request instanceof CopyNumberLookupRequest) {
				processCopyNumberLookupRequest((CopyNumberLookupRequest) request, resultDestination);
			}
			

			// sendResult(request);

		} catch (JMSException ex) {
            logger.error("AnalysisProcessor exception: " + ex);
            logger.error(ex);
//			System.err.println("AnalysisProcessor exception: " + ex);
//			ex.printStackTrace();

		} catch (Exception ex2) {
		  logger.error("Got exception in onMessage:");
		  StringWriter sw = new StringWriter();
	      PrintWriter pw  = new PrintWriter(sw);
	      ex2.printStackTrace(pw);
	      logger.error(sw.toString());
		}

	}
	
	
	private void processGeneralizedLinearModelRequest(GeneralizedLinearModelRequest request, Destination resultDestination) {
	  logger.debug("processGeneralizedLinearModelRequest request=" + request);
	  GeneralizedLinearModelTaskR glmTaskR = new GeneralizedLinearModelTaskR(request, debugRcommands);
	  glmTaskR.setJMSDestination(resultDestination);
	  executor.execute(glmTaskR);
		
	}

	private void processClassComparisonLookupRequest(ClassComparisonLookupRequest request, Destination resultDestination) {
	  logger.debug("processClassComparisonLookupRequest request=" + request);
	  ClassComparisonLookupTaskR ccLookupTaskR = new ClassComparisonLookupTaskR(request, debugRcommands);
	  ccLookupTaskR.setJMSDestination(resultDestination);
	  executor.execute(ccLookupTaskR);
	}

	private void processCategoricalCorrelationRequest(CategoricalCorrelationRequest request, Destination resultDestination) {
	  logger.debug("processCategoricalCorrelationRequest request=" + request);
	  CategoricalCorrelationTaskR catCorrTaskR = new CategoricalCorrelationTaskR(request, debugRcommands);
	  catCorrTaskR.setJMSDestination(resultDestination);
	  executor.execute(catCorrTaskR);
	}
	
	private void processExpressionLookupRequest(ExpressionLookupRequest request, Destination resultDestination) {
	  logger.debug("processExpressionLookupRequest request=" + request);
	  ExpressionLookupTaskR lookupTaskR = new ExpressionLookupTaskR(request, debugRcommands);
	  lookupTaskR.setJMSDestination(resultDestination);
	  executor.execute(lookupTaskR);
	}
	
	private void processCopyNumberLookupRequest(CopyNumberLookupRequest request, Destination resultDestination) {
		  logger.debug("processCopyNumberLookupRequest request=" + request);
		  CopyNumberLookupTaskR lookupTaskR = new CopyNumberLookupTaskR(request, debugRcommands);
		  lookupTaskR.setJMSDestination(resultDestination);
		  executor.execute(lookupTaskR);
	}

	private void processFTest(FTestRequest request, Destination resultDestination) {
		logger.debug("processFTest=" + request);
		FTestTaskR ftTaskR = new FTestTaskR(request, debugRcommands);
		ftTaskR.setJMSDestination(resultDestination);
		executor.execute(ftTaskR);
	}

	private void processCompoundAnalysisReqeust(CompoundAnalysisRequest request, Destination resultDestination) {
	  logger.debug("processCompoundAnalysisRequest request=" + request);
	  CompoundRequestTaskR compoundTaskR = new CompoundRequestTaskR(request, debugRcommands);
	  compoundTaskR.setJMSDestination(resultDestination);
	  executor.execute(compoundTaskR);
	}

	/**
	 * Process a class comparison analysis request.
	 * 
	 * @param ccRequest object containing the request parameters for the class comparison request.
	 * @param resultQueue2 
	 */
	public void processClassComparisonRequest(ClassComparisonRequest ccRequest, Destination resultDestination) {
		logger.debug("processClassComparisionRequest request=" + ccRequest);
		ClassComparisonTaskR ccTaskR = new ClassComparisonTaskR(ccRequest, debugRcommands);
		ccTaskR.setJMSDestination(resultDestination);
		executor.execute(ccTaskR);
	}

	/**
	 * Process a hierarchicalClusteringAnalysisRequest.
	 * 
	 * @param hcRequest object containing the request parameters for the hierarchical clustering request.
	 * @param resultQueue2 
	 */
	public void processHierarchicalClusteringRequest(HierarchicalClusteringRequest hcRequest, Destination resultDestination) {
		logger.debug("processHierarchicalClusteringRequest request=" + hcRequest);
		HierarchicalClusteringTaskR hcTaskR = new HierarchicalClusteringTaskR(hcRequest, debugRcommands);
		hcTaskR.setJMSDestination(resultDestination);
		executor.execute(hcTaskR);
	}

	/**
	 * Process a PrincipalComponentAnalysisRequest.
	 * 
	 * @param pcaRequest object containing the request parameters for the PCA analysis
	 * @param resultQueue2 
	 */
	public void processPrincipalComponentAnalysisRequest(PrincipalComponentAnalysisRequest pcaRequest, Destination resultDestination) {
		logger.debug("processPrincipalComponentAnalysisRequest request=" + pcaRequest);
		PrincipalComponentAnalysisTaskR pcaTaskR = new PrincipalComponentAnalysisTaskR(pcaRequest, debugRcommands);
		pcaTaskR.setJMSDestination(resultDestination);
		executor.execute(pcaTaskR);
	}
	
	private void processCorrelationRequest(CorrelationRequest corrRequest, Destination resultDestination) {
	  logger.debug("processCorrelationRequest request=" + corrRequest);
	  CorrelationTaskR corrTaskR = new CorrelationTaskR(corrRequest, debugRcommands);
	  corrTaskR.setJMSDestination(resultDestination);
	  executor.execute(corrTaskR);
	}

	/**
	 * Sends an exception object to the response queue indicating that the request was not processes. 
	 * Failure to process a request usually occurs when there is a problem with the input parameters for a request.
	 */
	public void sendException(AnalysisServerException analysisServerException, Destination exceptionDestination) {
		try {
			logger.info("AnalysisServer sending AnalysisServerException sessionId="
							+ analysisServerException.getFailedRequest()
									.getSessionId()
							+ " taskId="
							+ analysisServerException.getFailedRequest()
									.getTaskId() + " msg=" + analysisServerException.getMessage());
			
			QueueSession exceptionSession = queueConnection.createQueueSession(false,
					QueueSession.AUTO_ACKNOWLEDGE);
			ObjectMessage msg = exceptionSession
			        .createObjectMessage(analysisServerException);
			
			Queue exceptionQueue = (Queue) exceptionDestination;
			
			QueueSender exceptionSender = exceptionSession.createSender(exceptionQueue);
			exceptionSender.send(msg, DeliveryMode.NON_PERSISTENT,
					Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
			
			exceptionSender.close();
			exceptionSession.close();
		} catch (JMSException ex) {
			logger.error("Error while sending AnalysisException");
			logger.error(ex);
			//ex.printStackTrace(System.out);
		}
		catch (Exception ex) {
		   logger.error("Caught exception when trying to send exception analysisServerException:");
		   logger.error(ex);
		}
	}

	
    /**
     * Sends an analysis result to the response queue.
     */
	public void sendResult(AnalysisResult result, Destination resultDestination) {

		try {
			logger.debug("AnalysisServer sendResult sessionId="
							+ result.getSessionId() + " taskId="
							+ result.getTaskId());
			
			QueueSession resultSession = queueConnection.createQueueSession(false,
					QueueSession.AUTO_ACKNOWLEDGE);
		
			ObjectMessage msg = resultSession
			        .createObjectMessage(result);
			
			Queue resultQueue = (Queue) resultDestination;
		
			QueueSender resultSender = resultSession.createSender(resultQueue);
		
			resultSender.send(msg, DeliveryMode.NON_PERSISTENT,
					Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
			
			resultSender.close();
			resultSession.close();
		} catch (JMSException ex) {
			logger.error("Caught JMS exception when trying to send result.");
			logger.error(ex);
		} catch (Exception ex) {
		   logger.error("Caught exception when trying to send result.");
		   logger.error(ex);
		}
	}

	
	/**
	 * Instantiates the server which runs continuously listening for requests.
	 * @param args
	 */
	public static void main(String[] args) {

		try {
			if (args.length > 0) {
			  String serverPropsFile = args[0];
			  
			  AnalysisServer server = new AnalysisServer("ConnectionFactory", serverPropsFile);
			}
			else {
			  AnalysisServer server = new AnalysisServer("ConnectionFactory");
			}
		} 
		catch (Exception ex) {

			logger.error("An exception occurred while testing AnalysisProcessor: "
					+ ex);
			logger.error(ex);
//			System.err
//					.println("An exception occurred while testing AnalysisProcessor: "
//							+ ex);
//			ex.printStackTrace();

		}

	}

	/**
	 * If there is a problem with the connection then re-establish 
	 * the connection.
	 */
	public void onException(JMSException exception) {
	  //System.out.println("onException: caught JMSexception: " + exception.getMessage());
	  logger.error("onException: caught JMSexception: " + exception.getMessage());
	  try
      {
		 if (queueConnection != null) {
           queueConnection.setExceptionListener(null);
           //close();
           queueConnection.close();
		 }
      }
      catch (JMSException c)
      {
    	logger.info("Ignoring exception thrown when closing broken connection msg=" + c.getMessage());
        //System.out.println("Ignoring exception thrown when closing broken connection msg=" + c.getMessage());
        //c.printStackTrace(System.out);
      }
	  
	  //attempt to re-establish the queue connection
	  establishQueueConnection();
	}

}
