##############################################################################################################
# File: Gneralized Linear Model
# Description: R module for higher order analysis tools - generalized linear model regression
# Author: Wei Lin
# Date: April 2007
###############################################################################################################
#
#  Note: Input includes the data frame with subject id, selected reporter id 
#         group id and selected covariance. 
#
#############################################################################################################

eagle.glm.single <- function(rptr_exps, subids, grpids, is.covar=FALSE, covar) ##covs is for data matrix
{
	rptrSubExp<-rptr_exps[subids]
	grps<-as.factor(grpids)
	uniqGrp<-unique(grpids)
	uniqGrpCount<-length(uniqGrp)
	if(is.covar){
		if(is.null(dim(covar)) & length(covar)==length(grpids)){
			adjustment<-covar
			if (mode(as.vector(covar))== "character"){
				adjustment<-as.factor(covar)
			}
			glm<-glm(rptrSubExp~grps+adjustment)
			summary.glm<-summary(glm)
			glm.coefs<-summary.glm$coef
		}
		else {
			if (dim(covar)[2]==2){
				adjustment1<-covar[,1]
				adjustment2<-covar[,2]
				if (mode(as.vector(covar[,1]))== "character"){
					adjustment1<-as.factor(covar[,1])
				}
				if (mode(as.vector(covar[,2]))== "character"){
					adjustment2<-as.factor(covar[,2])
				}
				glm<-glm(rptrSubExp~grps+adjustment1+adjustment2)
				summary.glm<-summary(glm)
				glm.coefs<-summary.glm$coef
			}
			if (dim(covar)[2]==3){
				adjustment1<-covar[,1]
				adjustment2<-covar[,2]
				adjustment3<-covar[,3]
				if (mode(as.vector(covar[,1]))== "character"){
					adjustment1<-as.factor(covar[,1])
				}
				if (mode(as.vector(covar[,2]))== "character"){
					adjustment2<-as.factor(covar[,2])
				}
				if (mode(as.vector(covar[,3]))== "character"){
					adjustment3<-as.factor(covar[,3])
				}
				glm<-glm(rptrSubExp~grps+adjustment1+adjustment2+adjustment3)
				summary.glm<-summary(glm)
				glm.coefs<-summary.glm$coef
			}
			if (dim(covar)[2]==4){
				adjustment1<-covar[,1]
				adjustment2<-covar[,2]
				adjustment3<-covar[,3]
				adjustment4<-covar[,4]
				if (mode(as.vector(covar[,1]))== "character"){
					adjustment1<-as.factor(covar[,1])
				}
				if (mode(as.vector(covar[,2]))== "character"){
					adjustment2<-as.factor(covar[,2])
				}
				if (mode(as.vector(covar[,3]))== "character"){
					adjustment3<-as.factor(covar[,3])
				}
				if (mode(as.vector(covar[,4]))== "character"){
					adjustment4<-as.factor(covar[,4])
				}
				glm<-glm(rptrSubExp~grps+adjustment1+adjustment2+adjustment3+adjustment4)
				summary.glm<-summary(glm)
				glm.coefs<-summary.glm$coef
			}
		}
	}
	else { 
		glm<-glm(rptrSubExp~grps);
		summary.glm<-summary(glm);
		glm.coefs<-summary.glm$coef;
	}
	pValues<-glm.coefs[2:(uniqGrpCount), 4]
	pValNames<-names(pValues)
	pValNewNames<-substr(pValNames, 5, 10000)
	pValNewNames->names(pValues)
	return(pValues)
}

eagle.glm.array<- function(datamat, subids, group.ids, is.covar=FALSE, covar){
	if (is.covar){
		pv_glm<-apply(datamat, 1, eagle.glm.single, subids, group.ids, is.covar=FALSE)
		pv_cov<-apply(datamat, 1, eagle.glm.single, subids, group.ids, is.covar=TRUE, covar)
		
		pb_grps<-rownames(pv_glm)
		pb_grps<-paste(pb_grps, "_beforeAdjustment", sep="")
		rownames(pv_glm)<-pb_grps
		
		pa_grps<-rownames(pv_cov)
		pa_grps<-paste(pa_grps, "_afterAdjustment", sep="")
		rownames(pv_cov)<-pa_grps
	
		Pv_Pairs<-rbind(pv_glm, pv_cov)
	}
	else {
		pv_glm<-apply(datamat, 1, eagle.glm.single, subids, group.ids, is.covar=FALSE)
		
		pb_grps<-rownames(pv_glm)
		pb_grps<-paste(pb_grps, "_beforeAdjustment", sep="")
		rownames(pv_glm)<-pb_grps
		
		Pv_Pairs<-pv_glm
	}
	Pv_Pairs<-t(Pv_Pairs)
	return(Pv_Pairs)
}

#ealge.boxplot<- function(rptr_exps, sub_id=subject.ids, grpids=group.ids, reporter=rptr){
#	rptr_exp<-rptr_exps[rptr, subject.ids]
#	mainstr<-paste("Boxplot of ", rptr, "Expression Across Groups")
#	boxplot_rptr<-boxplot(rptr_exp~group.ids, main=mainstr, ylab="Expressions", xlab="Groups")
#	return(boxplot)
#}
