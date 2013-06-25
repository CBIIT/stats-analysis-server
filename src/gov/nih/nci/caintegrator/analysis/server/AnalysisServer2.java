/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
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
 * This new version of the analysis server will use a registration file to associate
 * analysis requests with tasks.
 * 
 * @author Michael A. Harris
 * 
 * @see gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest
 * @see gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult
 * 
 * 
 */




public class AnalysisServer2 implements MessageListener, ExceptionListener, AnalysisResultSender {

	/**
	 * The server version number.
	 */
	public static String version = "11.0";

	private boolean debugRcommands = false;

	private static String JBossMQ_locationIp = null;
																		
	private static int numComputeThreads = -1;
	
	private static int defaultNumComputeThreads = 1;

	private static String rServerIp = null;
	
	private static int rServerPort = -1;
	
	private static String defaultRserverIp = "localhost";
	
	private static int defaultRserverPort = 6311;

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

	private static Logger logger = Logger.getLogger(AnalysisServer2.class);
	
	
	private QueueReceiver requestReceiver;

	//private QueueSender resultSender;
	
	private Map requestToTaskMap = new HashMap();

	
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
	public AnalysisServer2(String factoryJNDI, String serverPropertiesFileName) throws JMSException,
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

			rServerIp = getStringProperty(analysisServerConfigProps,"rserve_location", defaultRserverIp);
			
			rServerPort = getIntegerProperty(analysisServerConfigProps,"rserve_port", defaultRserverPort);
			
			numComputeThreads = getIntegerProperty(analysisServerConfigProps,"num_compute_threads", defaultNumComputeThreads);
			
			RinitializationFileName = getMandatoryStringProperty(analysisServerConfigProps,"RinitializationFile");
			
			RdataFileDirectory = getMandatoryStringProperty(analysisServerConfigProps, "RdataFileDirectory" );
			
			debugRcommands = getBooleanProperty(analysisServerConfigProps, "debugRcommands", false);
			
			reconnectWaitTimeMS = getLongProperty(analysisServerConfigProps, "reconnectWaitTimeMS", defaultReconnectWaitTimeMS);
			
			requestQueueName = getMandatoryStringProperty(analysisServerConfigProps, "analysis_request_queue");
			
			//responseQueueName = getMandatoryStringProperty(analysisServerConfigProps, "analysis_response_queue");
			
			
			
		} catch (Exception ex) {
		  logger.error("Error loading server properties from file: " + analysisServerConfigProps);
		  logStackTrace(ex);
		}
		finally {
		  try { in.close(); }
		  catch (IOException ex2) {
			 logger.error("Error closing properties file.");
			 logStackTrace(ex2);
		  }
		}
		
		// initialize the compute threads
		
		executor = new RThreadPoolExecutor(numComputeThreads, rServerIp, rServerPort,
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
				+ " successfully initialized. numComputeThreads=" + numComputeThreads + " RserverIp=" + rServerIp + " RinitializationFileName=" + RinitializationFileName);
		

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

	public AnalysisServer2(String factoryJNDI) throws JMSException,
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
			    logStackTrace(ex);
			    
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
			    logStackTrace(ex2);			  
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
			
			processRequest(request, resultDestination);
		} catch (JMSException ex) {
            logger.error("AnalysisProcessor exception: " + ex);
            logStackTrace(ex);
		} catch (Exception ex2) {
		  logger.error("Got exception in onMessage:");
	      logStackTrace(ex2);
		}

	}
	
	
	/**
	 * Process the analysis request by looking up and creating the task registered to handle the request type.
	 * 
	 * @param request  the AnalysisRequest to process
	 * @param resultDestination the JMS destination to send the result to
	 */
	private void processRequest(AnalysisRequest request, Destination resultDestination) {
		
		String requestClassName = request.getClass().getName();
		
		String taskClassName = (String) requestToTaskMap.get(requestClassName);
		
		try {
		
			if (taskClassName != null) {
				//Create the task
				Class taskClass = Class.forName(taskClassName);
				AnalysisTask task = (AnalysisTask) taskClass.newInstance();
				task.setJMSDestination(resultDestination);
				executor.execute(task);
			}
		}
		catch (ClassNotFoundException ex) {
			logger.error("Caught ClassNotFoundException in processRequest sessionId=" + request.getSessionId() + " taskId=" + request.getTaskId());
			logStackTrace(ex);
		}
		catch (InstantiationException ex2) {
			logger.error("Caught InstantiationException in processRequest sessionId=" + request.getSessionId() + " taskId=" + request.getTaskId());
		    logStackTrace(ex2);
		} 
		catch (IllegalAccessException ex3) {
			logger.error("Caught IllegalAccessException in processRequest sessionId=" + request.getSessionId() + " taskId=" + request.getTaskId());
		    logStackTrace(ex3);
		}
	}
	
	/**
	 * This method will log an error and will print the stack trace to the log file
	 * @param ex
	 */
	private static void logStackTrace(Throwable ex) {	 
	  StringWriter sw = new StringWriter();
	  PrintWriter pw = new PrintWriter(sw);
	  ex.printStackTrace(pw);
	  logger.error(sw.toString());
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
			logStackTrace(ex);
		}
		catch (Exception ex) {
		   logger.error("Caught exception when trying to send exception analysisServerException:");
		   logStackTrace(ex);
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
			logStackTrace(ex);
		} catch (Exception ex) {
		   logger.error("Caught exception when trying to send result.");
		   logStackTrace(ex);
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
			  
			  AnalysisServer2 server = new AnalysisServer2("ConnectionFactory", serverPropsFile);
			}
			else {
			  AnalysisServer2 server = new AnalysisServer2("ConnectionFactory");
			}
		} 
		catch (Exception ex) {

			logger.error("An exception occurred running the main method of testing AnalysisServer: "
					+ ex);
			logStackTrace(ex);
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
      }
	  
	  //attempt to re-establish the queue connection
	  establishQueueConnection();
	}

}
