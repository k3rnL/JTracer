package edaniel.jtracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class KDTree extends OpenCLObject {

    private List<KDNode> mNodes = new LinkedList<>();
    private List<Integer> mObjectId = new LinkedList<>();

    private final float mEmptyBonus;
    private final float mTraversalCost;
    private final float mIntersectionCost;

    @OpenCLField
    protected int mTotalSize;
    @OpenCLField
    protected int mNodeCount;
    @OpenCLField
    protected KDNode[] mKDNode;
    @OpenCLField
    protected Integer[]   mIndexes;

    public KDTree(List<IndexedBoundedObject> objects, float traversalCost, float intersectionCost, float emptyBonus) {
        mEmptyBonus = emptyBonus;
        mTraversalCost = traversalCost;
        mIntersectionCost = intersectionCost;

        build(objects, 0, 0);

        mTotalSize = 56 * mNodes.size() + 4 * mObjectId.size();
        mNodeCount = mNodes.size();
        mKDNode = mNodes.toArray(new KDNode[0]);
        mIndexes = mObjectId.toArray(new Integer[0]);

        init();
    }

    // return id of built node
    private Integer build(List<IndexedBoundedObject> objects, int depth, int badRefines) {

        // prepare a list of IndexedBounds sorted for each axis
        ArrayList<List<IndexedEdge>> sortedEdges = new ArrayList<>(3);
        for (int axis = 0; axis < 3; axis++) {
            sortedEdges.add(axis, new ArrayList<>(objects.size()));
            for (IndexedBoundedObject o : objects) {
                sortedEdges.get(axis).add(new IndexedEdge(o.getBoundingBox().mins[axis], o, EdgeType.START));
                sortedEdges.get(axis).add(new IndexedEdge(o.getBoundingBox().maxs[axis], o, EdgeType.END));
            }
            sortedEdges.get(axis).sort(new IndexedEdgeComparator());
        }

        KDNode node = new KDNode();
        Integer nodeId = mNodes.size();
        mNodes.add(node);

        // create bounding box around all objects
        BoundingBox boundingBox = objects.get(0).getBoundingBox();
        objects.forEach(o -> boundingBox.merge(o.getBoundingBox()));
        float[] boxDiagonal = boundingBox.diagonal();
        float totalSA = boundingBox.surfaceArea();
        float invTotalSA = 1f / totalSA;

        // Compute cost for all possible splits in the current area and keep the best
        int longestAxis = boundingBox.longestAxis().axis;
        float bestCost = 999999999;
        int bestAxis = -1;
        int bestEdgeIndex = -1;
        for (int axis : new int[]{longestAxis, (longestAxis + 1) % 3, (longestAxis + 2) % 3}) {
            int nBelow = 0, nAbove = objects.size(); // records number of primitive around the split
            List<IndexedEdge> indexedEdgesOnAxis = sortedEdges.get(axis);
            for (int i = 0; i < indexedEdgesOnAxis.size(); i++) {
                IndexedEdge edge = indexedEdgesOnAxis.get(i);
                if (edge.type == EdgeType.END) nAbove--;
                if (edge.pos > boundingBox.mins[axis] && edge.pos < boundingBox.maxs[axis]) {
                    // compute the surface area below and above the split
                    int axis1 = (axis + 1) % 3, axis2 = (axis + 2) % 3;
                    float belowSA = 2 *
                            (boxDiagonal[axis1] * boxDiagonal[axis2] +
                                    (edge.pos - boundingBox.mins[axis]) *
                                            (boxDiagonal[axis1] + boxDiagonal[axis2]));
                    float aboveSA = 2 *
                            (boxDiagonal[axis1] * boxDiagonal[axis2] +
                                    (boundingBox.maxs[axis] - edge.pos) *
                                            (boxDiagonal[axis1] + boxDiagonal[axis2]));
                    float pBelow = belowSA * invTotalSA;
                    float pAbove = aboveSA * invTotalSA;
                    float eb = (nAbove == 0 || nBelow == 0) ? mEmptyBonus : 0;
                    float cost = mTraversalCost + mIntersectionCost * (1 - eb) * (pBelow * nBelow + pAbove * nAbove);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestAxis = axis;
                        bestEdgeIndex = i;
                    }
                }
                if (edge.type == EdgeType.START) nBelow++;
            }

        }

        float maxCost = 4 * objects.size();
        if (bestCost > maxCost) badRefines++;
        if ((bestCost > 4 * maxCost && objects.size() < 16) || bestAxis == -1 || badRefines == 3) {
            // create a leaf
            node.bbox = boundingBox;
            node.index = mObjectId.size();
            node.count = objects.size();
            objects.forEach(o -> mObjectId.add(o.getIndex()));
            return nodeId;
        }

        // continue tree creation
        List<IndexedBoundedObject> belowObject = new LinkedList<>();
        List<IndexedBoundedObject> aboveObject = new LinkedList<>();

        for (int i = 0; i < bestEdgeIndex; i++) {
            IndexedEdge edge = sortedEdges.get(bestAxis).get(i);
            if (edge.type == EdgeType.START) {
                belowObject.add(edge.object);
            }
        }
        for (int i = bestEdgeIndex + 1; i < objects.size() * 2; i++) {
            IndexedEdge edge = sortedEdges.get(bestAxis).get(i);
            if (edge.type == EdgeType.END) {
                aboveObject.add(edge.object);
            }
        }

        node.splitAxis = bestAxis;
        node.splitPos = sortedEdges.get(bestAxis).get(bestEdgeIndex).pos;
        node.bbox = boundingBox;
        node.left = build(belowObject, depth + 1, badRefines);
        node.right = build(aboveObject, depth + 1, badRefines);

        return nodeId;
    }

    @Alignment(alignment = 64)
    public class KDNode extends OpenCLObject {
        @OpenCLField
        BoundingBox bbox = new BoundingBox();
        @OpenCLField
        Integer left = 0;
        @OpenCLField
        Integer right = 0;
        @OpenCLField
        Integer splitAxis = 0;
        @OpenCLField
        Float splitPos = 0f;
        @OpenCLField
        Integer index = 0;
        @OpenCLField
        Integer count = 0;

        public KDNode() {
            init();
        }

        @Override
        public void recreateOpenCLObject() {
            bbox.recreateOpenCLObject();
            super.recreateOpenCLObject();
        }
    }

    public class IndexedEdgeComparator implements Comparator<IndexedEdge> {

        @Override
        public int compare(IndexedEdge o1, IndexedEdge o2) {
            if (o1.pos == o2.pos)
                return o2.type.ordinal() - o1.type.ordinal();
            return Float.compare(o1.pos, o2.pos);
        }
    }

    public class IndexedEdge {
        float                   pos;
        IndexedBoundedObject    object;
        EdgeType                type;

        public IndexedEdge(float pos, IndexedBoundedObject object, EdgeType type) {
            this.pos = pos;
            this.object = object;
            this.type = type;
        }
    }

    public enum EdgeType {
        START,
        END
    }

    public interface IndexedBoundedObject {
        int getIndex();
        BoundingBox getBoundingBox();
    }
}
