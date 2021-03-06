package co.epitre.aelf_lectures.sync;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import co.epitre.aelf_lectures.R;
import co.epitre.aelf_lectures.SyncPrefActivity;
import co.epitre.aelf_lectures.data.LecturesController;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "AELFSyncAdapter";
    public static final int SYNC_NOT_ID = 1;

    Context mContext;
    NotificationManager mNotificationManager;
    LecturesController mController;

    NotificationCompat.Builder mNotificationBuilder;

    int mTodo;
    int mDone;

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);

        Object service = context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.mNotificationManager = (NotificationManager) service;
        this.mContext = context;
        this.mController = LecturesController.getInstance(this.getContext());

        PendingIntent intent =
                PendingIntent.getActivity(
                mContext,
                0,
                new Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT
            );

        mNotificationBuilder = new NotificationCompat.Builder(mContext)
            .setContentTitle("AELF")
            .setContentText("Pré-chargement des lectures...")
            .setContentIntent(intent)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true);
    }

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }

    // code to display a sync notification
    // http://stackoverflow.com/questions/5061760/how-does-one-animate-the-android-sync-status-icon
    private void updateNotification() {
        mNotificationBuilder.setProgress(mTodo, mDone, false);
        mNotificationManager.notify(SYNC_NOT_ID, mNotificationBuilder.build());
    }

    private void cancelNotification() {
        mNotificationManager.cancel(SYNC_NOT_ID);
    }

    // Sync one reading for the day
    private void syncReading(LecturesController.WHAT what, GregorianCalendar when) throws IOException {
        // Load from network, if not in cache
        if(mController.getLecturesFromCache(what, when) == null) {
            mController.getLecturesFromNetwork(what, when);
        }
        mDone++;
        updateNotification();
    }

    // Sync all readings for the day
    private void syncDay(GregorianCalendar when, int max) throws IOException {
        syncReading(LecturesController.WHAT.METAS, when);
        while(max-- > 0) {
            LecturesController.WHAT what = LecturesController.WHAT.values()[max];
            syncReading(what, when);
        }
    }

    /**
     * Pre-load readings for
     *  - yesterday
     *  - today
     *  - tomorrow
     *  - next Sunday
     */
    @Override
    public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult) {
        Log.i(TAG, "Beginning network synchronization");

        // ** PREFS **

        // defaults
        Resources res = mContext.getResources();
        String pLectures = res.getString(R.string.pref_lectures_def);
        String pDuree    = res.getString(R.string.pref_duree_def);
        String pConserv  = res.getString(R.string.pref_conserv_def);

        // read preferences
        SharedPreferences syncPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        pLectures = syncPref.getString(SyncPrefActivity.KEY_PREF_SYNC_LECTURES, pLectures);
        pDuree    = syncPref.getString(SyncPrefActivity.KEY_PREF_SYNC_DUREE,    pDuree);
        pConserv  = syncPref.getString(SyncPrefActivity.KEY_PREF_SYNC_CONSERV,  pConserv);

        Log.i(TAG, "Pref lectures="+pLectures);
        Log.i(TAG, "Pref durée="+pDuree);
        Log.i(TAG, "Pref conservation="+pConserv);

        LecturesController controller = LecturesController.getInstance(this.getContext());

        // turn params into something usable
        int daysToSync = 0;
        // FIXME: -1 because 8 is Meta, which is always synced
        int whatMax = (pLectures.equals("messe-offices"))?LecturesController.WHAT.values().length-1:1;
        GregorianCalendar whenMax = new GregorianCalendar();

        if(pDuree.equals("auj")) {
            // take tomorrow for free as well or we might be quite late if running at 23h50..
            whenMax.add(Calendar.DATE, 1);
            daysToSync += 1;
        } else if(pDuree.equals("auj-dim")) {
            daysToSync += 2;
        } else if(pDuree.equals("semaine")) {
            whenMax.add(Calendar.DATE, 7);
            daysToSync += 7;
        } else if(pDuree.equals("mois")) {
            whenMax.add(Calendar.DATE, 31);
            daysToSync += 31;
        }

        mTodo = daysToSync * (whatMax+1); // all readings + meta for all days
        mDone = 0;

        // notify user
        updateNotification();

        // ** SYNC **
        try {
            // loop until when > dayMax
            GregorianCalendar when = new GregorianCalendar();
            do {
                syncDay(when, whatMax);
                when.add(Calendar.DATE, +1);
            } while(when.before(whenMax));

            // finally, do we need to explicitly grab next Sunday ?
            if(pDuree.equals("auj-dim")) {
                when = new GregorianCalendar();
                do when.add(Calendar.DATE, +1); while (when.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY); // next Sunday
                syncDay(when, whatMax);
            }
        } catch (IOException e) {
            // Aelf servers down ? It appends ...
            Log.e(TAG, "I/O error while syncing. AELF servers down ?");
            syncResult.delayUntil = 60L*15; // Wait 15min before retrying
        } finally {
            this.cancelNotification();
        }

        // ** CLEANUP **
        GregorianCalendar minConserv = new GregorianCalendar();
        if(pConserv.equals("semaine")) {
            minConserv.add(Calendar.DATE, -7);
        } else if (pConserv.equals("mois")) {
            minConserv.add(Calendar.MONTH, -1);
        } else if (pConserv.equals("toujours")) {
            // let's be honest: If I keep all, users will kill me !
            minConserv.add(Calendar.YEAR, -1);
        }
        controller.truncateBefore(minConserv);

        // TODO: persist last sync time

    }
}
