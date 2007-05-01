
package gov.nih.nci.caintegrator.analysis.server;

import java.util.Comparator;
import gov.nih.nci.caintegrator.analysis.messaging.GeneralizedLinearModelResultEntry;


public class GeneralizedLinearModelComparator implements Comparator {
	
	public int compare(Object o1, Object o2) {
		
//		if(!(o1 instanceof GeneralizedLinearModelResultEntry)) {
//			return 0;
//		   }
//		if(!(o2 instanceof GeneralizedLinearModelResultEntry)) {
//		     return 0;
//	       }
//		
//		GeneralizedLinearModelResultEntry glmResultEntry = (GeneralizedLinearModelResultEntry)o1;
//		GeneralizedLinearModelResultEntry glmResultEntry2 = (GeneralizedLinearModelResultEntry)o2;
//		
//		if(glmResultEntry.getPvalue() < glmResultEntry2.getPvalue()) {
//			return -1;
//			}
//		if(glmResultEntry.getPvalue() > glmResultEntry2.getPvalue()) {
//			return 1;
//			}
//		
//		if(glmResultEntry.getAdjustedPvalue() < glmResultEntry2.getAdjustedPvalue()) {
//			return -1;
//			}
//		if(glmResultEntry.getAdjustedPvalue() > glmResultEntry2.getAdjustedPvalue()) {
//			return 1;
//			}
//		
//		
//		double abFC = Math.abs(glmResultEntry.getAbsoluteFoldChange());
//		double abFC2 = Math.abs(glmResultEntry2.getAbsoluteFoldChange());
//		
//		
//		
//		if(abFC < abFC2) {
//			return -1;
//			}
//		if(abFC > abFC2) {		
//			return 1;
//			}
//		
//		 return 0;
//		}
        return 0;
    }
	
}