package edaniel.jtracer;

public abstract class SceneObject extends OpenCLObject {

    @OpenCLField
    protected int mType;
    @OpenCLField
    @Alignment(alignment = 16)
    protected float[] pos = new float[]{5,0,0};
    @OpenCLField
    @Alignment(alignment = 16)
    protected float[] color = new float[]{5,0,0};

    public SceneObject(Type type) {
        mType = type.ordinal();
    }

    abstract BoundingBox getBoundingBox();
    abstract float[]     getCentroid();

    enum Type {
        SPHERE,
        MESH
    }
}
