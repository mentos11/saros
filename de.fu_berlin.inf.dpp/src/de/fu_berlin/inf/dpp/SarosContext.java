package de.fu_berlin.inf.dpp;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.helpers.LogLog;
import org.picocontainer.Characteristics;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.Parameter;
import org.picocontainer.PicoBuilder;
import org.picocontainer.PicoCompositionException;
import org.picocontainer.PicoContainer;
import org.picocontainer.injectors.AnnotatedFieldInjection;
import org.picocontainer.injectors.CompositeInjection;
import org.picocontainer.injectors.ConstructorInjection;
import org.picocontainer.injectors.ProviderAdapter;
import org.picocontainer.injectors.Reinjector;

import de.fu_berlin.inf.dpp.accountManagement.XMPPAccountStore;
import de.fu_berlin.inf.dpp.communication.SkypeManager;
import de.fu_berlin.inf.dpp.communication.audio.AudioService;
import de.fu_berlin.inf.dpp.communication.audio.AudioServiceManager;
import de.fu_berlin.inf.dpp.communication.audio.MixerManager;
import de.fu_berlin.inf.dpp.communication.muc.MUCManager;
import de.fu_berlin.inf.dpp.communication.muc.negotiation.MUCSessionPreferencesNegotiatingManager;
import de.fu_berlin.inf.dpp.communication.muc.singleton.MUCManagerSingletonWrapperChatView;
import de.fu_berlin.inf.dpp.concurrent.undo.UndoManager;
import de.fu_berlin.inf.dpp.concurrent.watchdog.ConsistencyWatchdogClient;
import de.fu_berlin.inf.dpp.concurrent.watchdog.ConsistencyWatchdogServer;
import de.fu_berlin.inf.dpp.concurrent.watchdog.IsInconsistentObservable;
import de.fu_berlin.inf.dpp.concurrent.watchdog.SessionViewOpener;
import de.fu_berlin.inf.dpp.editor.EditorManager;
import de.fu_berlin.inf.dpp.editor.internal.EditorAPI;
import de.fu_berlin.inf.dpp.feedback.DataTransferCollector;
import de.fu_berlin.inf.dpp.feedback.ErrorLogManager;
import de.fu_berlin.inf.dpp.feedback.FeedbackManager;
import de.fu_berlin.inf.dpp.feedback.FollowModeCollector;
import de.fu_berlin.inf.dpp.feedback.JumpFeatureUsageCollector;
import de.fu_berlin.inf.dpp.feedback.ParticipantCollector;
import de.fu_berlin.inf.dpp.feedback.PermissionChangeCollector;
import de.fu_berlin.inf.dpp.feedback.SelectionCollector;
import de.fu_berlin.inf.dpp.feedback.SessionDataCollector;
import de.fu_berlin.inf.dpp.feedback.StatisticManager;
import de.fu_berlin.inf.dpp.feedback.TextEditCollector;
import de.fu_berlin.inf.dpp.feedback.VoIPCollector;
import de.fu_berlin.inf.dpp.invitation.ArchiveStreamService;
import de.fu_berlin.inf.dpp.net.IncomingTransferObject;
import de.fu_berlin.inf.dpp.net.RosterTracker;
import de.fu_berlin.inf.dpp.net.SarosNet;
import de.fu_berlin.inf.dpp.net.StunHelper;
import de.fu_berlin.inf.dpp.net.UPnP.UPnPManager;
import de.fu_berlin.inf.dpp.net.business.ActivitiesHandler;
import de.fu_berlin.inf.dpp.net.business.CancelInviteHandler;
import de.fu_berlin.inf.dpp.net.business.CancelProjectSharingHandler;
import de.fu_berlin.inf.dpp.net.business.ConsistencyWatchdogHandler;
import de.fu_berlin.inf.dpp.net.business.DispatchThreadContext;
import de.fu_berlin.inf.dpp.net.business.InvitationHandler;
import de.fu_berlin.inf.dpp.net.business.LeaveHandler;
import de.fu_berlin.inf.dpp.net.business.RequestForActivityHandler;
import de.fu_berlin.inf.dpp.net.business.UserListHandler;
import de.fu_berlin.inf.dpp.net.internal.ActivitiesExtensionProvider;
import de.fu_berlin.inf.dpp.net.internal.ConnectionTestManager;
import de.fu_berlin.inf.dpp.net.internal.DataTransferManager;
import de.fu_berlin.inf.dpp.net.internal.DefaultInvitationInfo;
import de.fu_berlin.inf.dpp.net.internal.IBBTransport;
import de.fu_berlin.inf.dpp.net.internal.InvitationInfo;
import de.fu_berlin.inf.dpp.net.internal.Socks5Transport;
import de.fu_berlin.inf.dpp.net.internal.StreamServiceManager;
import de.fu_berlin.inf.dpp.net.internal.UserListInfo;
import de.fu_berlin.inf.dpp.net.internal.XMPPReceiver;
import de.fu_berlin.inf.dpp.net.internal.XMPPTransmitter;
import de.fu_berlin.inf.dpp.net.internal.discoveryManager.DiscoveryManager;
import de.fu_berlin.inf.dpp.net.internal.extensions.CancelInviteExtension;
import de.fu_berlin.inf.dpp.net.internal.extensions.CancelProjectSharingExtension;
import de.fu_berlin.inf.dpp.net.internal.extensions.LeaveExtension;
import de.fu_berlin.inf.dpp.net.internal.extensions.RequestActivityExtension;
import de.fu_berlin.inf.dpp.net.internal.extensions.UserListExtension;
import de.fu_berlin.inf.dpp.net.internal.subscriptionManager.SubscriptionManager;
import de.fu_berlin.inf.dpp.observables.FileReplacementInProgressObservable;
import de.fu_berlin.inf.dpp.observables.InvitationProcessObservable;
import de.fu_berlin.inf.dpp.observables.JingleFileTransferManagerObservable;
import de.fu_berlin.inf.dpp.observables.ProjectNegotiationObservable;
import de.fu_berlin.inf.dpp.observables.SarosSessionObservable;
import de.fu_berlin.inf.dpp.observables.SessionIDObservable;
import de.fu_berlin.inf.dpp.observables.VideoSessionObservable;
import de.fu_berlin.inf.dpp.observables.VoIPSessionObservable;
import de.fu_berlin.inf.dpp.optional.jdt.JDTFacade;
import de.fu_berlin.inf.dpp.preferences.PreferenceManager;
import de.fu_berlin.inf.dpp.preferences.PreferenceUtils;
import de.fu_berlin.inf.dpp.project.PingPongCentral;
import de.fu_berlin.inf.dpp.project.SarosRosterListener;
import de.fu_berlin.inf.dpp.project.SarosSessionManager;
import de.fu_berlin.inf.dpp.project.SharedResourcesManager;
import de.fu_berlin.inf.dpp.project.internal.ChangeColorManager;
import de.fu_berlin.inf.dpp.project.internal.PermissionManager;
import de.fu_berlin.inf.dpp.project.internal.ProjectsAddedManager;
import de.fu_berlin.inf.dpp.synchronize.StopManager;
import de.fu_berlin.inf.dpp.ui.LocalPresenceTracker;
import de.fu_berlin.inf.dpp.ui.RemoteProgressManager;
import de.fu_berlin.inf.dpp.ui.SarosUI;
import de.fu_berlin.inf.dpp.ui.actions.SendFileAction;
import de.fu_berlin.inf.dpp.util.EclipseHelper;
import de.fu_berlin.inf.dpp.util.EclipseHelperTestSaros;
import de.fu_berlin.inf.dpp.util.StackTrace;
import de.fu_berlin.inf.dpp.util.VersionManager;
import de.fu_berlin.inf.dpp.util.pico.ChildContainer;
import de.fu_berlin.inf.dpp.util.pico.ChildContainerProvider;
import de.fu_berlin.inf.dpp.util.pico.DotGraphMonitor;
import de.fu_berlin.inf.dpp.videosharing.VideoSharing;
import de.fu_berlin.inf.dpp.videosharing.VideoSharingService;

