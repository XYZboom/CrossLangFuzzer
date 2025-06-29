

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.IrVisitorVoid

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.rootElement]
 */
interface IrElement {
    /**
     * Runs the provided [visitor] on the IR subtree with the root at this node.
     *
     * @param visitor The visitor to accept.
     * @param data An arbitrary context to pass to each invocation of [visitor]'s methods.
     * @return The value returned by the topmost `visit*` invocation.
     */
    fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitElement(this, data)

    /**
     * Runs the provided [transformer] on the IR subtree with the root at this node.
     *
     * @param transformer The transformer to use.
     * @param data An arbitrary context to pass to each invocation of [transformer]'s methods.
     * @return The transformed node.
     */
    @Suppress("UNCHECKED_CAST")
    fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformElement(this, data) as E

    /**
     * Runs the provided [visitor] on the IR subtree with the root at this node.
     *
     * @param visitor The visitor to accept.
     */
    fun accept(visitor: IrVisitorVoid) {
        accept(visitor, null)
    }

    /**
     * Runs the provided [visitor] on subtrees with roots in this node's children.
     *
     * Basically, calls `accept(visitor, data)` on each child of this node.
     *
     * Does **not** run [visitor] on this node itself.
     *
     * @param visitor The visitor for children to accept.
     * @param data An arbitrary context to pass to each invocation of [visitor]'s methods.
     */
    fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D)

    /**
     * Runs the provided [visitor] on subtrees with roots in this node's children.
     *
     * Basically, calls `accept(visitor)` on each child of this node.
     *
     * Does **not** run [visitor] on this node itself.
     *
     * @param visitor The visitor for children to accept.
     */
    fun acceptChildren(visitor: IrVisitorVoid) {
        acceptChildren(visitor, null)
    }

    /**
     * Recursively transforms this node's children *in place* using [transformer].
     *
     * Basically, executes `this.child = this.child.transform(transformer, data)` for each child of this node.
     *
     * Does **not** run [transformer] on this node itself.
     *
     * @param transformer The transformer to use for transforming the children.
     * @param data An arbitrary context to pass to each invocation of [transformer]'s methods.
     * @return `this`
     */
    fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrElement
}
