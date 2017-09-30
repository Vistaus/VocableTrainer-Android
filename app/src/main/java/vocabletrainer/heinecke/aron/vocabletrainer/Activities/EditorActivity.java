package vocabletrainer.heinecke.aron.vocabletrainer.Activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TableLayout;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import vocabletrainer.heinecke.aron.vocabletrainer.Activities.lib.EntryListAdapter;
import vocabletrainer.heinecke.aron.vocabletrainer.R;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Comparator.GenEntryComparator;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Comparator.GenericComparator;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Database;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.Entry;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.Table;

import static vocabletrainer.heinecke.aron.vocabletrainer.Activities.MainActivity.PREFS_NAME;
import static vocabletrainer.heinecke.aron.vocabletrainer.lib.Database.ID_RESERVED_SKIP;

/**
 * List editor activity
 */
public class EditorActivity extends AppCompatActivity {
    private final static int LIST_SELECT_REQUEST_CODE = 10;
    /**
     * Param key for new table, default is false
     */
    public static final String PARAM_NEW_TABLE = "NEW_TABLE";
    /**
     * Param key for list to load upon new_table false
     */
    public static final String PARAM_TABLE = "table";
    private static final String TAG = "EditorActivity";
    private static final String P_KEY_EA_SORT = "EA_sorting";
    private Table table;
    private ArrayList<Entry> entries;
    private EntryListAdapter adapter;
    private ListView listView;
    private Database db;
    private View undoContainer;
    private Entry lastDeleted;
    private int deletedPosition;
    private int sortSetting;
    private GenEntryComparator cComp;
    private GenEntryComparator compA;
    private GenEntryComparator compB;
    private GenEntryComparator compTip;

    /**
     * data save will be ignored when set
     */
    private boolean noDataSave = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        entries = new ArrayList<>();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        db = new Database(getBaseContext());

        this.setTitle(R.string.Editor_Title);

        compA = new GenEntryComparator(new GenericComparator.ValueRetriever[] {
                GenEntryComparator.retA,GenEntryComparator.retB,
                GenEntryComparator.retTip
        },ID_RESERVED_SKIP);
        compB = new GenEntryComparator(new GenericComparator.ValueRetriever[] {
                GenEntryComparator.retB,GenEntryComparator.retA,
                GenEntryComparator.retTip
        },ID_RESERVED_SKIP);
        compTip = new GenEntryComparator(new GenericComparator.ValueRetriever[] {
                GenEntryComparator.retTip,GenEntryComparator.retA,
                GenEntryComparator.retB
        },ID_RESERVED_SKIP);

        Intent intent = getIntent();
        undoContainer = findViewById(R.id.undobar);
        undoContainer.setVisibility(View.GONE);

