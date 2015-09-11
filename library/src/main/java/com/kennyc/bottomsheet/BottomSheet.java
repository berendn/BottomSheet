package com.kennyc.bottomsheet;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.MenuRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;

import com.kennyc.bottomsheet.adapters.AppAdapter;
import com.kennyc.bottomsheet.adapters.GridAdapter;
import com.kennyc.bottomsheet.menu.BottomSheetMenu;
import com.kennyc.bottomsheet.menu.BottomSheetMenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by kcampagna on 8/7/15.
 */
public class BottomSheet extends Dialog implements AdapterView.OnItemClickListener, CollapsingView.CollapseListener {
    private static final int MIN_LIST_TABLET_ITEMS = 6;

    private static final int NO_RESOURCE = -1;

    private static final String TAG = BottomSheet.class.getSimpleName();

    private static final int[] ATTRS = new int[]{
            R.attr.bottom_sheet_bg_color,
            R.attr.bottom_sheet_title_color,
            R.attr.bottom_sheet_list_item_color,
            R.attr.bottom_sheet_grid_item_color,
            R.attr.bottom_sheet_item_icon_color
    };

    private Builder mBuilder;

    private BaseAdapter mAdapter;

    private GridView mGrid;

    private TextView mTitle;

    private BottomSheetListener mListener;

    /**
     * Default constructor. It is recommended to use the {@link com.kennyc.bottomsheet.BottomSheet.Builder} for creating a BottomSheet
     *
     * @param context  App context
     * @param builder  {@link com.kennyc.bottomsheet.BottomSheet.Builder} with supplied options for the dialog
     * @param style    Style resource for the dialog
     * @param listener The optional {@link BottomSheetListener} for callbacks
     */
    BottomSheet(Context context, Builder builder, @StyleRes int style, BottomSheetListener listener) {
        super(context, style);
        mBuilder = builder;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!canCreateSheet()) {
            throw new IllegalStateException("Unable to create BottomSheet, missing params");
        }

        Window window = getWindow();
        int width = getContext().getResources().getDimensionPixelSize(R.dimen.bottom_sheet_width);

        if (window != null) {
            window.setLayout(width <= 0 ? ViewGroup.LayoutParams.MATCH_PARENT : width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        } else {
            Log.e(TAG, "Window came back as null, unable to set defaults");
        }

        TypedArray ta = getContext().obtainStyledAttributes(ATTRS);

        if (mBuilder.view != null) {
            initViewLayout(ta);
        } else {
            initLayout(ta, width);

            if (mBuilder.menuItems != null) {
                initMenu(ta);
                if (mListener != null) mListener.onSheetShown();
            } else {
                mGrid.setAdapter(mAdapter = new AppAdapter(getContext(), mBuilder.apps, mBuilder.isGrid));
            }
        }

        ta.recycle();
    }

    @Override
    public void dismiss() {
        if (mListener != null) mListener.onSheetDismissed();
        super.dismiss();
    }

    private void initViewLayout(TypedArray ta) {
        CollapsingView collapsingView = new CollapsingView(getContext());
        collapsingView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        collapsingView.setCollapseListener(this);

        if (mBuilder.backgroundColor != Integer.MIN_VALUE) {
            mBuilder.view.setBackgroundColor(mBuilder.backgroundColor);
        } else {
            mBuilder.view.setBackgroundColor(ta.getColor(0, Color.WHITE));
        }

        collapsingView.addView(mBuilder.view);
        setContentView(collapsingView);
        if (mListener != null) mListener.onSheetShown();
    }

    private void initLayout(TypedArray ta, int width) {
        Resources res = getContext().getResources();
        setCancelable(mBuilder.cancelable);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_layout, null);
        ((CollapsingView) view).setCollapseListener(this);

        if (mBuilder.backgroundColor != Integer.MIN_VALUE) {
            view.findViewById(R.id.container).setBackgroundColor(mBuilder.backgroundColor);
        } else {
            view.findViewById(R.id.container).setBackgroundColor(ta.getColor(0, Color.WHITE));
        }

