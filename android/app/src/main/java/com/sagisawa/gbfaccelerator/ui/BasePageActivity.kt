package com.sagisawa.gbfaccelerator.ui

import androidx.appcompat.widget.Toolbar
import com.sagisawa.gbfaccelerator.R

abstract class BasePageActivity : BaseActivity() {

    protected fun setupPageHeader(titleRes: Int) {
        findViewById<Toolbar>(R.id.toolbar)?.apply {
            setTitle(titleRes)
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationContentDescription(R.string.navigate_back)
            setNavigationOnClickListener { finish() }
        }
    }
}
