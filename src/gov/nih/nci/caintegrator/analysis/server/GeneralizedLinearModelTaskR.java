package gov.nih.nci.caintegrator.analysis.server;

import gov.nih.nci.caintegrator.analysis.messaging.AnalysisResult;
import gov.nih.nci.caintegrator.analysis.messaging.GLMSampleGroup;
import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelRequest;
import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelResult;
import gov.nih.nci.caintegrator.analysis.messaging.SampleGroup;
import gov.nih.nci.caintegrator.exceptions.AnalysisServerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

public class GeneralizedLinearModelTaskR extends AnalysisTaskR {

    private GeneralizedLinearModelResult glmResult = null;

    GeneralizedLinearModelComparator glmComparator = new GeneralizedLinearModelComparator();

    public static final int MIN_GROUP_SIZE = 3;

    private static Logger logger = Logger
            .getLogger(GeneralizedLinearModelTaskR.class);

    public GeneralizedLinearModelTaskR(GeneralizedLinearModelRequest request) {
        this(request, false);
    }

    public GeneralizedLinearModelTaskR(GeneralizedLinearModelRequest request,
            boolean debugRcommands) {
        super(request, debugRcommands);
    }

    public void run() {

        GeneralizedLinearModelRequest glmRequest = (GeneralizedLinearModelRequest) getRequest();
        glmResult = new GeneralizedLinearModelResult(glmRequest.getSessionId(),
                glmRequest.getTaskId());

        logger
                .info(getExecutingThreadName()
                        + ": processing generalized linear model request="
                        + glmRequest);

        // Validate that all the groups are correct and not overlapping

        List<SampleGroup> groups = glmRequest.getComparisonGroups();
        groups.add(glmRequest.getBaselineGroup());

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
            List<SampleGroup> sampleGroups = glmRequest.getComparisonGroups();

            String glmPatients = "GLMPATIENTS";
            String glmGroups = "GLMGROUPS";

            String groupPatientCmd = getGlmPatientGroupCommand(glmPatients,
                    baselineGroup, sampleGroups);
            String groupNameCommand = getGlmGroupNameCommand(glmGroups,
                    baselineGroup, sampleGroups);

            doRvoidEval(groupPatientCmd);
            doRvoidEval(groupNameCommand);

            // Construct the data matrix for the confounding factors
            int count = 0;
            HashMap<String, HashMap> patientMap = ((GLMSampleGroup) baselineGroup)
                    .getAnnotationMap();
            List<String> patientIds = new ArrayList<String>();
            Set<String> rowNames = null;
            List<String> columnVarNames = new ArrayList<String>();
            String varName = "COLUMN";
            String command = "<-c(";
            String rowValues = null;

            for (String patientId : patientMap.keySet()) {
                Map<String, List<String>> values = patientMap.get(patientId);
                if (rowNames == null) {
                    rowNames = values.keySet();
                }
                for (String s : rowNames) {
                    rowValues = "\""
                            + StringUtils
                                    .join(values.get(s).toArray(), "\",\"")
                            + "\"";
                }
                patientIds.add(patientId);
                columnVarNames.add(varName + count);
                String rCommand = varName + count + command + rowValues + ")";
                doRvoidEval(rCommand);
                count++;
            }
            for (SampleGroup group : sampleGroups) {
                patientMap = ((GLMSampleGroup) group).getAnnotationMap();
                for (String patientId : patientMap.keySet()) {
                    Map<String, List<String>> values = patientMap
                            .get(patientId);
                    if (rowNames == null) {
                        rowNames = values.keySet();
                    }
                    for (String s : rowNames) {
                        rowValues = StringUtils.join(values.get(s).toArray(),
                                ",");
                    }
                    patientIds.add(patientId);
                    columnVarNames.add(varName + count);
                    String rCommand = varName + count + command + rowValues
                            + ")";
                    doRvoidEval(rCommand);
                    count++;
                }
            }

            String bindCmd = "boundCol <- cbind(";
            String matrixName = "GLMMATRIX";
            String matrixCommand = matrixName + "<-as.matrix(boundCol)";
            String dimColumns = "dimnames(" + matrixName + ")[[2]]<-";
            String dimRows = "dimnames(" + matrixName + ")[[1]]<-";

            String columnNames = "\""
                    + StringUtils.join(columnVarNames.toArray(), "\",\"")
                    + "\"";
            String cbindCommand = bindCmd + columnNames + ")";
            String patientColumnNames = "\""
                    + StringUtils.join(patientIds.toArray(), "\",\"") + "\"";
            String rowDimNames = "\""
                    + StringUtils.join(rowNames.toArray(), "\",\"") + "\"";

            String columns = "DIMCOLUMNS";
            String rows = "DIMROWS";

            doRvoidEval(columns + command + patientColumnNames + ")");
            doRvoidEval(rows + command + rowDimNames + ")");

            doRvoidEval(cbindCommand);
            doRvoidEval(matrixCommand);

            doRvoidEval(dimColumns + columns);
            doRvoidEval(dimRows + rows);

            String glmCommand = "glmResult<-eagle.glm.array(dataMatrix, "
                    + glmPatients + ", " + glmGroups + ", is.covar=TRUE, "
                    + matrixName + ")";

            doRvoidEval(glmCommand);
            doRvoidEval("glmResult");
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

        // int grp1Length = 0;
        // int baselineGrpLength = 0;
        //		
        // grp1Length = group1.size();

        /*
         * below are made-up names, but need to be unique
         */
        //		
        // String grp1RName = "GLMGRP1IDS";
        // String baselineGrpRName = "GLMBLGRPIDS";
        //		
        //		
        // String rCmd = null;
        //	   
        // /**
        // * This method will take a SampleGroup and generate the R command for
        // to
        // * create the sampleId list. The returned lists can then be used as
        // input
        // * parameters to the statistical methods (for example: glm method).
        // *
        // */
        //	   
        // rCmd = getRgroupCmd(grp1RName, group1);
        //		
        // try {
        //			
        // /**
        // * Evaluate an R command with no return value
        // *
        // * @param c
        // * @param command
        // * @throws AnalysisServerException
        // * @throws RSrvException
        // */
        //			
        // doRvoidEval(rCmd);
        //			
        //			
        // if (baselineGroup != null) {
        //				
        // // two group comparison
        // baselineGrpLength = baselineGroup.size();
        //				
        // rCmd = getRgroupCmd(baselineGrpRName, baselineGroup);
        // doRvoidEval(rCmd);
        //	
        // // create the input data matrix using the sample groups
        // // the word "dataMatrix" is defined in "R", "glmInputMatrix" can be
        // made-up.
        // rCmd = "glmInputMatrix <- getSubmatrix.twogrps(dataMatrix,"
        // + grp1RName + "," + baselineGrpRName + ")";
        // doRvoidEval(rCmd);
        //	
        // // check to make sure all identifiers matched in the R data file
        // rCmd = "dim(glmInputMatrix)[2]";
        // int numMatched = doREval(rCmd).asInt();
        // if (numMatched != (grp1Length + baselineGrpLength)) {
        // AnalysisServerException ex = new AnalysisServerException(
        // "Some sample ids did not match R data file for class comparison
        // request.");
        // ex.setFailedRequest(glmRequest);
        // setException(ex);
        // return;
        // }
        //				
        // else {
        //				
        // //single group comparison??
        // baselineGrpLength = 0;
        // rCmd = "glmInputMatrix <- getSubmatrix.onegrp(dataMatrix,"
        // + grp1RName + ")";
        // doRvoidEval(rCmd);}
        // }
        //			
        // /*
        // * this is used to checked if the numbers of sample ids fetched from R
        // will match the total
        // * number of sample ids from both group1 and baseline group
        // */
        // rCmd = "dim(glmInputMatrix)[2]";
        // int numMatched = doREval(rCmd).asInt();
        // if (numMatched != (grp1Length + baselineGrpLength)) {
        // AnalysisServerException ex = new AnalysisServerException(
        // "Some sample ids did not match R data file for generalized linear
        // model request.");
        // ex.setFailedRequest(glmRequest);
        // ex.setFailedRequest(glmRequest);
        // setException(ex);
        // return;
        // }
        //			
        // /*
        // * make sure it is GLM type gets seleted as the statistical method in
        // the first place
        // */
        // if (glmRequest.getStatisticalMethod() == StatisticalMethodType.GLM) {
        // // do the GLM computation, the name "eagle.glm.array" is the function
        // from the R
        // // the name "glmResult" can be made up
        // rCmd = "glmResult <- eagle.glm.array(glmInputMatrix, " + grp1Length +
        // ","
        // + baselineGrpLength + ")";
        // doRvoidEval(rCmd);
        // }
        //			
        // else {
        // logger.error("Generalized linear model unrecognized statistical
        // method.");
        // this.setException(new AnalysisServerException("Internal error:
        // unrecognized adjustment type."));
        // return;
        // }
        //			
        // // get the results and send
        //			
        // double[] meanGrp1 = doREval("mean1 <-
        // glmResult[,1]").asDoubleArray();
        // double[] meanBaselineGrp = doREval("meanBaseline <-
        // glmResult[,2]").asDoubleArray();
        // double[] meanDif = doREval("meanDif <-
        // glmResult[,3]").asDoubleArray();
        // double[] absoluteFoldChange = doREval("fc <-
        // glmResult[,4]").asDoubleArray();
        // double[] pva = doREval("pva <- glmResult[,5]").asDoubleArray();
        // double[] ajustedPva = doREval("ajustedPva <-
        // glmResult[,6]").asDoubleArray();
        //			
        // // get the labels
        // Vector reporterIds = doREval("glmLabels <- dimnames(glmResult)[[1]]")
        // .asVector();
        //			
        // // load the result object
        // // need to see if this works for single group comparison
        // List<GeneralizedLinearModelResultEntry> resultEntries = new
        // ArrayList<GeneralizedLinearModelResultEntry>(
        // meanGrp1.length);
        // GeneralizedLinearModelResultEntry resultEntry;
        //	
        // for (int i = 0; i < meanGrp1.length; i++) {
        // resultEntry = new GeneralizedLinearModelResultEntry();
        // resultEntry.setReporterId(((REXP) reporterIds.get(i)).asString());
        // resultEntry.setMeanGrp1(meanGrp1[i]);
        // resultEntry.setMeanBaselineGrp(meanBaselineGrp[i]);
        // resultEntry.setMeanDiff(meanDif[i]);
        // resultEntry.setAbsoluteFoldChange(absoluteFoldChange[i]);
        // resultEntry.setPvalue(pva[i]);
        // resultEntry.setAdjustedPvalue(ajustedPva[i]);
        // resultEntries.add(resultEntry);
        // }
        //			
        //			
        // Collections.sort(resultEntries, glmComparator);
        //	
        // glmResult.setGlmResultEntries(resultEntries);
        //	
        // glmResult.setGroup1(group1);
        // if (baselineGroup != null) {
        // glmResult.setBaselineGroup(baselineGroup);
        // }
        //			
        // }// end of try block
        //		
        //		
        //		
        //		
        // catch (AnalysisServerException asex) {
        // AnalysisServerException aex = new AnalysisServerException(
        // "Internal Error. Caught AnalysisServerException in
        // GeneralizedLinearModelTaskR." + asex.getMessage());
        // aex.setFailedRequest(glmRequest);
        // setException(aex);
        // return;
        // }
        // catch (Exception ex) {
        // AnalysisServerException asex = new AnalysisServerException(
        // "Internal Error. Caught AnalysisServerException in
        // GeneralizedLinearModelTaskR." + ex.getMessage());
        // asex.setFailedRequest(glmRequest);
        // setException(asex);
        // return;
        // }

    }

