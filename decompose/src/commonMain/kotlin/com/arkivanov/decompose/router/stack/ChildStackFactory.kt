package com.arkivanov.decompose.router.stack

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.children.ChildNavState.Status
import com.arkivanov.decompose.router.children.NavState
import com.arkivanov.decompose.router.children.NavigationSource
import com.arkivanov.decompose.router.children.SimpleChildNavState
import com.arkivanov.decompose.router.children.SimpleNavigation
import com.arkivanov.decompose.router.children.children
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.consumeRequired
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Initializes and manages a stack of components.
 *
 * @param source a source of navigation events.
 * @param serializer an optional [KSerializer] to be used for serializing and deserializing configurations.
 * If `null` then the navigation state will not be preserved.
 * @param initialStack a stack of component configurations (ordered from tail to head) that should be set
 * if there is no saved state, must be not empty and unique.
 * @param key a key of the stack, must be unique if there are multiple stacks in the same component.
 * @param handleBackButton determines whether the stack should be automatically popped on back button press or not,
 * default is `false`.
 * @param childFactory a factory function that creates new child instances.
 * @return an observable [Value] of [ChildStack].
 */
fun <C : Any, T : Any> ComponentContext.childStack(
    source: StackNavigationSource<C>,
    serializer: KSerializer<C>?,
    initialStack: () -> List<C>,
    key: String = "DefaultChildStack",
    handleBackButton: Boolean = false,
    childFactory: (configuration: C, ComponentContext) -> T,
): ChildStackValue<C, T> =
    childStack(
        source = source,
        saveStack = { stack ->
            if (serializer != null) {
                SerializableContainer(value = stack, strategy = ListSerializer(serializer))
            } else {
                null
            }
        },
        restoreStack = { container ->
            if (serializer != null) {
                container.consumeRequired(strategy = ListSerializer(serializer))
            } else {
                null
            }
        },
        initialStack = initialStack,
        key = key,
        handleBackButton = handleBackButton,
        childFactory = childFactory,
    )

/**
 * A convenience extension function for [ComponentContext.childStack].
 */
fun <C : Any, T : Any> ComponentContext.childStack(
    source: StackNavigationSource<C>,
    serializer: KSerializer<C>?,
    initialConfiguration: C,
    key: String = "DefaultChildStack",
    handleBackButton: Boolean = false,
    childFactory: (configuration: C, ComponentContext) -> T
): ChildStackValue<C, T> =
    childStack(
        source = source,
        serializer = serializer,
        initialStack = { listOf(initialConfiguration) },
        key = key,
        handleBackButton = handleBackButton,
        childFactory = childFactory,
    )

/**
 * Initializes and manages a stack of components.
 *
 * @param source a source of navigation events.
 * @param initialStack a stack of component configurations (ordered from tail to head) that should be set
 * if there is no saved state, must be not empty and unique.
 * @param saveStack a function that saves the provided stack of configurations into [SerializableContainer].
 * The navigation state is not saved if `null` is returned.
 * @param restoreStack a function that restores the stack of configuration from the provided [SerializableContainer].
 * If `null` is returned then [initialStack] is used instead.
 * The restored stack must have the same amount of configurations and in the same order.
 * @param key a key of the stack, must be unique if there are multiple stacks in the same component.
 * @param handleBackButton determines whether the stack should be automatically popped on back button press or not,
 * default is `false`.
 * @param childFactory a factory function that creates new child instances.
 * @return an observable [Value] of [ChildStack].
 */
fun <C : Any, T : Any> ComponentContext.childStack(
    source: StackNavigationSource<C>,
    initialStack: () -> List<C>,
    saveStack: (List<C>) -> SerializableContainer?,
    restoreStack: (SerializableContainer) -> List<C>?,
    key: String = "DefaultChildStack",
    handleBackButton: Boolean = false,
    childFactory: (configuration: C, ComponentContext) -> T,
): ChildStackValue<C, T> {
    val mergedSource = SimpleNavigation<NavEvent<C>>()

    val observer: (StackNavigationSource.Event<C>) -> Unit = { mergedSource.navigate(NavEvent.StackEvent(it)) }
    source.subscribe(observer)
    lifecycle.doOnDestroy { source.unsubscribe(observer) }

    val value: Value<ChildStack<C, T>> = children<C, T, NavEvent<C>, StackNavState<C>, ChildStack<C, T>>(
        source = mergedSource,
        key = key,
        initialState = { StackNavState(configurations = initialStack()) },
        saveState = { saveStack(it.configurations) },
        restoreState = { container -> StackNavState(configurations = restoreStack(container) ?: initialStack()) },
        navTransformer = { state, event ->
            when (event) {
                is NavEvent.StackEvent -> state.copy(configurations = event.event.transformer(state.configurations))
                is NavEvent.VisibilityHint -> state.copy(visibilityHint = event.configurations)
            }
        },
        stateMapper = { _, children ->
            @Suppress("UNCHECKED_CAST")
            val createdChildren = children as List<Child.Created<C, T>>

            ChildStack(
                active = createdChildren.last(),
                backStack = createdChildren.dropLast(1),
            )
        },
        onEventComplete = { event, newState, oldState ->
            when (event) {
                is NavEvent.StackEvent -> event.event.onComplete(newState.configurations, oldState.configurations)
                is NavEvent.VisibilityHint -> Unit //no-op
            }
        },
        backTransformer = { state ->
            if (handleBackButton && (state.configurations.size > 1)) {
                { StackNavState(configurations = state.configurations.dropLast(1)) }
            } else {
                null
            }
        },
        childFactory = childFactory,
    )

    return object : ChildStackValue<C, T>() {
        override fun onVisibilityHint(configurations: Set<C>) {
            mergedSource.navigate(NavEvent.VisibilityHint(configurations))
        }

        override val value: ChildStack<C, T> get() = value.value

        override fun subscribe(observer: (ChildStack<C, T>) -> Unit): Cancellation =
            value.subscribe(observer)
    }
}

private sealed interface NavEvent<C : Any> {
    class StackEvent<C : Any>(val event: StackNavigationSource.Event<C>) : NavEvent<C>
    class VisibilityHint<C : Any>(val configurations: Set<C>) : NavEvent<C>
}

private data class StackNavState<out C : Any>(
    val configurations: List<C>,
    val visibilityHint: Set<C> = emptySet(),
) : NavState<C> {

    init {
        require(configurations.isNotEmpty()) { "Configuration stack must not be empty" }
    }

    override val children: List<SimpleChildNavState<C>> =
        configurations.mapIndexed { index, configuration ->
            SimpleChildNavState(
                configuration = configuration,
                status = when {
                    index == configurations.lastIndex -> Status.RESUMED
                    configuration in visibilityHint -> Status.STARTED
                    else -> Status.CREATED
                },
            )
        }
}

private class MergedNavigationSource<T1 : Any, R : Any>(
    private val first: NavigationSource<T1>,
) : NavigationSource<R> {
    override fun subscribe(observer: (R) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun unsubscribe(observer: (R) -> Unit) {
        TODO("Not yet implemented")
    }
}
