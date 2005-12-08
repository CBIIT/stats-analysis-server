###################################
# File: FDR.ComparisonAdjustment.R
# Author: Huaitian Liu
# Date: September 2005
###################################

# Note: Input includes pvalues from t test or Wilcox test
#       Output - adjusted p-values

#       Multiple comparison adjustment:
#       False Discovery Rate (FDR): Benjamini-Hochberg

adjustP.Benjamini.Hochberg <- function(raw.result) {
adjustP <- p.adjust(raw.result$pval, "BH", length(raw.result$pval))
adjust.result <- cbind(raw.result[,1:4],adjustP)
return(adjust.result)
}

# adjust.result <- adjustP.Benjamini.Hochberg(raw.result)
# return adjust.result
# call DifferentiallyGenesIdentified.R