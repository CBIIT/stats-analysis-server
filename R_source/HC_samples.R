###########################################################################################
# File: HC_samples.R
# Description: R module for higher order analysis tools - hierarchical clustering
# Author: Huaitian Liu
# Date: August 2005
###########################################################################################

# Clustering over samples 
mysamplecluster <- function(x, diss=c("Correlation", "Euclidean"), meth=c("average","single","complete")) {
    library(cluster)
    
    # Clustering over samples
    
    if (diss=='Correlation') {
      sampclst <- hclust(as.dist(1 - cor(x,use="pair")), method=meth)
    }
	else if (diss=='Euclidean') {
      sampclst<- hclust(dist(t(x)),method=meth)
    }
    return(sampclst)
}


# TO PLOT
# png(filename="samplecluster%d.png")
# plot(sampclst, labels=arraylab, xlab="", ylab="",cex=.5,sub="", hang=-1)
# mtext(paste("1-correlation",meth), side=2,line=3,cex=.8)
# title("Clustering over the samples", font=1,cex=0.5)
# plot(sampclst, labels=arraylab, xlab="", ylab="", cex=.5,sub="", hang=-1)
# mtext(paste("Euclidean",meth), side=2,line=3,cex=.8)
#    #title("Clustering over the samples", font=1,cex=0.5)
# dev.off()

