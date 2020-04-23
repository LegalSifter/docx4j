package org.docx4j;

import org.apache.commons.io.FileUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.junit.Test;

import java.io.File;

public class ScratchTest {
   @Test
   public void testIssue212() throws Exception {

      File origFile = new File("./created-in-word.docx");


      File f = new File("./outfile.zip");
      FileUtils.copyFile(origFile, f);

      WordprocessingMLPackage wp = WordprocessingMLPackage.load(f);

      MainDocumentPart documentPart = wp.getMainDocumentPart();
      documentPart.getXML();
      wp.save(f);
   }
}
