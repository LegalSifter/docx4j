
package org.docx4j


import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart
import org.docx4j.wml.RunDel
import org.junit.Test

//import org.docx4j.*
//import org.docx4j.wml.*
//import org.docx4j.wml.Document

public class ScratchTest {
    @Test
    public void testIssue212() throws Exception {
//        File f = new File("/home/jason/Projects/legalsifter/lsift/lsift/lsift/test/resources/docx-test/empty-del-empty-ins.zip")
        File f = new File("/home/jason/Projects/legalsifter/lsift/lsift/lsift/test/resources/docx-test/del-in" +
                "-rprchange.zip")
//        File f = new File("/home/jason/Projects/legalsifter/lsift/lsift/lsift/test/resources/docx-test/ins2.zip")


        WordprocessingMLPackage wml = WordprocessingMLPackage.load(f);
        MainDocumentPart mdp = wml.getMainDocumentPart();
//        List<RunDel> ps = mdp.getJAXBNodesViaXPath(".//w:del", false)
        List<RunDel> ps = mdp.getJAXBNodesViaXPath(".//w:del", false)

        println ps.size()


    }
}
