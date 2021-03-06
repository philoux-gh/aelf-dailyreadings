package co.epitre.aelf_lectures;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.WindowManager;

public class SyncPrefActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    public static final String KEY_PREF_DISP_FONT_SIZE = "pref_disp_font_size";
    public static final String KEY_PREF_SYNC_LECTURES = "pref_sync_lectures";
    public static final String KEY_PREF_SYNC_DUREE = "pref_sync_duree";
    public static final String KEY_PREF_SYNC_CONSERV = "pref_sync_conserv";

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.sync_preferences);

        // hacky hack, but does the job --> init summaries
        onSharedPreferenceChanged(null, KEY_PREF_DISP_FONT_SIZE);
        onSharedPreferenceChanged(null, KEY_PREF_SYNC_LECTURES);
        onSharedPreferenceChanged(null, KEY_PREF_SYNC_DUREE);
        onSharedPreferenceChanged(null, KEY_PREF_SYNC_CONSERV);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // set summary
        if (key.equals(KEY_PREF_DISP_FONT_SIZE) ||
            key.equals(KEY_PREF_SYNC_LECTURES) ||
            key.equals(KEY_PREF_SYNC_DUREE) ||
            key.equals(KEY_PREF_SYNC_CONSERV)) {
            ListPreference pref = (ListPreference)findPreference(key);
            pref.setSummary(pref.getEntry());
        }
        
        // called with null from the constructor
        if(sharedPreferences != null) {
            // Apply changes so that sync engines takes them into account
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.commit(); // commit to file so that sync service is able to load it from disk
        }
    }

    @SuppressLint("NewApi")
    public void prepare_fullscreen() {
        // copied from LectureActivity
        // Hide status (top) bar. Navigation bar (> 4.0) still visible.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (android.os.Build.VERSION.SDK_INT < 14) // 4.0 min
            return;

        // Android 4.0+: make navigation bar 'discret' ('dots' instead of icons)
        int uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE;

        // Android 4.4+: hide navigation bar, make it accessible via edge scroll
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    |  View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        // apply settings
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // manage application's intrusiveness for different Android versions
        super.onWindowFocusChanged(hasFocus);

        if(hasFocus)
            prepare_fullscreen();
    }

}
