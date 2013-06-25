########################
# File: GeneFilter1.R
# Author: Huaitian Liu
# Date: September 2005
########################


# Filtering option 1 - 95%Percentile / 5%Percentile >= 2 or 3

    GeneFilterFoldChange <- function(datmat, cons=3, ...) {
    tmp <- apply(datmat, 1, quantile, c(.05, .95) , na.rm=TRUE)
    sum(2^(tmp[2,]-tmp[1,])>cons) 
    datmat2 <- datmat[2^(tmp[2,]-tmp[1,])>cons, ]   
    }   