        FloatingActionButton bNewEntry = (FloatingActionButton) findViewById(R.id.bEditorNewEntry);
        bNewEntry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addEntry();
            }
        });

        // setup listview
        initListView();

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        sortSetting = settings.getInt(P_KEY_EA_SORT, R.id.eMenu_sort_A);
        updateComp();

        // handle passed params
        boolean newTable = intent.getBooleanExtra(PARAM_NEW_TABLE, false);
        if (newTable) {
            table = new Table(getString(R.string.Editor_Default_Column_A), getString(R.string.Editor_Default_Column_B), getString(R.string.Editor_Default_List_Name));
            Log.d(TAG, "new table mode");
            showTableInfoDialog(true);
        } else {
            Table tbl = (Table) intent.getSerializableExtra(PARAM_TABLE);
            if (tbl != null) {
                this.table = tbl;
                entries.addAll(db.getVocablesOfTable(table));
                adapter.setTableData(tbl);
                adapter.updateSorting(cComp);
                Log.d(TAG, "edit table mode");
            } else {
                Log.e(TAG, "Edit Table Flag set without passing a table");
            }
        }
    }

    /**
     * Changes cComp to current selection
     */
    private void updateComp(){
        switch(sortSetting){
            case R.id.eMenu_sort_A:
                cComp = compA;
                break;
            case R.id.eMenu_sort_B:
                cComp = compB;
                break;
            case R.id.eMenu_sort_Tip:
                cComp = compTip;
                break;
            default:
                cComp = compA;
                sortSetting = R.id.eMenu_sort_A;
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor, menu);
        return true;
    }

    @Override
    public void onPause() {
        saveTable();
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.tEditorListEdit:
                showTableInfoDialog(false);
                return true;
            case R.id.eMenu_sort_A:
            case R.id.eMenu_sort_B:
            case R.id.eMenu_sort_Tip:
                sortSetting = item.getItemId();
                updateComp();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called upon click on save changes
     */
    public void onSaveChangesClicked(View view) {
        saveTable();
    }

    /**
     * Save the table to disk
     */
    private void saveTable() {
        if(noDataSave){
            return;
        }
        Log.d(TAG, "table: " + table);
        if (db.upsertTable(table)) {
            Log.d(TAG, "table: " + table);
            if (db.upsertEntries(adapter.getAllEntries())) {
                adapter.clearDeleted();
            } else {
                Log.e(TAG, "unable to upsert entries! aborting");
            }
        } else {
            Log.e(TAG, "unable to upsert table! aborting");
        }
    }

    /**
     * Setup listview
     */
    private void initListView() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        listView = (ListView) findViewById(R.id.listviewEditor);

        listView.setLongClickable(true);

        entries = new ArrayList<>();
        adapter = new EntryListAdapter(this, entries, this);

        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int pos, long id) {
                showEntryEditDialog((Entry) adapter.getItem(pos), false);
            }

        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
                                           int pos, long id) {
                showEntryDeleteDialog((Entry) adapter.getItem(pos), pos);
                return true;
            }
        });
    }

    /**
     * Add an entry
     */
    public void addEntry() {
        Entry entry = new Entry("", "", "", table, -1);
        adapter.addEntryUnrendered(entry);
        showEntryEditDialog(entry, true);
    }

    /**
     * Show entry delete dialog
     *
     * @param entry
     * @param position
     */
    private void showEntryDeleteDialog(final Entry entry, final int position) {
        if (entry.getId() == ID_RESERVED_SKIP)
            return;
        AlertDialog.Builder delDiag = new AlertDialog.Builder(this);

        delDiag.setTitle(R.string.Editor_Diag_delete_Title);
        delDiag.setMessage(String.format(getString(R.string.Editor_Diag_delete_MSG_part) + "\n %s %s %s", entry.getAWord(), entry.getBWord(), entry.getTip()));

        delDiag.setPositiveButton(R.string.Editor_Diag_delete_btn_OK, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                lastDeleted = entry;
                deletedPosition = position;
                adapter.setDeleted(entry);
                showUndo();
                Log.d(TAG, "deleted");
            }
        });

        delDiag.setNegativeButton(R.string.Editor_Diag_delete_btn_CANCEL, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(TAG, "canceled");
            }
        });

        delDiag.show();
    }

    /**
     * Show entry edit dialog
     *
     * @param entry          Entry to edit
     * @param deleteOnCancel True if entry should be deleted on cancel
     */
    private void showEntryEditDialog(final Entry entry, final boolean deleteOnCancel) {
        if (entry.getId() == ID_RESERVED_SKIP) {
            showTableInfoDialog(false);
            return;
        }
        AlertDialog.Builder editDiag = new AlertDialog.Builder(this);

        editDiag.setTitle(R.string.Editor_Diag_edit_Title);

        final EditText editA = new EditText(this);
        final EditText editB = new EditText(this);
        final EditText editTipp = new EditText(this);
        editA.setText(entry.getAWord());
        editB.setText(entry.getBWord());
        editTipp.setText(entry.getTip());
        editA.setHint(R.string.Editor_Default_Column_A);
        editB.setHint(R.string.Editor_Default_Column_B);
        editTipp.setHint(R.string.Editor_Default_Tip);

        LinearLayout rl = new TableLayout(this);
        rl.addView(editA);
        rl.addView(editB);
        rl.addView(editTipp);

        editDiag.setView(rl);

        editDiag.setPositiveButton(R.string.Editor_Diag_edit_btn_OK, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                entry.setAWord(editA.getText().toString());
                entry.setBWord(editB.getText().toString());
                entry.setTip(editTipp.getText().toString());
                adapter.notifyDataSetChanged();
                Log.d(TAG, "edited");
            }
        });

        editDiag.setNegativeButton(R.string.Editor_Diag_edit_btn_CANCEL, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (deleteOnCancel) {
                    adapter.setDeleted(entry);
                    adapter.notifyDataSetChanged();
                }
                Log.d(TAG, "canceled");
            }
        });

        editDiag.show();
    }

    /**
     * Shows a dialog to edit the specified list data
     *
     * @param newTbl   Is new table
     * @param tbl      Table object to edit
     * @param onSuccessCallable Called upon ok press<br>
     *                 Not called when user cancels dialog in any way
     * @param onCancelCallable Called when <b>newTbl is true and the dialog was canceled</b><br>
     *                         Ignored when null
     * @param context  Context to be used for this dialog
     * @return Dialog created
     */
    public static AlertDialog showListEditorDialog(final boolean newTbl, final Table tbl, final Callable<Void> onSuccessCallable, final Callable<Void> onCancelCallable, final Context context) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);

        if (newTbl) {
            alert.setTitle(R.string.Editor_Diag_table_Title_New);
        } else {
            alert.setTitle(R.string.Editor_Diag_table_Title_Edit);
        }
        alert.setMessage("Please set the table information");

        // Set an EditText view to get user iName
        final EditText iName = new EditText(context);
        final EditText iColA = new EditText(context);
        final EditText iColB = new EditText(context);
        iName.setText(tbl.getName());
        iName.setSingleLine();
        iName.setHint(R.string.Editor_Default_List_Name);
        iColA.setHint(R.string.Editor_Default_Column_A);
        iColB.setHint(R.string.Editor_Default_Column_B);
        iColA.setText(tbl.getNameA());
        iColA.setSingleLine();
        iColB.setSingleLine();
        iColB.setText(tbl.getNameB());
        if (newTbl) {
            iName.setSelectAllOnFocus(true);
            iColA.setSelectAllOnFocus(true);
            iColB.setSelectAllOnFocus(true);
        }

        LinearLayout rl = new TableLayout(context);
        rl.addView(iName);
        rl.addView(iColA);
        rl.addView(iColB);
        alert.setView(rl);

        alert.setPositiveButton(R.string.Editor_Diag_table_btn_Ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (iColA.getText().length() == 0 || iColB.length() == 0 || iName.getText().length() == 0) {
                    Log.d(TAG, "empty insert");
                }

                tbl.setNameA(iColA.getText().toString());
                tbl.setNameB(iColB.getText().toString());
                tbl.setName(iName.getText().toString());
                try {
                    onSuccessCallable.call();
                } catch (Exception e) { // has to be caught
                    e.printStackTrace();
                }
                Log.d(TAG, "set table info");
            }
        });
        if (newTbl) {
            alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    try {
                        if(onCancelCallable != null)
                            onCancelCallable.call();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            alert.setNegativeButton(R.string.Editor_Diag_table_btn_Canel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        if(onCancelCallable != null)
                            onCancelCallable.call();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        return alert.show();
    }

    /**
     * Show table title editor dialog<br>
     *     Exit editor when newTbl is set and user cancels the dialog
     *
     * @param newTbl set to true if this is a new table
     */
    private void showTableInfoDialog(final boolean newTbl) {
        Callable<Void> callable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                setTitle("VocableTrainer - " + table.getName());
                adapter.setTableData(table);
                return null;
            }
        };
        Callable<Void> callableCancel = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if(newTbl) {
                    noDataSave = true;
                    finish();
                }
                return null;
            }
        };
        showListEditorDialog(newTbl, table, callable,callableCancel, this);
    }

    /**
     * Show undo view
     */
    private void showUndo() {
        undoContainer.setVisibility(View.VISIBLE);
        undoContainer.bringToFront();
        ScaleAnimation scaleAnimation = new ScaleAnimation(0f,1f,1f,1f,
                Animation.RELATIVE_TO_SELF, 0f, // Pivot point of X scaling
                Animation.RELATIVE_TO_SELF, 1f);
        final AlphaAnimation alphaAnimation = new AlphaAnimation(0.0f,1.0f);

        AnimationSet animationSet = new AnimationSet(true);
        animationSet.addAnimation(scaleAnimation);
        animationSet.addAnimation(alphaAnimation);
        animationSet.setDuration(500);
        animationSet.setFillEnabled(true);

        animationSet.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                AnimationSet animationSetOut = new AnimationSet(true);
                AlphaAnimation alphaAnimation1 = new AlphaAnimation(1f,0f);
                ScaleAnimation scaleAnimation1 = new ScaleAnimation(1f,0f,1f,1f,
                        Animation.RELATIVE_TO_SELF, 1f,
                        Animation.RELATIVE_TO_SELF, 1f);
                ScaleAnimation scaleAnimation2 = new ScaleAnimation(1f,0f,1f,0f,
                        Animation.RELATIVE_TO_SELF, 1f,
                        Animation.RELATIVE_TO_SELF, 1f);

                scaleAnimation2.setStartOffset(500);
                animationSetOut.addAnimation(alphaAnimation1);
                animationSetOut.addAnimation(scaleAnimation1);
                animationSetOut.addAnimation(scaleAnimation2);
                animationSetOut.setDuration(2000);
                animationSetOut.setStartOffset(2000);
                animationSetOut.setFillEnabled(true);
                animationSetOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        undoContainer.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
                undoContainer.setAnimation(animationSetOut);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        undoContainer.clearAnimation();
        undoContainer.setAnimation(animationSet);

        undoContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "undoing");
                lastDeleted.setDelete(false);
                undoContainer.clearAnimation();
                adapter.addEntryRendered(lastDeleted, deletedPosition);
                undoContainer.setVisibility(View.GONE);
                listView.setFocusable(true);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(P_KEY_EA_SORT, sortSetting);
        editor.apply();
    }

}
