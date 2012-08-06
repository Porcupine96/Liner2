package liner2.writer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;

import liner2.structure.Chunk;
import liner2.structure.Paragraph;
import liner2.structure.Sentence;
import liner2.structure.Tag;
import liner2.structure.Token;

public class CclStreamWriter extends StreamWriter {

	private final String TAG_ANN			= "ann";
	private final String TAG_BASE 			= "base";
	private final String TAG_CHAN			= "chan";
	private final String TAG_CTAG			= "ctag";
	private final String TAG_DISAMB			= "disamb";
	private final String TAG_ID				= "id";
	private final String TAG_NS				= "ns";
	private final String TAG_ORTH			= "orth";
	private final String TAG_PARAGRAPH 		= "chunk";
	private final String TAG_PARAGRAPH_SET 	= "chunkList";
	private final String TAG_SENTENCE		= "sentence";
	private final String TAG_TAG			= "lex";
	private final String TAG_TOKEN 			= "tok";

	private XMLStreamWriter xmlw;
	private OutputStream os;
	private boolean open = false;
	private boolean indent = true;
	
	public CclStreamWriter(OutputStream os) {
		this.os = os;
		XMLOutputFactory xmlof = XMLOutputFactory.newFactory();
		try {
			this.xmlw = xmlof.createXMLStreamWriter(os);
		} catch (XMLStreamException ex) {
			ex.printStackTrace();
		}
	}
	
	public void open() {
		if (open)
			return;
		try {
			xmlw.writeStartDocument();
			xmlw.writeCharacters("\n");
			xmlw.writeStartElement(TAG_PARAGRAPH_SET);
			xmlw.writeCharacters("\n");
		} catch (XMLStreamException ex) {
			ex.printStackTrace();
		}
		open = true;
	}
	
	@Override
	public void close() {
		try {
			xmlw.writeEndDocument();
			xmlw.close();
			os.close();
		} catch (XMLStreamException ex) {
			ex.printStackTrace();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	
	@Override
	public void writeParagraph(Paragraph paragraph) {
		try {
			if (!open)
				open();
			this.indent(1);
			xmlw.writeStartElement(TAG_PARAGRAPH);
			
			Set<String> chunkMetaDataKeys = paragraph.getKeysChunkMetaData();
			for(String key : chunkMetaDataKeys){
				xmlw.writeAttribute(key, paragraph.getChunkMetaData(key));
			}
			
			if (paragraph.getId() != null)
				xmlw.writeAttribute(TAG_ID, paragraph.getId());
			xmlw.writeCharacters("\n");
			for (Sentence sentence : paragraph.getSentences())
				writeSentence(sentence);
			this.indent(1);
			xmlw.writeEndElement();
			xmlw.writeCharacters("\n");
			xmlw.flush();
		} catch (XMLStreamException ex) {
			ex.printStackTrace();
		}
	}
	
	private void writeSentence(Sentence sentence) throws XMLStreamException {
		this.indent(2);
		xmlw.writeStartElement(TAG_SENTENCE);
		if (sentence.getId() != null)
			xmlw.writeAttribute(TAG_ID, sentence.getId());
		xmlw.writeCharacters("\n");
		
		// prepare annotation channels
		HashSet<Chunk> chunks = sentence.getChunks();	
		Hashtable<String, Integer> numChannels = new Hashtable<String, Integer>();
		Hashtable<Chunk, Integer> channels = new Hashtable<Chunk, Integer>();
		for (Chunk chunk : chunks) {
			if (numChannels.containsKey(chunk.getType()))
				numChannels.put(chunk.getType(),
					numChannels.get(chunk.getType()) + 1);
			else
				numChannels.put(chunk.getType(), new Integer(1));
			channels.put(chunk, numChannels.get(chunk.getType()));
		}
		
		ArrayList<Token> tokens = sentence.getTokens();
		for (int i = 0; i < tokens.size(); i++)
			writeToken(i, tokens.get(i), chunks, channels);
		this.indent(2);
		xmlw.writeEndElement();
		xmlw.writeCharacters("\n");
	}
	
	private void writeToken(int idx, Token token, HashSet<Chunk> chunks, Hashtable<Chunk, Integer> channels)
		throws XMLStreamException {
		this.indent(3);
		xmlw.writeStartElement(TAG_TOKEN);
		if (token.getId() != null)
			xmlw.writeAttribute(TAG_ID, token.getId());
		xmlw.writeCharacters("\n");
		this.indent(4);
		xmlw.writeStartElement(TAG_ORTH);
		//xmlw.writeCharacters(token.getFirstValue().replace("&", "&amp;"));
		//xmlw.writeCharacters(escapeXml(token.getFirstValue()));
		writeText(token.getFirstValue());
		xmlw.writeEndElement();
		xmlw.writeCharacters("\n");
		for (Tag tag : token.getTags())
			writeTag(tag);
		
		Hashtable<String, Integer> strChannels = new Hashtable<String, Integer>();
		for (Chunk chunk : chunks) {
			if ((chunk.getBegin() <= idx) &&
				(chunk.getEnd() >= idx))
				strChannels.put(chunk.getType(), channels.get(chunk));
		}
		for (Chunk chunk : chunks)
			if (!strChannels.containsKey(chunk.getType()))
				strChannels.put(chunk.getType(), new Integer(0));
		
		for (String channel : strChannels.keySet()) {
			this.indent(4);
			xmlw.writeStartElement(TAG_ANN);
			xmlw.writeAttribute(TAG_CHAN, channel.toLowerCase());
			xmlw.writeCharacters("" + strChannels.get(channel));
			xmlw.writeEndElement();
			xmlw.writeCharacters("\n");
		}
		
		this.indent(3);
		xmlw.writeEndElement();
		xmlw.writeCharacters("\n");

		if (token.getNoSpaceAfter()) {
			this.indent(3);
			xmlw.writeEmptyElement(TAG_NS);
			xmlw.writeCharacters("\n");
		}
	}
	
	private void writeTag(Tag tag) throws XMLStreamException {
		this.indent(4);
		xmlw.writeStartElement(TAG_TAG);
		if (tag.getDisamb())
			xmlw.writeAttribute(TAG_DISAMB, "1");
		xmlw.writeStartElement(TAG_BASE);
		//xmlw.writeCharacters(tag.getBase().replace("&", "&amp;"));
		//xmlw.writeCharacters(escapeXml(tag.getBase()));
		writeText(tag.getBase());
		xmlw.writeEndElement();
		xmlw.writeStartElement(TAG_CTAG);
		//xmlw.writeCharacters(tag.getCtag());
		writeText(tag.getCtag());
		xmlw.writeEndElement();
		xmlw.writeEndElement();
		xmlw.writeCharacters("\n");
	}

	private String escapeXml(String text) {
		//text = text.replace("&", "&amp;");
		text = text.replace("\"", "&quot;");
		text = text.replace("\'", "&apos;");
		text = text.replace("<", "&lt;");
		text = text.replace(">", "&gt;");
		return text;
	}

	private void writeText(String text) throws XMLStreamException {
		if (text.equals("\""))
			xmlw.writeEntityRef("quot");
		else if (text.equals("\'"))
			xmlw.writeEntityRef("apos");
		else if (text.equals("<"))
			xmlw.writeEntityRef("lt");
		else if (text.equals(">"))
			xmlw.writeEntityRef("gt");
		else 
			xmlw.writeCharacters(text);
	}
	
	private void indent(int repeat) throws XMLStreamException{
		if (this.indent)
			for (int i=0; i<repeat; i++)
				xmlw.writeCharacters(" ");
	}
}
