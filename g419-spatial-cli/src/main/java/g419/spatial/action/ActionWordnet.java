package g419.spatial.action;

import g419.lib.cli.Action;
import g419.toolbox.wordnet.Wordnet3;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.log4j.Logger;
import pl.wroc.pwr.ci.plwordnet.plugins.princetonadapter.da.PrincetonDataRaw;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ActionWordnet extends Action {

  // ToDo: przerobić na parametr
  //private String wordnet = "/nlp/resources/plwordnet/plwordnet_2_1_0/plwordnet_2_1_0_pwn_format";
  private String wordnet = "/nlp/resources/plwordnet/plwordnet_2_3_mod/plwordnet_2_3_pwn_format/";

  public ActionWordnet() {
    super("wordnet");
    this.setDescription("test wordnet core");

    this.options.addOption(Option.builder("w").hasArg().argName("FILENAME").required()
        .desc("ścieżka do katalogu z wordnetem w formacie Princeton").longOpt("wordnet").build());
  }

  /**
   * Parse action options
   *
   * @param arg0 The array with command line parameters
   */
  @Override
  public void parseOptions(final CommandLine line) throws Exception {
    if (line.hasOption("w")) {
      this.wordnet = line.getOptionValue("w");
    }
  }

  @Override
  public void run() throws Exception {
    Logger.getLogger(this.getClass()).info("Wczytuję wordnet ...");
    Wordnet3 w = new Wordnet3(this.wordnet);
    Logger.getLogger(this.getClass()).info("gotowe.");

    String line = null;
    System.out.print("Podaj słowo: ");
    while (((line = System.console().readLine().trim())).length() > 0) {
      List<PrincetonDataRaw> synsets = w.getSynsets(line);

      for (PrincetonDataRaw synset : synsets) {
        System.out.println();
        System.out.println("Synset: " + String.join(", ", w.getLexicalUnits(synset)) + "; Domena=" + synset.domain);
        Set<String> holonyms = new TreeSet<String>();
        for (PrincetonDataRaw s : w.getHolonyms(synset)) {
          holonyms.addAll(w.getLexicalUnits(s));
        }
        System.out.println("Holonimy: " + String.join(", ", holonyms));

        Set<String> hiperonyms = new TreeSet<String>();
        for (PrincetonDataRaw s : w.getAllSynsets(synset, Wordnet3.REL_HYPERNYM)) {
          hiperonyms.addAll(w.getLexicalUnits(s));
        }
        System.out.println("Hiperonimy: " + String.join(", ", hiperonyms));
      }

      System.out.println();
      System.out.print("Podaj słowo: ");
    }


  }


}
