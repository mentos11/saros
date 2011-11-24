package de.fu_berlin.inf.dpp.ui.wizards.pages;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.SarosPluginContext;
import de.fu_berlin.inf.dpp.accountManagement.XMPPAccount;
import de.fu_berlin.inf.dpp.accountManagement.XMPPAccountStore;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.ui.Messages;
import de.fu_berlin.inf.dpp.ui.widgets.wizard.EnterXMPPAccountComposite;
import de.fu_berlin.inf.dpp.ui.widgets.wizard.events.EnterXMPPAccountCompositeListener;
import de.fu_berlin.inf.dpp.ui.widgets.wizard.events.IsSarosXMPPServerChangedEvent;
import de.fu_berlin.inf.dpp.ui.widgets.wizard.events.XMPPServerChangedEvent;

/**
 * Allows the user to edit a given {@link XMPPAccount}.
 * 
 * @author bkahlert
 */
public class EditXMPPAccountWizardPage extends WizardPage {
    public static final String TITLE = Messages.EditXMPPAccountWizardPage_title;
    public static final String DESCRIPTION = Messages.EditXMPPAccountWizardPage_description;

    @Inject
    protected Saros saros;

    @Inject
    protected XMPPAccountStore accountStore;

    protected EnterXMPPAccountComposite enterXMPPAccountComposite;

    /**
     * This flag is true if {@link JID} was already valid.
     */
    protected boolean wasJIDValid = false;

    /**
     * This flag is true if the password was already valid.
     */
    protected boolean wasPasswordValid = false;

    /**
     * This flag is true if the server was already valid.
     */
    protected boolean wasXMPPServerValid = false;
    protected boolean isXMPPServerValid = false;

    protected final JID initialJID;
    protected final String initialPassword;
    protected final String initialServer;

    /**
     * This flag is true if Saros's Jabber server restriction should be
     * displayed.
     */
    protected boolean showSarosXMPPRestriction = false;

    public EditXMPPAccountWizardPage(JID initialJID, String initialPassword,
        String initialServer) {
        super(EditXMPPAccountWizardPage.class.getName());

        SarosPluginContext.initComponent(this);
        setTitle(TITLE);
        setDescription(DESCRIPTION);

        this.initialJID = initialJID;
        this.initialPassword = initialPassword;
        this.initialServer = initialServer;
    }

    public void createControl(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        setControl(composite);

        composite.setLayout(new GridLayout(1, false));

        this.enterXMPPAccountComposite = new EnterXMPPAccountComposite(
            composite, SWT.NONE);
        this.enterXMPPAccountComposite.setLayoutData(new GridData(SWT.FILL,
            SWT.FILL, true, true));

        this.isXMPPServerValid = this.enterXMPPAccountComposite
            .isXMPPServerValid();

        setInitialValues();
        hookListeners();
        updatePageCompletion();
    }

    protected void setInitialValues() {
        this.enterXMPPAccountComposite.setJID(this.initialJID);
        this.enterXMPPAccountComposite.setPassword(this.initialPassword);
        this.enterXMPPAccountComposite.setServer(this.initialServer);
    }

    protected void hookListeners() {
        this.enterXMPPAccountComposite.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                updatePageCompletion();
            }
        });

        this.enterXMPPAccountComposite
            .addEnterXMPPAccountCompositeListener(new EnterXMPPAccountCompositeListener() {
                public void xmppServerValidityChanged(
                    XMPPServerChangedEvent event) {
                    isXMPPServerValid = event.isXMPPServerValid();
                    updatePageCompletion();
                }

                public void isSarosXMPPServerChanged(
                    IsSarosXMPPServerChangedEvent event) {
                    showSarosXMPPRestriction = event.isSarosXMPPServer();
                    updatePageCompletion();
                }
            });
    }

    protected void updatePageCompletion() {

        boolean isJIDValid = this.getJID().isValid();
        boolean isPasswordNotEmpty = !this.getPassword().isEmpty();
        boolean accountExists = accountStore.exists(getJID());

        // allow password modification
        if (accountExists && initialJID.equals(getJID())
            && initialServer.equals(getServer()))
            accountExists = false;

        if (isJIDValid)
            wasJIDValid = true;

        if (isPasswordNotEmpty)
            wasPasswordValid = true;

        if (getServer().isEmpty() || isXMPPServerValid) {
            wasXMPPServerValid = true;
        }

        if (showSarosXMPPRestriction)
            setMessage(Messages.xmpp_saros_restriction_short, WARNING);
        else
            setMessage(null);

        setPageComplete(false);
        String errorMessage = null;

        if (isJIDValid && isPasswordNotEmpty && isXMPPServerValid
            && !accountExists) {
            /*
             * TODO Connect and login attempt to new server Not done because
             * Saros.connect holds the whole connection code. Few reusable code.
             * 2011/02/19 bkahlert
             */
            setPageComplete(true);
        } else if (!isJIDValid && wasJIDValid) {
            errorMessage = Messages.jid_format_errorMessage;
        } else if (accountExists) {
            errorMessage = Messages.account_exists_errorMessage;
        } else if (!isPasswordNotEmpty && wasPasswordValid) {
            errorMessage = Messages.password_empty_errorMessage;
        } else if (!isXMPPServerValid && wasXMPPServerValid) {
            errorMessage = Messages.server_unresolvable_errorMessage;
        }

        updateErrorMessage(errorMessage);
    }

    private String lastErrorMessage = null;

    private void updateErrorMessage(String message) {
        if (lastErrorMessage != null && lastErrorMessage.equals(message))
            return;

        lastErrorMessage = message;

        setErrorMessage(message);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible)
            return;

        this.enterXMPPAccountComposite.setFocus();
    }

    /*
     * WizardPage Results
     */

    /**
     * Returns the entered {@link XMPPAccount}.
     * 
     * @return
     */
    public JID getJID() {
        return this.enterXMPPAccountComposite.getJID();
    }

    /**
     * Returns the entered password.
     * 
     * @return
     */
    public String getPassword() {
        return this.enterXMPPAccountComposite.getPassword();
    }

    /**
     * Returns the entered server.
     * 
     * @return empty string if no server has been provided
     */
    public String getServer() {
        return this.enterXMPPAccountComposite.getServer();
    }
}