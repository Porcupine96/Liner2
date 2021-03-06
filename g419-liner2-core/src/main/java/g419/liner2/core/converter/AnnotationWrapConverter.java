package g419.liner2.core.converter;

import g419.corpus.structure.Annotation;
import g419.corpus.structure.Document;
import g419.corpus.structure.Sentence;
import g419.corpus.structure.Token;
import g419.liner2.core.features.tokens.ClassFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by michal on 2/20/15.
 */
public class AnnotationWrapConverter extends Converter {

  private File log_file;
  private String current_doc;

  public AnnotationWrapConverter(String log_file) {
    this.log_file = new File(log_file);
    try {
      this.log_file.delete();
      this.log_file.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private ClassFeature classFeature = new ClassFeature("class");

  @Override
  public void finish(Document doc) {
    current_doc = null;
  }

  @Override
  public void start(Document doc) {
    current_doc = doc.getName();
  }

  @Override
  public void apply(Sentence sentence) {
    HashMap<Token, String> textFormsMapping = new HashMap<>();
    HashSet<Token> annotationHeads = new HashSet<>();
    HashSet<Token> wrappedTokens = new HashSet<>();
    HashSet<Token> annotatedTokens = new HashSet<>();
    HashMap<Token, String> newAnns = new HashMap<>();
    List<Token> sentenceTokens = sentence.getTokens();
    HashMap<String, LinkedHashMap<String, HashSet<String>>> bases = new HashMap<>();
    for (Annotation ann : sentence.getChunks()) {
      if (ann.getBegin() != ann.getEnd()) {
        int substIdx = -1;
        int ignIdx = -1;
        for (int i = ann.getBegin(); i <= ann.getEnd(); i++) {
          String tokClass = classFeature.generate(sentenceTokens.get(i), sentence.getAttributeIndex());
          if (tokClass != null) {
            if (tokClass.equals("subst")) {
              substIdx = i;
              break;
            } else if (tokClass.equals("ign") && ignIdx == -1) {
              ignIdx = i;
            }
          }
        }

        int headIdx = substIdx != -1 ? substIdx : ignIdx;
        if (headIdx == -1) {
          headIdx = ann.getBegin();
        }
        Token head = sentenceTokens.get(headIdx);
        String oldText = ann.getText();

        LinkedHashMap<String, HashSet<String>> annBases = new LinkedHashMap<>();
        bases.put(oldText, annBases);
        for (int i : ann.getTokens()) {
          Token t = sentenceTokens.get(i);
          HashSet<String> tokenBases = new HashSet<>();
          t.getTags().forEach(tag -> tokenBases.add(tag.getCtag().equals("ign") ? "ign" : tag.getBase()));
          annBases.put(t.getOrth(), tokenBases);
        }

        setText(head, ann, sentenceTokens, headIdx);
        head.setNoSpaceAfter(sentenceTokens.get(ann.getEnd()).getNoSpaceAfter());
        textFormsMapping.put(head, oldText);
        annotationHeads.add(head);
        newAnns.put(head, ann.getType());
        wrappedTokens.add(head);
      } else {
        annotationHeads.add(sentenceTokens.get(ann.getBegin()));
        newAnns.put(sentenceTokens.get(ann.getBegin()), ann.getType());
      }

      ann.getTokens().forEach((token) -> annotatedTokens.add(sentenceTokens.get(token)));

    }
    annotatedTokens.removeAll(annotationHeads); //tokens to remove
    sentenceTokens.removeAll(annotatedTokens);
    sentence.getChunks().clear();
    try {
      BufferedWriter logger = new BufferedWriter(new FileWriter(this.log_file, true));
      for (int i = 0; i < sentenceTokens.size(); i++) {
        if (newAnns.containsKey(sentenceTokens.get(i))) {
          Annotation wrapped = new Annotation(i, newAnns.get(sentenceTokens.get(i)), sentence);
          sentence.addChunk(wrapped);
          if (textFormsMapping.containsKey(sentenceTokens.get(i))) {
            logger.write("ANNOTATION:\t" + current_doc + "\t" + sentence.getId() + "\t" + i + "\t" + textFormsMapping.get(sentenceTokens.get(i)) + "\n");
            LinkedHashMap<String, HashSet<String>> annBases = bases.get(textFormsMapping.get(sentenceTokens.get(i)));
            for (String token : annBases.keySet()) {
              logger.write(token + "\t" + String.join("\t", annBases.get(token)) + "\n");
            }
          }
        }
      }
      logger.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void setText(Token tok, Annotation ann, List<Token> sentenceTokens, int headIdx) {
    String orth = tok.getOrth();
    String base = tok.getAttributeValue("base");
    List<Token> tokensAfter = sentenceTokens.subList(headIdx, Math.min(ann.getEnd() + 1, sentenceTokens.size()));
    List<Token> tokensBefore = sentenceTokens.subList(ann.getBegin(), headIdx);

    if (tok.getNoSpaceAfter()) {
      for (int i = 1; i < tokensAfter.size(); i++) {
        Token t = tokensAfter.get(i);
        if (t.getNoSpaceAfter()) {
          orth += t.getOrth();
          base += t.getAttributeValue("base");
        } else {
          break;
        }
      }
    }

    for (int i = tokensBefore.size() - 1; i >= 0; i--) {
      Token t = tokensBefore.get(i);
      if (t.getNoSpaceAfter()) {
        orth = t.getOrth() + orth;
        base = t.getAttributeValue("base") + base;
      } else {
        break;
      }
    }

    tok.setAttributeValue("orth", orth);
    tok.setAttributeValue("base", base);
  }
}
