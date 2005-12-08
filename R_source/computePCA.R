########################
# File: computePCA.R
# Author: Huaitian Liu
# Date: September 2005
########################


# Filtering option 1 - 95%Percentile / 5%Percentile >= 2 or 3

    GeneFilter1 <- function(datmat, cons=3, ...) {
    tmp <- apply(datmat, 1, quantile, c(.05, .95) , na.rm=TRUE)
    sum(2^(tmp[2,]-tmp[1,])>cons) 
    datmat2 <- datmat[2^(tmp[2,]-tmp[1,])>cons, ]   
    }   

    computePCA1 <- function(datmat, filterParam) {
    filteredDataMatrix <- GeneFilter1(datmat, 3)
    tmp <- apply(is.na(filteredDataMatrix),1,sum,na.rm=T)
    dpca <- prcomp(t(filteredDataMatrix[tmp==0,]))
    return(dpca)
    }

# Filtering option 2 - The variance of each gene over all samples was calculated and the top  
# high variance genes selected

    GeneFilter2 <- function(datmat, cons=0.70) {
    tmp1 <- apply(datmat, 1, var, na.rm=TRUE)
    tmp2 <- quantile(tmp1,cons)
    datmat2 <- datmat[tmp1>=tmp2,]
    return(datmat2)
    }
    
    computePCA2 <- function(datmat, filterParam) {
    filteredDataMatrix <- GeneFilter2(datmat, 0.70)
    tmp <- apply(is.na(filteredDataMatrix),1,sum,na.rm=T)
    dpca <- prcomp(t(filteredDataMatrix[tmp==0,]))
    return(dpca)
    }
