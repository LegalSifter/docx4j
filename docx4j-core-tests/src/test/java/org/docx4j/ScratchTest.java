package org.docx4j;

import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.CTBookmark;
import org.junit.Test;

import javax.xml.bind.JAXBElement;
import java.io.File;

public class ScratchTest {
   @Test
   public void testIssue212() throws Exception {
   
      File origFile = new File("/home/jason/Projects/legalsifter/lsift/lsift/lsift/created-in-word.docx");
   
   
      File  f = new File("/home/jason/Projects/legalsifter/lsift/lsift/lsift/cop33333llly.zip");
      FileUtils.copyFile(origFile, f);
   
      WordprocessingMLPackage wp = WordprocessingMLPackage.load(f);
   
      String xpath = "//butttt";
      MainDocumentPart documentPart = wp.getMainDocumentPart();
//      wp.save(new File("/home/jason/Projects/legalsifter/lsift/lsift/lsift/pre.docx"));
//      List<Object> parts = documentPart.getJAXBNodesViaXPath(xpath, false);
      documentPart.getXML();
      wp.save(f);
      
   }
}