/**
 * Encapsulates a {@link org.picocontainer.PicoContainer} and its saros-specific
 * initializiation. Basicly it's used to get or reinject components in the
 * context:
 * 
 * {@link de.fu_berlin.inf.dpp.SarosContext#getComponent(Class)},
 * {@link de.fu_berlin.inf.dpp.SarosContext#reinject(Object)}
 * 
 * These methods change the context respectively the PicoContainer!
 * 
 * If you want to initialize a component with the components of the context
 * without changing the context you can use the method
 * {@link de.fu_berlin.inf.dpp.SarosContext#initComponent(Object)}.
 * 
 * @author philipp.cordes
 */
public class SarosContext {

    protected DotGraphMonitor dotMonitor;

    /**
     * A caching container which holds all the singletons in Saros.
     */
    private MutablePicoContainer container;

    /**
     * The reinjector used to inject dependencies into those objects that are
     * created by Eclipse and not by our PicoContainer.
     */
    private Reinjector reinjector;

    /**
     * Because many components which are included in the pico-container need
     * saros
     */
    private Saros saros;

    private boolean isTestContext = false;

    private static final Class<?>[] picoContainerComponents = new Class<?>[] {
        // Thread Context
        DispatchThreadContext.class,

        // Core Managers
        ChangeColorManager.class,
        ConsistencyWatchdogClient.class,
        ConsistencyWatchdogServer.class,
        EditorAPI.class,
        EditorManager.class,
        ErrorLogManager.class,
        FeedbackManager.class,
        JDTFacade.class,
        LocalPresenceTracker.class,
        MUCManager.class,
        MUCManagerSingletonWrapperChatView.class,
        PreferenceManager.class,
        PreferenceUtils.class,
        PermissionManager.class,
        SarosUI.class,
        SarosSessionManager.class,
        SessionViewOpener.class,
        SharedResourcesManager.class,
        StatisticManager.class,
        StopManager.class,
        AudioServiceManager.class,
        MixerManager.class,
        UndoManager.class,
        VideoSharing.class,
        VersionManager.class,
        MUCSessionPreferencesNegotiatingManager.class,
        RemoteProgressManager.class,
        XMPPAccountStore.class,
        ProjectsAddedManager.class,
        EclipseHelper.class,

        // Network
        ConnectionTestManager.class,
        DataTransferManager.class,
        DiscoveryManager.class,
        IBBTransport.class,
        PingPongCentral.class,
        RosterTracker.class,
        SarosNet.class,
        SarosRosterListener.class,
        SkypeManager.class,
        Socks5Transport.class,
        StreamServiceManager.class,
        StunHelper.class,
        SubscriptionManager.class,
        UPnPManager.class,
        XMPPReceiver.class,
        XMPPTransmitter.class,

        // Observables
        FileReplacementInProgressObservable.class,
        InvitationProcessObservable.class,
        ProjectNegotiationObservable.class,
        IsInconsistentObservable.class,
        JingleFileTransferManagerObservable.class,
        SessionIDObservable.class,
        SarosSessionObservable.class,
        VoIPSessionObservable.class,
        VideoSessionObservable.class,

        // Handlers
        CancelInviteHandler.class,
        CancelProjectSharingHandler.class,
        UserListHandler.class,
        InvitationHandler.class,
        LeaveHandler.class,
        RequestForActivityHandler.class,
        ConsistencyWatchdogHandler.class,
        ActivitiesHandler.class,

        // Extensions
        CancelInviteExtension.class,
        CancelProjectSharingExtension.class,
        UserListExtension.class,
        RequestActivityExtension.class,
        LeaveExtension.class,

        // Extension Providers
        ActivitiesExtensionProvider.class,
        InvitationInfo.InvitationExtensionProvider.class,
        IncomingTransferObject.IncomingTransferObjectExtensionProvider.class,
        DefaultInvitationInfo.InvitationAcknowledgementExtensionProvider.class,
        DefaultInvitationInfo.FileListRequestExtensionProvider.class,
        UserListInfo.JoinExtensionProvider.class,
        DefaultInvitationInfo.UserListConfirmationExtensionProvider.class,
        DefaultInvitationInfo.InvitationCompleteExtensionProvider.class,

        // Statistic collectors
        DataTransferCollector.class, PermissionChangeCollector.class,
        ParticipantCollector.class, SessionDataCollector.class,
        TextEditCollector.class, JumpFeatureUsageCollector.class,
        FollowModeCollector.class, SelectionCollector.class,
        VoIPCollector.class,

        // streaming services
        SendFileAction.SendFileStreamService.class, AudioService.class,
        VideoSharingService.class, ArchiveStreamService.class

    };

