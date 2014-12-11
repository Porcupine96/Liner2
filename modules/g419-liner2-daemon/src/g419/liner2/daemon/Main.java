package g419.liner2.daemon;

import g419.corpus.TerminateException;
import g419.lib.cli.action.Action;
import g419.liner2.api.tools.ParameterException;
import org.apache.commons.cli.ParseException;

import java.util.HashMap;

/**
 * Run the module. 
 * 
 * @author Michał Marcińczuk
 * @author Maciej Janicki
 */
public class Main {
    
    /**
     * Here the story begins.
     */

    private HashMap<String, Action> actions = new HashMap<String, Action>();

    public static void main(String[] args) throws Exception {

        Main main = new Main();
        main.registerAction(new ActionSQL());
        main.registerAction(new ActionFileBased());

        if ( args.length == 0 ){
            main.printCredits();
            System.out.println("[Error] Mode not given. \n\nUse one of the following modes:");
            main.printActions();
            System.out.println();
            System.out.println("usage: ./liner2-daemon <mode> [options]");
            System.out.println();
        }
        else{
            String name = args[0];
            Action action = main.getAction(name);
            if ( action == null ){
                main.printCredits();
                System.out.println(String.format("[Error] Mode '%s' does not exist. \n\nUse one of the following modes:", name));
                main.printActions();
                System.out.println();
                System.err.println("usage: ./liner2-daemon <mode> [options]");
                System.out.println();
            }
            else{
                try{
                    action.parseOptions(args);
                    action.run();
                }
                catch (TerminateException e){
                    System.out.println(e.getMessage());
                }
                catch (ParseException e) {
                    main.printCredits();
                    System.out.println(String.format("[Options parse error] %s\n", e.getMessage()));
                    action.printOptions();
                    System.out.println();
                }
                catch (Exception e) {
                    e.printStackTrace();
                    System.out.println(e);
                    e.printStackTrace();
                }
            }
        }
    }

    public void printCredits(){
        System.out.println("*-----------------------------------------------------------------------------------------------*");
        System.out.println("* A framework for multitask sequence labeling, including: named entities, temporal expressions. *");
        System.out.println("*                                                                                               *");
        System.out.println("* Authors: Michał Marcińczuk (2010–2014), Michał Krautforst (2013-2014), Jan Kocoń (2014)       *");
        System.out.println("*          Adam Kaczmarek (2014) Dominik Piasecki (2013), Maciej Janicki (2011)                 *");
        System.out.println("* Contact: michal.marcinczuk@pwr.wroc.pl                                                        *");
        System.out.println("*                                                                                               *");
        System.out.println("*          G4.19 Research Group, Wrocław University of Technology                               *");
        System.out.println("*-----------------------------------------------------------------------------------------------*");
        System.out.println();
    }

    /**
     * Register a new action. The action must have unique name.
     * @param action -- object used to run the action.
     */
    public void registerAction(Action action){
        this.actions.put(action.getName(), action);
    }

    /**
     * Prints a list of available actions.
     */
    public void printActions(){
        int maxLength = 0;
        for ( String name : this.actions.keySet())
            maxLength = Math.max(maxLength, name.length());

        String lineFormat = " - %-" + maxLength + "s -- %s";

        String newLine = String.format("   %"+maxLength+"s    ", " ");

        for (Action action : this.actions.values()){
            System.out.println(String.format(lineFormat,
                    action.getName(),
                    action.getDescription()).replaceAll("#", "\n" + newLine));
        }
    }

    public Action getAction(String name){
        if ( this.actions.containsKey(name))
            return this.actions.get(name);
        else
            return null;
    }
    
}
