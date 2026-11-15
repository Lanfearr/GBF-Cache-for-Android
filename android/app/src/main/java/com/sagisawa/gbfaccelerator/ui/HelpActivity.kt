package com.sagisawa.gbfaccelerator.ui

import android.os.Bundle
import com.sagisawa.gbfaccelerator.R

class HelpActivity : BasePageActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        setupPageHeader(R.string.title_help)
    }
}
