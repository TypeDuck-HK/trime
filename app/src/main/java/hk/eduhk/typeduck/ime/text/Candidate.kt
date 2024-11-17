package hk.eduhk.typeduck.ime.text

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.PaintDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import hk.eduhk.typeduck.R
import hk.eduhk.typeduck.core.CandidateListItem
import hk.eduhk.typeduck.core.Rime
import hk.eduhk.typeduck.data.AppPrefs
import hk.eduhk.typeduck.data.AppPrefs.Companion.defaultInstance
import hk.eduhk.typeduck.data.theme.Config
import hk.eduhk.typeduck.data.theme.FontManager.getTypeface
import hk.eduhk.typeduck.ime.core.Trime
import hk.eduhk.typeduck.ime.keyboard.Keyboard
import hk.eduhk.typeduck.ime.text.ComputedCandidate.Symbol
import hk.eduhk.typeduck.ime.text.ComputedCandidate.Word
import hk.eduhk.typeduck.util.GraphicUtils.drawText
import hk.eduhk.typeduck.util.GraphicUtils.measureText
import hk.eduhk.typeduck.util.dp2px
import java.lang.ref.WeakReference

/** 顯示候選字詞  */
class Candidate(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

	/** 處理候選條選字事件  */
	interface EventListener {
		fun onCandidatePressed(index: Int)
		fun onCandidateSymbolPressed(arrow: String)
		fun onCandidateLongClicked(index: Int)
		fun onCandidateSwipeDown(candidate: ComputedCandidate?)
	}

	companion object {
		const val maxCandidateCount = 30
		const val PAGE_UP_BUTTON = "◀"
		const val PAGE_DOWN_BUTTON = "▶"
		const val PAGE_EX_BUTTON = "▼"
	}

	private val infoIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_outline_info_24, null)
	private val highlightedInfoIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_info_24, null)
	private val expandIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_expand_more_24, null)
	private val swipeYThreshold = dp2px(80f) * Keyboard.adjustRatioSmall

	private var expectWidth = 0

	private var listener = WeakReference<EventListener?>(null)
	private var highlightIndex = -1
	private var candidates: Array<CandidateListItem>? = null
	private val computedCandidates = ArrayList<ComputedCandidate?>(maxCandidateCount)
	private var numCandidates = 0
	private var startNum = 0
	private val mainHandler = Handler(Looper.getMainLooper())
	private var longPressTimer: Runnable? = null
	private var beginPoint: Point? = null

	private var candidateHighlight: PaintDrawable? = null
	private val separatorPaint: Paint
	private val candidatePaint: TextPaint
	private val symbolPaint: TextPaint
	private val commentPaint: TextPaint
	private var candidateFont: Typeface? = null
	private var symbolFont: Typeface? = null
	private var commentFont: Typeface? = null
	private var candidateTextColor = 0
	private var hilitedCandidateTextColor = 0
	private var commentTextColor = 0
	private var hilitedCommentTextColor = 0
	private var candidateHeight = 0
	private var candidateViewPaddingTop = 0
	private var commentHeight = 0
	private var candidateSpacing = 0
	private var candidatePadding = 0
	private var candidateGap = 0
	private var shouldShowRomanization = true
	private var shouldShowReverseLookup = false
	private var candidateUseCursor = false
	private var infoIsHighlighted = false

	private val appPrefs: AppPrefs
		get() = defaultInstance()

	private val hasReverseLookup: Boolean
		get() = shouldShowReverseLookup && Rime.getCurrentRimeSchema() != "jyut6ping3"

	private val topCommentsHeight: Int
		get() = commentHeight * ((if (hasReverseLookup) 1 else 0) + (if (shouldShowRomanization) 1 else 0))

	fun reset() {
		val config = Config.get()
		candidateHighlight = PaintDrawable(config.colors.getColor("hilited_candidate_back_color"))
		candidateHighlight!!.setCornerRadius(config.style.getFloat("round_corner") * Keyboard.adjustRatio)
		separatorPaint.color = config.colors.getColor("candidate_separator_color")
		candidateSpacing = dp2px(config.style.getFloat("candidate_spacing")).toInt()
		candidatePadding = appPrefs.typeDuck.candidateGap.padding.toInt()
		candidateGap = appPrefs.typeDuck.candidateGap.gap.toInt()

		candidateTextColor = config.colors.getColor("candidate_text_color")
		commentTextColor = config.colors.getColor("comment_text_color")
		hilitedCandidateTextColor = config.colors.getColor("hilited_candidate_text_color")
		hilitedCommentTextColor = config.colors.getColor("hilited_comment_text_color")

		candidateViewPaddingTop = (dp2px(config.style.getFloat("candidate_view_padding_top")) * Keyboard.adjustRatioSmall).toInt()
		candidateFont = getTypeface(config.style.getString("candidate_font"))
		commentFont = getTypeface(config.style.getString("comment_font"))
		symbolFont = getTypeface(config.style.getString("symbol_font"))

		val fontSize = appPrefs.typeDuck.candidateFontSize.fontSize
		candidatePaint.apply { textSize = fontSize; typeface = candidateFont }
		symbolPaint.apply { textSize = fontSize; typeface = symbolFont }
		commentPaint.apply { textSize = fontSize / 1.8f; typeface = commentFont }
		candidateHeight = (fontSize * 1.4f).toInt()
		commentHeight = (fontSize * 0.7f).toInt()

		shouldShowRomanization = appPrefs.typeDuck.showRomanization
		shouldShowReverseLookup = appPrefs.typeDuck.showReverseLookup
		candidateUseCursor = config.style.getBoolean("candidate_use_cursor")
		invalidate()
	}

	init {
		candidatePaint = TextPaint()
		candidatePaint.isAntiAlias = true
		candidatePaint.strokeWidth = 0f
		symbolPaint = TextPaint()
		symbolPaint.isAntiAlias = true
		symbolPaint.strokeWidth = 0f
		commentPaint = TextPaint()
		commentPaint.isAntiAlias = true
		commentPaint.strokeWidth = 0f

		separatorPaint = Paint()
		separatorPaint.isAntiAlias = false
		separatorPaint.color = Color.BLACK

		// reset(context)

		setWillNotDraw(false)
	}

	fun setCandidateListener(listener: EventListener?) {
		this.listener = WeakReference(listener)
	}

	/**
	 * 刷新候選列表
	 *
	 * @param start 候選的起始編號
	 */
	fun setText(start: Int) {
		startNum = start
		removeHighlight()
		updateCandidateWidth()
		if (updateCandidates() > 0) {
			invalidate()
		}
	}

	fun setExpectWidth(expectWidth: Int) {
		this.expectWidth = expectWidth
	}

	/**
	 * 選取候選項
	 *
	 * @param index 候選項序號（從0開始），`-1`表示選擇當前高亮候選項
	 */
	private fun onCandidateClick(index: Int, isLongClick: Boolean) {
		val candidate: ComputedCandidate?
		if (index >= 0 && index < computedCandidates.size) {
			candidate = computedCandidates[index]
			if (candidate != null) {
				if (candidate is Word) {
					if (listener.get() != null) {
						if (isLongClick && appPrefs.keyboard.shouldLongClickDeleteCandidate) {
							listener.get()!!.onCandidateLongClicked(index + startNum)
						} else {
							listener.get()!!.onCandidatePressed(index + startNum)
						}
					}
				}
				if (candidate is Symbol) {
					val arrow = candidate.arrow
					if (listener.get() != null) {
						listener.get()!!.onCandidateSymbolPressed(arrow)
					}
				}
			}
		}
	}

	private fun removeHighlight() {
		highlightIndex = -1
		invalidate()
		requestLayout()
	}

	private fun isHighlighted(i: Int) = candidateUseCursor && i == highlightIndex

	private fun infoIsHighlighted(i: Int) = infoIsHighlighted && i == highlightIndex

	val highlightLeft: Int
		get() = if (highlightIndex < computedCandidates.size && highlightIndex >= 0) computedCandidates[highlightIndex]!!.geometry.left else 0
	val highlightRight: Int
		get() = if (highlightIndex < computedCandidates.size && highlightIndex >= 0) computedCandidates[highlightIndex]!!.geometry.right else 0

	override fun onDraw(canvas: Canvas) {
		if (candidates == null) return
		super.onDraw(canvas)

		for (computedCandidate in computedCandidates) {
			if (computedCandidate == null) continue
			val i = computedCandidates.indexOf(computedCandidate)
			val geometry = computedCandidate.geometry
			// Draw highlight
			if (isHighlighted(i))
				candidateHighlight!!.apply {
					bounds = geometry
					draw(canvas)
				}
			// Draw candidates
			val primaryColor = if (isHighlighted(i)) hilitedCandidateTextColor else candidateTextColor
			val secondaryColor = if (isHighlighted(i)) hilitedCommentTextColor else commentTextColor
			if (computedCandidate is Word) {
				val centerX = geometry.centerX().toFloat() - (if (computedCandidate.hasDictionaryEntry) commentHeight / 2f else 0f)
				if (hasReverseLookup && computedCandidate.isReverseLookup) {
					val note = computedCandidate.note
					if (note.isNotEmpty()) {
						val noteY = geometry.top + commentHeight / 2.0f -
								(commentPaint.ascent() + commentPaint.descent()) / 2
						commentPaint.color = primaryColor
						canvas.drawText(note, centerX, noteY, commentPaint, commentFont!!)
					}
				}
				if (shouldShowRomanization && computedCandidate.romanization.isNotEmpty()) {
					val romanY = geometry.top + (if (hasReverseLookup) commentHeight else 0) + commentHeight / 2.0f -
							(commentPaint.ascent() + commentPaint.descent()) / 2
					commentPaint.color = primaryColor
					canvas.drawText(computedCandidate.romanization, centerX, romanY, commentPaint, commentFont!!)
				}
				val word = computedCandidate.word
				val wordY = geometry.top + topCommentsHeight + candidateHeight / 2.0f -
						(candidatePaint.ascent() + candidatePaint.descent()) / 2
				candidatePaint.color = primaryColor
				canvas.drawText(word, centerX, wordY, candidatePaint, candidateFont!!)

				val definition = computedCandidate.entry?.mainLanguageOrLabel
				if (!definition.isNullOrEmpty()) {
					val definitionY = geometry.top + topCommentsHeight + candidateHeight + commentHeight / 2.0f -
							(commentPaint.ascent() + commentPaint.descent()) / 2
					commentPaint.color = if (computedCandidate.entry.isDictionaryEntry) primaryColor else secondaryColor
					val definitionWidth = geometry.width() + 1f - candidatePadding * 2f - (if (computedCandidate.hasDictionaryEntry) commentHeight / 2f else 0f)
					val trimmedDefinition = TextUtils.ellipsize(definition, commentPaint, definitionWidth, TextUtils.TruncateAt.END).toString()
					canvas.drawText(trimmedDefinition, centerX, definitionY, commentPaint, commentFont!!)
				}

				if (computedCandidate.hasDictionaryEntry) {
					(if (infoIsHighlighted(i)) highlightedInfoIcon else infoIcon)?.apply {
						setTint(primaryColor)
						setBounds(geometry.right - candidateHeight / 2, geometry.top, geometry.right, geometry.top + candidateHeight / 2)
						draw(canvas)
					}
					expandIcon?.apply {
						setTint(primaryColor)
						setBounds(geometry.right - candidateHeight * 2 / 3, geometry.top + candidateHeight * 3 / 8, geometry.right + candidateHeight / 6, geometry.top + candidateHeight * 3 / 4)
						draw(canvas)
					}
				}
			} else if (computedCandidate is Symbol) {
				// Draw page up / down buttons
				val arrow = computedCandidate.arrow
				val arrowX = geometry.centerX() - symbolPaint.measureText(arrow, symbolFont!!) / 2
				val arrowY = geometry.top + topCommentsHeight + candidateHeight / 2.0f -
						(candidatePaint.ascent() + candidatePaint.descent()) / 2
				symbolPaint.color = primaryColor
				canvas.drawText(arrow, arrowX, arrowY, symbolPaint)
			}
			// Draw separators
			if (i + 1 < computedCandidates.size) {
				canvas.drawRect(
					(geometry.right + candidateGap).toFloat(),
					geometry.top.toFloat(),
					(geometry.right + candidateGap + candidateSpacing).toFloat(),
					geometry.bottom.toFloat(),
					separatorPaint
				)
			}
		}
	}

	private fun updateCandidateWidth() {
		var hasExButton = false
		val pageEx = defaultInstance().keyboard.candidatePageSize.toInt() - 10000
		val pageBottomWidth =
			((candidateSpacing + symbolPaint.measureText(PAGE_DOWN_BUTTON, symbolFont!!)) + 2 * (candidatePadding + candidateGap)).toInt()
		val minWidth =
			if (pageEx > 2) (expectWidth * (pageEx / 10f + 1) - pageBottomWidth).toInt()
			else if (pageEx == 2) expectWidth - pageBottomWidth * 2
			else expectWidth - pageBottomWidth
		computedCandidates.clear()
		updateCandidates()
		var x = if (!Rime.hasLeft()) 0 else pageBottomWidth
		for (i in 0 until numCandidates) {
			val n = i + startNum
			val text = candidates!![n].text
			val comment = candidates!![n].comment
			val candidate = Word(text, comment)

			if (pageEx >= 0) {
				if (x >= minWidth) {
					computedCandidates.add(
						Symbol(
							PAGE_EX_BUTTON, Rect(x + candidateGap, candidateViewPaddingTop, x + pageBottomWidth - candidateGap, measuredHeight)
						)
					)
					x += pageBottomWidth
					hasExButton = true
					break
				}
			}
			var candidateWidth = 0f
			if (hasReverseLookup && candidate.isReverseLookup) {
				val note = candidate.note
				if (note.isNotEmpty()) {
					val noteWidth = commentPaint.measureText(note, commentFont!!)
					candidateWidth = candidateWidth.coerceAtLeast(noteWidth)
				}
			}
			if (shouldShowRomanization) {
				if (candidate.romanization.isNotEmpty()) {
					val romanWidth = commentPaint.measureText(candidate.romanization, commentFont!!)
					candidateWidth = candidateWidth.coerceAtLeast(romanWidth)
				}
			}
			if (text.isNotEmpty()) {
				val textWidth = commentPaint.measureText(text, candidateFont!!)
				candidateWidth = candidateWidth.coerceAtLeast(textWidth)
			}
			val definition = candidate.entry?.mainLanguageOrLabel
			if (!definition.isNullOrEmpty()) {
				val definitionWidth = commentPaint.measureText(definition, commentFont!!)
				candidateWidth = candidateWidth.coerceAtLeast(definitionWidth.coerceAtMost(candidateWidth + commentHeight * 4))
			}

			if (candidate.hasDictionaryEntry) {
				candidateWidth += commentHeight / 2
			}

			candidateWidth += 2 * (candidatePadding + candidateGap)

			// 自动填满候选栏，并保障展开候选按钮显示出来
			if (pageEx == 0 && x + candidateWidth + candidateSpacing > minWidth) {
				computedCandidates.add(
					Symbol(
						PAGE_EX_BUTTON, Rect(x + candidateGap, candidateViewPaddingTop, x + pageBottomWidth - candidateGap, measuredHeight)
					)
				)
				x += pageBottomWidth
				hasExButton = true
				break
			}

			candidate.geometry = Rect(x + candidateGap, candidateViewPaddingTop, x + candidateWidth.toInt() - candidateGap, measuredHeight)
			computedCandidates.add(candidate)
			x += candidateWidth.toInt() + candidateSpacing
		}
		if (Rime.hasLeft()) {
			computedCandidates.add(
				Symbol(
					PAGE_UP_BUTTON, Rect(candidateGap, candidateViewPaddingTop, pageBottomWidth - candidateGap, measuredHeight)
				)
			)
		}
		if (Rime.hasRight()) {
			computedCandidates.add(
				Symbol(
					PAGE_DOWN_BUTTON, Rect(x + candidateGap, candidateViewPaddingTop, x + pageBottomWidth - candidateGap, measuredHeight)
				)
			)
			x += pageBottomWidth
		}

		val params = layoutParams
		params.width = x
		params.height = candidateViewPaddingTop + topCommentsHeight + candidateHeight + commentHeight
		layoutParams = params

		Trime.getService().setCandidateExPage(hasExButton)
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		updateCandidateWidth()
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(me: MotionEvent): Boolean {
		val x = me.x.toInt()
		val y = me.y.toInt()
		when (me.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
		   		beginPoint = Point(x, y)
				isPressed = true
				highlightIndex = getCandidateIndex(x, y)
				longPressTimer = Runnable {
					onCandidateClick(highlightIndex, true)
					deselectCandidate()
					invalidate()
				}
				mainHandler.postDelayed(longPressTimer!!, appPrefs.keyboard.deleteCandidateTimeout.toLong())
				invalidate()
			}
			MotionEvent.ACTION_MOVE -> {
				if (!computedCandidates.indices.contains(highlightIndex) || beginPoint == null) return true
				if (y - beginPoint!!.y > swipeYThreshold) {
					listener.get()?.onCandidateSwipeDown(computedCandidates[highlightIndex])
					deselectCandidate()
				} else {
					computedCandidates[highlightIndex]?.geometry?.let {
						if (x < it.left || x > it.right || y < it.top) {
							deselectCandidate()
						} else if (y > it.bottom) {
							infoIsHighlighted = true
							clearLongPressTimer()
						} else {
							infoIsHighlighted = false
						}
					}
				}
				invalidate()
			}
			MotionEvent.ACTION_UP -> {
				if (computedCandidates.indices.contains(highlightIndex) && computedCandidates[highlightIndex]?.geometry?.contains(x, y) == true) {
					onCandidateClick(highlightIndex, false)
				}
				deselectCandidate()
				invalidate()
			}
			MotionEvent.ACTION_CANCEL -> {
				deselectCandidate()
				invalidate()
			}
		}
		return true
	}

	private fun deselectCandidate() {
		isPressed = false
		infoIsHighlighted = false
		highlightIndex = -1
		clearLongPressTimer()
		beginPoint = null
	}

	private fun clearLongPressTimer() {
		longPressTimer?.let {
			mainHandler.removeCallbacks(it)
			longPressTimer = null
		}
	}

	/**
	 * 獲得觸摸處候選項序號
	 *
	 * @param x 觸摸點橫座標
	 * @param y 觸摸點縱座標
	 * @return `>=0`: 觸摸點 (x, y) 處候選項序號，從0開始編號； `-1`: 觸摸點 (x, y) 處無候選項； `-4`: 觸摸點
	 * (x, y) 處爲`Page_Up`； `-5`: 觸摸點 (x, y) 處爲`Page_Down`
	 */
	private fun getCandidateIndex(x: Int, y: Int): Int {
		// val r = new Rect()
		var retIndex = -1
		for (computedCandidate in computedCandidates) {
			/*
			Enlarge the rectangle to be more responsive to user clicks.
			r.set(candidateRect[j++])
			r.inset(0, CANDIDATE_TOUCH_OFFSET)
			*/
			if (computedCandidate != null && computedCandidate.geometry.contains(x, y)) {
				retIndex = computedCandidates.indexOf(computedCandidate)
				break
			}
		}
		return retIndex
	}

	private fun updateCandidates(): Int {
		candidates = Rime.getCandidatesOrStatusSwitches()
		highlightIndex = -1
		numCandidates = if (candidates == null) 0 else candidates!!.size - startNum
		return numCandidates
	}
}
