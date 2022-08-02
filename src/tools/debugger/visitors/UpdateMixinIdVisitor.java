package tools.debugger.visitors;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import som.compiler.MixinBuilder;

public class UpdateMixinIdVisitor implements NodeVisitor {
    private MixinBuilder.MixinDefinitionId mixinDefinitionId;

    public UpdateMixinIdVisitor(MixinBuilder.MixinDefinitionId mixinDefinitionId) {
        this.mixinDefinitionId = mixinDefinitionId;
    }

    @Override
    public boolean visit(Node node) {
        return true;
    }

    public MixinBuilder.MixinDefinitionId getMixinDefinitionId() {
        return mixinDefinitionId;
    }
}
