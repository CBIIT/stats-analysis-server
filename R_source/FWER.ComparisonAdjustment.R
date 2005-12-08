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
adjustP <- p.adjust(raw.result$pval, "bonferroni", length(raw.result$pval))
adjust.result <- cbind(raw.result[,1:4],adjustP)
return(adjust.result)
}

# adjust.result <- adjustP.Bonferroni(raw.result)
# return adjust.result
# call DifferentiallyGenesIdentified.R