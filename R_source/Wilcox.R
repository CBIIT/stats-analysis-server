##############################################################################################################
# File: Wilcox.R
# Author: Huaitian Liu
# Date: September 2005
###############################################################################################################

#  Note: Input includes data matrix containing log2 expression data (Genes in rows, Expts in cols)
#        First column in data matrix contains row identifiers (Reporters).

#        The results are (a header line and):
#        Wilcoxon: median of group1, median of group2, difference of medians, absolute fold change, p-values

### Wilcoxon rank sum test is the nonparametric analog to the independent two-sample t-test. 

 mywilcox <- function(datmat,m1=length(grp1ids), m2=length(grp21ids),
    datafilter=as.numeric){
  # datmat matrix of normalized expression values (reporters * arrays)

  m <- m1+m2
  if(!is.matrix(datmat)) datmat <- matrix(datmat, nr=1)
  if(ncol(datmat) != m) stop("m1 or m2 is not specified correctly")
  if(any(is.infinite(m))) stop("Some of expressions are +(-) Inf")
  pval <- rep(NA,nrow(datmat))
  
  median1 <- apply(datmat[,1:m1],1, median,na.rm=TRUE)
  median2 <- apply(datmat[,(m1+1):m],1, median,na.rm=TRUE)

  std1 <- apply(datmat[,1:m1],1, sd,na.rm=TRUE)    
  std2 <- apply(datmat[,(m1+1):m],1, sd,na.rm=TRUE)

  f <- function(i) {
      return(wilcox.test(datafilter(datmat[i,1:m1]),
      datafilter(datmat[i,(m1+1):m]))$p.value)
      }
  pval <- sapply(1:length(datmat[,1]),f)
  
  result<-return(data.frame(median1, median2, median.dif=median1-median2,fc=2^(abs(median1-median2)),pval,std1=std1,std2=std2))
   }
  
# Call function 
# result <- mywilcox(datmat, length(grp1ids), length(grp2ids))
 
