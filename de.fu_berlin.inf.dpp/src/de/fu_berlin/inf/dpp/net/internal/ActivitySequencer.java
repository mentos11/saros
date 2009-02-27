/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universitaet Berlin - Fachbereich Mathematik und Informatik - 2006
 * (c) Riad Djemili - 2006
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 1, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package de.fu_berlin.inf.dpp.net.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;

import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.activities.EditorActivity;
import de.fu_berlin.inf.dpp.activities.FileActivity;
import de.fu_berlin.inf.dpp.activities.FolderActivity;
import de.fu_berlin.inf.dpp.activities.IActivity;
import de.fu_berlin.inf.dpp.activities.TextEditActivity;
import de.fu_berlin.inf.dpp.activities.TextSelectionActivity;
import de.fu_berlin.inf.dpp.activities.ViewportActivity;
import de.fu_berlin.inf.dpp.activities.EditorActivity.Type;
import de.fu_berlin.inf.dpp.concurrent.jupiter.Request;
import de.fu_berlin.inf.dpp.concurrent.jupiter.RequestForwarder;
import de.fu_berlin.inf.dpp.concurrent.jupiter.internal.RequestError;
import de.fu_berlin.inf.dpp.concurrent.management.ConcurrentDocumentManager;
import de.fu_berlin.inf.dpp.concurrent.management.ConcurrentDocumentManager.Side;
import de.fu_berlin.inf.dpp.net.IActivitySequencer;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.TimedActivity;
import de.fu_berlin.inf.dpp.project.IActivityManager;
import de.fu_berlin.inf.dpp.project.IActivityProvider;
import de.fu_berlin.inf.dpp.project.ISharedProject;
import de.fu_berlin.inf.dpp.util.Util;

/**
 * Implements {@link IActivitySequencer} and {@link IActivityManager}.
 * 
 * @author rdjemili
 */
public class ActivitySequencer implements RequestForwarder, IActivitySequencer {

    private static Logger logger = Logger.getLogger(ActivitySequencer.class
        .getName());

    public static class ExecuterQueue {

        /** Queue with IActivity Elements */
        private final List<TextEditActivity> executerQueue;

        private TextEditActivity currentExecutedActivity;

        private boolean executed = true;

        public ExecuterQueue() {
            this.executerQueue = new Vector<TextEditActivity>();
        }

        /**
         * check status of created activity. After execution in
         * ActivitySequencer activity has created new call of activityCreated.
         * 
         * @param activity
         */
        public synchronized boolean checkCreatedActivity(IActivity activity) {
            if (this.currentExecutedActivity != null) {
                if ((activity instanceof TextEditActivity)
                    && this.currentExecutedActivity.sameLike(activity)) {
                    logger.debug("TextEditActivity " + activity
                        + " is executed.");
                    this.executed = true;
                    notify();
                }
            }
            return this.executed;
        }

        public synchronized void addActivity(IActivity activity) {
            if (activity instanceof TextEditActivity) {
                logger.debug("Add new Activity " + activity
                    + " to executer queue.");
                this.executerQueue.add((TextEditActivity) activity);
                notify();
            }
        }

        public synchronized IActivity getNextActivity() {
            try {
                while ((this.executerQueue.size() < 1) && !this.executed) {
                    wait();
                }
                this.currentExecutedActivity = this.executerQueue.remove(0);
                this.executed = false;
                logger.debug("Remove " + this.currentExecutedActivity
                    + " form executer queue.");
                /* get next activity in queue. */
                return this.currentExecutedActivity;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }

        }
    }

    public static final int UNDEFINED_TIME = -1;

    private final List<IActivity> activities = new LinkedList<IActivity>();

    // TODO never used!
    private final List<IActivity> flushedLog = new LinkedList<IActivity>();

    private final List<IActivityProvider> providers = new LinkedList<IActivityProvider>();

    private final List<TimedActivity> queue = new CopyOnWriteArrayList<TimedActivity>();

    private final List<TimedActivity> activityHistory = new LinkedList<TimedActivity>();

    private int timestamp = ActivitySequencer.UNDEFINED_TIME;

    private ConcurrentDocumentManager concurrentManager;

    /** outgoing queue for direct client sync messages for all driver. */
    private final List<Request> outgoingSyncActivities = new Vector<Request>();

    private final ExecuterQueue executer;

    public ActivitySequencer() {
        this.executer = new ExecuterQueue();
    }