    public String getGlmPatientGroupCommand(String groupName,
            SampleGroup baseline, List<SampleGroup> comparisons) {
        StringBuffer sb = new StringBuffer();
        sb.append(groupName);
        sb.append(" <- c(");
        String id;

        if (baseline != null) {
            for (Iterator i = baseline.iterator(); i.hasNext();) {
                id = (String) i.next();
                sb.append("\"").append(id).append("\"");
                if (i.hasNext()) {
                    sb.append(",");
                }
            }
        }
        for (SampleGroup group : comparisons) {
            for (Iterator i = group.iterator(); i.hasNext();) {
                sb.append(",");
                id = (String) i.next();
                sb.append("\"").append(id).append("\"");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public String getGlmGroupNameCommand(String groupName,
            SampleGroup baseline, List<SampleGroup> comparisons) {
        StringBuffer sb = new StringBuffer();
        sb.append(groupName);
        sb.append(" <- c(");
        String id;

        if (baseline != null) {
            for (Iterator i = baseline.iterator(); i.hasNext();) {
                id = (String) "0" + baseline.getGroupName();
                sb.append("\"").append(id).append("\"");
                if (i.hasNext()) {
                    sb.append(",");
                }
            }
        }
        for (SampleGroup group : comparisons) {
            for (Iterator i = group.iterator(); i.hasNext();) {
                sb.append(",");
                id = (String) group.getGroupName();
                sb.append("\"").append(id).append("\"");
            }
        }
        sb.append(")");
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