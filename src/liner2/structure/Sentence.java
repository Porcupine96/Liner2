package liner2.structure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;


/**
 * Reprezentuje zdanie jako sekwencję tokenów i zbiór anotacji.
 * @author czuk
 *
 */
public class Sentence {
	
	/* Indeks nazw atrybutów */
	AttributeIndex attributesIndex = null;
	
	/* Sekwenale cja tokenów wchodzących w skład zdania */
	ArrayList<Token> tokens = new ArrayList<Token>();
	
	/* Zbiór anotacji */
	HashSet<Chunk> chunks = new HashSet<Chunk>();
	
	public Sentence()	{}
	
	public void addChunk(Chunk chunk) {
		chunks.add(chunk);
	}
	
	public void addToken(Token token) {
		tokens.add(token);
	}
	
	/*
	 * Zwraca chunk dla podanego indeksu tokenu.
	 */
	public Chunk getChunkAt(int idx) {
		Chunk returning = null;
		Iterator<Chunk> i_chunk = chunks.iterator();
		while (i_chunk.hasNext()) {
			Chunk currentChunk = i_chunk.next();
			if ((currentChunk.getBegin() <= idx) &&
				(currentChunk.getEnd() >= idx)) {
				returning = currentChunk;
				break;
			}
		}
		return returning;
	}
	
	public HashSet<Chunk> getChunks() {
		return this.chunks;
	}
	
	/*
	 * Zwraca ilość tokenów.
	 */
	public int getTokenNumber() {
		return tokens.size();
	}
	
	public ArrayList<Token> getTokens() {
		return tokens;
	}
}
