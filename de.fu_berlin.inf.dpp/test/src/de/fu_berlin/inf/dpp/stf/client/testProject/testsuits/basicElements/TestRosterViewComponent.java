package de.fu_berlin.inf.dpp.stf.client.testProject.testsuits.basicElements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.rmi.RemoteException;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import de.fu_berlin.inf.dpp.stf.client.testProject.testsuits.STFTest;
import de.fu_berlin.inf.dpp.stf.server.rmiSarosSWTBot.eclipse.workbench.views.sarosViews.RosterView;

public class TestRosterViewComponent extends STFTest {
    private static final Logger log = Logger
        .getLogger(TestRosterViewComponent.class);

    /**
     * Preconditions:
     * <ol>
     * <li>Alice (Host, Write Access)</li>
     * <li>Bob (Read-Only Access)</li>
     * <li>Car (Read-Only Access)</li>
     * <li>Alice share a java project with bob</li>
     * </ol>
     * 
     * @throws RemoteException
     */

    @BeforeClass
    public static void runBeforeClass() throws RemoteException,
        InterruptedException {
        initTesters(TypeOfTester.ALICE, TypeOfTester.BOB, TypeOfTester.CARL);
        setUpWorkbenchs();
        setUpSaros();
        // setUpSessionByDefault(alice, bob);
    }

    @AfterClass
    public static void runAfterClass() throws RemoteException,
        InterruptedException {
        // alice.leaveSessionHostFirstDone(bob);
    }

    @Before
    public void runBeforeEveryTest() {
        //
    }

    @After
    public void runAfterEveryTest() throws RemoteException {
        if (alice.sarosBuddiesV.hasBuddyNickName(bob.jid)) {
            alice.sarosBuddiesV.renameBuddy(bob.jid, bob.jid.getBase());

        }
        if (!alice.sarosBuddiesV.hasBuddy(bob.jid)) {
            alice.addBuddyGUIDone(bob);
        }
    }

    @Test
    public void openCloseRosterView() throws RemoteException {
        alice.sarosBuddiesV.closeSarosBuddiesView();
        assertEquals(false, alice.sarosBuddiesV.isSarosBuddiesViewOpen());
        alice.sarosBuddiesV.openSarosBuddiesView();
        assertEquals(true, alice.sarosBuddiesV.isSarosBuddiesViewOpen());
    }

    @Test
    public void setFocusOnRosterView() throws RemoteException {
        alice.sarosBuddiesV.setFocusOnRosterView();
        assertTrue(alice.sarosBuddiesV.isSarosBuddiesViewActive());
        alice.sarosBuddiesV.closeSarosBuddiesView();
        assertFalse(alice.sarosBuddiesV.isSarosBuddiesViewActive());

        alice.sarosBuddiesV.openSarosBuddiesView();
        assertTrue(alice.sarosBuddiesV.isSarosBuddiesViewActive());

        alice.sarosSessionV.setFocusOnSessionView();
        assertFalse(alice.sarosBuddiesV.isSarosBuddiesViewActive());
    }

    /**
     * Steps:
     * <ol>
     * <li>alice rename bob to "bob_stf".</li>
     * <li>alice rename bob to "new bob".</li>
     * </ol>
     * 
     * Result:
     * <ol>
     * <li>alice hat contact with bob and bob'name is changed.</li>
     * <li>alice hat contact with bob and bob'name is changed.</li>
     * </ol>
     * 
     * @throws RemoteException
     */
    @Test
    public void renameBuddyWithGUI() throws RemoteException {
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        alice.sarosBuddiesV.renameBuddyGUI(bob.jid, bob.getName());
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertTrue(alice.sarosBuddiesV.getBuddyNickName(bob.jid).equals(
            bob.getName()));
        // assertTrue(alice.sessionV.isContactInSessionView(bob.jid));
        alice.sarosBuddiesV.renameBuddyGUI(bob.jid, "new bob");
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertTrue(alice.sarosBuddiesV.getBuddyNickName(bob.jid).equals(
            "new bob"));
        // assertTrue(alice.sessionV.isContactInSessionView(bob.jid));
    }

    @Test
    public void renameBuddyWithoutGUI() throws RemoteException {
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        alice.sarosBuddiesV.renameBuddy(bob.jid, bob.getName());
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertTrue(alice.sarosBuddiesV.getBuddyNickName(bob.jid).equals(
            bob.getName()));
        // assertTrue(alice.sessionV.isContactInSessionView(bob.jid));
        alice.sarosBuddiesV.renameBuddy(bob.jid, "new bob");
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertTrue(alice.sarosBuddiesV.getBuddyNickName(bob.jid).equals(
            "new bob"));
        // assertTrue(alice.sessionV.isContactInSessionView(bob.jid));
    }

    @Test
    public void addBuddyWithGUI() throws RemoteException {
        alice.deleteBuddyGUIDone(bob);
        assertFalse(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertFalse(bob.sarosBuddiesV.hasBuddy(alice.jid));
        alice.addBuddyGUIDone(bob);
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertTrue(bob.sarosBuddiesV.hasBuddy(alice.jid));
    }

    /**
     * FIXME the method
     * {@link RosterView#addANewContact(de.fu_berlin.inf.dpp.net.JID)}
     * 
     * @throws RemoteException
     * @throws XMPPException
     */
    @Test
    @Ignore
    public void addBuddyWithoutGUI() throws RemoteException, XMPPException {
        alice.deleteBuddyGUIDone(bob);
        assertFalse(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertFalse(bob.sarosBuddiesV.hasBuddy(alice.jid));
        alice.addBuddyDone(bob);
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertTrue(bob.sarosBuddiesV.hasBuddy(alice.jid));
    }

    /**
     * Steps:
     * <ol>
     * <li>alice delete bob</li>
     * </ol>
     * 
     * Result:
     * <ol>
     * <li>alice and bob don't contact each other</li>
     * </ol>
     * 
     * @throws RemoteException
     */
    @Test
    public void deleteBuddyWithGUI() throws RemoteException {
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        alice.deleteBuddyGUIDone(bob);
        assertFalse(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertFalse(bob.sarosBuddiesV.hasBuddy(alice.jid));
    }

    @Test
    public void deleteBuddyWithoutGUI() throws RemoteException, XMPPException {
        assertTrue(alice.sarosBuddiesV.hasBuddy(bob.jid));
        alice.deleteBuddyDone(bob);
        assertFalse(alice.sarosBuddiesV.hasBuddy(bob.jid));
        assertFalse(bob.sarosBuddiesV.hasBuddy(alice.jid));
    }

    /**
     * Steps:
     * <ol>
     * <li>alice invite bob</li>
     * </ol>
     * 
     * Result:
     * <ol>
     * <li>bob is in the session</li>
     * </ol>
     * 
     * @throws RemoteException
     * @throws InterruptedException
     */
    @Test
    public void inviteBuddyWithGUI() throws RemoteException,
        InterruptedException {
        setUpSessionByDefault(alice, bob);
        assertFalse(carl.sarosSessionV.isInSessionGUI());
        alice.sarosBuddiesV.inviteBuddyGUI(carl.jid);
        carl.sarosC.confirmShellSessionnInvitation();
        carl.sarosC.confirmShellAddProjectWithNewProject(PROJECT1);
        carl.sarosSessionV.waitUntilIsInSession();
        assertTrue(carl.sarosSessionV.isInSessionGUI());
        alice.leaveSessionHostFirstDone(bob, carl);
    }

}
