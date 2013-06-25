/*L
 *  Copyright SAIC
 *
 *  Distributed under the OSI-approved BSD 3-Clause License.
 *  See http://ncip.github.com/stats-analysis-server/LICENSE.txt for details.
 */

package gov.nih.nci.caintegrator.analysis.server.test;

import java.util.*;
import java.net.*;





public class getHostName {

  public static void main(String args[]) {
  
     try {
        InetAddress addr = InetAddress.getLocalHost();
    
        // Get IP Address
        byte[] ipAddr = addr.getAddress();
    
        // Get hostname
        String hostname = addr.getHostName();
        System.out.println("Host Name=" + hostname);
    } catch (UnknownHostException e) {
    }       

  }

}
