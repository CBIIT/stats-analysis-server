###########################################################################################
# File: HC_genes.R
# Description: R module for higher order analysis tools - hierarchical clustering
# Author: Huaitian Liu
# Date: August 2005
###########################################################################################

# Clustering over reporters
mygenecluster <- function(x, diss=c("Correlation", "Euclidean"), meth=c("average","single","complete")) {
    library(cluster)

    if (diss=='Correlation') {
      genclst <- hclust(as.dist(1-cor(t(x),use="pair")), method=meth) 
    }
    else if (diss=='Euclidean') {
      genclst<- hclust(dist(t(t(x))),method=meth)
    } 
    return(genclst)
}


# TO PLOT
#    png(filename="genecluster%d.png")
#    title("Clustering over the reporters", font=1,cex=0.5)
#    plot(genclst,  xlab="", ylab="", cex=.5, sub="", hang=-1)
#    mtext(paste("1-correlation",meth), side=2,line=3,cex=.8)
# OR
#    plot(genclst,  xlab="", ylab="", cex=.5, sub="", hang=-1)
#    mtext(paste("Euclidean",meth), side=2,line=3,cex=.8)
#    dev.off()
