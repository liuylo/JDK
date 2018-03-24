/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package org.graalvm.compiler.loop;

import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.DuplicationReplacement;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.util.EconomicSet;

public class LoopFragmentWhole extends LoopFragment {

    public LoopFragmentWhole(LoopEx loop) {
        super(loop);
    }

    public LoopFragmentWhole(LoopFragmentWhole original) {
        super(null, original);
    }

    @Override
    public LoopFragmentWhole duplicate() {
        LoopFragmentWhole loopFragmentWhole = new LoopFragmentWhole(this);
        loopFragmentWhole.reify();
        return loopFragmentWhole;
    }

    private void reify() {
        assert this.isDuplicate();

        patchNodes(null);

        mergeEarlyExits();
    }

    @Override
    public NodeBitMap nodes() {
        if (nodes == null) {
            Loop<Block> loop = loop().loop();
            nodes = LoopFragment.computeNodes(graph(), LoopFragment.toHirBlocks(loop.getBlocks()), LoopFragment.toHirExits(loop.getExits()));
        }
        return nodes;
    }

    @Override
    protected ValueNode prim(ValueNode b) {
        return getDuplicatedNode(b);
    }

    @Override
    protected DuplicationReplacement getDuplicationReplacement() {
        final FixedNode entry = loop().entryPoint();
        final Graph graph = this.graph();
        return new DuplicationReplacement() {

            private EndNode endNode;

            @Override
            public Node replacement(Node o) {
                if (o == entry) {
                    if (endNode == null) {
                        endNode = graph.add(new EndNode());
                    }
                    return endNode;
                }
                return o;
            }
        };
    }

    public FixedNode entryPoint() {
        if (isDuplicate()) {
            LoopBeginNode newLoopBegin = getDuplicatedNode(original().loop().loopBegin());
            return newLoopBegin.forwardEnd();
        }
        return loop().entryPoint();
    }

    @Override
    protected void finishDuplication() {
        // TODO (gd) ?
    }

    void cleanupLoopExits() {
        LoopBeginNode loopBegin = original().loop().loopBegin();
        assert nodes == null || nodes.contains(loopBegin);
        StructuredGraph graph = loopBegin.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
            // After FrameStateAssignment ControlFlowGraph treats loop exits differently which means
            // that the LoopExitNodes can be in a block which post dominates the true loop exit. For
            // cloning to work right they must agree.
            EconomicSet<LoopExitNode> exits = EconomicSet.create();
            for (Block exitBlock : original().loop().loop().getExits()) {
                LoopExitNode exitNode = exitBlock.getLoopExit();
                if (exitNode == null) {
                    exitNode = graph.add(new LoopExitNode(loopBegin));
                    graph.addAfterFixed(exitBlock.getBeginNode(), exitNode);
                    if (nodes != null) {
                        nodes.mark(exitNode);
                    }
                    graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Adjusting loop exit node for %s", loopBegin);
                }
                exits.add(exitNode);
            }
            for (LoopExitNode exitNode : loopBegin.loopExits()) {
                if (!exits.contains(exitNode)) {
                    if (nodes != null) {
                        nodes.clear(exitNode);
                    }
                    graph.removeFixed(exitNode);
                }
            }
        }

    }

    @Override
    protected void beforeDuplication() {
        cleanupLoopExits();
    }

    @Override
    public void insertBefore(LoopEx loop) {
        // TODO Auto-generated method stub

    }
}