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
woNAinPvalue.result <- raw.result[!is.na(raw.result$pval),]
adjustP <- p.adjust(woNAinPvalue.result$pval, "BH", length(woNAinPvalue.result$pval))
adjust.result <- cbind(woNAinPvalue.result[,1:4],adjustP,woNAinPvalue.result[,6:7] )
return(adjust.result)
}

# adjust.result <- adjustP.Benjamini.Hochberg(raw.result)
# return adjust.result
# call DifferentiallyGenesIdentified.R