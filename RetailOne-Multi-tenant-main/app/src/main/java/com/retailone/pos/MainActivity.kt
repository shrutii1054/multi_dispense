package com.retailone.pos

import com.retailone.pos.ui.Activity.LocalizedAppCompatActivity
import android.os.Bundle

class MainActivity : LocalizedAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}