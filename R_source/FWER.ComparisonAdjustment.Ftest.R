#############################################
# File: FWER.ComparisonAdjustment.Ftest.R
# Author: Huaitian Liu
# Date: September 2005
#################################################

# Note: Input includes pvalues from F test
#       Output - adjusted p-values

#       Multiple comparison adjustment:
#       Family-Wise Type-I Error Rate (FWER): Bonferroni

adjustP.Bonferroni.Ftest <- function(raw.result) {
woNAinPvalue.result <- raw.result[!is.na(raw.result$pval),]
adjustP <- p.adjust(woNAinPvalue.result$pval, "bonferroni", length(woNAinPvalue.result$pval))
adjust.result <- cbind(woNAinPvalue.result[,-dim(raw.result)[2]],adjustP)
return(adjust.result)
}

# adjust.result.Ftest <- adjustP.Bonferroni.Ftest(raw.result)
# return adjust.result.Ftest
# call DifferentiallyGenesIdentified.R
