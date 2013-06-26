Welcome to the Stats Analysis Server Project!
=====================================

The Analysis Server performs on-the-fly statistical calculations for the caIntegartor application. It can be hosted on the same machine that runs the any application (ex. Rembrandt or ISPY) or it can be run on any number of remote compute machines. In the remote configuration, each compute machine communicates with the appropriate application via the JBossMQ Java Messaging Service (JMS) provided by JBoss. It is important to note that each compute machine does not require its own JBoss instance. The Analysis Server relies on the JMS queues defined in the JBoss instance running the caIntegator application, such as Rembrandt.

Analysis-commons is a module which defines classes for communicating with the analysis server. caIntegrator Applications like Rembrandt , I-SPY etc use the request and response classes defined in the analysis commons module for communication with the analysis server.

The Stats Analysis Server is an Open Source project and it is written in Java using Log4J, JBoss, JMS Technologies.

The Stats Analysis Server is distributed under the BSD 3-Clause License.
Please see the NOTICE and LICENSE files for details.

You will find more details about the Stats Analysis Server in the following links:
 * [Code Repository] (https://github.com/NCIP/stats-analysis-server)
 
Please join us in further developing and improving Stats Analysis Server.