    /**
     * TODO Refactor like this:
     * 
     * <code>
     * concurrentManager.exec(activity); 
     * editorManager.exec(activity);
     * roleManager.exec(activity); 
     * sharedResourceManager.exec(activity);
     * </code>
     * 
     * Is easier to read and debug :-) But watch out for interdependencies
     * between these.
     */
    public void exec(final IActivity activity) {

        try {
            if (activity instanceof EditorActivity) {
                this.concurrentManager.exec(activity);
            }
            if (activity instanceof FileActivity) {
                this.concurrentManager.exec(activity);
            }
            if (activity instanceof FolderActivity) {
                // TODO
            }
        } catch (Exception e) {
            logger.error("Error while executing activity.", e);
        }

        Util.runSafeSWTSync(logger, new Runnable() {
            public void run() {
                if (activity instanceof TextEditActivity) {

                    /*
                     * check if document is already managed by jupiter
                     * mechanism.
                     */
                    if (!concurrentManager.isHostSide()
                        && (concurrentManager.exec(activity) != null)) {
                        // CLIENT SIDE
                        logger
                            .debug("Execute received activity (without jupiter): "
                                + activity);
                        for (IActivityProvider executor : ActivitySequencer.this.providers) {
                            executor.exec(activity);
                        }
                    }
                } else {

                    // Execute all other activities
                    for (IActivityProvider executor : ActivitySequencer.this.providers) {
                        executor.exec(activity);
                    }

                    // TODO CO Checksums are not used at the moment, aren't
                    // they?
                    // Check for file checksum after incoming save file
                    // activity.
                    if ((activity instanceof EditorActivity)
                        && (((EditorActivity) activity).getType() == EditorActivity.Type.Saved)) {
                        checkSavedFile((EditorActivity) activity);
                    }

                }
            }
        });

    }

