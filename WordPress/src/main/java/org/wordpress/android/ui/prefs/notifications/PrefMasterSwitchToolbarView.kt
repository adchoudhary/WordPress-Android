package org.wordpress.android.ui.prefs.notifications

import android.R.color
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import org.wordpress.android.R
import org.wordpress.android.util.redirectContextClickToLongPressListener
import androidx.core.content.ContextCompat

/**
 * Custom view for master switch in toolbar for preferences.
 */
class PrefMasterSwitchToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyle, defStyleRes),
        OnCheckedChangeListener,
        OnLongClickListener,
        OnClickListener {
    private var backgroundStyle: Int? = null
    private var hintOn: String? = null
    private var hintOff: String? = null
    private var masterSwitch: SwitchCompat
    private var masterSwitchToolbarListener: MasterSwitchToolbarListener? = null
    private var toolbarSwitch: Toolbar
    private var titleOff: String? = null
    private var titleOn: String? = null

    val isMasterChecked: Boolean
        get() = masterSwitch.isChecked

    /**
     * Interface definition for callbacks to be invoked on interaction with master switch toolbar.
     */
    interface MasterSwitchToolbarListener {
        /**
         * Called when the checked state of master switch is changed.
         *
         * @param buttonView The master switch whose state has changed.
         * @param isChecked The new checked state of master switch.
         */
        fun onMasterSwitchCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean)
    }

    init {
        inflate(context, R.layout.preferences_master_switch_toolbar, this)

        toolbarSwitch = findViewById(R.id.toolbar_with_switch)
        toolbarSwitch.inflateMenu(R.menu.notifications_settings_secondary)

        val menuItem = toolbarSwitch.menu.findItem(R.id.master_switch)
        masterSwitch = menuItem.actionView as SwitchCompat
        masterSwitch.isChecked = true

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(
                    it,
                    R.styleable.PrefMasterSwitchToolbarView, 0, 0
            )
            try {
                val titleOn = typedArray.getString(R.styleable.PrefMasterSwitchToolbarView_masterTitleOn)
                val titleOff = typedArray.getString(R.styleable.PrefMasterSwitchToolbarView_masterTitleOff)
                val hintOn = typedArray.getString(R.styleable.PrefMasterSwitchToolbarView_masterHintOn)
                val hintOff = typedArray.getString(R.styleable.PrefMasterSwitchToolbarView_masterHintOff)
                val titleContentDescription = typedArray
                        .getString(R.styleable.PrefMasterSwitchToolbarView_masterContentDescription)
                val contentInsetStart = resources.getDimensionPixelSize(
                        typedArray.getResourceId(
                                R.styleable.PrefMasterSwitchToolbarView_masterContentInsetStart,
                                R.dimen.toolbar_content_offset
                        )
                )
                val masterOffsetEndResId = typedArray.getResourceId(
                        R.styleable.PrefMasterSwitchToolbarView_masterOffsetEnd,
                        -1
                )
                var masterOffsetEnd = -1
                if (masterOffsetEndResId != -1) {
                    masterOffsetEnd = resources.getDimensionPixelSize(
                            masterOffsetEndResId
                    )
                }
                val backgroundStyle = typedArray.getInt(R.styleable.PrefMasterSwitchToolbarView_backgroundStyle,
                        HIGHLIGHTED)

                setTitleOn(titleOn)
                setTitleOff(titleOff)
                setHintOn(hintOn)
                setHintOff(hintOff)
                setToolbarTitleContentDescription(titleContentDescription)
                setContentOffset(contentInsetStart)
                setMasterOffsetEnd(masterOffsetEnd)
                setBackgroundStyle(backgroundStyle)
            } finally {
                typedArray.recycle()
            }
        }

        masterSwitch.setOnCheckedChangeListener(this)
        toolbarSwitch.setOnLongClickListener(this)
        toolbarSwitch.setOnClickListener(this)

        ViewCompat.setLabelFor(toolbarSwitch, masterSwitch.id)
        setupFocusabilityForTalkBack()
        toolbarSwitch.redirectContextClickToLongPressListener()
    }

    fun setToolbarTitleContentDescription(titleContentDescription: String?) {
        titleContentDescription?.let {
            for (i in 0 until toolbarSwitch.childCount) {
                if (toolbarSwitch.getChildAt(i) is TextView) {
                    toolbarSwitch.getChildAt(i).contentDescription = titleContentDescription
                }
            }
        }
    }

    private fun setContentOffset(offset: Int) {
        toolbarSwitch.setContentInsetsAbsolute(offset, 0)
    }

    /**
     * Applies end padding to the switch menu
     */
    fun setMasterOffsetEnd(offsetEnd: Int) {
        if (offsetEnd != -1) {
            masterSwitch.setPaddingRelative(
                    masterSwitch.left,
                    masterSwitch.top,
                    offsetEnd,
                    masterSwitch.bottom
            )
        }
    }

    private fun setupFocusabilityForTalkBack() {
        masterSwitch.isFocusable = false
        masterSwitch.isClickable = false
        toolbarSwitch.isFocusableInTouchMode = false
        toolbarSwitch.isFocusable = true
        toolbarSwitch.isClickable = true
    }

    /**
     * Loads initial state of the master switch and toolbar
     */
    fun loadInitialState(checkMaster: Boolean) {
        masterSwitch.isChecked = checkMaster

        toolbarSwitch.title = if (checkMaster) {
            titleOn
        } else {
            titleOff
        }
        toolbarSwitch.visibility = View.VISIBLE
    }

    fun setTitleOn(titleOn: String?) {
        this.titleOn = titleOn ?: resources.getString(R.string.master_switch_default_title_on)
    }

    fun setTitleOff(titleOff: String?) {
        this.titleOff = titleOff ?: resources.getString(R.string.master_switch_default_title_off)
    }

    fun setHintOn(hintOn: String?) {
        this.hintOn = hintOn ?: resources.getString(R.string.master_switch_default_hint_on)
    }

    fun setHintOff(hintOff: String?) {
        this.hintOff = hintOff ?: resources.getString(R.string.master_switch_default_hint_off)
    }

    /**
     * Overrides style attributes for the switch and the toolbar
     *
     * @param backgroundStyle HIGHLIGHTED default style
     *                        NORMAL light style (recommended to be used on the dialog)
     */
    fun setBackgroundStyle(backgroundStyle: Int) {
        this.backgroundStyle = backgroundStyle

        when (backgroundStyle) {
            HIGHLIGHTED -> {} // Do nothing. Use default toolbar, switch style set in the xml
            NORMAL -> {
                val titleColor = ContextCompat.getColor(context, color.black)
                val backgroundColor = ContextCompat.getColor(context, color.white)
                val thumbColorList = ContextCompat.getColorStateList(context,
                        R.color.master_switch_normal_thumb_selector)
                val trackColorList = ContextCompat.getColorStateList(context,
                        R.color.master_switch_normal_track_selector)

                toolbarSwitch.setTitleTextColor(titleColor)
                toolbarSwitch.setBackgroundColor(backgroundColor)
                masterSwitch.thumbTintList = thumbColorList
                masterSwitch.trackTintList = trackColorList
            }
        }
    }

    /*
     * User long clicked the toolbar
     */
    override fun onLongClick(v: View?): Boolean {
        Toast.makeText(
                context,
                if (masterSwitch.isChecked) {
                    hintOn
                } else {
                    hintOff
                },
                Toast.LENGTH_SHORT
        ).show()
        return true
    }

    /*
    * User clicked the toolbar
    */
    override fun onClick(v: View?) {
        masterSwitch.isChecked = !masterSwitch.isChecked
    }

    /*
     * User toggled the master switch
     */
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        toolbarSwitch.title = if (isChecked) {
            titleOn
        } else {
            titleOff
        }

        masterSwitchToolbarListener?.onMasterSwitchCheckedChanged(buttonView, isChecked)
    }

    fun setMasterSwitchToolbarListener(masterSwitchToolbarListener: MasterSwitchToolbarListener) {
        this.masterSwitchToolbarListener = masterSwitchToolbarListener
    }

    companion object {
        // Types for the "backgroundStyle" XML parameter.
        private const val HIGHLIGHTED = 0
        private const val NORMAL = 1
    }
}
