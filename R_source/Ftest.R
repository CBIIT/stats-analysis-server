
###############################################################################################
# File: Ftest.R
# Description: R module for higher order analysis tools - Class comparison for multiple groups
# Author: Huaitian Liu
# Date: June 2006
###############################################################################################

################################################################################################
# F test
# Input  =  1. matrix containing log2 expression data; 2. vector containing groups information
# Output =  data frame (with header row) containing:
# Reporters, means of groups, maximal fold change, p-values.

Ftests <- function(m, pheno) {
  ngenes <- dim(m)[1]
  tbl <- table(pheno)
  grps <- names(tbl)
  ngrps <- length(grps)
  n <- matrix(NA, ngenes, ngrps)
  means <- matrix(NA, ngenes, ngrps)
  ssi <- matrix(NA, ngenes, ngrps)
  for(i in 1:ngrps){
    if (tbl[i] > 1) {
      temp <- m[,pheno==grps[i]]
      n[,i] <- apply(!is.na(temp), 1, sum)
      means[,i] <- apply(temp, 1, mean, na.rm=T)
      ssi[,i]<- apply((temp-matrix(means[,i],nrow(temp),ncol(temp)))^2, 1, sum, na.rm=T)
    } else {
      temp <- m[,pheno==grps[i]]
      n[,i] <- !is.na(temp)
      means[,i] <- temp
      ssi[,i]<- 0
    }
  }
  nvec <- apply(n, 1, sum)
  within<- apply(ssi, 1, sum)
  between<- apply(m, 1, var, na.rm=T)*(nvec-1) - within
  ndegenerate <- apply(n==0, 1, sum) 
  df1 <- ngrps-ndegenerate-1
  df2 <- nvec-ngrps+ndegenerate
  Fval<- (between*df2)/(within*df1)
  indic <- (!( is.na(Fval) | df1==0 | df2==0 )) & (apply(n<=1,1,sum)==0)
  pval <- rep(NA, ngenes)
  pval[indic] <- 1-pf( Fval[indic], df1[indic], df2[indic])
  mfc <- rep(NA, ngenes)
  mfc <- apply(means,1,max, na.rm=T) - apply(means,1,min, na.rm=T)
  result <- data.frame(means=means,mfc=2^mfc,pval=pval)
  if (is.null(rownames(m)))
    rownames(result) <- paste("v",1:nrow(m), sep="")
    else
    rownames(result) <- rownames(m)
  return(result)
 }

# raw.result<- Ftests(dataMatrix,pheno)