    /**
     * this class check the match of local and remote file checksum.
     * 
     * @param editor
     *            incoming editor activity with type saved
     */
    private void checkSavedFile(EditorActivity editor) {
        // /* 1. reset appropriate jupiter document. */
        // if (isHostSide() || this.sharedProject.isDriver()) {
        // ActivitySequencer.logger.debug("reset jupiter server for "
        // + editor.getPath());
        // this.concurrentManager.resetJupiterDocument(editor.getPath());
        // }
        //
        // /* check match of file checksums. */
        //
        // if (!isHostSide() && (editor.getType() == Type.Saved)) {
        // long checksum = FileUtil.checksum(this.sharedProject.getProject()
        // .getFile(editor.getPath()));
        // ActivitySequencer.logger
        // .debug("Checksumme on client side : " + checksum
        // + " for path : " + editor.getPath().toOSString());
        // if (checksum != editor.getChecksum()) {
        // ActivitySequencer.logger.error("Checksum error of file "
        // + editor.getPath());
        // }
        // }
        // if (isHostSide()) {
        // /* create local checksum. */
        // long checksum = FileUtil.checksum(this.sharedProject.getProject()
        // .getFile(editor.getPath()));
        //
        // if (checksum != editor.getChecksum()) {
        // /* send checksum error */
        // ActivitySequencer.logger.error("Checksum error for file "
        // + editor.getPath() + " of " + editor.getSource()
        // + " ( " + checksum + " != " + editor.getChecksum()
        // + " )");
        //
        // /* send checksum error */
        // FileActivity fileError = new FileActivity(
        // FileActivity.Type.Error, editor.getPath(), new JID(
        // editor.getSource()));
        // activityCreated(fileError);
        // /* send sync file. */
        // FileActivity file = new FileActivity(FileActivity.Type.Created,
        // editor.getPath(), new JID(editor.getSource()));
        // activityCreated(file);
        // }
        // }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.net.IActivitySequencer
     */
    public void exec(TimedActivity timedActivity) {
        this.queue.add(timedActivity);
        execQueue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.IActivitySequencer
     */
    public void exec(List<TimedActivity> activities) {
        this.queue.addAll(activities);
        execQueue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.IActivityManager
     */
    public List<IActivity> flush() {
        List<IActivity> out = new ArrayList<IActivity>(this.activities);
        this.activities.clear();
        out = optimizeCO(out);
        this.flushedLog.addAll(out);
        return out.size() > 0 ? out : null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.net.IActivitySequencer
     */
    public List<TimedActivity> flushTimed() {
        List<IActivity> activities = flush();

        if (activities == null) {
            return null;
        }

        if (this.timestamp == ActivitySequencer.UNDEFINED_TIME) {
            this.timestamp = 0;
        }

        List<TimedActivity> timedActivities = new ArrayList<TimedActivity>();
        for (IActivity activity : activities) {
            timedActivities.add(new TimedActivity(activity, this.timestamp++));
        }

        return timedActivities;
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.IActivityManager
     */
    public void addProvider(IActivityProvider provider) {
        this.providers.add(provider);
        provider.addActivityListener(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.IActivityManager
     */
    public void removeProvider(IActivityProvider provider) {
        this.providers.remove(provider);
        provider.removeActivityListener(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.IActivitySequencer
     */
    public List<IActivity> getLog() {
        return this.flushedLog;
    }

    /**
     * All the ActivityProviders will call this method when new events occurred
     * in the UI.
     * 
     * @see de.fu_berlin.inf.dpp.IActivityListener
     */
    public void activityCreated(IActivity activity) {

        if ((activity instanceof EditorActivity)
            || (activity instanceof FileActivity)) {
            /*
             * Host: start and stop jupiter server process depending on editor
             * activities of remote clients. Client: start and stop local
             * jupiter clients depending on editor activities.
             */
            this.concurrentManager.activityCreated(activity);
        }

        if (activity instanceof TextEditActivity) {

            /* check for execute next activity in queue. */
            logger.debug("activity created : " + activity);
            this.executer.checkCreatedActivity(activity);

            /*
             * new text edit activity has been created and has to be
             * synchronized with jupiter logic.
             */
            IActivity resultAC = this.concurrentManager
                .activityCreated(activity);

            /*
             * host activity: put into outgoing queue and send to all if
             * activity is generated by host. otherwise: send request to host.
             */
            if (resultAC != null || this.concurrentManager.isHostSide()) {
                this.activities.add(activity);
            }

        } else {

            this.activities.add(activity);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.net.IActivitySequencer
     */
    public int getTimestamp() {
        return this.timestamp;
    }

    public int getQueuedActivities() {
        return this.queue.size();
    }

    public List<TimedActivity> getActivityHistory() {
        return this.activityHistory;
    }

    /**
     * Executes as much activities as possible from the current queue regarding
     * to their individual time stamps.
     */
    private void execQueue() {
        boolean executed;

        do {
            executed = false;

            for (TimedActivity timedActivity : this.queue) {
                if (this.timestamp == ActivitySequencer.UNDEFINED_TIME) {
                    this.timestamp = timedActivity.getTimestamp();
                }

                if (timedActivity.getTimestamp() <= this.timestamp) {
                    this.queue.remove(timedActivity);

                    this.timestamp++;
                    exec(timedActivity.getActivity());
                    executed = true;
                }
            }

        } while (executed);

    }

    /**
     * This method tries to reduce the number of activities transmitted by
     * removing activities that would overwrite each other and joining
     * activities that can be send as a single activity.
     */
    private List<IActivity> optimizeCO(List<IActivity> toOptimize) {

        List<IActivity> result = new ArrayList<IActivity>(toOptimize.size());

        TextSelectionActivity selection = null;
        LinkedHashMap<IPath, ViewportActivity> viewport = new LinkedHashMap<IPath, ViewportActivity>();

        for (IActivity activity : toOptimize) {

            if (activity instanceof TextEditActivity) {
                TextEditActivity textEdit = (TextEditActivity) activity;
                textEdit = joinTextEdits(result, textEdit);
                result.add(textEdit);
            } else if (activity instanceof TextSelectionActivity) {
                selection = (TextSelectionActivity) activity;
            } else if (activity instanceof ViewportActivity) {
                ViewportActivity viewActivity = (ViewportActivity) activity;
                viewport.remove(viewActivity.getEditor());
                viewport.put(viewActivity.getEditor(), viewActivity);
            } else {
                result.add(activity);
            }
        }

        // only send one selection activity
        if (selection != null)
            result.add(selection);

        // Add only one viewport per editor
        for (Map.Entry<IPath, ViewportActivity> entry : viewport.entrySet()) {
            result.add(entry.getValue());
        }

        return result;
    }

    /**
     * TODO extract this into the activities themselves
     * 
     * TODO CJ: review needed
     * 
     * TODO CO: I replaced this by a simpler optimizeCO
     */
    private List<IActivity> optimize(List<IActivity> activities) {
        List<IActivity> result = new ArrayList<IActivity>(activities.size());

        ITextSelection selection = null;
        String source = null;
        IPath path = null;

        for (IActivity activity : activities) {
            source = null;
            path = null;
            if (activity instanceof TextEditActivity) {
                TextEditActivity textEdit = (TextEditActivity) activity;

                textEdit = joinTextEdits(result, textEdit);

                selection = new TextSelection(textEdit.offset
                    + textEdit.text.length(), 0);
                source = textEdit.getSource();
                path = textEdit.getEditor();
                result.add(textEdit);

            } else if (activity instanceof TextSelectionActivity) {
                TextSelectionActivity textSelection = (TextSelectionActivity) activity;

                selection = new TextSelection(textSelection.getOffset(),
                    textSelection.getLength());
                source = textSelection.getSource();
                path = textSelection.getEditor();

            } else if (activity instanceof ViewportActivity) {
                ViewportActivity viewport = (ViewportActivity) activity;
                path = viewport.getEditor();
                source = viewport.getSource();
                selection = addSelection(result, selection, source, path);
                result.add(activity);
            } else {
                selection = addSelection(result, selection, source, path);
                result.add(activity);
            }

            selection = addSelection(result, selection, source, path);
        }

        return result;
    }

    private TextEditActivity joinTextEdits(List<IActivity> result,
        TextEditActivity textEdit) {
        if (result.size() == 0) {
            return textEdit;
        }

        IActivity lastActivity = result.get(result.size() - 1);
        if (lastActivity instanceof TextEditActivity) {
            TextEditActivity lastTextEdit = (TextEditActivity) lastActivity;

            if (((lastTextEdit.getSource() == null) || lastTextEdit.getSource()
                .equals(textEdit.getSource()))
                && (textEdit.offset == lastTextEdit.offset
                    + lastTextEdit.text.length())) {
                result.remove(lastTextEdit);
                textEdit = new TextEditActivity(lastTextEdit.offset,
                    lastTextEdit.text + textEdit.text,
                    lastTextEdit.replacedText + textEdit.replacedText,
                    lastTextEdit.getEditor(), lastTextEdit.getSource());
            }
        }

        return textEdit;
    }

    private ITextSelection addSelection(List<IActivity> result,
        ITextSelection selection, String source, IPath path) {
        if (selection == null) {
            return null;
        }

        if (result.size() > 0) {
            IActivity lastActivity = result.get(result.size() - 1);
            if (lastActivity instanceof TextEditActivity) {
                TextEditActivity lastTextEdit = (TextEditActivity) lastActivity;

                if ((selection.getOffset() == lastTextEdit.offset
                    + lastTextEdit.text.length())
                    && (selection.getLength() == 0)) {

                    return selection;
                }
            }
        }

        // HACK TODO CJ: review
        if (path != null) {
            TextSelectionActivity newSel = new TextSelectionActivity(selection
                .getOffset(), selection.getLength(), path);
            newSel.setSource(source);
            result.add(newSel);
        }

        return null;
    }

    public void initConcurrentManager(Side side, User host, JID myJID,
        ISharedProject sharedProject) {
        this.concurrentManager = new ConcurrentDocumentManager(side, host,
            myJID, sharedProject);
        sharedProject.addListener(this.concurrentManager);
        this.concurrentManager.setRequestForwarder(this);
        this.concurrentManager.setActivitySequencer(this);
    }

    public ConcurrentDocumentManager getConcurrentManager() {
        return this.concurrentManager;
    }

    public synchronized void forwardOutgoingRequest(Request req) {

        /* check for errors. */
        if (req instanceof RequestError) {
            /* create save activity. */
            IActivity activity = new EditorActivity(Type.Saved, req
                .getEditorPath());
            /* execute save activity and start consistency check. */
            exec(activity);
            return;
        }

        /* put request into outgoing queue. */
        this.outgoingSyncActivities.add(req);

        notify();
    }

    public synchronized Request getNextOutgoingRequest()
        throws InterruptedException {
        Request request = null;
        /* get next message and transfer to client. */
        while (!(this.outgoingSyncActivities.size() > 0)) {
            wait();
        }
        /* remove first queue element. */
        request = this.outgoingSyncActivities.remove(0);

        return request;
    }

    /**
     * Execute activity after jupiter transforming process.
     * 
     * @param activity
     */
    public void execTransformedActivity(IActivity activity) {
        try {
            logger.debug("execute transformed activity: " + activity);

            for (IActivityProvider exec : this.providers) {
                exec.exec(activity);
            }
            /* send activity to all observer. */
            if (this.concurrentManager.isHostSide()) {
                logger.debug("send transformed activity: " + activity);
                this.activities.add(activity);
            }
        } catch (Exception e) {
            logger.error("Error while executing activity.", e);
        }
    }

    /**
     * Given a List of TimedActivities it will either return the Activity for
     * the given timestamp (andup == false) or all activities that have a
     * timestamp < than the given (andup == true).
     * 
     * If not Activities can be found an empty list is returned.
     */
    public static List<TimedActivity> filterActivityHistory(
        List<TimedActivity> toFilter, int timestamp, boolean andup) {

        List<TimedActivity> result = new LinkedList<TimedActivity>();

        if (andup) {
            for (TimedActivity tact : toFilter) {
                if (tact.getTimestamp() >= timestamp) {
                    result.add(tact);
                }
            }
        } else {
            for (TimedActivity tact : toFilter) {
                if (tact.getTimestamp() == timestamp) {
                    result.add(tact);
                    // Found the one we were looking for
                    return result;
                }
            }
        }

        return result;
    }
}
