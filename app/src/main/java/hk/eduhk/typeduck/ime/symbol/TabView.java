/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package hk.eduhk.typeduck.ime.symbol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import androidx.annotation.NonNull;
import hk.eduhk.typeduck.core.Rime;
import hk.eduhk.typeduck.data.AppPrefs;
import hk.eduhk.typeduck.data.theme.Config;
import hk.eduhk.typeduck.data.theme.FontManager;
import hk.eduhk.typeduck.ime.core.Trime;
import hk.eduhk.typeduck.ime.enums.SymbolKeyboardType;
import hk.eduhk.typeduck.ime.keyboard.Keyboard;
import hk.eduhk.typeduck.util.DimensionsKt;
import hk.eduhk.typeduck.util.GraphicUtils;
import java.util.ArrayList;
import timber.log.Timber;

// 这是滑动键盘顶部的view，展示了键盘布局的多个标签。
// 为了公用候选栏的皮肤参数以及外观，大部分代码从Candidate.java复制而来。
public class TabView extends View {

  private int highlightIndex;
  private ArrayList<TabTag> tabTags;

  private PaintDrawable candidateHighlight;
  private final Paint separatorPaint;
  private final Paint candidatePaint;
  private Typeface candidateFont;
  private int candidateTextColor, hilitedCandidateTextColor;
  private int candidateViewHeight, candidateViewPaddingTop, commentHeight, candidateSpacing, candidatePadding;
  private boolean shouldShowRomanization, shouldShowReverseLookup;
  private boolean shouldCandidateUseCursor;
  // private final Rect[] tabGeometries = new Rect[MAX_CANDIDATE_COUNT + 2];

  private boolean hasReverseLookup() {
    return shouldShowReverseLookup && Rime.getCurrentRimeSchema().equals("jyut6ping3");
  }

  private int getTopCommentsHeight() {
    return commentHeight * ((hasReverseLookup() ? 1 : 0) + (shouldShowRomanization ? 1 : 0));
  }

  public void reset() {
    Config config = Config.get();
    candidateHighlight = new PaintDrawable(config.colors.getColor("hilited_candidate_back_color"));
    candidateHighlight.setCornerRadius(config.style.getFloat("round_corner") * Keyboard.adjustRatio);

    separatorPaint.setColor(config.colors.getColor("candidate_separator_color"));

    candidateSpacing = (int) DimensionsKt.dp2px(config.style.getFloat("candidate_spacing"));
    candidatePadding = (int) AppPrefs.defaultInstance().getTypeDuck().getCandidateGap().getPadding();

    candidateTextColor = config.colors.getColor("candidate_text_color");
    hilitedCandidateTextColor = config.colors.getColor("hilited_candidate_text_color");

    final float candidateTextSize = AppPrefs.defaultInstance().getTypeDuck().getCandidateFontSize().getFontSize();
    candidateViewHeight = (int) (candidateTextSize * 1.4f);
    commentHeight = (int) (candidateTextSize * 0.7f);
    candidateViewPaddingTop = (int) (DimensionsKt.dp2px(config.style.getFloat("candidate_view_padding_top")) * Keyboard.adjustRatioSmall);

    candidateFont = FontManager.getTypeface(config.style.getString("candidate_font"));

    candidatePaint.setTextSize(candidateTextSize);
    candidatePaint.setTypeface(candidateFont);

    shouldShowRomanization = AppPrefs.defaultInstance().getTypeDuck().getShowRomanization();
    shouldShowReverseLookup = AppPrefs.defaultInstance().getTypeDuck().getShowReverseLookup();
    shouldCandidateUseCursor = config.style.getBoolean("candidate_use_cursor");
    invalidate();
  }

  public TabView(Context context, AttributeSet attrs) {
    super(context, attrs);
    candidatePaint = new Paint();
    candidatePaint.setAntiAlias(true);
    candidatePaint.setStrokeWidth(0);

    separatorPaint = new Paint();
    separatorPaint.setColor(Color.BLACK);
    reset();

    setWillNotDraw(false);
  }

  private boolean isHighlighted(int i) {
    return shouldCandidateUseCursor && i >= 0 && i == highlightIndex;
  }

  public int getHightlightLeft() {
    return tabTags.get(highlightIndex).geometry.left;
  }