        mGrid = (GridView) view.findViewById(R.id.grid);
        mGrid.setOnItemClickListener(this);
        mTitle = (TextView) view.findViewById(R.id.title);
        boolean hasTitle = !TextUtils.isEmpty(mBuilder.title);

        if (hasTitle) {
            mTitle.setText(mBuilder.title);
            mTitle.setVisibility(View.VISIBLE);

            if (mBuilder.titleColor != Integer.MIN_VALUE) {
                mTitle.setTextColor(mBuilder.titleColor);
            } else {
                mTitle.setTextColor(ta.getColor(1, res.getColor(R.color.black_55)));
            }
        } else {
            mTitle.setVisibility(View.GONE);
        }

        if (mBuilder.isGrid) {
            int gridPadding = res.getDimensionPixelSize(R.dimen.bottom_sheet_grid_padding);
            int topPadding = res.getDimensionPixelSize(R.dimen.bottom_sheet_dialog_padding);
            mGrid.setVerticalSpacing(res.getDimensionPixelSize(R.dimen.bottom_sheet_grid_spacing));
            mGrid.setPadding(0, topPadding, 0, gridPadding);
        } else {
            int padding = res.getDimensionPixelSize(R.dimen.bottom_sheet_list_padding);
            mGrid.setPadding(0, hasTitle ? 0 : padding, 0, padding);
        }

