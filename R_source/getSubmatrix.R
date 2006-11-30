
########################
# File: getSubmatrix.R
# Author: Huaitian Liu
# Date: September 2005
########################

# Generate submatrix based on sample IDs
getSubmatrix.twogrps <- function(datmat, grp1ids, grp2ids) {
	allids <- dimnames(datmat)[[2]]
	if (length(dimnames(datmat)[[1]])==1) {
	Submatrix <- t(as.matrix(c(datmat[,allids%in%grp1ids],datmat[,allids%in%grp2ids])))
	dimnames(Submatrix)[[1]] <- dimnames(datmat)[[1]]
	}
	else {
    	Submatrix <- cbind(datmat[,allids%in%grp1ids],datmat[,allids%in%grp2ids])
	}
    	return(as.matrix(Submatrix))
}

# Submatrix <- getSubmatrix.twogrps(datmat, grp1ids, grp2ids)  

# Generate submatrix based on one group sample IDs
getSubmatrix.onegrp <- function(datmat, grpids) {
	allids <- dimnames(datmat)[[2]]
    Submatrix <- as.matrix(datmat[,allids%in%grpids])
    return(Submatrix)
}

# Submatrix <- getSubmatrix.onegrp(datmat, grpids)
  
# Generate submatrix based on reporters
getSubmatrix.rep <- function(datmat, rep.ids) {
	allrep.ids <- dimnames(datmat)[[1]]
	if (length(rep.ids)==1) {
	Submatrix.rep <- t(as.matrix(datmat[allrep.ids%in%rep.ids,]))
	dimnames(Submatrix.rep)[[1]] <- rep.ids
	}
	else {
        Submatrix.rep <- as.matrix(datmat[allrep.ids%in%rep.ids,])
	}
   	return(Submatrix.rep)
}

# Submatrix.rep <- getSubmatrix.rep(datmat, rep.ids)  
