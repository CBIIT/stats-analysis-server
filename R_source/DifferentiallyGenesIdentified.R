##############################################################################################################
# File: DifferentiallyGenesIdentified.R
# Author: Huaitian Liu
# Date: September 2005
###############################################################################################################

# Identify differentially expressed genes based on absolute fold change>=2 and pvalue<=0.001

mydiferentiallygenes <- function(raw.result,cons1=2, cons2=0.001, na.rm=TRUE) {
woNAinPvalue.result <- raw.result[!is.na(raw.result$pval),]
sel <- (woNAinPvalue.result$fc >= cons1) & (woNAinPvalue.result$pval <=cons2)
filtered.result <- woNAinPvalue.result[sel,]
return(filtered.result)
}


# Call function
# filtered.result <- mydiferentiallygenes(raw.result,2,0.001)

###############################################################################################################
# Identify differentially expressed genes based on absolute fold change>=2 and adjustP<=0.1

mydiferentiallygenes.adjustP <- function(adjust.result, cons1=2, cons2=0.1) {
sel <- (adjust.result$fc >= cons1) & (adjust.result$adjustP <=cons2)
filtered.result <- adjust.result[sel,]
return(filtered.result)
}

# Call function
# filtered.result <- mydiferentiallygenes.adjustP(adjust.result,2,0.1)
