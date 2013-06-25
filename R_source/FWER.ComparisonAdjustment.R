######################################
# File: FWER.ComparisonAdjustment.R
# Author: Huaitian Liu
# Date: September 2005
######################################

# Note: Input includes pvalues from t test or Wilcox test
#       Output - adjusted p-values

#       Multiple comparison adjustment:
#       Family-Wise Type-I Error Rate (FWER): Bonferroni

adjustP.Bonferroni <- function(raw.result) {
woNAinPvalue.result <- raw.result[!is.na(raw.result$pval),]
adjustP <- p.adjust(woNAinPvalue.result$pval, "bonferroni", length(woNAinPvalue.result$pval))
adjust.result <- cbind(woNAinPvalue.result[,1:4],adjustP,woNAinPvalue.result[,6:7])
return(adjust.result)
}

# adjust.result <- adjustP.Bonferroni(raw.result)
# return adjust.result
# call DifferentiallyGenesIdentified.R