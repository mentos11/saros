package de.fu_berlin.inf.dpp.server.console;

import static java.util.stream.Collectors.partitioningBy;

import de.fu_berlin.inf.dpp.net.util.XMPPUtils;
import de.fu_berlin.inf.dpp.net.xmpp.JID;
import de.fu_berlin.inf.dpp.session.ISarosSessionManager;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

public class InviteCommand extends ConsoleCommand {
  private static final Logger log = Logger.getLogger(InviteCommand.class);
  private final ISarosSessionManager sessionManager;

  public InviteCommand(ISarosSessionManager sessionManager, ServerConsole console) {
    this.sessionManager = sessionManager;
    console.registerCommand(this);
  }

  @Override
  public String identifier() {
    return "invite";
  }

  @Override
  public String help() {
    return "invite <JID>... - Invite users to session";
  }

  @Override
  public void execute(List<String> args, PrintStream out) {
    try {
      Map<Boolean, List<JID>> jids =
          args.stream().map(JID::new).collect(partitioningBy(XMPPUtils::validateJID));

      for (JID jid : jids.get(false)) {
        log.warn("Invalid JID skipped: " + jid);
      }

      sessionManager.invite(jids.get(true), "Invitation by server command");
      for (JID jid : jids.get(true)) {
        sessionManager.startSharingProjects(jid);
      }
    } catch (Exception e) {
      log.error("Error inviting users", e);
    }
  }
}
