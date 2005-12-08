##############################################################################################################
# File: ttest.R
# Description: R module for higher order analysis tools - class comparison
# Author: Huaitian Liu
# Date: September 2005
###############################################################################################################

#  Note: Input includes data matrix containing log2 expression data (Genes in rows, Expts in cols)
#        First column in data matrix contains row identifiers (Reporters).

#        The results are (a header line and):
#        Two sample t-test: mean of group1, mean of group2, difference of means, absolute fold change,p-values
#        One sample t-test: mean, absolute fold change,p-values
#        The p-values are two-tailed values.

#############################################################################################################

myttest <- function(datmat, m1=length(grp1ids), m2=length(grp21ids), var.equal=TRUE)
{

  # datmat matrix of normalized expression values (reporters * arrays)
  # Missing values are denoted by NA and will be taken into account on the
  # degree of freedoms of the T-test.

  m <- m1+m2
  if(!is.matrix(datmat)) datmat <- matrix(datmat, nr=1)
  if(ncol(datmat) != m) stop("m1 or m2 is not specified correctly")
  if(any(is.infinite(m))) stop("Some of expressions are +(-) Inf")
  pval <- rep(NA,nrow(datmat))

  if (m1<m) {
    # two sample t test
    n1 <- rowSums(!is.na(datmat[,1:m1,drop=FALSE]))
    n2 <- rowSums(!is.na(datmat[,(m1+1):m,drop=FALSE]))
    n <- n1+n2
    mean1 <- rowMeans(datmat[,1:m1,drop=FALSE],na.rm=TRUE)
    mean2 <- rowMeans(datmat[,(m1+1):m,drop=FALSE],na.rm=TRUE)
    
   rowVars <- function(datmat, na.rm=FALSE, dims=1, unbiased=TRUE, SumSquares=FALSE,
                    twopass=FALSE) {
    if (SumSquares) return(rowSums(datmat^2, na.rm, dims))
      N <- rowSums(!is.na(datmat), FALSE, dims)
      Nm1 <- if (unbiased) N-1 else N
    if (twopass) {datmat <- if (dims==0) datmat - mean(datmat, na.rm=na.rm) else
                     sweep(datmat, 1:dims, rowMeans(datmat,na.rm,dims))}
      (rowSums(datmat^2, na.rm, dims) - rowSums(datmat, na.rm, dims)^2/N) / Nm1
   } 
   
    if (var.equal) {
       # denom=pooled variance.
       denom <- (rowVars(datmat[,1:m1,drop=FALSE], na.rm=TRUE)*(n1-1) +
                 rowVars(datmat[,(m1+1):m,drop=FALSE], na.rm=TRUE)*(n2-1))/(n-2)
       tmp.sd <- sqrt(denom*((n1+n2)/(n1*n2)))
       tmp.df <- n-2
      }
       tpt <- (mean1-mean2)/tmp.sd
       ind <- which(n>2)
       pval[ind] <- ifelse(tpt[ind]<=0, 2*pt(tpt[ind],tmp.df[ind]), 2*(1-pt(tpt[ind],tmp.df[ind])))
       result <- data.frame(mean1, mean2, mean.dif=mean1-mean2,fc=2^(abs(mean1-mean2)),pval=pval)
      }
  else {
    # one sample t test
    n <- apply(datmat,1,function(datmat) sum(is.finite(datmat)))
    tmp.mean <- apply(datmat,1,mean,na.rm=TRUE)
    tmp.sd <- apply(datmat,1,sd,na.rm=TRUE)
    tmp.t <- tmp.mean*sqrt(n)/tmp.sd
    tmp <- which(n>1)
    pval[tmp] <- ifelse(tmp.t[tmp]<=0, 2*pt(tmp.t[tmp],n[tmp]-1), 2*(1-pt(tmp.t[tmp],n[tmp]-1)))
    result <- data.frame(mean=tmp.mean, fc=2^(abs(tmp.mean)),pval=pval)
    }
    if (is.null(rownames(datmat)))
    rownames(result) <- paste("v",1:nrow(datmat), sep="")
    else
    rownames(result) <- rownames(datmat)
  return(result)
}

# raw.result<- myttest(datmat,length(grp1ids), length(grp21ids))
# return: result$mean1,result$mean2,result$mean.dif,result$fc,result$pval
# call FDR.ComparisonAdjustment.R or FWER.ComparisonAdjustment.R 

# Identify differentially expressed genes based on absolute fold change>=2 and pvalue<=0.001

mydiferentiallygenes <- function(datmat,raw.result,cons1=2, cons2=0.001) {
sel <- (raw.result$fc >= cons1) & (raw.result$pval <=cons2)
filtered.result <- raw.result[sel,]
return(filtered.result)
}

# Call function
# dif.exp.gene <- mydiferentiallygenes(datmat,raw.result,2,0.001)
