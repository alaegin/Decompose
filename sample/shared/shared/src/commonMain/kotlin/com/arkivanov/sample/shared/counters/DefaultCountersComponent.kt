package com.arkivanov.sample.shared.counters

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStackValue
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arkivanov.sample.shared.counters.counter.CounterComponent
import com.arkivanov.sample.shared.counters.counter.DefaultCounterComponent
import kotlinx.serialization.Serializable

internal class DefaultCountersComponent(
    componentContext: ComponentContext,
) : CountersComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _childStack =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config(index = 0, isBackEnabled = false),
            childFactory = ::child,
        )

    override val childStack: ChildStackValue<*, CounterComponent> get() = _childStack

    private fun child(
        config: Config,
        componentContext: ComponentContext,
    ): CounterComponent =
        DefaultCounterComponent(
            componentContext = componentContext,
            title = "Counter ${config.index}",
            isBackEnabled = config.isBackEnabled,
            onNext = { navigation.push(Config(index = config.index + 1, isBackEnabled = true)) },
            onPrev = navigation::pop,
        )

    override fun onBackClicked() {
        navigation.pop()
    }

    override fun onBackClicked(toIndex: Int) {
        navigation.popTo(index = toIndex)
    }

    @Serializable
    private data class Config(
        val index: Int,
        val isBackEnabled: Boolean,
    )
}
