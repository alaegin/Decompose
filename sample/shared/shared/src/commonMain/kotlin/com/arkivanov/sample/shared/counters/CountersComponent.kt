package com.arkivanov.sample.shared.counters

import com.arkivanov.decompose.router.stack.ChildStackValue
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.arkivanov.sample.shared.counters.counter.CounterComponent

interface CountersComponent : BackHandlerOwner {

    val childStack: ChildStackValue<*, CounterComponent>

    fun onBackClicked()
    fun onBackClicked(toIndex: Int)
}
