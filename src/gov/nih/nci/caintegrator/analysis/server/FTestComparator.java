/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server;


import gov.nih.nci.caintegrator.analysis.messaging.FTestResultEntry;

import java.util.Comparator;

/**
 * This comparator will sort a list of ClassComparisonResultEntries 
 * first by ascending p-value and then by descenging absolute fold change.
 * 
 * @author harrismic
 *
 */




public class FTestComparator implements Comparator {

	public FTestComparator() {
		
	}

	public int compare(Object o1, Object o2) {
		if (!(o1 instanceof FTestResultEntry)) {
		  return 0;
		}
		
		if (!(o2 instanceof FTestResultEntry)) {
			  return 0;
		}
		
		FTestResultEntry e1 = (FTestResultEntry) o1;
		FTestResultEntry e2 = (FTestResultEntry) o2;
		
		if (e1.getPvalue() < e2.getPvalue()) {
		  return -1;
		}
		
		if (e2.getPvalue() < e1.getPvalue()) {
		  return 1;
		}
		
		double absFC1 = Math.abs(e1.getMaximumFoldChange());
		double absFC2 = Math.abs(e2.getMaximumFoldChange());
		
		if (absFC1 > absFC2) {
		  return -1;
		}
		
		if (absFC2 > absFC1) {
		  return 1;
		}
		
		return 0;
	}

}
