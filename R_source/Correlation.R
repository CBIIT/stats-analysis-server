
#################################################################################################
# File: Correlation.R
# Description: Write R fuction to calculate correlation coefficient
# Author: Huaitian Liu
# Date: July 2006
#################################################################################################

# Pearson Correlation is parametric test for correlation.
# Pearson correlation analysis was used to describe associations between measurements. Pearson's 
# correlation coefficient is a measure of linear association. Two variables can be perfectly related, 
# however, if the relationship is not linear, Pearson's correlation coefficient is not an appropriate
# statistics for measuring their association. 

# Spearman's correlation is a non-parametric test for the strength of the relationship between pairs of variables
# Spearman Rank Correlation measures the correlation between two sequences of values. The two sequences 
# are ranked separately and the differences in rank are calculated at each position. The range of Spearman 
# Correlation is from -1 to 1. Spearman Correlation can detect certain linear and non-linear correlations. 
# However, Pearson Correlation may be more appropriate for finding linear correlations.

# cor(a): show correlations
# cor.test(a,b): test correlation

# correlation matrix
correlation <- function(x,y,method = c("pearson", "spearman")) {
r <- cor(x, y, use = "complete.obs", method = "pearson")
return(r)
}

# A graph for correlation analysis shows the dispersion of data points, with the variable of first 
# choice on the x axis, and second one on the y axis. For better utilization of this graphic function, 
# you need to specify each of the selected variables whether it is continuous type or enumerative type.  

panel.cor <- function(x, y, digits=2, prefix="", cex.cor)
     {
         usr <- par("usr"); on.exit(par(usr))
         par(usr = c(0, 1, 0, 1))
         r <- abs(cor(x, y))
         txt <- format(c(r, 0.123456789), digits=digits)[1]
         txt <- paste(prefix, txt, sep="")
         if(missing(cex.cor)) cex <- 0.3/strwidth(txt)
         text(0.5, 0.5, txt, cex = cex)
    }

# Visualization of a Correlation Matrix. On top the (absolute) value of the correlation. On botttom, 
# the bivariate scatterplots

ScatterPlotForCorrelation <- function(x,y) {
dataMatrix <- cbind(x,y)
tmp <- apply(is.na(dataMatrix),1,sum)  
pairs(dataMatrix[tmp==0,], labels=dimnames(dataMatrix)[[2]],upper.panel=panel.cor)
}
