package edaniel.jtracer;

@Alignment(alignment = 64)
public class Sphere extends SceneObject {

    @OpenCLField
    protected float mSize;

    public Sphere(float size) {
        super(Type.SPHERE);
        mSize = size;
        init();
    }

    public Sphere() {
        this(10);
    }

    void setPosition(float x, float y, float z) {
        pos[0] = x;
        pos[1] = y;
        pos[2] = z;
    }

    public BoundingBox getBoundingBox() {
        BoundingBox bbox = new BoundingBox();
        bbox.setBounds(pos[0] - 10, pos[1] - 10, pos[2] - 10,
                        pos[0] + 10, pos[1] + 10, pos[2] + 10);
        return bbox;
    }

    @Override
    public float[] getCentroid() {
        return pos;
    }
}
