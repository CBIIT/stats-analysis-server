##############################################################################################################
# File: Paired.ttest.R
# Description: R module for higher order analysis tools - class comparison
# Author: Huaitian Liu
# Date: August 2006
###############################################################################################################

#  Note: Input includes data matrix containing log2 expression data (Genes in rows, Expts in cols)
#        First column in data matrix contains row identifiers (Reporters).

#        The results are (a header line and):
#        Paired t-test: mean of group1, mean of group2, difference of means, absolute fold change,p-values
#        The p-values are two-tailed values.
#############################################################################################################

paired.ttest <- function(datmat, m1=length(grp1ids), m2=length(grp21ids))
{

  # datmat matrix of normalized expression values (reporters * arrays)
        m <- m1+m2
        if(!is.matrix(datmat)) datmat <- matrix(datmat, nr=1)
        if(ncol(datmat) != m) stop("m1 or m2 is not specified correctly")
        if(any(is.infinite(m))) stop("Some of expressions are +(-) Inf")
        pval <- rep(NA,nrow(datmat))
        mean1 <- rep(NA,nrow(datmat))
        mean2 <- rep(NA,nrow(datmat))
        
  # paired t test
   for (i in 1:dim(datmat)[[1]]) {   
    sel <- is.na(datmat[i,1:m1]) | is.na(datmat[i,(m1+1):m])
    if (sum(!sel) >=2) {
    pval[i] <- t.test(datmat[i,1:m1][!sel],
      datmat[i,(m1+1):m][!sel],paired=T, var.equal=T)$p.value  
    mean1[i] <- mean(datmat[i,1:m1][!sel])
    mean2[i] <- mean(datmat[i,(m1+1):m][!sel])
    }
   }  
  result<-return(data.frame(mean1, mean2, mean.dif=mean1-mean2,fc=2^(abs(mean1-mean2)),pval=pval))
}

# raw.result<- paired.ttest(datmat,length(grp1ids), length(grp2ids))
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
