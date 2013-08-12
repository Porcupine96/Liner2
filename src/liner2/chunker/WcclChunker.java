package liner2.chunker;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import liner2.reader.ReaderFactory;
import liner2.reader.StreamReader;

import liner2.structure.TokenAttributeIndex;
import liner2.structure.Annotation;
import liner2.structure.AnnotationSet;
import liner2.structure.Paragraph;
import liner2.structure.ParagraphSet;
import liner2.structure.Sentence;

import liner2.writer.StreamWriter;
import liner2.writer.WriterFactory;

import liner2.Main;

/*
 * @author Maciej Janicki
 */

public class WcclChunker extends Chunker {
	
	private String wcclFile = null;
	
	public WcclChunker()	{}
	
	public void setWcclFile(String filename) {
		this.wcclFile = filename;
	}
	
	private AnnotationSet chunkSentence(Sentence sentence) {
		AnnotationSet chunking = new AnnotationSet(sentence);
		String cmd = "wccl-rules -q -t nkjp -i ccl -I - -C " + this.wcclFile;
		Process p = null;
		
		try {
			p = Runtime.getRuntime().exec(cmd);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
		BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		InputStream in = p.getInputStream();
		OutputStream out = p.getOutputStream();
		
		// zapamiętaj AttributeIndex, żeby nie stracić go przy addSentence()
		TokenAttributeIndex ai = sentence.getAttributeIndex();
		ParagraphSet paragraphSet = new ParagraphSet();
		Paragraph paragraph = new Paragraph(null);
		paragraph.addSentence(sentence);
		paragraphSet.addParagraph(paragraph);
		paragraphSet.setAttributeIndex(ai);
		
		try {
			StreamWriter writer = WriterFactory.get().getStreamWriter(out, "ccl");
			writer.writeParagraphSet(paragraphSet);
			writer.close();
			StreamReader reader = ReaderFactory.get().getStreamReader(in, "ccl");
			if (err.ready()) {
				while (err.ready())
					// TODO rzucić wyjątek?
					Main.log("WCCL error: " + err.readLine()); 
				return null;
			}
			paragraph = reader.readParagraph();
			reader.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		Sentence resultSentence = paragraph.getSentences().get(0);
		for (Annotation chunk : resultSentence.getChunks())
			if (!chunking.contains(chunk))
				chunking.addChunk(chunk);
		return chunking;
	}	
	
	@Override
	public HashMap<Sentence, AnnotationSet> chunk(ParagraphSet ps) {
		HashMap<Sentence, AnnotationSet> chunkings = new HashMap<Sentence, AnnotationSet>();
		for ( Paragraph paragraph : ps.getParagraphs() )
			for (Sentence sentence : paragraph.getSentences())
				chunkings.put(sentence, this.chunkSentence(sentence));
		return chunkings;
	}
	
}
