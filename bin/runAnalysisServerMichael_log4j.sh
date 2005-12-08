#!/bin/bash
nohup /usr/j2sdk1.5.0_05/bin/java -classpath ./:../lib/concurrent.jar:../lib/jboss-common.jar:../lib/jbossmq-client.jar:../lib/jnp-client.jar:../lib/jboss-common-client.jar:../lib/jboss-system-client.jar:../lib/jms.jar:../lib/log4j.jar:../lib/Rserve.jar:../lib/runClasses.jar gov.nih.nci.caintegrator.analysis.server.AnalysisServer analysisServerMichael.properties &
