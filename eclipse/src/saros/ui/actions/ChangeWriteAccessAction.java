package saros.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import saros.SarosPluginContext;
import saros.repackaged.picocontainer.annotations.Inject;
import saros.session.ISarosSession;
import saros.session.ISarosSessionManager;
import saros.session.ISessionLifecycleListener;
import saros.session.ISessionListener;
import saros.session.SessionEndReason;
import saros.session.User;
import saros.session.User.Permission;
import saros.ui.ImageManager;
import saros.ui.Messages;
import saros.ui.util.SWTUtils;
import saros.ui.util.selection.SelectionUtils;
import saros.ui.util.selection.retriever.SelectionRetrieverFactory;
import saros.util.ThreadUtils;

/**
 * Change the write access of a session participant (granting write access, restricting to
 * read-only).
 */
public class ChangeWriteAccessAction extends Action implements Disposable {

  private static final Logger log = Logger.getLogger(ChangeWriteAccessAction.class);

  public static final class WriteAccess {
    public static final String ACTION_ID =
        ChangeWriteAccessAction.class.getName() + "." + WriteAccess.class.getSimpleName();

    public static ChangeWriteAccessAction newInstance() {
      return new ChangeWriteAccessAction(
          ACTION_ID,
          Permission.WRITE_ACCESS,
          Messages.GiveWriteAccessAction_title,
          Messages.GiveWriteAccessAction_tooltip,
          ImageManager.ICON_CONTACT_SAROS_SUPPORT);
    }
  }

  public static final class ReadOnly {
    public static final String ACTION_ID =
        ChangeWriteAccessAction.class.getName() + "." + ReadOnly.class.getSimpleName();

    public static ChangeWriteAccessAction newInstance() {
      return new ChangeWriteAccessAction(
          ACTION_ID,
          Permission.READONLY_ACCESS,
          Messages.RestrictToReadOnlyAccessAction_title,
          Messages.RestrictToReadOnlyAccessAction_tooltip,
          ImageManager.ICON_USER_SAROS_READONLY);
    }
  }

  private Permission permission;

  @Inject private ISarosSessionManager sessionManager;

  private ISessionLifecycleListener sessionLifecycleListener =
      new ISessionLifecycleListener() {
        @Override
        public void sessionStarted(ISarosSession newSarosSession) {
          newSarosSession.addListener(sessionListener);
          updateEnablement();
        }

        @Override
        public void sessionEnded(ISarosSession oldSarosSession, SessionEndReason reason) {
          oldSarosSession.removeListener(sessionListener);
        }
      };

  private ISessionListener sessionListener =
      new ISessionListener() {
        @Override
        public void permissionChanged(User user) {
          updateEnablement();
        }
      };

  private ISelectionListener selectionListener =
      new ISelectionListener() {
        @Override
        public void selectionChanged(IWorkbenchPart part, ISelection selection) {
          updateEnablement();
        }
      };

  private ChangeWriteAccessAction(
      final String id,
      final Permission permission,
      final String text,
      final String tooltip,
      final Image icon) {

    super(text);

    SarosPluginContext.initComponent(this);

    setId(id);

    setImageDescriptor(
        new ImageDescriptor() {
          @Override
          public ImageData getImageData() {
            return icon.getImageData();
          }
        });

    setToolTipText(tooltip);

    this.permission = permission;

    /*
     * if SessionView is not "visible" on session start up this constructor
     * will be called after session started (and the user uses this view)
     * That's why the method sessionListener.sessionStarted has to be called
     * manually. If the permissionListener is not added to the session and
     * the action enablement cannot be updated.
     */
    if (sessionManager.getSession() != null) {
      sessionLifecycleListener.sessionStarted(sessionManager.getSession());
    }

    sessionManager.addSessionLifecycleListener(sessionLifecycleListener);
    SelectionUtils.getSelectionService().addSelectionListener(selectionListener);
    updateEnablement();
  }

  private void updateEnablement() {
    List<User> participants =
        SelectionRetrieverFactory.getSelectionRetriever(User.class).getSelection();

    boolean sessionRunning = (sessionManager.getSession() != null);
    boolean selectedOneWithOppositePermission =
        (participants.size() == 1 && participants.get(0).getPermission() != permission);

    setEnabled(sessionRunning && selectedOneWithOppositePermission);
  }

  @Override
  public void dispose() {
    SelectionUtils.getSelectionService().removeSelectionListener(selectionListener);
    sessionManager.removeSessionLifecycleListener(sessionLifecycleListener);
  }

  @Override
  public void run() {
    ThreadUtils.runSafeSync(
        log,
        new Runnable() {
          @Override
          public void run() {
            ISarosSession session = sessionManager.getSession();

            if (session == null) return;

            List<User> participants =
                SelectionRetrieverFactory.getSelectionRetriever(User.class).getSelection();
            if (participants.size() == 1) {
              User selected = participants.get(0);
              if (selected.getPermission() != permission) {
                performPermissionChange(session, selected, permission);
                updateEnablement();
              } else {
                log.warn(
                    "Did not change write access of " + selected + ", because it's already set.");
              }
            } else {
              log.warn("More than one participant selected."); // $NON-NLS-1$
            }
          }
        });
  }

  // SWT
  private void performPermissionChange(
      final ISarosSession session, final User user, final Permission newPermission) {

    ProgressMonitorDialog dialog = new ProgressMonitorDialog(SWTUtils.getShell());

    try {
      dialog.run(
          true,
          false,
          new IRunnableWithProgress() {
            @Override
            public void run(final IProgressMonitor monitor) {

              try {

                monitor.beginTask(Messages.SarosUI_permission_change, IProgressMonitor.UNKNOWN);

                session.changePermission(user, newPermission);
                /*
                 * FIXME run this at least 2 times and if this still
                 * does not succeed kick the user
                 */
                // } catch (CancellationException e) {
              } catch (InterruptedException e) {
                log.error(e); // cannot happen
              } finally {
                monitor.done();
              }
            }
          });
    } catch (InvocationTargetException e) {
      Throwable t = e.getCause();

      if (t instanceof CancellationException) {
        log.warn("permission change failed, user " + user + " did not respond"); // $NON-NLS-1$
        MessageDialog.openWarning(
            SWTUtils.getShell(),
            Messages.SarosUI_permission_canceled,
            Messages.SarosUI_permission_canceled_text);
      } else {
        log.error("permission change failed", e); // $NON-NLS-1$
        MessageDialog.openError(
            SWTUtils.getShell(),
            Messages.SarosUI_permission_failed,
            Messages.SarosUI_permission_failed_text);
      }
    } catch (InterruptedException e) {
      log.error(e); // cannot happen
    }
  }
}
