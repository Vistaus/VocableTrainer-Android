package vocabletrainer.heinecke.aron.vocabletrainer.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

import org.apache.commons.csv.CSVFormat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import vocabletrainer.heinecke.aron.vocabletrainer.R;
import vocabletrainer.heinecke.aron.vocabletrainer.fragment.BaseFragment;
import vocabletrainer.heinecke.aron.vocabletrainer.fragment.ExportFragment;
import vocabletrainer.heinecke.aron.vocabletrainer.fragment.ImportFragment;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.CSVCustomFormat;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.GenericSpinnerEntry;

import static vocabletrainer.heinecke.aron.vocabletrainer.activity.MainActivity.PREFS_NAME;

/**
 * Activity for import/export
 */
public class ExImportActivity extends FragmentActivity {
    private final static String P_KEY_S_CSV_FORMAT = "csv_format";

    private static final String TAG = "ExImportActivity";
    /**
     * This permission is required for this activity to work
     */
    public static final String REQUIRED_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    /**
     * Pass this as false to show export options
     */
    public static final String PARAM_IMPORT = "show_import";
    private static final String KEY_FORMAT = "customFormat";
    private static final String KEY_FRAGMENT = "fragmentExIm";
    private static CSVCustomFormat CUSTOM_FORMAT;
    private BaseFragment fragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate");

        setContentView(R.layout.fragment_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
        fragment = null;
        if(savedInstanceState == null){ // init
            Intent intent = getIntent();
            boolean showImport = intent.getBooleanExtra(PARAM_IMPORT, true);
            Log.d(TAG,"import mode:"+showImport);

            if (showImport) {
                fragment = new ImportFragment();
            } else {
                fragment = new ExportFragment();
            }
            setFragment(fragment);
        } // otherwise let android handle the restore
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = preferences.edit();
        saveCustomFormat(editor);
        editor.apply();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case android.R.id.home:
                Log.d(TAG,"onOptionsItemSelected:home");
                this.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Returns the custom format<br>
     * This can be user defined
     *
     * @param settings required to load preferences
     * @return
     */
    public static CSVCustomFormat getCustomFormat(SharedPreferences settings) {
        if (CUSTOM_FORMAT == null) {
            String serialized = settings.getString(P_KEY_S_CSV_FORMAT, null);
            if (serialized == null) {
                CUSTOM_FORMAT = CSVCustomFormat.DEFAULT;
            } else {
                try {
                    byte b[] = Base64.decode(serialized.getBytes(),Base64.DEFAULT);
                    ByteArrayInputStream bi = new ByteArrayInputStream(b);
                    ObjectInputStream si = new ObjectInputStream(bi);
                    CUSTOM_FORMAT = (CSVCustomFormat) si.readObject();
                    si.close();
                    bi.close();
                    Log.d(TAG,"decoded format");
                } catch (Exception e) {
                    Log.e(TAG, "unable to load custom format ", e);
                    CUSTOM_FORMAT = CSVCustomFormat.DEFAULT;
                }
            }
        }
        return CUSTOM_FORMAT;
    }

    /**
     * Update format to new one
     * @param newFormat
     */
    public static void updateCustomFormat(final CSVCustomFormat newFormat){
        CUSTOM_FORMAT = newFormat;
    }

    /**
     * Save custom format settings
     *
     * @param editor editor to store stuff to
     */
    private static void saveCustomFormat(SharedPreferences.Editor editor) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream so = new ObjectOutputStream(bo);
            so.writeObject(CUSTOM_FORMAT);
            so.flush();
            editor.putString(P_KEY_S_CSV_FORMAT, new String(Base64.encode(bo.toByteArray(), Base64.DEFAULT)));
            so.close();
            bo.close();
            Log.d(TAG,"saved custom format");
        } catch (IOException e) {
            Log.e(TAG, "unable to save format ", e);
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG,"onSaveInstanceState");
        super.onSaveInstanceState(outState);

        outState.putParcelable(KEY_FORMAT,CUSTOM_FORMAT);
    }

    /**
     * Static method to populate format spinner adapter
     *
     * @param adapter Adapter to populate
     * @param context Context for string resolve
     */
    public static void populateFormatSpinnerAdapter(ArrayAdapter<GenericSpinnerEntry<CSVCustomFormat>> adapter, Context context, SharedPreferences settings) {
        adapter.clear();
        adapter.add(new GenericSpinnerEntry<>(CSVCustomFormat.DEFAULT, context.getString(R.string.CSV_Format_Default)));
        adapter.add(new GenericSpinnerEntry<>(new CSVCustomFormat(CSVFormat.EXCEL), context.getString(R.string.CSV_Format_EXCEL)));
        adapter.add(new GenericSpinnerEntry<>(new CSVCustomFormat(CSVFormat.RFC4180), context.getString(R.string.CSV_Format_RFC4180)));
        adapter.add(new GenericSpinnerEntry<>(new CSVCustomFormat(CSVFormat.TDF), context.getString(R.string.CSV_Format_Tabs)));
        adapter.add(new GenericSpinnerEntry<>(new CSVCustomFormat(CSVFormat.MYSQL), context.getString(R.string.CSV_Format_Mysql)));
        adapter.add(new GenericSpinnerEntry<>(new CSVCustomFormat(CSVFormat.INFORMIX_UNLOAD), context.getString(R.string.CSV_Format_INFORMIX_UNLOAD)));
        adapter.add(new GenericSpinnerEntry<>(new CSVCustomFormat(CSVFormat.INFORMIX_UNLOAD_CSV), context.getString(R.string.CSV_Format_INFORMIX_UNLOAD_CSV)));
        adapter.add(new GenericSpinnerEntry<>(getCustomFormat(settings), context.getString(R.string.CSV_Format_Custom_Format)));
        adapter.notifyDataSetChanged();
    }
}
