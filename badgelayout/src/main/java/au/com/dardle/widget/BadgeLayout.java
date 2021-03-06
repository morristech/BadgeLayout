/*
 * Copyright (C) 2016 Dardle Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.com.dardle.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pools;
import android.support.v7.widget.TintTypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * BadgeLayout provides a horizontal layout to display badges.
 * <p>
 * <p>Population of the badges to display is
 * done through {@link Badge} instances. You create badges via {@link #newBadge()}. From there you can
 * change the badge's label or icon via {@link Badge#setText(CharSequence)} and {@link Badge#setIcon(Drawable)}
 * respectively. To display the badge, you need to add it to the layout via one of the
 * {@link #addBadge(Badge)} methods. For example:
 * <pre>
 * BadgeLayout badgeLayout = ...;
 * badgeLayout.addBadge(badgeLayout.newBadge().setText("Badge 1"));
 * badgeLayout.addBadge(badgeLayout.newBadge().setText("Badge 2"));
 * badgeLayout.addBadge(badgeLayout.newBadge().setText("Badge 3"));
 * </pre>
 * You should set a listener via {@link #addOnBadgeClickedListener(OnBadgeClickedListener)} to be
 * notified when any badge is clicked.
 * <p>
 * <p>You can also add items to BadgeLayout in your layout through the use of {@link BadgeItem}.
 * An example usage is like so:</p>
 * <p>
 * <pre>
 * &lt;au.com.dardle.widget.BadgeLayout
 *         android:layout_height=&quot;wrap_content&quot;
 *         android:layout_width=&quot;match_parent&quot;&gt;
 *
 *     &lt;au.com.dardle.widget.BadgeItem
 *             android:text=&quot;@string/badge_text&quot;/&gt;
 *
 *     &lt;au.com.dardle.widget.BadgeItem
 *             android:icon=&quot;@drawable/ic_android&quot;/&gt;
 *
 * &lt;/au.com.dardle.widget.BadgeLayout&gt;
 * </pre>
 */
