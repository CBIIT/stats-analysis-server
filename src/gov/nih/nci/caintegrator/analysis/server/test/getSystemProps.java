/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server.test;

import java.util.*;





public class getSystemProps {

  public static void main(String args[]) {
     Properties p = System.getProperties();

     p.list(System.out);

  }

}
