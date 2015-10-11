package de.fhg.igd.georocket.input;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import de.fhg.igd.georocket.util.Window;
import de.fhg.igd.georocket.util.XMLStreamEvent;

/**
 * Abstract base class for splitters that split XML streams
 * @author Michel Kraemer
 */
public abstract class XMLSplitter implements Splitter {
  /**
   * A marked position. See {@link #mark(int)}
   */
  private int mark = -1;
  
  /**
   * A buffer for incoming data
   */
  private final Window window;
  
  /**
   * A stack keeping all encountered start elements
   */
  private final Deque<XMLStartElement> startElements = new ArrayDeque<>();
  
  /**
   * Create splitter
   * @param window a buffer for incoming data
   */
  public XMLSplitter(Window window) {
    this.window = window;
  }
  
  @Override
  public String onEvent(XMLStreamEvent event) {
    String chunk = onXMLEvent(event);
    if (!isMarked()) {
      if (event.getEvent() == XMLEvent.START_ELEMENT) {
        startElements.push(makeXMLStartElement(event.getXMLReader()));
      } else if (event.getEvent() == XMLEvent.END_ELEMENT) {
        startElements.pop();
      }
    }
    return chunk;
  }
  
  /**
   * Creates an {@link XMLStartElement} from the current parser state
   * @param xmlReader the XML parser
   * @return the {@link XMLStartElement}
   */
  private XMLStartElement makeXMLStartElement(XMLStreamReader xmlReader) {
    // copy namespaces (if there are any)
    int nc = xmlReader.getNamespaceCount();
    String[] namespacePrefixes = null;
    String[] namespaceUris = null;
    if (nc > 0) {
      namespacePrefixes = new String[nc];
      namespaceUris = new String[nc];
      for (int i = 0; i < nc; ++i) {
        namespacePrefixes[i] = xmlReader.getNamespacePrefix(i);
        namespaceUris[i] = xmlReader.getNamespaceURI(i);
      }
    }
    
    // copy attributes (if there are any)
    int ac = xmlReader.getAttributeCount();
    String[] attributePrefixes = null;
    String[] attributeLocalNames = null;
    String[] attributeValues = null;
    if (ac > 0) {
      attributePrefixes = new String[ac];
      attributeLocalNames = new String[ac];
      attributeValues = new String[ac];
      for (int i = 0; i < ac; ++i) {
        attributePrefixes[i] = xmlReader.getAttributePrefix(i);
        attributeLocalNames[i] = xmlReader.getAttributeLocalName(i);
        attributeValues[i] = xmlReader.getAttributeValue(i);
      }
    }
    
    // make element
    return new XMLStartElement(xmlReader.getPrefix(),
        xmlReader.getLocalName(), namespacePrefixes, namespaceUris,
        attributePrefixes, attributeLocalNames, attributeValues);
  }
  
  /**
   * Mark a position
   * @param pos the position to mark
   */
  protected void mark(int pos) {
    mark = pos;
  }
  
  /**
   * @return true if a position is marked currently
   */
  protected boolean isMarked() {
    return mark >= 0;
  }
  
  /**
   * Create a new chunk starting from the marked position and ending on the
   * given position. Reset the mark afterwards and advance the window to the
   * end position.
   * @param pos the end position
   * @return the chunk
   */
  protected String makeChunk(int pos) {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
    
    // append the full stack of start elements (backwards)
    startElements.descendingIterator().forEachRemaining(e -> sb.append(e + "\n"));
    
    // append current element
    byte[] bytes = window.getBytes(mark, pos);
    sb.append(new String(bytes, StandardCharsets.UTF_8));
    window.advanceTo(pos);
    mark = -1;
    
    // append the full stack of end elements
    startElements.iterator().forEachRemaining(e -> sb.append("\n</" + e.getName() + ">"));
    
    return sb.toString();
  }
  
  /**
   * @see #onEvent(XMLStreamEvent)
   */
  protected abstract String onXMLEvent(XMLStreamEvent event);
}