        mGrid.setNumColumns(getNumColumns(res, width));
        setContentView(view);
    }

    /**
     * Returns the number of columns the {@link BottomSheet} will use. If grid styled, it will load the
     * value from resources. If list styled and a tablet, it will be grid styled (2 columns) if >= 6 items are in the list
     *
     * @param resources   System resources
     * @param dialogWidth The width of the dialog. This will determine if it is a tablet. Anything > 0 is a tablet.
     * @return
     */
    private int getNumColumns(Resources resources, int dialogWidth) {
        if (mBuilder.isGrid) {
            return resources.getInteger(R.integer.bottomsheet_num_columns);
        }

        // If a talbet with more than 6 items are present, split them into 2 columns
        if (dialogWidth > 0) {
            if (mBuilder.menuItems != null) {
                return mBuilder.menuItems.size() >= MIN_LIST_TABLET_ITEMS ? 2 : 1;
            } else {
                return mBuilder.apps.size() >= MIN_LIST_TABLET_ITEMS ? 2 : 1;
            }
        }

        // Regular phone, one column
        return 1;
    }

    private void initMenu(TypedArray ta) {
        Resources res = getContext().getResources();
        int listColor;
        int gridColor;

        if (mBuilder.itemColor != Integer.MIN_VALUE) {
            listColor = mBuilder.itemColor;
            gridColor = mBuilder.itemColor;
        } else {
            listColor = ta.getColor(2, res.getColor(R.color.black_85));
            gridColor = ta.getColor(3, res.getColor(R.color.black_85));
        }

        if (mBuilder.menuItemTintColor == Integer.MIN_VALUE) {
            mBuilder.menuItemTintColor = ta.getColor(4, Integer.MIN_VALUE);
        }

        mAdapter = new GridAdapter(getContext(), mBuilder.menuItems, mBuilder.isGrid, listColor, gridColor, mBuilder.menuItemTintColor);
        mGrid.setAdapter(mAdapter);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mAdapter instanceof GridAdapter) {
            if (mListener != null) {
                MenuItem item = ((GridAdapter) mAdapter).getItem(position);
                mListener.onSheetItemSelected(item);
            }
        } else if (mAdapter instanceof AppAdapter) {
            AppAdapter.AppInfo info = ((AppAdapter) mAdapter).getItem(position);
            Intent intent = new Intent(mBuilder.shareIntent);
            intent.setComponent(new ComponentName(info.packageName, info.name));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }

        dismiss();
    }

    /**
     * Returns if the {@link BottomSheet} can be created based on the {@link com.kennyc.bottomsheet.BottomSheet.Builder}
     *
     * @return
     */
    private boolean canCreateSheet() {
        return mBuilder != null && ((mBuilder.menuItems != null && !mBuilder.menuItems.isEmpty()) || (mBuilder.apps != null && !mBuilder.apps.isEmpty()) || mBuilder.view != null);
    }

    @Override
    public void onCollapse() {
        // Post a runnable for dismissing to avoid "Attempting to destroy the window while drawing!" error
        if (getWindow() != null) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    dismiss();
                }
            });
        }
    }

    /**
     * Returns a {@link BottomSheet} to be used as a share intent like Android 5.x+ Share Intent.<p>
     * An example of an intent to pass is sharing some form of text:<br>
     * Intent intent = new Intent(Intent.ACTION_SEND);<br>
     * intent.setType("text/*");<br>
     * intent.putExtra(Intent.EXTRA_TEXT, "Some text to share");<br>
     * BottomSheet bottomSheet = BottomSheet.createShareBottomSheet(this, intent, "Share");<br>
     * if (bottomSheet != null) bottomSheet.show();<br>
     *
     * @param context    App context
     * @param intent     Intent to get apps for
     * @param shareTitle The optional title string resource for the share intent
     * @param isGrid     If the share intent BottomSheet should be grid styled
     * @param appsFilter If provided share will be limited to contained packaged names
     * @return A {@link BottomSheet} with the apps that can handle the share intent. NULL maybe returned if no
     * apps can handle the share intent
     */
    @Nullable
    public static BottomSheet createShareBottomSheet(Context context, Intent intent, String shareTitle, boolean isGrid, @Nullable Set<String> appsFilter) {
        if (context == null || intent == null) return null;

        PackageManager manager = context.getPackageManager();
        List<ResolveInfo> apps = manager.queryIntentActivities(intent, 0);

        if (apps != null && !apps.isEmpty()) {
            List<AppAdapter.AppInfo> appResources = new ArrayList<>(apps.size());
            boolean shouldCheckPackages = appsFilter != null && !appsFilter.isEmpty();

            for (ResolveInfo resolveInfo : apps) {
                String packageName = resolveInfo.activityInfo.packageName;

                if (shouldCheckPackages && !appsFilter.contains(resolveInfo.activityInfo.packageName)) {
                    continue;
                }

                String title = resolveInfo.loadLabel(manager).toString();
                String name = resolveInfo.activityInfo.name;
                Drawable drawable = resolveInfo.loadIcon(manager);
                appResources.add(new AppAdapter.AppInfo(title, packageName, name, drawable));
            }

            Builder b = new Builder(context)
                    .setApps(appResources, intent)
                    .setTitle(shareTitle);

            if (isGrid) b.grid();
            return b.create();
        }

        return null;
    }

    /**
     * Returns a {@link BottomSheet} to be used as a share intent like Android 5.x+ Share Intent.<p>
     * An example of an intent to pass is sharing some form of text:<br>
     * Intent intent = new Intent(Intent.ACTION_SEND);<br>
     * intent.setType("text/*");<br>
     * intent.putExtra(Intent.EXTRA_TEXT, "Some text to share");<br>
     * BottomSheet bottomSheet = BottomSheet.createShareBottomSheet(this, intent, "Share");<br>
     * if (bottomSheet != null) bottomSheet.show();<br>
     *
     * @param context    App context
     * @param intent     Intent to get apps for
     * @param shareTitle The optional title for the share intent
     * @param isGrid     If the share intent BottomSheet should be grid styled
     * @param appsFilter If provided share will be limited to contained packaged names
     * @return A {@link BottomSheet} with the apps that can handle the share intent. NULL maybe returned if no
     * apps can handle the share intent
     */
    @Nullable
    public static BottomSheet createShareBottomSheet(Context context, Intent intent, @StringRes int shareTitle, boolean isGrid, @Nullable Set<String> appsFilter) {
        return createShareBottomSheet(context, intent, context.getString(shareTitle), isGrid, appsFilter);
    }

    /**
     * Returns a {@link BottomSheet} to be used as a share intent like Android 5.x+ Share Intent.<p>
     * An example of an intent to pass is sharing some form of text:<br>
     * Intent intent = new Intent(Intent.ACTION_SEND);<br>
     * intent.setType("text/*");<br>
     * intent.putExtra(Intent.EXTRA_TEXT, "Some text to share");<br>
     * BottomSheet bottomSheet = BottomSheet.createShareBottomSheet(this, intent, "Share");<br>
     * if (bottomSheet != null) bottomSheet.show();<br>
     *
     * @param context    App context
     * @param intent     Intent to get apps for
     * @param shareTitle The optional title string resource for the share intent
     * @param isGrid     If the share intent BottomSheet should be grid styled
     * @return A {@link BottomSheet} with the apps that can handle the share intent. NULL maybe returned if no
     * apps can handle the share intent
     */
    @Nullable
    public static BottomSheet createShareBottomSheet(Context context, Intent intent, @StringRes int shareTitle, boolean isGrid) {
        return createShareBottomSheet(context, intent, context.getString(shareTitle), isGrid, null);
    }

    /**
     * Returns a {@link BottomSheet} to be used as a share intent like Android 5.x+ Share Intent.<p>
     * An example of an intent to pass is sharing some form of text:<br>
     * Intent intent = new Intent(Intent.ACTION_SEND);<br>
     * intent.setType("text/*");<br>
     * intent.putExtra(Intent.EXTRA_TEXT, "Some text to share");<br>
     * BottomSheet bottomSheet = BottomSheet.createShareBottomSheet(this, intent, "Share");<br>
     * if (bottomSheet != null) bottomSheet.show();<br>
     *
     * @param context    App context
     * @param intent     Intent to get apps for
     * @param shareTitle The optional title for the share intent
     * @param isGrid     If the share intent BottomSheet should be grid styled
     * @return A {@link BottomSheet} with the apps that can handle the share intent. NULL maybe returned if no
     * apps can handle the share intent
     */
    public static BottomSheet createShareBottomSheet(Context context, Intent intent, String shareTitle, boolean isGrid) {
        return createShareBottomSheet(context, intent, shareTitle, isGrid, null);
    }

    /**
     * Returns a {@link BottomSheet} to be used as a share intent like Android 5.x+ Share Intent. This will be List styled by default.<br>
     * If grid style is desired, use {@link #createShareBottomSheet(Context, Intent, String, boolean)}<p>
     * An example of an intent to pass is sharing some form of text:<br>
     * Intent intent = new Intent(Intent.ACTION_SEND);<br>
     * intent.setType("text/*");<br>
     * intent.putExtra(Intent.EXTRA_TEXT, "Some text to share");<br>
     * BottomSheet bottomSheet = BottomSheet.createShareBottomSheet(this, intent, "Share");<br>
     * if (bottomSheet != null) bottomSheet.show();<br>
     *
     * @param context    App context
     * @param intent     Intent to get apps for
     * @param shareTitle The optional title for the share intent
     * @return A {@link BottomSheet} with the apps that can handle the share intent. NULL maybe returned if no
     * apps can handle the share intent
     */
    @Nullable
    public static BottomSheet createShareBottomSheet(Context context, Intent intent, String shareTitle) {
        return createShareBottomSheet(context, intent, shareTitle, false, null);
    }

    /**
     * Returns a {@link BottomSheet} to be used as a share intent like Android 5.x+ Share Intent. This will be list styled by default.<br>
     * If grid style is desired, use {@link #createShareBottomSheet(Context, Intent, String, boolean)}<p>
     * An example of an intent to pass is sharing some form of text:<br>
     * Intent intent = new Intent(Intent.ACTION_SEND);<br>
     * intent.setType("text/*");<br>
     * intent.putExtra(Intent.EXTRA_TEXT, "Some text to share");<br>
     * BottomSheet bottomSheet = BottomSheet.createShareBottomSheet(this, intent, "Share");<br>
     * if (bottomSheet != null) bottomSheet.show();<br>
     *
     * @param context    App context
     * @param intent     Intent to get apps for
     * @param shareTitle The optional title for the share intent
     * @return A {@link BottomSheet} with the apps that can handle the share intent. NULL maybe returned if no
     * apps can handle the share intent
     */
    @Nullable
    public static BottomSheet createShareBottomSheet(Context context, Intent intent, @StringRes int shareTitle) {
        return createShareBottomSheet(context, intent, context.getString(shareTitle), false, null);
    }

    /**
     * Builder factory used for creating {@link BottomSheet}
     */
    public static class Builder {
        @StyleRes
        int style = NO_RESOURCE;

        String title = null;

        boolean cancelable = true;

        boolean isGrid = false;

        List<MenuItem> menuItems;

        @ColorInt
        int menuItemTintColor = Integer.MIN_VALUE;

        @ColorInt
        int titleColor = Integer.MIN_VALUE;

        @ColorInt
        int itemColor = Integer.MIN_VALUE;

        @ColorInt
        int backgroundColor = Integer.MIN_VALUE;

        Context context;

        Resources resources;

        BottomSheetListener listener;

        List<AppAdapter.AppInfo> apps;

        Intent shareIntent;

        @Nullable
        View view;

        /**
         * Constructor for creating a {@link BottomSheet}, {@link #setSheet(int)} will need to be called to set the menu resource
         *
         * @param context App context
         */
        public Builder(Context context) {
            this(context, NO_RESOURCE, R.style.BottomSheet);
        }

        /**
         * Constructor for creating a {@link BottomSheet}
         *
         * @param context    App context
         * @param sheetItems The menu resource for constructing the sheet
         */
        public Builder(Context context, @MenuRes int sheetItems) {
            this(context, sheetItems, R.style.BottomSheet);

        }

        /**
         * Constructor for creating a {@link BottomSheet}
         *
         * @param context    App context
         * @param sheetItems The menu resource for constructing the sheet
         * @param style      The style for the sheet to use
         */
        public Builder(Context context, @MenuRes int sheetItems, @StyleRes int style) {
            this.context = context;
            this.style = style;
            this.resources = context.getResources();
            if (sheetItems != NO_RESOURCE) setSheet(sheetItems);
        }

        /**
         * Sets the title of the {@link BottomSheet}
         *
         * @param title String for the title
         * @return
         */
        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the title of the {@link BottomSheet}
         *
         * @param title String resource for the title
         * @return
         */
        public Builder setTitle(@StringRes int title) {
            return setTitle(resources.getString(title));
        }

        /**
         * Sets the {@link BottomSheet} to use a grid for displaying options. When set, the dialog buttons <b><i>will not</i></b> be shown
         *
         * @return
         */
        public Builder grid() {
            isGrid = true;
            return this;
        }

        /**
         * Sets whether the {@link BottomSheet} is cancelable with the {@link KeyEvent#KEYCODE_BACK BACK} key.
         *
         * @param cancelable
         * @return
         */
        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        /**
         * Sets the {@link BottomSheetListener} to receive callbacks
         *
         * @param listener
         * @return
         */
        public Builder setListener(BottomSheetListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Sets the {@link BottomSheet} to use a dark theme
         *
         * @return
         */
        public Builder dark() {
            style = R.style.BottomSheet_Dark;
            return this;
        }

        /**
         * Sets the style of the {@link BottomSheet}
         *
         * @param style
         * @return
         */
        public Builder setStyle(@StyleRes int style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the menu resource to use for the {@link BottomSheet}
         *
         * @param sheetItems
         * @return
         */
        public Builder setSheet(@MenuRes int sheetItems) {
            BottomSheetMenu menu = new BottomSheetMenu(context);
            new MenuInflater(context).inflate(sheetItems, menu);
            return setMenu(menu);
        }

        /**
         * Sets the menu to use for the {@link BottomSheet}
         *
         * @param menu
         * @return
         */
        public Builder setMenu(@Nullable Menu menu) {
            if (menu != null) {
                List<MenuItem> items = new ArrayList<>(menu.size());

                for (int i = 0; i < menu.size(); i++) {
                    items.add(menu.getItem(i));
                }

                return setMenuItems(items);
            }

            return this;
        }

        /**
         * Sets the {@link List} of menu items to use for the {@link BottomSheet}
         *
         * @param menuItems
         * @return
         */
        public Builder setMenuItems(@Nullable List<MenuItem> menuItems) {
            this.menuItems = menuItems;
            return this;
        }

        /**
         * Adds a {@link MenuItem} to the {@link BottomSheet}. For creating a {@link MenuItem}, see {@link BottomSheetMenuItem}
         *
         * @param item
         * @return
         */
        public Builder addMenuItem(MenuItem item) {
            if (menuItems == null) menuItems = new ArrayList<>();
            menuItems.add(item);
            return this;
        }

        /**
         * Resolves the color resource id and tints the menu item icons with the resolved color
         *
         * @param colorRes
         * @return
         */
        public Builder setMenuItemTintColorRes(@ColorRes int colorRes) {
            final int menuItemTintColor = resources.getColor(colorRes);
            return setMenuItemTintColor(menuItemTintColor);
        }

        /**
         * Sets the color to use for tinting the menu item icons
         *
         * @param menuItemTintColor
         * @return
         */
        public Builder setMenuItemTintColor(@ColorInt int menuItemTintColor) {
            this.menuItemTintColor = menuItemTintColor;
            return this;
        }

        /**
         * Sets the color to use for the title. Will be ignored if {@link #setTitle(int)} or {@link #setTitle(String)} are not called
         *
         * @param titleColor
         * @return
         */
        public Builder setTitleColor(@ColorInt int titleColor) {
            this.titleColor = titleColor;
            return this;
        }

        /**
         * Sets the color resource id to use for the title. Will be ignored if {@link #setTitle(int)} or {@link #setTitle(String)} are not called
         *
         * @param colorRes
         * @return
         */
        public Builder setTitleColorRes(@ColorRes int colorRes) {
            return setTitleColor(resources.getColor(colorRes));
        }

        /**
         * Sets the color to use for the list item. Will apply to either list or grid style.
         *
         * @param itemColor
         * @return
         */
        public Builder setItemColor(@ColorInt int itemColor) {
            this.itemColor = itemColor;
            return this;
        }

        /**
         * Sets the color resource id to use for the list item. Will apply to either list or grid style.
         *
         * @param colorRes
         * @return
         */
        public Builder setItemColorRes(@ColorRes int colorRes) {
            return setItemColor(resources.getColor(colorRes));
        }

        /**
         * Sets the background color
         *
         * @param backgroundColor
         * @return
         */
        public Builder setBackgroundColor(@ColorInt int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        /**
         * Sets the color resource id of the background
         *
         * @param backgroundColor
         * @return
         */
        public Builder setBackgroundColorRes(@ColorRes int backgroundColor) {
            return setBackgroundColor(resources.getColor(backgroundColor));
        }

        /**
         * Sets the view the {@link BottomSheet} will show. If called, any attempt to add menu items will be ignored
         *
         * @param view The view to display
         * @return
         */
        public Builder setView(View view) {
            this.view = view;
            return this;
        }

        /**
         * Sets the view the {@link BottomSheet} will show. If called, any attempt to add menu items will be ignored
         *
         * @param view The view resource to display
         * @return
         */
        public Builder setView(@LayoutRes int view) {
            return setView(LayoutInflater.from(context).inflate(view, null));
        }

        /**
         * Sets the apps to be used for a share intent. This is not a public facing method.<p>
         * See {@link BottomSheet#createShareBottomSheet(Context, Intent, String, boolean)} for creating a share intent {@link BottomSheet}
         *
         * @param apps   List of apps to use in the share intent
         * @param intent The {@link Intent} used for creating the share intent
         * @return
         */
        private Builder setApps(List<AppAdapter.AppInfo> apps, Intent intent) {
            this.apps = apps;
            shareIntent = intent;
            return this;
        }

        /**
         * Creates the {@link BottomSheet} but does not show it.
         *
         * @return
         */
        public BottomSheet create() {
            return new BottomSheet(context, this, style, listener);
        }

        /**
         * Creates the {@link BottomSheet} and shows it.
         */
        public void show() {
            create().show();
        }
    }
}
