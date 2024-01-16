package com.arkivanov.sample.shared.counters

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.ChildStackValue
import com.arkivanov.decompose.router.stack.childStackValueOf
import com.arkivanov.sample.shared.PreviewComponentContext
import com.arkivanov.sample.shared.counters.counter.CounterComponent
import com.arkivanov.sample.shared.counters.counter.PreviewCounterComponent

class PreviewCountersComponent : CountersComponent, ComponentContext by PreviewComponentContext {

    override val childStack: ChildStackValue<*, CounterComponent> =
        childStackValueOf(
            ChildStack(
                configuration = Unit,
                instance = PreviewCounterComponent(),
            )
        )

    override fun onBackClicked() {}
    override fun onBackClicked(toIndex: Int) {}
}
