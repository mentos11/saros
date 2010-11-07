package de.fu_berlin.inf.dpp.stf.client.test.basicElements;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.rmi.RemoteException;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.fu_berlin.inf.dpp.stf.client.Musician;
import de.fu_berlin.inf.dpp.stf.client.test.helpers.InitMusician;
import de.fu_berlin.inf.dpp.stf.server.BotConfiguration;
import de.fu_berlin.inf.dpp.stf.server.SarosConstant;

public class TestSessionViewObject {
    private static final String PROJECT = BotConfiguration.PROJECTNAME;
    private static final String PKG = BotConfiguration.PACKAGENAME;
    private static final String CLS = BotConfiguration.CLASSNAME;
    private static final String ROLENAME = SarosConstant.ROLENAME;
    private static final String OWNCONTACTNAME = SarosConstant.OWNCONTACTNAME;

    private static final Logger log = Logger
        .getLogger(TestSessionViewObject.class);
    private static Musician alice;
    private static Musician bob;

    @BeforeClass
    public static void initMusican() throws RemoteException {
        alice = InitMusician.newAlice();
        bob = InitMusician.newBob();
        log.trace("alice create a new proejct and a new class.");
        alice.bot.newJavaProjectWithClass(PROJECT, PKG, CLS);
        log.trace("alice share session with bob.");
        alice.buildSessionSequential(PROJECT,
            SarosConstant.CONTEXT_MENU_SHARE_PROJECT, bob);
    }

    @AfterClass
    public static void resetSaros() throws RemoteException {
        bob.bot.resetSaros();
        alice.bot.resetSaros();
    }

    @Before
    public void startUp() throws RemoteException {
        bob.bot.openSarosViews();
        alice.bot.openSarosViews();
        if (!alice.state.isInSession()) {
            bob.typeOfSharingProject = SarosConstant.USE_EXISTING_PROJECT;
            alice.buildSessionSequential(PROJECT,
                SarosConstant.CONTEXT_MENU_SHARE_PROJECT, bob);
        }
        if (bob.state.isDriver()) {
            alice.removeDriverRole(bob);
        }
        if (!alice.state.isDriver()) {
            alice.sessionV.giveDriverRole(alice.state);
        }

    }

    @After
    public void cleanUp() throws RemoteException {
        bob.bot.resetWorkbench();
        alice.bot.resetWorkbench();
        // bob.sessionV.stopFollowing();
        // alice.sessionV.stopFollowing();
    }

    @Test
    public void testSetFocusOnSessionView() throws RemoteException {
        log.trace("alice set focus on session view.");
        alice.sessionV.setFocusOnSessionView();
        assertTrue(alice.sessionV.isSessionViewActive());
        log.trace("alice close session view.");
        alice.sessionV.closeSessionView();
        assertFalse(alice.sessionV.isSessionViewActive());
        log.trace("alice open session view again");
        alice.sessionV.openSessionView();
        assertTrue(alice.sessionV.isSessionViewActive());
        log.trace("alice focus on roster view.");
        alice.rosterV.setFocusOnRosterView();
        assertFalse(alice.sessionV.isSessionViewActive());
        log.trace("testSetFocusOnSessionView is done.");
    }

    @Test
    public void testIsInSession() throws RemoteException, InterruptedException {
        assertTrue(alice.sessionV.isInSession());
        assertTrue(alice.state.isInSession());
        assertTrue(bob.sessionV.isInSession());
        assertTrue(bob.state.isInSession());
        alice.leaveSessionFirst(bob);
        assertFalse(alice.sessionV.isInSession());
        assertFalse(alice.state.isInSession());
        assertFalse(bob.sessionV.isInSession());
        assertFalse(bob.state.isInSession());
    }

    @Test
    public void testGiveDriverRole() throws RemoteException {
        assertTrue(alice.sessionV.isContactInSessionView(OWNCONTACTNAME
            + ROLENAME));
        assertTrue(alice.sessionV.isContactInSessionView(bob.getBaseJid()));
        assertTrue(bob.sessionV.isContactInSessionView(OWNCONTACTNAME));
        assertTrue(bob.sessionV.isContactInSessionView(alice.getBaseJid()
            + ROLENAME));

        log.trace("alice give bob driver role.");
        alice.sessionV.giveDriverRole(bob.state);
        assertTrue(alice.state.isDriver());
        assertTrue(bob.state.isDriver());

        assertTrue(alice.sessionV.isContactInSessionView(OWNCONTACTNAME
            + ROLENAME));
        assertTrue(alice.sessionV.isContactInSessionView(bob.getBaseJid()
            + ROLENAME));
        assertTrue(bob.sessionV.isContactInSessionView(OWNCONTACTNAME
            + ROLENAME));
        assertTrue(bob.sessionV.isContactInSessionView(alice.getBaseJid()
            + ROLENAME));

    }

    @Test
    public void testGiveExclusiveDriverRole() throws RemoteException {
        assertTrue(alice.sessionV.isContactInSessionView(OWNCONTACTNAME
            + ROLENAME));
        assertTrue(alice.sessionV.isContactInSessionView(bob.getBaseJid()));
        assertTrue(bob.sessionV.isContactInSessionView(OWNCONTACTNAME));
        assertTrue(bob.sessionV.isContactInSessionView(alice.getBaseJid()
            + ROLENAME));

        log.trace("alice give bob exclusive driver role.");
        alice.giveExclusiveDriverRole(bob);
        assertFalse(alice.state.isDriver());
        assertTrue(bob.state.isDriver());

        assertTrue(alice.sessionV.isContactInSessionView(OWNCONTACTNAME));
        assertTrue(alice.sessionV.isContactInSessionView(bob.getBaseJid()
            + ROLENAME));
        assertTrue(bob.sessionV.isContactInSessionView(OWNCONTACTNAME
            + ROLENAME));
        assertTrue(bob.sessionV.isContactInSessionView(alice.getBaseJid()));
    }

    @Test
    public void testIsInFollowMode() throws RemoteException {
        assertFalse(alice.state.isInFollowMode());
        assertFalse(bob.state.isInFollowMode());
        bob.sessionV.followThisUser(alice.state);
        assertTrue(bob.state.isInFollowMode());
        alice.sessionV.followThisUser(bob.state);
        assertTrue(alice.state.isInFollowMode());
    }

    @Test
    public void testShareYourScreenWithSelectedUser() throws RemoteException {
        alice.sessionV.shareYourScreenWithSelectedUser(bob.state);
    }
}
