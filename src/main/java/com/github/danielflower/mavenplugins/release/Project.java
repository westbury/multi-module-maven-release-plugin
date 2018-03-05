package com.github.danielflower.mavenplugins.release;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class Project {

	private String XML;
	private int processIndex;
	private List<Object> segments = new ArrayList<>();
	
	public String groupId;
	public String artifactId;
	public LocatableText version;
	public Coordinates parent;
	public Set<Coordinates> dependencies;

	public Project(String XML) throws FactoryConfigurationError, XMLStreamException {
		this.XML = XML;
		this.processIndex = 0;
		parsePom(XML);
	}
	
	private void parsePom(String XML) throws FactoryConfigurationError, XMLStreamException {
		XMLInputFactory inputFactory = XMLInputFactory.newInstance();

		//            XMLEventReader reader = inputFactory.createXMLEventReader(new StringReader(XML));
		XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(XML)); 

		while (reader.hasNext()) {
			int event = reader.next();

			switch (event) {
			case XMLStreamConstants.START_ELEMENT: 
				String localName = reader.getLocalName();
				assert(localName.equals("project"));
				parseProjectElement(reader);
				break;

			case XMLStreamConstants.END_ELEMENT:
				assert(false);
				break;

			case XMLStreamConstants.START_DOCUMENT:
				break;
			}
		}

		String remainingText = XML.substring(processIndex);
		segments.add(remainingText);
	}

	private void parseProjectElement(XMLStreamReader reader) throws XMLStreamException {
		int event = reader.next();
		while (event != XMLStreamConstants.END_ELEMENT) {
			if (event == XMLStreamConstants.START_ELEMENT) {
				String localName = reader.getLocalName();
				switch (localName) {
				case "groupId":
					groupId = parseText(reader);
					break;
				case "artifactId":
					artifactId = parseText(reader);
					break;
				case "version":
					version = parseTextAsLocation(reader);
					break;
				case "parent":
					parent = parseParentElement(reader);
					break;
				case "dependencies":
					dependencies = parseDependenciesElement(reader);
					break;
				default:
					parseElement(reader);
				}
			}
			event = reader.next();
		}
	}

	private Coordinates parseParentElement(XMLStreamReader reader) throws XMLStreamException {
		Coordinates parent = new Coordinates();
		int event = reader.next();
		while (event != XMLStreamConstants.END_ELEMENT) {
			if (event == XMLStreamConstants.START_ELEMENT) {
				String localName = reader.getLocalName();
				switch (localName) {
				case "groupId":
					parent.groupId = parseText(reader);
					break;
				case "artifactId":
					parent.artifactId = parseText(reader);
					break;
				case "version":
					parent.version = parseTextAsLocation(reader);
					break;
				default:
					parseElement(reader);
				}
			}
			event = reader.next();
		}
		return parent;
	}

	/**
	 * On entry, the reader will be positioned on the start element event.  On exit
	 * the reader will be positioned on the matching end element event.
	 * 
	 * @param reader
	 * @throws XMLStreamException 
	 */
	private void parseElement(XMLStreamReader reader) throws XMLStreamException {
		int event = reader.next();
		while (event != XMLStreamConstants.END_ELEMENT) {
			if (event == XMLStreamConstants.START_ELEMENT) {
				parseElement(reader);
			}
			event = reader.next();
		}
	}

	/**
	 * On entry, the reader will be positioned on the <dependencies> start element event.  On exit
	 * the reader will be positioned on the matching end element event.
	 * <P>
	 * @param reader
	 * @return 
	 * @throws XMLStreamException 
	 */
	private Set<Coordinates> parseDependenciesElement(XMLStreamReader reader) throws XMLStreamException {
		Set<Coordinates> dependencies = new HashSet<>();
		int event = reader.next();
		while (event != XMLStreamConstants.END_ELEMENT) {
			if (event == XMLStreamConstants.START_ELEMENT) {
				switch (reader.getLocalName()) {
				case "dependency":
					Coordinates d = parseDependencyElement(reader);
					dependencies.add(d);
					break;
				default:
					parseElement(reader);
				}
			}
			event = reader.next();
		}
		return dependencies;
	}

	/**
	 * On entry, the reader will be positioned on the <dependencies> start element event.  On exit
	 * the reader will be positioned on the matching end element event.
	 * <P>
	 * @param reader
	 * @return 
	 * @throws XMLStreamException 
	 */
	private Coordinates parseDependencyElement(XMLStreamReader reader) throws XMLStreamException {
		Coordinates dependency = new Coordinates();
		int event = reader.next();
		while (event != XMLStreamConstants.END_ELEMENT) {
			if (event == XMLStreamConstants.START_ELEMENT) {
				switch (reader.getLocalName()) {
				case "groupId":
					dependency.groupId = parseText(reader);
					break;
				case "artifactId":
					dependency.artifactId = parseText(reader);
					break;
				case "version":
					dependency.version = parseTextAsLocation(reader);
					break;
				default:
					parseElement(reader);
				}
			}
			event = reader.next();
		}
		return dependency;
	}

	/**
	 * On entry, the reader will be positioned on the start element event.  On exit
	 * the reader will be positioned on the matching end element event.
	 * <P>
	 * @param reader
	 * @return 
	 * @throws XMLStreamException 
	 */
	private String parseText(XMLStreamReader reader) throws XMLStreamException {
		String result = "";
		int nextEvent = reader.next();
		if (nextEvent == XMLStreamConstants.CHARACTERS) {
			result = reader.getText();
			nextEvent = reader.next();
		}
		return result;
	}


	/**
	 * On entry, the reader will be positioned on the start element event.  On exit
	 * the reader will be positioned on the matching end element event.
	 * <P>
	 * @param reader
	 * @return 
	 * @throws XMLStreamException 
	 */
	private LocatableText parseTextAsLocation(XMLStreamReader reader) throws XMLStreamException {
		LocatableText result;
		int startIndex = reader.getLocation().getCharacterOffset();
		int nextEvent = reader.next();
		int endIndex;
		String text;
		if (nextEvent == XMLStreamConstants.CHARACTERS) {
			text = reader.getText();
			endIndex = reader.getLocation().getCharacterOffset()-2;
			nextEvent = reader.next();
		} else {
			text = "";
			endIndex = startIndex;
		}
		
		result = new LocatableText(text, startIndex, endIndex);

		String priorUnprocessedText = XML.substring(processIndex, startIndex);
		segments.add(priorUnprocessedText);
		segments.add(result);
		processIndex = endIndex;
		
		return result;
	}

	public void setVersion(String newVersion) {
		version.setValue(newVersion);
	}

	public void setParentVersion(String newVersion) {
		parent.version.setValue(newVersion);
	}

	public void setDependencyVersion(String groupId, String artifactId, String newVersion) {
		Optional<Coordinates> dependentProject = dependencies.stream().filter(p -> p.groupId.equals(groupId) && p.artifactId.equals(artifactId)).findFirst();
		dependentProject.get().version.setValue(newVersion);
	}

	public String getPom() {
		StringBuffer buffer = new StringBuffer(XML.length() + 100);
		segments.stream().forEach(segment -> buffer.append(segment.toString()));
		return buffer.toString();
	}
	
	public class Coordinates {
		public String groupId;
		public String artifactId;
		public LocatableText version;
	}

//	class Dependency {
//		public String groupId;
//		public String artifactId;
//		public String version;
//	}

	public class LocatableText {
		String text;
		int startOffset;
		int endOffset;

		public LocatableText(String text, int startOffset, int endOffset) {
			this.text = text;
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			
			assert(text.equals(XML.substring(startOffset, endOffset)));
		}

		public void setValue(String newText) {
			this.text = newText;
		}
		
		// This is used when combining segments
		public String toString() {
			return text;
		}
	}

}