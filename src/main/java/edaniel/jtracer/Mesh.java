package edaniel.jtracer;

import com.jogamp.opengl.math.VectorUtil;
import de.javagl.obj.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class Mesh extends SceneObject {

    @OpenCLField
    protected int   mNbTriangles;
    @OpenCLField
    protected int   mNbNormals;
    @OpenCLField
    protected float[] mTriangles;
    @OpenCLField
    protected float[] mNormals;
    @OpenCLField
    protected KDTree    mTree;
    private BoundingBox         mBoundingBox = new BoundingBox();

    public Mesh(String file) throws IOException {
        super(Type.MESH);
        ReadableObj readableObj = ObjReader.read(new FileInputStream(file));
        System.out.println("Loading "+readableObj.getNumFaces()+" faces");
        Obj obj = ObjUtils.convertToRenderable(readableObj);
        //obj = ObjUtils.triangulate(obj);

        mTriangles = new float[obj.getNumFaces() * 3 * 4];
        mNbTriangles = obj.getNumFaces();
        mNormals = new float[obj.getNumFaces() * 3 * 4];
        mNbNormals = obj.getNumFaces() * 3;

        List<Triangle> triangles = new ArrayList<>(obj.getNumFaces());
        for (int i = 0; i < obj.getNumFaces(); i++) {
            ObjFace face = obj.getFace(i);
            Triangle tr = new Triangle();

            FloatTuple v1 = obj.getVertex(face.getVertexIndex(0));
            tr.v1[0] = v1.getX(); tr.v1[1] = v1.getY(); tr.v1[2] = v1.getZ();
//            VectorUtil.scaleVec3(tr.v1, tr.v1, 10);

            FloatTuple v2 = obj.getVertex(face.getVertexIndex(1));
            tr.v2[0] = v2.getX(); tr.v2[1] = v2.getY(); tr.v2[2] = v2.getZ();
//            VectorUtil.scaleVec3(tr.v1, tr.v1, 10);

            FloatTuple v3 = obj.getVertex(face.getVertexIndex(2));
            tr.v3[0] = v3.getX(); tr.v3[1] = v3.getY(); tr.v3[2] = v3.getZ();
//            VectorUtil.scaleVec3(tr.v1, tr.v1, 10);

            mBoundingBox.merge(tr.getBoundingBox());

            triangles.add(tr);

            if (face.containsNormalIndices()) {
                for (int normalIndex = 0 ; normalIndex < 3 ; normalIndex++) {
                    FloatTuple tuple = obj.getNormal(face.getNormalIndex(normalIndex));
                    for (int tupleIndex = 0; tupleIndex < 3; tupleIndex++) {
                        mNormals[i * 3 * 4 + normalIndex * 4 + tupleIndex] = tuple.get(tupleIndex);
                    }
                }
            }
            else {
                float[] generatedNormal = new float[3];
                VectorUtil.crossVec3(generatedNormal, tr.v2, tr.v3);
                System.arraycopy(generatedNormal, 0, mNormals, i * 3 * 4, 3);
                System.arraycopy(generatedNormal, 0, mNormals, i * 3 * 4 + 4, 3);
                System.arraycopy(generatedNormal, 0, mNormals, i * 3 * 4 + 8, 3);
            }
        }

        mTree = new KDTree(new AbstractList<KDTree.IndexedBoundedObject>() {
            @Override
            public KDTree.IndexedBoundedObject get(int index) {
                return new KDTree.IndexedBoundedObject() {
                    BoundingBox bbox;

                    @Override
                    public int getIndex() {
                        return index;
                    }

                    @Override
                    public BoundingBox getBoundingBox() {
                        return triangles.get(index).getBoundingBox();
                    }
                };
            }

            @Override
            public int size() {
                return mTriangles.length / 3 / 4;
            }
        }, 10, 100, 0.5f);

        for (int i = 0; i < triangles.size(); i++) {
            Triangle tr = triangles.get(i);

            VectorUtil.subVec3(tr.v2, tr.v2, tr.v1);
            VectorUtil.subVec3(tr.v3, tr.v3, tr.v1);

            System.arraycopy(tr.v1, 0, mTriangles, i * 3 * 4    , 3);
            System.arraycopy(tr.v2, 0, mTriangles, i * 3 * 4 + 4, 3);
            System.arraycopy(tr.v3, 0, mTriangles, i * 3 * 4 + 8, 3);
        }

        init();
    }

    @Override
    BoundingBox getBoundingBox() {
        return mBoundingBox;
    }

    @Override
    float[] getCentroid() {
        return mBoundingBox.center;
    }
}
