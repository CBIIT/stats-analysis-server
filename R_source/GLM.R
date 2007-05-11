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
			if (dim(covar)[2]==1){
				adjustment1<-covar[,1]
				if (mode(as.vector(covar[,1]))== "character"){
					adjustment1<-as.factor(covar[,1])
				}
				glm<-glm(rptrSubExp~grps+adjustment1)
				summary.glm<-summary(glm)
				glm.coefs<-summary.glm$coef
			}
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
			#if there are more than 3 covariates, we need the following code
			#if (dim(covar)[2]==4){
			#	adjustment1<-covar[,1]
			#	adjustment2<-covar[,2]
			#	adjustment3<-covar[,3]
			#	adjustment4<-covar[,4]
			#	if (mode(as.vector(covar[,1]))== "character"){
			#		adjustment1<-as.factor(covar[,1])
			#	}
			#	if (mode(as.vector(covar[,2]))== "character"){
			#		adjustment2<-as.factor(covar[,2])
			#	}
			#	if (mode(as.vector(covar[,3]))== "character"){
			#		adjustment3<-as.factor(covar[,3])
			#	}
			#	if (mode(as.vector(covar[,4]))== "character"){
			#		adjustment4<-as.factor(covar[,4])
			#	}
			#	glm<-glm(rptrSubExp~grps+adjustment1+adjustment2+adjustment3+adjustment4)
			#	summary.glm<-summary(glm)
			#	glm.coefs<-summary.glm$coef
			#}
		}
	}
	else { 
		glm<-glm(rptrSubExp~grps);
		summary.glm<-summary(glm);
		glm.coefs<-summary.glm$coef;
	}
pValues<-glm.coefs[2:(uniqGrpCount), 4]
return(pValues)
}

eagle.glm.array<- function(datamat, subids, group.ids, is.covar=FALSE, covar){
	if (is.covar){
		pv_glm<-apply(datamat, 1, eagle.glm.single, subids, group.ids, is.covar=FALSE)
		pv_cov<-apply(datamat, 1, eagle.glm.single, subids, group.ids, is.covar=TRUE, covar)
		ngrp<-length(unique(group.ids))
		pb_grps<-sort(unique(group.ids))[2:ngrp]
		pa_grps<-sort(unique(group.ids))[2:ngrp]
		if(is.null(dim(pv_glm))){
			pv_glm<-t(as.matrix(pv_glm))
		}
		if(is.null(dim(pv_cov))){
			pv_cov<-t(as.matrix(pv_cov))
		}

		pb_grps<-paste(pb_grps, "_beforeAdjustment", sep="")
		rownames(pv_glm)<-pb_grps
		
		pa_grps<-paste(pa_grps, "_afterAdjustment", sep="")
		rownames(pv_cov)<-pa_grps
	
		Pv_Pairs<-rbind(pv_glm, pv_cov)
	}
	else {
		pv_glm<-apply(datamat, 1, eagle.glm.single, subids, group.ids, is.covar=FALSE)
		if(is.null(dim(pv_glm))){
			pv_glm<-t(as.matrix(pv_glm))
		}
		ngrp<-length(unique(group.ids))
		pb_grps<-sort(unique(group.ids))[2:ngrp]
		pb_grps<-paste(pb_grps, "_beforeAdjustment", sep="")
		rownames(pv_glm)<-pb_grps
		Pv_Pairs<-pv_glm
	}
	Pv_Pairs<-t(Pv_Pairs)
	return(Pv_Pairs)
}

eagle.fold.array<- function(datamat, subids, group.ids){
	means<-t(apply(datamat,1, eagle.grpmean.single, subids, group.ids))
	dim.means<-dim(means)
	fold<-means/means[,1]
	ret<-list(FOLD=fold, AVEAGE=means)
	return(ret)
}

#ealge.boxplot<- function(rptr_exps, sub_id=subject.ids, grpids=group.ids, reporter=rptr){
#	rptr_exp<-rptr_exps[rptr, subject.ids]
#	mainstr<-paste("Boxplot of ", rptr, "Expression Across Groups")
#	boxplot_rptr<-boxplot(rptr_exp~group.ids, main=mainstr, ylab="Expressions", xlab="Groups")
#	return(boxplot)
#}

eagle.grpmean.single<-function(rptr_exps, subids, grpids){
	unigrps<-sort(unique(grpids))
	ngrp<-length(unique(grpids))
	base.subids<-subids[grpids==unigrps[1]]
	base.mean<-mean(rptr_exps[base.subids])
	mean<-c(base.mean)
	for (grp in unigrps[2:ngrp]){
		grp.subids<-subids[grpids==grp]
		grp.mean<-mean(rptr_exps[grp.subids])
		mean<-c(mean, grp.mean)
	}
	names(mean)<-unigrps
	return(mean)
}