  public int getHightlightRight() {
    return tabTags.get(highlightIndex).geometry.right;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (canvas == null) return;
    if (tabTags == null) return;
    super.onDraw(canvas);

    // Draw highlight background
    if (isHighlighted(highlightIndex)) {
      candidateHighlight.setBounds(tabTags.get(highlightIndex).geometry);
      candidateHighlight.draw(canvas);
    }
    // Draw tab text
    float tabY = tabTags.get(0).geometry.centerY()
            - (candidatePaint.ascent() + candidatePaint.descent()) / 2.0f;

    for (TabTag computedTab : tabTags) {
      int i = tabTags.indexOf(computedTab);
      // Calculate a position where the text could be centered in the rectangle.
      float tabX = computedTab.geometry.centerX();

      candidatePaint.setColor(isHighlighted(i) ? hilitedCandidateTextColor : candidateTextColor);
      GraphicUtils.drawText(canvas, computedTab.text, tabX, tabY, candidatePaint, candidateFont);
      // Draw the separator at the right edge of each candidate.
      canvas.drawRect(
          computedTab.geometry.right - candidateSpacing,
          computedTab.geometry.top,
          computedTab.geometry.right + candidateSpacing,
          computedTab.geometry.bottom,
          separatorPaint);
    }
  }

  public void updateTabWidth() {
    tabTags = TabManager.get().getTabCandidates();
    highlightIndex = TabManager.get().getSelected();

    int x = 0;
    for (TabTag computedTab : tabTags) {
      int i = tabTags.indexOf(computedTab);
      computedTab.geometry = new Rect(x, 0, (int) (x + getTabWidth(i)), getHeight());
      x += getTabWidth(i) + candidateSpacing;
    }
    LayoutParams params = getLayoutParams();
    Timber.i("update, from Height=" + params.height + " width=" + params.width);
    params.width = x;
    params.height = candidateViewPaddingTop + getTopCommentsHeight() + candidateViewHeight + commentHeight;
    Timber.i("update, to Height=" + candidateViewHeight + " width=" + x);
    setLayoutParams(params);
    params = getLayoutParams();
    Timber.i("update, reload Height=" + params.height + " width=" + params.width);
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateTabWidth();
    Timber.i("onSizeChanged() w=" + w + ", Height=" + oldh + "=>" + h);
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  int x0, y0;
  long time0;

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent me) {
    int x = (int) me.getX();
    int y = (int) me.getY();

    switch (me.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        x0 = x;
        y0 = y;
        time0 = System.currentTimeMillis();
        //        updateHighlight(x, y);
        break;
      case MotionEvent.ACTION_MOVE:
        if (Math.abs(x - x0) > 100) time0 = 0;
        break;
      case MotionEvent.ACTION_UP:
        int i = getTabIndex(x, y);
        if (i > -1) {
          performClick();
          TabTag tag = TabManager.getTag(i);
          if (tag.type == SymbolKeyboardType.NO_KEY) {
            switch (tag.command) {
              case EXIT:
                Trime.getService().selectLiquidKeyboard(-1);
                break;
                // TODO liquidKeyboard中除返回按钮外，其他按键均未实装
              case DEL_LEFT:
              case DEL_RIGHT:
              case REDO:
              case UNDO:
                break;
            }
          } else if (System.currentTimeMillis() - time0 < 500) {
            highlightIndex = i;
            invalidate();
            Trime.getService().selectLiquidKeyboard(i);
          }
          Timber.d("index=" + i + " length=" + tabTags.size());
        }
        break;
    }
    return true;
  }

  /**
   * 獲得觸摸處候選項序號
   *
   * @param x 觸摸點橫座標
   * @param y 觸摸點縱座標
   * @return {@code >=0}: 觸摸點 (x, y) 處候選項序號，從0開始編號； {@code -1}: 觸摸點 (x, y) 處無候選項；
   */
  private int getTabIndex(int x, int y) {
    // Rect r = new Rect();
    int retIndex = -1; // Returns -1 if there is no tab in the hitting rectangle.
    for (TabTag computedTab : tabTags) {
      /* Enlarge the rectangle to be more responsive to user clicks.
      // r.set(tabGeometries[j++]);
      //r.inset(0, CANDIDATE_TOUCH_OFFSET); */
      if (computedTab.geometry.contains(x, y)) {
        retIndex = tabTags.indexOf(computedTab);
      }
    }
    return retIndex;
  }

  private float getTabWidth(int i) {
    String s = tabTags.get(i).text;
    return s != null
        ? 2 * candidatePadding + GraphicUtils.measureText(candidatePaint, s, candidateFont)
        : 2 * candidatePadding;
  }
}
