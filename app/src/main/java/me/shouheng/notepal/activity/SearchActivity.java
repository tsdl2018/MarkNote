package me.shouheng.notepal.activity;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.LinkedList;
import java.util.List;

import me.shouheng.notepal.R;
import me.shouheng.notepal.activity.base.CommonActivity;
import me.shouheng.notepal.adapter.SearchItemsAdapter;
import me.shouheng.notepal.adapter.SearchItemsAdapter.OnItemSelectedListener;
import me.shouheng.notepal.databinding.ActivitySearchBinding;
import me.shouheng.notepal.model.Note;
import me.shouheng.notepal.util.GsonUtils;
import me.shouheng.notepal.util.LogUtils;
import me.shouheng.notepal.util.preferences.PreferencesUtils;
import me.shouheng.notepal.util.ToastUtils;
import me.shouheng.notepal.util.tools.SearchConditions;
import me.shouheng.notepal.viewmodel.SearchViewModel;
import me.shouheng.notepal.widget.tools.CustomItemAnimator;
import me.shouheng.notepal.widget.tools.DividerItemDecoration;

public class SearchActivity extends CommonActivity<ActivitySearchBinding> implements
        OnQueryTextListener,
        OnItemSelectedListener {

    private final static String EXTRA_NAME_REQUEST_CODE = "extra.request.code";

    private final static int REQUEST_FOR_NOTE = 20004;

    private SearchItemsAdapter adapter;

    private SearchView mSearchView;

    private String queryString;

    private SearchConditions conditions;

    private SearchViewModel searchViewModel;

    /**
     * Field to remark that is the content changed, will be used to set result to caller. */
    private boolean isContentChanged = false;

    public static void start(Activity mContext, int requestCode){
        Intent intent = new Intent(mContext, SearchActivity.class);
        intent.putExtra(EXTRA_NAME_REQUEST_CODE, requestCode);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        mContext.startActivityForResult(intent, requestCode);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_search;
    }

    @Override
    protected void doCreateView(Bundle savedInstanceState) {
        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        if (!isDarkTheme()){
            getBinding().toolbarLayout.toolbar.setPopupTheme(R.style.AppTheme_PopupOverlay);
        }

        adapter = new SearchItemsAdapter(this, this);

        getBinding().recyclerview.setEmptyView(findViewById(R.id.iv_empty));
        getBinding().recyclerview.addItemDecoration(new DividerItemDecoration(
                this, DividerItemDecoration.VERTICAL_LIST, isDarkTheme()));
        getBinding().recyclerview.setItemAnimator(new CustomItemAnimator());
        getBinding().recyclerview.setLayoutManager(new LinearLayoutManager(this));
        getBinding().recyclerview.setAdapter(adapter);

        initViewModel();

        initSearchConditions();
    }

    private void initViewModel() {
        searchViewModel = ViewModelProviders.of(this).get(SearchViewModel.class);
    }

    private void initSearchConditions() {
        String searchConditions = PreferencesUtils.getInstance(this).getSearchConditions();
        conditions = TextUtils.isEmpty(searchConditions) ? SearchConditions.getDefaultConditions()
                : GsonUtils.toObject(searchConditions, SearchConditions.class);
        searchViewModel.setConditions(conditions);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.item_include_tags).setChecked(conditions.isIncludeTags());
        menu.findItem(R.id.item_include_archived).setChecked(conditions.isIncludeArchived());
        menu.findItem(R.id.item_include_trashed).setChecked(conditions.isIncludeTrashed());
        searchViewModel.setConditions(conditions);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search, menu);
        getMenuInflater().inflate(R.menu.filter_search_condition, menu);

        mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setQueryHint(getString(R.string.search_by_conditions));
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setIconified(false);

        MenuItemCompat.setOnActionExpandListener(menu.findItem(R.id.action_search), new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                finish();
                return false;
            }
        });

        menu.findItem(R.id.action_search).expandActionView();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                break;
            case R.id.item_include_tags:
                conditions.setIncludeTags(!conditions.isIncludeTags());
                invalidateOptionsMenu();
                break;
            case R.id.item_include_archived:
                conditions.setIncludeArchived(!conditions.isIncludeArchived());
                invalidateOptionsMenu();
                break;
            case R.id.item_include_trashed:
                conditions.setIncludeTrashed(!conditions.isIncludeTrashed());
                invalidateOptionsMenu();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        onQueryTextChange(query);
        hideInputManager();
        return true;
    }

    private void hideInputManager() {
        if (mSearchView != null) {
            mSearchView.clearFocus();
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (newText.equals(queryString)) {
            return true;
        }

        queryString = newText;

        if (!TextUtils.isEmpty(queryString)) {
            queryAll(queryString);
        } else {
            setupQueryResults(null);
        }

        return true;
    }

    private void queryAll(String queryText) {
        getBinding().topMpb.setVisibility(View.VISIBLE);
        searchViewModel.getSearchResult(queryText).observe(this, searchResultResource -> {
            LogUtils.d(searchResultResource);
            getBinding().topMpb.setVisibility(View.GONE);
            if (searchResultResource == null) {
                ToastUtils.makeToast(R.string.text_error_when_save);
                return;
            }
            switch (searchResultResource.status) {
                case SUCCESS:
                    if (searchResultResource.data != null) {
                        setupQueryResults(searchResultResource.data.getNotes());
                    }
                    break;
                case FAILED:
                    ToastUtils.makeToast(R.string.text_error_when_save);
                    break;
            }
        });
    }

    private void setupQueryResults(List<Note> notes) {
        List searchResults = new LinkedList();
        if (notes != null && !notes.isEmpty()) {
            searchResults.addAll(notes);
        }
        adapter.updateSearchResults(searchResults);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onNoteSelected(Note note, int position) {
        ContentActivity.viewNote(this, note, REQUEST_FOR_NOTE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_FOR_NOTE:
                    queryAll(queryString);
                    isContentChanged = true;
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (isContentChanged) {
            Intent intent = new Intent();
            setResult(Activity.RESULT_OK, intent);
            finish();
        } else {
            super.onBackPressed();
        }
    }
}
