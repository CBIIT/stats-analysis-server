package gov.nih.nci.caintegrator.analysis.server.test;

import java.util.*;

public class getSystemProps {

  public static void main(String args[]) {
     Properties p = System.getProperties();

     p.list(System.out);

  }

}
