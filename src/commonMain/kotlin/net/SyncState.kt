package net

import korlibs.inject.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import kotlinx.serialization.Serializable
import kotlin.reflect.*


//@Serializable
//data class State(
//    val id: String,
//    val prototype: String,
//    val props: Map<String, String>,
//)
//
//suspend fun View.sync(
//    id: String,
//    setters: Map<String, View.(String) -> Unit> = propSetters
//) {
//    injector().get<Client>().syncState(id) { props ->
//        updateFromProps(props)
//    }
//}
//
//fun View.updateFromProps(props: Map<String, String>) {
//    props.forEach { (name, value) ->
//        propSetters[name]?.invoke(this, value)
//    }
//}
//
//val View.propSetters: Map<String, View.(String) -> Unit>
//    get() = mapOf(
//        "alpha" to { alpha = it.toDouble() },
//        "rotation" to { rotation = it.toDouble().degrees },
//        "x" to { x = it.toDouble() },
//        "y" to { y = it.toDouble() },
//    )
//
//fun prop(prop: KMutableProperty0<Double>) {
//
//}
//
//fun setter(prop: KMutableProperty0<Double>): (String) -> Unit = {
//    prop.set(it.toDouble())
//}
