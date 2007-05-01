package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.GLMSampleGroup;
import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelRequest;
import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelResult;
import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelResultEntry;
import gov.nih.nci.caintegrator.analysis.messaging.SampleGroup;
import gov.nih.nci.caintegrator.enumeration.CoVariateType;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.rosuda.JRclient.REXP;

public class GeneralizedLinearModelTaskR extends AnalysisTaskR {

    private GeneralizedLinearModelResult glmResult = null;

    GeneralizedLinearModelComparator glmComparator = new GeneralizedLinearModelComparator();

    public static final int MIN_GROUP_SIZE = 3;

    private static Logger logger = Logger
            .getLogger(GeneralizedLinearModelTaskR.class);

    public GeneralizedLinearModelTaskR(GeneralizedLinearModelRequest request) {
        this(request, true);
        logger.debug("constructting glm with true");
    }

    public GeneralizedLinearModelTaskR(GeneralizedLinearModelRequest request,
            boolean debugRcommands) {
        super(request, debugRcommands);
        logger.debug("constructting glm with " + debugRcommands);
    }

    public void run() {
        logger.debug("starting glm with " + this.getDebugRcommands());
        GeneralizedLinearModelRequest glmRequest = (GeneralizedLinearModelRequest) getRequest();
        glmResult = new GeneralizedLinearModelResult(glmRequest.getSessionId(),
                glmRequest.getTaskId());

        logger
                .info(getExecutingThreadName()
                        + ": processing generalized linear model request="
                        + glmRequest);

        // Validate that all the groups are correct and not overlapping

        List<GLMSampleGroup> groups = glmRequest.getComparisonGroups();
        groups.add((GLMSampleGroup) glmRequest.getBaselineGroup());

        boolean errorCondition = false;
        SampleGroup idsSeen = new SampleGroup();
        String errorMsg = null;
        for (SampleGroup group : groups) {
            if (group.size() < 2) {
                errorMsg = "Group: " + group.getGroupName()
                        + " has less than two members. Sending exception.";
                logger.error(errorMsg);
                errorCondition = true;
                break;
            }

            if (idsSeen.containsAny(group)) {
                errorMsg = "Group: " + group.getGroupName()
                        + " contains overlapping ids. Sending exception.";
                logger.error(errorMsg);
                errorCondition = true;
                break;
            }

            idsSeen.addAll(group);
        }

        if (errorCondition) {
            AnalysisServerException ex = new AnalysisServerException(
                    "One or more groups have overlapping members or contain less than 3 entries.");
            ex.setFailedRequest(glmRequest);
            logger
                    .error("Groups have overlapping members or less than 3 entries.");
            setException(ex);
            return;
        }

        // set the data file
        // check to see if the data file on the compute connection is the
        // same as that for the analysis task

        try {
            setDataFile(glmRequest.getDataFileName());
        } catch (AnalysisServerException e) {
            e.setFailedRequest(glmRequest);
            logger
                    .error("Internal Error. Error setting data file to fileName for generalized linear model ="
                            + glmRequest.getDataFileName());
            setException(e);
            return;
        }

        try {
            SampleGroup baselineGroup = glmRequest.getBaselineGroup();
            List<GLMSampleGroup> sampleGroups = glmRequest
                    .getComparisonGroups();

            String glmPatients = "GLMPATIENTS";
            String glmGroups = "GLMGROUPS";
            logger.debug("building");
            String groupPatientCmd = getGlmPatientGroupCommand(glmPatients,
                    baselineGroup, sampleGroups);
            String groupNameCommand = getGlmGroupNameCommand(glmGroups,
                    baselineGroup, sampleGroups);
            logger.debug("about to invoke r");
            doRvoidEval(groupPatientCmd);
            doRvoidEval(groupNameCommand);
            logger.debug("invoking r");
            // Construct the data matrix for the confounding factors
            logger.debug("about to construct data matrix");
            int count = 0;
            HashMap<String, HashMap> patientMap = ((GLMSampleGroup) baselineGroup)
                    .getAnnotationMap();
            List<String> patientIds = new ArrayList<String>();
            Set<String> colNames = null;
            List<String> rowVarNames = new ArrayList<String>();
            String varName = "PATIENT";
            String command = "<-c(";
            String rowValues = null;

            for (String patientId : patientMap.keySet()) {
                Map<String, Map> values = patientMap.get(patientId);
                if (colNames == null) {
                    colNames = values.keySet();
                }
                List<String> stringValues = new ArrayList<String>();
                for (String s : colNames) {
                    Object o = values.get(s);
                    stringValues.add(o.toString());

                }
                rowValues = "\""
                        + StringUtils.join(stringValues.toArray(), "\",\"")
                        + "\"";
                patientIds.add(patientId);
                rowVarNames.add(varName + count);
                String rCommand = varName + count + command + rowValues + ")";
                doRvoidEval(rCommand);
                count++;
            }
            for (SampleGroup group : sampleGroups) {
                patientMap = ((GLMSampleGroup) group).getAnnotationMap();
                for (String patientId : patientMap.keySet()) {
                    Map<String, List<String>> values = patientMap
                            .get(patientId);
                    if (colNames == null) {
                        colNames = values.keySet();
                    }
                    List<String> stringValues = new ArrayList<String>();
                    for (String s : colNames) {
                        Object o = values.get(s);
                        stringValues.add(o.toString());

                    }

                    rowValues = "\""
                            + StringUtils.join(stringValues.toArray(), "\",\"")
                            + "\"";
                    patientIds.add(patientId);
                    rowVarNames.add(varName + count);
                    String rCommand = varName + count + command + rowValues
                            + ")";
                    doRvoidEval(rCommand);
                    count++;
                }
            }

            logger.debug("about to bind data matrix");
            String bindCmd = "boundCol <- rbind(";
            String matrixName = "GLMMATRIX";
            String matrixCommand = matrixName + "<-as.matrix(boundCol)";
            String dimColumns = "dimnames(" + matrixName + ")[[2]]<-";
            String dimRows = "dimnames(" + matrixName + ")[[1]]<-";

            String columnNames = StringUtils.join(rowVarNames.toArray(), ",");
            String cbindCommand = bindCmd + columnNames + ")";
            String columnDimNames = "\""
                    + StringUtils.join(colNames.toArray(), "\",\"") + "\"";
            String rowDimNames = "\""
                    + StringUtils.join(patientIds.toArray(), "\",\"") + "\"";

            String columns = "DIMCOLUMNS";
            String rows = "DIMROWS";

            doRvoidEval(columns + command + columnDimNames + ")");
            doRvoidEval(rows + command + rowDimNames + ")");

            doRvoidEval(cbindCommand);
            doRvoidEval(matrixCommand);

            doRvoidEval(dimColumns + columns);
            doRvoidEval(dimRows + rows);

            String glmCommand = null;
            List<CoVariateType> coVariateTypes = glmRequest.getCoVariateTypes();
            if (coVariateTypes == null || coVariateTypes.size() == 0) {
                glmCommand = "glmResult<-eagle.glm.array(dataMatrix, "
                        + glmPatients + ", " + glmGroups + ", FALSE, " + "null"
                        + ")";
            } else {
                glmCommand = "glmResult<-eagle.glm.array(dataMatrix, "
                        + glmPatients + ", " + glmGroups + ", TRUE, "
                        + matrixName + ")";
            }

            doRvoidEval(glmCommand);

            // get the labels
            Vector reporterIds = doREval(
                    "glmReporters <- dimnames(glmResult)[[1]]").asVector();
            Vector groupIds = doREval(
                    "glmReporters <- dimnames(glmResult)[[2]]").asVector();

            List<SampleGroup> resultSampleGroups = new ArrayList<SampleGroup>();
            for (Object groupId : groupIds) {
                resultSampleGroups.add(new SampleGroup(((REXP) groupId)
                        .asString()));
            }
            glmResult.setSampleGroups(resultSampleGroups);

            List<GeneralizedLinearModelResultEntry> entries = new ArrayList<GeneralizedLinearModelResultEntry>();
            for (Object reporterId : reporterIds) {
                GeneralizedLinearModelResultEntry entry = new GeneralizedLinearModelResultEntry();
                String reporter = ((REXP) reporterId).asString();
                entry.setReporterId(reporter);
                double[] pvals = doREval("pvals <- glmResult$" + reporter)
                        .asDoubleArray();
                entry.setGroupPvalues(pvals);
                entries.add(entry);
            }
            glmResult.setGlmResultEntries(entries);
            logger.debug(reporterIds);
            logger.debug("reporterIds.size=" + reporterIds.size());
            logger.debug(groupIds);
            logger.debug("groupIds.size=" + groupIds.size());

            logger.debug(entries);
            // glmResult.setSampleGroups(sampleGroups);

        } catch (AnalysisServerException asex) {
            AnalysisServerException aex = new AnalysisServerException(
                    "Problem computing FTest. Caught AnalysisServerException in FTestTaskR."
                            + asex.getMessage());
            aex.setFailedRequest(glmRequest);
            setException(aex);
            logger.error("Caught AnalysisServerException in FTestTaskR");
            logStackTrace(logger, asex);
            return;
        } catch (Exception ex) {
            AnalysisServerException asex = new AnalysisServerException(
                    "Internal Error. Caught Exception in FTestTaskR exClass="
                            + ex.getClass() + " msg=" + ex.getMessage());
            asex.setFailedRequest(glmRequest);
            setException(asex);
            logger.error("Caught Exception in FTestTaskR");
            logStackTrace(logger, ex);
            return;
        }

    }

