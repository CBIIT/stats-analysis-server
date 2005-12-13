package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisRequest;
import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.ClassComparisonRequest;
import gov.nih.nci.caintegrator.analysis.messaging.HierarchicalClusteringRequest;
import gov.nih.nci.caintegrator.analysis.messaging.PrincipalComponentAnalysisRequest;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.io.FileInputStream;
import java.util.Hashtable;
import java.util.Properties;

import javax.jms.DeliveryMode;
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
 * This class implements the analysis server module.
 * 
 * @author Michael A. Harris
 * 
 * 
 * AnalysisServer should register handlers which are specifed from a
 * configuration file.
 * 
 * Version 5.6  - changed error check to allow any number of reporters for clustering by samples
 *                and allow 3000 reporters for clustering by genes.
 */
public class AnalysisServer implements MessageListener, ExceptionListener, AnalysisResultSender {

	public static String version = "5.6";

	private boolean debugRcommands = false;

	private static String JBossMQ_locationIp = null;
																		
	private static int numComputeThreads = -1;
	
	private static int defaultNumComputeThreads = 1;

	private static String RserverIp = null;
	
	private static String defaultRserverIp = "localhost";

	private static String RdataFileName = null;

	private RThreadPoolExecutor executor;

	private QueueConnection queueConnection;

	private Queue requestQueue;

	private Queue resultQueue;

	private QueueSession queueSession;
	
	private Hashtable contextProperties = new Hashtable();
	
	private String factoryJNDI = null;
	
	private static long reconnectWaitTimeMS = -1L;
	
	private static long defaultReconnectWaitTimeMS = 10000L;

	private static Logger logger = Logger.getLogger(AnalysisServer.class);
	
	
	private QueueReceiver requestReceiver;

	//private QueueSender resultSender;

	
	/**
	 * Sets up all the JMS fixtures.
	 * 
	 * Use close() when finished with object.
	 * 
	 * @param factoryJNDI
	 *            name of the topic connection factory to look up.
	 * @param topicJNDI
	 *            name of the topic destination to look up
	 */
	public AnalysisServer(String factoryJNDI, String serverPropertiesFileName) throws JMSException,
			NamingException {
		
		this.factoryJNDI = factoryJNDI;

		// load properties from a properties file
		Properties analysisServerConfigProps = new Properties();

		try {
			analysisServerConfigProps.load(new FileInputStream(
					serverPropertiesFileName));
			
			//Configure log4J
			PropertyConfigurator.configure(analysisServerConfigProps);
			
			JBossMQ_locationIp = getMandatoryStringProperty(analysisServerConfigProps, "jmsmq_location");

			RserverIp = getStringProperty(analysisServerConfigProps,"rserve_location", defaultRserverIp);
			
			numComputeThreads = getIntegerProperty(analysisServerConfigProps,"num_compute_threads", defaultNumComputeThreads);
			
			RdataFileName = getMandatoryStringProperty(analysisServerConfigProps,"RdefinitionFile");
			
			debugRcommands = getBooleanProperty(analysisServerConfigProps, "debugRcommands", false);
			
			reconnectWaitTimeMS = getLongProperty(analysisServerConfigProps, "reconnectWaitTimeMS", defaultReconnectWaitTimeMS);
			
			
		} catch (Exception ex) {
		  logger.error("Error loading server properties from file: " + analysisServerConfigProps);
		  logger.error(ex);
		  //System.out.println("Error loading server properties from file: " + analysisServerConfigProps);
		  //ex.printStackTrace(System.out);
		}
		
		// initialize the compute threads
		
		executor = new RThreadPoolExecutor(numComputeThreads, RserverIp,
				RdataFileName, this);
		
		executor.setDebugRcommmands(debugRcommands);
		
		//establish the JMS queue connections
		contextProperties.put(Context.INITIAL_CONTEXT_FACTORY,
		   "org.jnp.interfaces.NamingContextFactory");
		contextProperties.put(Context.PROVIDER_URL, JBossMQ_locationIp);
		contextProperties.put("java.naming.rmi.security.manager", "yes");
		contextProperties.put(Context.URL_PKG_PREFIXES, "org.jboss.naming");
		
		establishQueueConnection();
		
		logger.info("AnalysisServer version=" + version
				+ " successfully initialized. numComputeThreads=" + numComputeThreads + " RserverIp=" + RserverIp + " RdataFileName=" + RdataFileName);
		

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
	
			  requestQueue = (Queue) context.lookup("queue/AnalysisRequest");
			  resultQueue = (Queue) context.lookup("queue/AnalysisResponse");
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

			// String msg = ((TextMessage)m).getText();
			ObjectMessage msg = (ObjectMessage) m;
			AnalysisRequest request = (AnalysisRequest) msg.getObject();
			//System.out.println("AnalysisProcessor got request: " + request);
			logger.info("AnalysisProcessor got request: " + request);
			
			if (request instanceof ClassComparisonRequest) {
				processClassComparisonRequest((ClassComparisonRequest) request);
			} else if (request instanceof HierarchicalClusteringRequest) {
				processHierarchicalClusteringRequest((HierarchicalClusteringRequest) request);
			} else if (request instanceof PrincipalComponentAnalysisRequest) {
				processPrincipalComponentAnalysisRequest((PrincipalComponentAnalysisRequest) request);
			}

			// sendResult(request);

		} catch (JMSException ex) {
            logger.error("AnalysisProcessor exception: " + ex);
            logger.error(ex);
//			System.err.println("AnalysisProcessor exception: " + ex);
//			ex.printStackTrace();

		} catch (Exception ex2) {
		  logger.error("Got exception in onMessage:");
		  logger.error(ex2);
		}

	}
	
	
	/**
	 * Process a class comparison analysis request.
	 * 
	 * @param ccRequest
	 */
	public void processClassComparisonRequest(ClassComparisonRequest ccRequest) {
		executor.execute(new ClassComparisonTaskR(ccRequest, true));
	}

	/**
	 * Process a hierarchicalClusteringAnalysisRequest.
	 * 
	 * @param hcRequest
	 */
	public void processHierarchicalClusteringRequest(
			HierarchicalClusteringRequest hcRequest) {
		executor.execute(new HierarchicalClusteringTaskR(hcRequest, true));
	}

	/**
	 * Process a PrincipalComponentAnalysisRequest.
	 * 
	 * @param pcaRequest
	 */
	public void processPrincipalComponentAnalysisRequest(
			PrincipalComponentAnalysisRequest pcaRequest) {
		executor.execute(new PrincipalComponentAnalysisTaskR(pcaRequest, true));
	}

	public void sendException(AnalysisServerException analysisServerException) {
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
			
			QueueSender exceptionSender = exceptionSession.createSender(resultQueue);
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

	

	public void sendResult(AnalysisResult result) {

		try {
			logger.debug("AnalysisServer sendResult sessionId="
							+ result.getSessionId() + " taskId="
							+ result.getTaskId());
			
			QueueSession resultSession = queueConnection.createQueueSession(false,
					QueueSession.AUTO_ACKNOWLEDGE);
		
			ObjectMessage msg = resultSession
			        .createObjectMessage(result);
		
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
