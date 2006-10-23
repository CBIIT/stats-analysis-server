########################
# File: GeneFilter2.R
# Author: Huaitian Liu
# Date: September 2005
########################


# Filtering option 2 - The variance of each gene over all samples was calculated and the top  
# high variance genes selected

    GeneFilterVariance <- function(datmat, cons=0.70) {
    tmp1 <- apply(datmat, 1, var, na.rm=TRUE)
    tmp2 <- quantile(tmp1,cons, na.rm=TRUE)
    datmat2 <- datmat[tmp1>=tmp2,]
    return(datmat2)
    }
    
