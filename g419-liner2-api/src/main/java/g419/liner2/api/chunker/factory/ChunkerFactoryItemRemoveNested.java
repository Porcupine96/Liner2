package g419.liner2.api.chunker.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.ini4j.Ini;

import g419.liner2.api.chunker.Chunker;
import g419.liner2.api.chunker.RemoveNestedChunker;


public class ChunkerFactoryItemRemoveNested extends ChunkerFactoryItem {

	public static String INI_TYPES = "annotation-types";
	
	public ChunkerFactoryItemRemoveNested() {
		super("remove-nested");
	}

	@Override
	public Chunker getChunker(Ini.Section description, ChunkerManager cm) throws Exception {
        g419.corpus.Logger.log("--> RemoveNested chunker");
        
        List<Pattern> types = new ArrayList<Pattern>(); 
        
        if ( description.containsKey(INI_TYPES) ) {
        	for ( String type : description.get(INI_TYPES).split(";") ){
        		types.add(Pattern.compile("^" + type.trim() + "$"));
        	}
        }
        else{
        	types.add(Pattern.compile("^.+$"));
        }
        
        return new RemoveNestedChunker(types);
	}

}