    public String getGlmPatientGroupCommand(String groupName,
            SampleGroup baseline, List<GLMSampleGroup> comparisons) {
        StringBuffer sb = new StringBuffer();
        sb.append(groupName);
        sb.append(" <- c(");
        String id;
        logger.debug("in glm patientGroupCommand");
        if (baseline != null) {
            logger.debug("baseline not null");
            for (Iterator i = baseline.iterator(); i.hasNext();) {
                id = (String) i.next();
                sb.append("\"").append(id).append("\"");
                if (i.hasNext()) {
                    sb.append(",");
                }
            }
        }
        for (SampleGroup group : comparisons) {
            logger.debug("got sample group " + group);
            for (Iterator i = group.iterator(); i.hasNext();) {
                sb.append(",");
                id = (String) i.next();
                sb.append("\"").append(id).append("\"");
            }
        }
        sb.append(")");
        logger.debug("returning from patientGroupCommand");
        return sb.toString();
    }

    public String getGlmGroupNameCommand(String groupName,
            SampleGroup baseline, List<GLMSampleGroup> comparisons) {
        StringBuffer sb = new StringBuffer();
        sb.append(groupName);
        sb.append(" <- c(");
        String id;
        logger.debug("in glmGroupNameCommand");
        if (baseline != null) {
            logger.debug("baseline is not null");
            for (Iterator i = baseline.iterator(); i.hasNext();) {
                i.next();
                id = (String) "0" + baseline.getGroupName();
                sb.append("\"").append(id).append("\"");
                if (i.hasNext()) {
                    sb.append(",");
                }
            }
        }
        for (SampleGroup group : comparisons) {
            logger.debug("groupname got " + group);
            for (Iterator i = group.iterator(); i.hasNext();) {
                i.next();
                sb.append(",");
                id = (String) group.getGroupName();
                sb.append("\"").append(id).append("\"");
            }
        }
        logger.debug("returning glmGroupNameCommand");
        sb.append(")");
        return sb.toString();
    }

    public AnalysisResult getResult() {
        return glmResult;
    }

    public GeneralizedLinearModelResult getGeneralizedLinearModelResult() {
        return glmResult;
    }

    /**
     * Clean up some of the resources
     */
    public void cleanUp() {
        try {
            setRComputeConnection(null);
        } catch (AnalysisServerException e) {
            logger.error("Error in cleanUp method.");
            logger.error(e);
            setException(e);
        }
    }

}