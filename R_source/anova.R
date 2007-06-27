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

eagle.anova.single <- function(rptr_exps, subids, grpids, is.covar=FALSE, covar) ##covs is for data matrix
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
			lm<-lm(rptrSubExp~grps+adjustment)
			anovalm<-anova(lm)
			anovaglm.pr<-anovalm$Pr
		}
		else {
			if (dim(covar)[2]==1){
				adjustment1<-covar[,1]
				if (mode(as.vector(covar[,1]))== "character"){
					adjustment1<-as.factor(covar[,1])
				}
				lm<-lm(rptrSubExp~grps+adjustment1)
				anovalm<-anova(lm)
				anovaglm.pr<-anovalm$Pr
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
				lm<-lm(rptrSubExp~grps+adjustment1+adjustment2)
				anovalm<-anova(lm)
				anovaglm.pr<-anovalm$Pr
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
				lm<-lm(rptrSubExp~grps+adjustment1+adjustment2+adjustment3)
				anovalm<-anova(lm)
				anovaglm.pr<-anovalm$Pr
			}
		}
	}
	else { 
		lm<-lm(rptrSubExp~grps)
		anovalm<-anova(lm)
		anovaglm.pr<-anovalm$Pr
	}
pValues<-anovaglm.pr[1]
return(pValues)
}

eagle.anova.array<- function(datamat, subids, group.ids, is.covar=FALSE, covar){
	if (is.covar){
		pv_anova<-apply(datamat, 1, eagle.anova.single, subids, group.ids, is.covar=FALSE)
		pv_cov<-apply(datamat, 1, eagle.anova.single, subids, group.ids, is.covar=TRUE, covar)
		
		Pv_Pairs<-rbind(pv_anova, pv_cov)
		rownames(Pv_Pairs)=c("beforeAdjustmentPvalue", "afterAdjustmentPvalue")
		Pv_Pairs<-t(Pv_Pairs)
	}
	else {
		pv_anova<-apply(datamat, 1, eagle.anova.single, subids, group.ids, is.covar=FALSE)	
		Pv_Pairs<-pv_anova
		dim(Pv_Pairs)<-c(dim(datamat)[1], 1)
		colnames(Pv_Pairs)="noAdjustmentPvalue"
		rownames(Pv_Pairs)=names(pv_anova)
	}
	return(Pv_Pairs)
}

eagle.fold.array<- function(datamat, subids, group.ids){
	means<-t(apply(datamat,1, eagle.grpmean.single, subids, group.ids))
	dim.means<-dim(means)
	fold<-means/means[,1]
	ret<-list(FOLD=fold, AVEAGE=means)
	return(ret)
}

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