public class BadgeLayout extends FrameLayout {
    private static final Pools.Pool<Badge> sBadgePool = new Pools.SynchronizedPool<>(16);
    private final Pools.Pool<BadgeView> mBadgeViewPool = new Pools.SimplePool<>(12);
    private final ArrayList<Badge> mBadges = new ArrayList<>();
    private final ArrayList<OnBadgeClickedListener> mOnBadgeClickedListeners = new ArrayList<>();
    private final OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view instanceof BadgeView) {
                BadgeView badgeView = (BadgeView) view;
                Badge badge = badgeView.mBadge;

                for (int i = mOnBadgeClickedListeners.size() - 1; i >= 0; i--) {
                    mOnBadgeClickedListeners.get(i).onBadgeClicked(badge);
                }
            }
        }
    };

    private int mSpacing;

    private int mBadgeContentSpacing;
    private int mBadgeBackgroundResId;
    private BadgeTextPosition mBadgeTextPosition;

    private ColorStateList mBadgeTextColors;
    private int mBadgeTextSize;

    public interface OnBadgeClickedListener {
        void onBadgeClicked(Badge badge);
    }

    @NonNull
    private final LinearLayout mContentContainer;

    public BadgeLayout(Context context) {
        this(context, null);
    }

    public BadgeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BadgeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Add the content linear layout
        mContentContainer = new LinearLayout(context);
        mContentContainer.setOrientation(LinearLayout.HORIZONTAL);
        mContentContainer.setGravity(Gravity.CENTER_VERTICAL);
        super.addView(mContentContainer, 0, new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams
                .WRAP_CONTENT));

        final TintTypedArray tintTypedArray = TintTypedArray.obtainStyledAttributes(context, attrs, R.styleable
                .BadgeLayout);
        mSpacing = tintTypedArray.getDimensionPixelSize(R.styleable.BadgeLayout_spacing, 8);
        mBadgeContentSpacing = tintTypedArray.getDimensionPixelSize(R.styleable.BadgeLayout_badgeContentSpacing, 0);
        mBadgeBackgroundResId = tintTypedArray.getResourceId(R.styleable.BadgeLayout_badgeBackground, 0);
        mBadgeTextPosition = BadgeTextPosition.values()[tintTypedArray.getInt(R.styleable.BadgeLayout_badgeTextPosition, BadgeTextPosition.BOTTOM.ordinal())];

        // Badge text color
        if (tintTypedArray.hasValue(R.styleable.BadgeLayout_badgeTextColor)) {
            mBadgeTextColors = tintTypedArray.getColorStateList(R.styleable.BadgeLayout_badgeTextColor);
        }

        // Badge text size
        mBadgeTextSize = tintTypedArray.getDimensionPixelSize(R.styleable.BadgeLayout_badgeTextSize, -1);

        tintTypedArray.recycle();
    }

    @Override
    public void addView(View child) {
        addViewInternal(child);
    }

    @Override
    public void addView(View child, int index) {
        addViewInternal(child);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        addViewInternal(child);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        addViewInternal(child);
    }

    @NonNull
    public Badge newBadge() {
        // Get a Badge object from pool or manual creation
        Badge badge = sBadgePool.acquire();
        if (badge == null) {
            badge = new Badge();
        }

        // Set badge's parent view
        badge.mParent = this;

        // Bind the view to the badge
        badge.mView = createBadgeView(badge);

        return badge;
    }

    public void addBadge(@NonNull Badge badge) {
        if (badge.mParent != this) {
            throw new IllegalArgumentException("Badge belongs to a different BadgeLayout");
        }

        // Add badge's view as a child view
        addBadgeView(badge);
        configureBadge(badge);
    }

    public void removeAllBadges() {
        // Remove all badge views
        mContentContainer.removeAllViews();

        // Remove all badges
        mBadges.clear();
    }

    public void setSpacing(int spacing) {
        mSpacing = spacing;
        for (int i = 1; i < mBadges.size(); i++) {
            Badge badge = mBadges.get(i);
            View view = badge.mView;
            if (view != null) {
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
                layoutParams.leftMargin = mSpacing;
            }
        }
    }

    public void setBadgeContentSpacing(int badgeContentSpacing) {
        mBadgeContentSpacing = badgeContentSpacing;

        updateBadges();
    }

    public void setBadgeBackground(int badgeBackgroundResId) {
        mBadgeBackgroundResId = badgeBackgroundResId;

        updateBadges();
    }

    public void setBadgeTextPosition(BadgeTextPosition badgeTextPosition) {
        mBadgeTextPosition = badgeTextPosition;

        updateBadges();
    }

    public void setBadgeTextColor(ColorStateList badgeTextColor) {
        mBadgeTextColors = badgeTextColor;

        updateBadges();
    }

    public void addOnBadgeClickedListener(@NonNull OnBadgeClickedListener onBadgeClickedListener) {
        if (!mOnBadgeClickedListeners.contains(onBadgeClickedListener)) {
            mOnBadgeClickedListeners.add(onBadgeClickedListener);
        }
    }

    public void removeOnBadgeClickedListener(@NonNull OnBadgeClickedListener onBadgeClickedListener) {
        mOnBadgeClickedListeners.remove(onBadgeClickedListener);
    }


    private void addViewInternal(final View child) {
        if (child instanceof BadgeItem) {
            // Add badge from BadgeItem object in XML
            addBadgeFromItemView((BadgeItem) child);
        } else {
            throw new IllegalArgumentException("Only BadgeItem instances can be added to BadgeLayout");
        }
    }

    private void addBadgeFromItemView(@NonNull BadgeItem badgeItem) {
        // Get a badge object
        final Badge badge = newBadge();

        // Set text
        badge.setText(badgeItem.mText);

        // Set icon
        badge.setIcon(badgeItem.mIcon);

        // Set enabled status
        badge.setEnabled(badgeItem.isEnabled());

        // Set selected status
        badge.setSelected(badgeItem.isSelected());

        addBadge(badge);
    }

    private BadgeView createBadgeView(@NonNull final Badge badge) {
        // Get a badge view from pool or manually create
        BadgeView badgeView = mBadgeViewPool.acquire();
        if (badgeView == null) {
            badgeView = new BadgeView(getContext());
        }

        // Bind badge to the view
        badgeView.setBadge(badge);

        return badgeView;
    }

    private void addBadgeView(Badge badge) {
        final BadgeView badgeView = badge.mView;
        if (badgeView != null) {
            if (badgeView.getParent() != null) {
                // Remove itself from parent
                mContentContainer.removeView(badgeView);
            }

            // Add view
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (mContentContainer.getChildCount() > 0) {
                layoutParams.leftMargin = mSpacing;
            }

            badgeView.setLayoutParams(layoutParams);
            mContentContainer.addView(badgeView);
        }
    }

    private void configureBadge(Badge badge) {
        // Add this badge to the list
        mBadges.add(badge);
        if (badge.mView != null) {
            badge.mView.setOnClickListener(mClickListener);
        }
    }

    private void updateBadges() {
        for (Badge badge : mBadges) {
            badge.updateView();
        }
    }


    /**
     * The Badge
     */
    public static final class Badge {
        // The icon
        @Nullable
        private Drawable mIcon;

        // The text
        @Nullable
        private CharSequence mText;

        // Selected status, default is un-selected
        private boolean mSelected = false;

        // Enabled status, default is disabled
        private boolean mEnabled = true;

        @Nullable
        private BadgeLayout mParent;
        @Nullable
        private BadgeView mView;

        @NonNull
        public Badge setText(@Nullable CharSequence text) {
            // Set text and update associated view
            mText = text;
            updateView();
            return this;
        }

        @NonNull
        public Badge setIcon(@Nullable Drawable icon) {
            // Set icon and update associated view
            mIcon = icon;
            updateView();
            return this;
        }

        @NonNull
        public Badge setSelected(boolean selected) {
            // Set selected status and update associated view
            mSelected = selected;
            updateView();
            return this;
        }

        @NonNull
        public Badge setEnabled(boolean enabled) {
            // Set enabled status and update associated view
            mEnabled = enabled;
            updateView();
            return this;
        }

        @Nullable
        public CharSequence getText() {
            return mText;
        }

        private void updateView() {
            if (mView != null) {
                mView.update();
            }
        }
    }

    public enum BadgeTextPosition {
        LEFT, TOP, RIGHT, BOTTOM
    }

    /**
     * The default badge view
     */
    private class BadgeView extends LinearLayout {
        @Nullable
        private Badge mBadge;

        @NonNull
        private final ImageView mImageView;
        @NonNull
        private final TextView mTextView;

        public BadgeView(Context context) {
            super(context);

            // Create the image view, and setup it's default parameters
            mImageView = new ImageView(context);

            // Create the text view, and setup it's default parameters
            mTextView = new TextView(context);
            mTextView.setLines(1);
            mTextView.setEllipsize(TextUtils.TruncateAt.END);

            // Update views based on the badge content
            update();
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);

            // Set child views' selected status
            mImageView.setSelected(selected);
            mTextView.setSelected(selected);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);

            // Set child views' enabled status
            mImageView.setEnabled(enabled);
            mTextView.setEnabled(enabled);
        }

        private void setBadge(Badge badge) {
            // Bind the badge object to the view and update using this badge's content
            mBadge = badge;
            update();
        }

        private void update() {
            updateLayout();
            updateContent();
        }

        private void updateLayout() {
            // Default gravity to center
            setGravity(Gravity.CENTER);

            // Setup background
            setBackgroundResource(mBadgeBackgroundResId);

            // Set orientation
            if (mBadgeTextPosition == BadgeTextPosition.LEFT || mBadgeTextPosition == BadgeTextPosition.RIGHT) {
                setOrientation(HORIZONTAL);
            } else {
                setOrientation(VERTICAL);
            }

            // Add views
            removeAllViews();

            int tvIndex = 0;
            int ivIndex = 0;
            LayoutParams tvLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            LayoutParams ivLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            switch (mBadgeTextPosition) {
                case LEFT:
                    ivIndex = 1;
                    tvIndex = 0;
                    tvLayoutParams.rightMargin = mBadgeContentSpacing;
                    break;

                case TOP:
                    ivIndex = 1;
                    tvIndex = 0;
                    tvLayoutParams.bottomMargin = mBadgeContentSpacing;
                    break;

                case RIGHT:
                    ivIndex = 0;
                    tvIndex = 1;
                    tvLayoutParams.leftMargin = mBadgeContentSpacing;
                    break;

                case BOTTOM:
                    ivIndex = 0;
                    tvIndex = 1;
                    tvLayoutParams.topMargin = mBadgeContentSpacing;
                    break;
            }

            addView(mImageView, ivIndex, ivLayoutParams);
            addView(mTextView, tvIndex, tvLayoutParams);
        }

        private void updateContent() {
            if (mBadge != null) {
                // Setup image
                mImageView.setImageDrawable(mBadge.mIcon);

                // Setup text
                mTextView.setText(mBadge.mText);
                if (mBadgeTextColors != null) {
                    mTextView.setTextColor(mBadgeTextColors);
                }
                if (mBadgeTextSize != -1) {
                    mTextView.setTextSize(mBadgeTextSize);
                }
                if (TextUtils.isEmpty(mTextView.getText())) {
                    mTextView.setVisibility(GONE);
                } else {
                    mTextView.setVisibility(VISIBLE);
                }

                // Set status
                setSelected(mBadge.mSelected);
                setEnabled(mBadge.mEnabled);
            }
        }
    }
}