    private static final List<Class<?>> excludedComponentsForTestContext = new ArrayList<Class<?>>(
        asList(new Class<?>[] { de.fu_berlin.inf.dpp.util.EclipseHelper.class }));

    private static final Map<Class<?>, Class<?>> testImplementations = new HashMap<Class<?>, Class<?>>();

    static {
        testImplementations.put(de.fu_berlin.inf.dpp.util.EclipseHelper.class,
            EclipseHelperTestSaros.class);
    }

    private SarosContext() {
        /*
         * Use the SarosContextBuilder to build a SarosContext. {@link
         * SarosContextBuilder}
         */
    }

    private void init(List<Class<?>> excludedComponentsForTestContext) {
        PicoBuilder picoBuilder = new PicoBuilder(new CompositeInjection(
            new ConstructorInjection(), new AnnotatedFieldInjection()))
            .withCaching().withLifecycle();

        /*
         * If given, the dotMonitor is used to capture an architecture diagram
         * of the application
         */
        if (dotMonitor != null) {
            picoBuilder = picoBuilder.withMonitor(dotMonitor);
        }

        // Initialize our dependency injection container
        this.container = picoBuilder.build();

        // Add Adapter which creates ChildContainers
        this.container.as(Characteristics.NO_CACHE).addAdapter(
            new ProviderAdapter(new ChildContainerProvider(this.container)));
        /*
         * All singletons which exist for the whole plug-in life-cycle are
         * managed by PicoContainer for us.
         * 
         * The addComponent() calls are sorted alphabetically according to the
         * first argument. This makes it easier to search for a class without
         * tool support.
         */
        this.container.addComponent(Saros.class, this.saros);

        /*
         * Add components.
         */
        for (Class<?> component : picoContainerComponents) {
            if (!excludedComponentsForTestContext.contains(component)) {
                this.container.addComponent(component);
            }
        }

        // add this context itself because some components need it ...
        this.container.addComponent(SarosContext.class, this);

        /*
         * The following classes are initialized by the re-injector because they
         * are created by Eclipse:
         * 
         * All User interface classes like all Views, but also
         * SharedDocumentProvider.
         * 
         * CAUTION: Classes from which duplicates can exists, should not be
         * managed by PicoContainer.
         */
        reinjector = new Reinjector(this.container);
    }

