package de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.sarosFinder.remoteComponents.views;

import java.rmi.RemoteException;

import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;

import de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.finder.remoteWidgets.STFBotStyledText;
import de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.finder.remoteWidgets.STFBotTree;
import de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.finder.remoteWidgets.STFBotView;
import de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.sarosFinder.remoteComponents.EclipseComponentImp;

public class ConsoleViewImp extends EclipseComponentImp implements ConsoleView {

    private static transient ConsoleViewImp consoleViewObject;
    private STFBotView view;
    private STFBotTree tree;

    /**
     * {@link ConsoleViewImp} is a singleton, but inheritance is possible.
     */
    public static ConsoleViewImp getInstance() {
        if (consoleViewObject != null)
            return consoleViewObject;
        consoleViewObject = new ConsoleViewImp();
        return consoleViewObject;
    }

    public ConsoleView setView(STFBotView view) throws RemoteException {
        this.view = view;
        tree = view.bot().tree();
        return this;
    }

    /**************************************************************
     * 
     * exported functions
     * 
     **************************************************************/

    /**********************************************
     * 
     * states
     * 
     **********************************************/
    public String getTextInConsole() throws RemoteException {
        return bot().view(VIEW_CONSOLE).bot().styledText().getText();
    }

    /**********************************************
     * 
     * waits until
     * 
     **********************************************/
    public void waitUntilTextInViewConsoleExists() throws RemoteException {
        bot().waitUntil(new DefaultCondition() {
            public boolean test() throws Exception {
                try {
                    STFBotStyledText styledText = bot().view(VIEW_CONSOLE)
                        .bot().styledText();
                    if (styledText != null && styledText.getText() != null
                        && !styledText.getText().equals(""))
                        return true;
                    else
                        return false;
                } catch (WidgetNotFoundException e) {
                    return false;
                }
            }

            public String getFailureMessage() {
                return "in the console view contains no text.";
            }
        });
    }
}
