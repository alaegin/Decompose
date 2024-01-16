package com.arkivanov.decompose.router.stack

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.value.Value

abstract class ChildStackValue<C : Any, T : Any> : Value<ChildStack<C, T>>() {

    abstract fun onVisibilityHint(configurations: Set<C>)
}

fun <C : Any, T : Any> childStackValueOf(stack: ChildStack<C, T>): ChildStackValue<C, T> =
    object : ChildStackValue<C, T>() {
        override val value: ChildStack<C, T> = stack

        override fun onVisibilityHint(configurations: Set<C>) {}

        override fun subscribe(observer: (ChildStack<C, T>) -> Unit): Cancellation {
            observer(stack)
            return Cancellation {}
        }
    }