    /**
     * Adds the object to Saros' container, and injects dependencies into the
     * annotated fields of the given object. It should only be used for objects
     * that were created by Eclipse, which have the same life cycle as the Saros
     * plug-in, e.g. the popup menu actions.
     */
    public synchronized void reinject(Object toInjectInto) {
        try {
            // Remove the component if an instance of it was already registered
            Class<? extends Object> clazz = toInjectInto.getClass();
            ComponentAdapter<Object> removed = this.container
                .removeComponent(clazz);
            if (removed != null && clazz != Saros.class) {
                LogLog.warn(clazz.toString() + " added more than once!",
                    new StackTrace());
            }

            // Add the given instance to the container
            this.container.addComponent(clazz, toInjectInto);

            /*
             * Ask PicoContainer to inject into the component via fields
             * annotated with @Inject
             */
            this.reinjector.reinject(clazz, new AnnotatedFieldInjection());
        } catch (PicoCompositionException e) {
            LogLog.error("Internal error in reinjection:", e);
        }
    }

    /**
     * Injects dependencies into the annotated fields of the given object. This
     * method should be used for objects that were created by Eclipse, which
     * have a different life cycle than the Saros plug-in.
     */
    public synchronized void initComponent(Object toInjectInto) {
        ChildContainer dummyContainer = this.container
            .getComponent(ChildContainer.class);
        dummyContainer.reinject(toInjectInto);
        this.container.removeChildContainer(dummyContainer);
    }

    public <T> T getComponent(Class<T> tClass) {
        return container.getComponent(tClass);
    }

    public <T> List<T> getComponents(Class<T> tClass) {
        return container.getComponents(tClass);
    }

    public void addComponent(Object o, Object o1, Parameter... parameters) {
        container.addComponent(o, o1, parameters);
    }

    public void removeComponent(Object o) {
        container.removeComponent(o);
    }

    public boolean removeChildContainer(PicoContainer picoContainer) {
        return container.removeChildContainer(picoContainer);
    }

    public void dispose() {
        container.dispose();
    }

    public boolean isTestContext() {
        return isTestContext;
    }

    /**
     * Starting point for getting a correct initialized SarosContext.
     */
    public static SarosContextBuilder getContextForSaros(Saros saros) {
        return new SarosContextBuilder(saros);
    }

    /**
     * Builder to create a correct initialized SarosContext.
     */
    public static class SarosContextBuilder {
        private Saros saros;
        private DotGraphMonitor dotMonitor;
        private boolean isTestContext = false;

        public SarosContextBuilder(Saros saros) {
            this.saros = saros;
        }

        public SarosContextBuilder withDotMonitor(DotGraphMonitor dotMonitor) {
            this.dotMonitor = dotMonitor;
            return this;
        }

        public SarosContextBuilder isTestContext() {
            this.isTestContext = true;
            return this;
        }

        public SarosContext build() {
            SarosContext result = new SarosContext();
            result.saros = this.saros;
            result.dotMonitor = this.dotMonitor;
            result.isTestContext = this.isTestContext;

            if (result.isTestContext()) {
                result.init(excludedComponentsForTestContext);
                addTestImplemantations(result);
            } else {
                result.init(new ArrayList<Class<?>>());
            }

            return result;
        }

        private void addTestImplemantations(SarosContext result) {
            for (Map.Entry<Class<?>, Class<?>> entry : testImplementations
                .entrySet()) {
                result.container.addComponent(entry.getKey(), entry.getValue());
            }
        }
    }

}
