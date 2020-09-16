package org.docx4j;

import org.docx4j.jaxb.JAXBAssociation;
import org.w3c.dom.NamedNodeMap;

public class JAXBAssociationError {
	private String id;
	private String owner;
	private String author;
	private String xpath;
	
	public JAXBAssociationError(JAXBAssociation association) {
		NamedNodeMap attrs = association.getDomNode().getAttributes();
		this.id = attrs.getNamedItem("w:id").getNodeValue();
		this.author = attrs.getNamedItem("w:author").getNodeValue();
		this.owner = association.getDomNode().getParentNode().getNodeName();
		this.xpath = association.getDomNode().getNodeName();
	}
	
	public String getId() {
		return id;
	}
	
	public String getOwner() {
		return owner;
	}
	
	public String getAuthor() {
		return author;
	}
	
	public String getXpath() {
		return xpath;
	}
	
	@Override
	public String toString() {
		return "JAXBAssociationError{" + "id='" + id + '\'' + ", owner='" + owner + '\'' + ", author='" + author + '\'' + ", xpath='" + xpath + '\'' + '}';
	}
}
